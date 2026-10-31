package npclaude.combat;

import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.ai.NpcBehavior;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * Fight or flight. Built into every NPC as an instinct, so it overrides follow/stay/wander while danger lasts.
 *
 * The NPC weighs the danger around it ({@link ThreatAssessment}) against its courage, which comes from its bravery.
 * Below its courage it fights: in melee or with a bow, depending on its combat style and gear. Above it, it runs;
 * when the danger is only moderately over its courage it still swings at anything that catches up (a fighting
 * retreat), and when it's far over it just runs.
 */
public class CombatBehavior implements NpcBehavior {
	public static final String ID = "combat";
	public static final int PRIORITY = 100;
	private static final int REASSESS_INTERVAL = 10;
	private static final int REPATH_INTERVAL = 10;
	private static final int FLEE_REPATH_INTERVAL = 20;
	/**
	 * Once in a fight (fighting or fleeing), hostiles are tracked out to this distance instead of the usual scan radius:
	 * runners keep pursuers in mind, and fighters keep chasing a target that retreats.
	 */
	private static final double ENGAGED_SCAN_RADIUS = 20.0;
	/** Ticks of straight running when no escape path can be found. */
	private static final int PANIC_RUN_TICKS = 20;
	private static final double MELEE_RANGE = 2.2;
	private static final double FLEE_DISTANCE = 10.0;
	/** Once running, keep running until danger drops this far below courage, so the NPC doesn't flip-flop. */
	private static final double CALM_DOWN_FACTOR = 0.8;
	/** Running from danger below this multiple of courage, the NPC still hits whatever is in reach. */
	private static final double PANIC_FACTOR = 1.5;
	/** In AUTO style, an NPC with both a bow and a melee weapon switches to melee inside this distance. */
	private static final double CLOSE_QUARTERS = 4.0;
	/** Archers keep their target between these distances. */
	private static final double BOW_MIN_RANGE = 5.0;
	private static final double BOW_MAX_RANGE = 14.0;
	private static final double BACK_OFF_DISTANCE = 6.0;
	private static final double SIDESTEP_DISTANCE = 3.0;

	private ThreatAssessment assessment;
	private int nextAssessTick;
	private boolean engaged;
	@Nullable
	private Mob currentTarget;
	private boolean fleeing;
	private int repathCooldown;
	private int sidestepSign = 1;

	/** Courage threshold: bravery 0 runs from anything but easy fights, bravery 1 stands against about 3 to 1. */
	public static double courageOf(EntityNPC npc) {
		return 0.5 + npc.personality.getBravery() * 2.5;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public int getPriority() {
		return PRIORITY;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return this.assess(npc).hasHostiles();
	}

	private ThreatAssessment assess(EntityNPC npc) {
		Mob nearest = this.assessment != null ? this.assessment.nearestHostile : null;
		boolean stale = nearest != null && !nearest.isAlive();
		// Each NPC re-assesses on its own offset tick, so a crowd doesn't all scan at once.
		boolean due = (npc.tickCount + npc.id) % REASSESS_INTERVAL == 0 || npc.tickCount >= this.nextAssessTick + REASSESS_INTERVAL;
		if (this.assessment == null || stale || due) {
			double radius = this.engaged ? ENGAGED_SCAN_RADIUS : ThreatAssessment.SCAN_RADIUS;
			this.assessment = ThreatAssessment.of(npc, radius, this.currentTarget);
			this.nextAssessTick = npc.tickCount + REASSESS_INTERVAL;
		}

		return this.assessment;
	}

	@Override
	public void start(EntityNPC npc) {
		this.engaged = true;
		this.fleeing = false;
		this.repathCooldown = 0;
	}

	@Override
	public void tick(EntityNPC npc) {
		ThreatAssessment threat = this.assess(npc);
		Mob target = threat.nearestHostile;
		if (target == null) {
			return;
		}

		this.currentTarget = target;

		double courage = courageOf(npc);
		double danger = threat.danger();
		boolean afraid = danger > (this.fleeing ? courage * CALM_DOWN_FACTOR : courage);
		if (afraid != this.fleeing) {
			this.fleeing = afraid;
			this.repathCooldown = 0;
		}

		if (this.fleeing) {
			this.flee(npc, threat);
			if (danger < courage * PANIC_FACTOR) {
				this.hitWhateverIsInReach(npc, threat);
			}

			return;
		}

		double distance = Math.sqrt(target.distanceToSqr(npc));
		if (this.useBow(npc, distance)) {
			this.shoot(npc, target, threat, distance);
		} else {
			this.melee(npc, target, distance);
		}
	}

	/** MELEE never shoots; RANGED shoots whenever it can; AUTO shoots unless it also has a melee weapon and the target is close. */
	private boolean useBow(EntityNPC npc, double distance) {
		if (!NpcArchery.canShoot(npc)) {
			return false;
		}

		return switch (npc.getCombatStyle()) {
			case MELEE -> false;
			case RANGED -> true;
			case AUTO -> !npc.hasMeleeWeapon() || distance > CLOSE_QUARTERS;
		};
	}

	private void melee(EntityNPC npc, Mob target, double distance) {
		npc.readyMeleeWeapon();
		npc.setLookTarget(target, 10);
		if (distance > MELEE_RANGE) {
			if (--this.repathCooldown <= 0) {
				this.repathCooldown = REPATH_INTERVAL;
				npc.navigator.moveTo(target);
			}
		} else {
			npc.navigator.stop();
		}

		npc.tryMeleeAttack(target);
	}

	/**
	 * Keeps the target at bow range: closes in if it's too far or out of sight, backs off if it's too close,
	 * and steps sideways when an ally is in the line of fire rather than shooting through them.
	 */
	private void shoot(EntityNPC npc, Mob target, ThreatAssessment threat, double distance) {
		NpcArchery.readyBow(npc);
		npc.setLookTarget(target, 10);
		boolean visible = npc.canEntityBeSeen(target);
		boolean clearShot = visible && NpcArchery.hasClearShot(npc, target, threat.hostiles);
		boolean repath = --this.repathCooldown <= 0;
		if (repath) {
			this.repathCooldown = REPATH_INTERVAL;
		}

		if (!visible || distance > BOW_MAX_RANGE) {
			if (repath) {
				npc.navigator.moveTo(target);
			}
		} else if (distance < BOW_MIN_RANGE) {
			if (repath) {
				this.moveAwayFrom(npc, List.of(target), BACK_OFF_DISTANCE);
			}
		} else if (!clearShot) {
			if (repath) {
				this.sidestep(npc, target);
			}
		} else {
			npc.navigator.stop();
		}

		if (clearShot && distance <= BOW_MAX_RANGE) {
			NpcArchery.tryShoot(npc, target);
		}
	}

	/** Moves a few blocks to one side of the line to the target, alternating sides if the first is blocked. */
	private void sidestep(EntityNPC npc, Entity target) {
		double angle = Math.atan2(target.z - npc.z, target.x - npc.x) + Math.PI / 2.0 * this.sidestepSign;
		int x = MathHelper.floor(npc.x + Math.cos(angle) * SIDESTEP_DISTANCE);
		int z = MathHelper.floor(npc.z + Math.sin(angle) * SIDESTEP_DISTANCE);
		if (!npc.navigator.moveTo(x, MathHelper.floor(npc.y), z)) {
			this.sidestepSign = -this.sidestepSign;
		}
	}

	/** While running, strikes any hostile that has caught up, with whatever is in hand. */
	private void hitWhateverIsInReach(EntityNPC npc, ThreatAssessment threat) {
		for (Mob hostile : threat.hostiles) {
			if (hostile.isAlive() && hostile.distanceToSqr(npc) <= MELEE_RANGE * MELEE_RANGE && npc.tryMeleeAttack(hostile)) {
				return;
			}
		}
	}

	private void flee(EntityNPC npc, ThreatAssessment threat) {
		if (--this.repathCooldown > 0 && npc.navigator.isMoving()) {
			return;
		}

		this.repathCooldown = FLEE_REPATH_INTERVAL;
		this.moveAwayFrom(npc, threat.hostiles, FLEE_DISTANCE);
	}

	/**
	 * Plans a route away from the threats that keeps clear of them (the navigator weighs every step by how close
	 * it passes to an attacker, so it won't run past one to get away from another). With no route at all,
	 * runs straight away rather than stand still.
	 */
	private void moveAwayFrom(EntityNPC npc, List<? extends Entity> threats, double distance) {
		if (npc.navigator.fleeFrom(threats, distance)) {
			return;
		}

		double cx = 0.0;
		double cz = 0.0;
		for (Entity threat : threats) {
			cx += threat.x;
			cz += threat.z;
		}

		npc.navigator.runDirect(Math.atan2(npc.z - cz / threats.size(), npc.x - cx / threats.size()), PANIC_RUN_TICKS);
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
		this.engaged = false;
		this.currentTarget = null;
		this.fleeing = false;
	}

	public boolean isFleeing() {
		return this.fleeing;
	}

	@Override
	public String describe() {
		return this.fleeing ? "fleeing" : "fighting";
	}
}
