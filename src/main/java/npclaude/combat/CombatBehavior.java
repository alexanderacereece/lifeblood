package npclaude.combat;

import net.minecraft.core.entity.Mob;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.ai.NpcBehavior;
import npclaude.entity.EntityNPC;

/**
 * Fight or flight. Built into every NPC as an instinct, so it overrides follow/stay/wander while danger lasts.
 *
 * The NPC weighs the danger around it ({@link ThreatAssessment}) against its courage, which comes from its bravery:
 * below its courage it fights the nearest hostile, above it it runs away from them.
 */
public class CombatBehavior implements NpcBehavior {
	public static final String ID = "combat";
	public static final int PRIORITY = 100;
	private static final int REASSESS_INTERVAL = 10;
	private static final int FIGHT_REPATH_INTERVAL = 10;
	private static final int FLEE_REPATH_INTERVAL = 20;
	private static final double MELEE_RANGE = 2.2;
	private static final double FLEE_DISTANCE = 10.0;
	/** Once running, keep running until danger drops this far below courage, so the NPC doesn't flip-flop. */
	private static final double CALM_DOWN_FACTOR = 0.8;

	private ThreatAssessment assessment;
	private int nextAssessTick;
	private boolean fleeing;
	private int repathCooldown;

	/** Courage threshold: bravery 0 runs at the first sign of a fair fight, bravery 1 stands against about 3 to 1. */
	public static double courageOf(EntityNPC npc) {
		return 0.5 + npc.getBravery() * 2.5;
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
		if (this.assessment == null || stale || npc.tickCount >= this.nextAssessTick) {
			this.assessment = ThreatAssessment.of(npc);
			this.nextAssessTick = npc.tickCount + REASSESS_INTERVAL;
		}

		return this.assessment;
	}

	@Override
	public void start(EntityNPC npc) {
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

		double courage = courageOf(npc);
		boolean afraid = threat.danger() > (this.fleeing ? courage * CALM_DOWN_FACTOR : courage);
		if (afraid != this.fleeing) {
			this.fleeing = afraid;
			this.repathCooldown = 0;
			npc.setRunning(afraid);
		}

		if (this.fleeing) {
			this.flee(npc, threat);
		} else {
			this.fight(npc, target);
		}
	}

	private void fight(EntityNPC npc, Mob target) {
		npc.setLookTarget(target, 10);
		double distance = Math.sqrt(target.distanceToSqr(npc));
		if (distance > MELEE_RANGE) {
			if (--this.repathCooldown <= 0) {
				this.repathCooldown = FIGHT_REPATH_INTERVAL;
				npc.navigator.moveTo(target);
			}
		} else {
			npc.navigator.stop();
		}

		npc.tryMeleeAttack(target);
	}

	/** Runs directly away from the hostiles' center; if that's blocked, tries angling off to either side. */
	private void flee(EntityNPC npc, ThreatAssessment threat) {
		if (--this.repathCooldown > 0 && npc.navigator.isMoving()) {
			return;
		}

		this.repathCooldown = FLEE_REPATH_INTERVAL;
		double cx = 0.0;
		double cz = 0.0;
		for (Mob hostile : threat.hostiles) {
			cx += hostile.x;
			cz += hostile.z;
		}

		cx /= threat.hostiles.size();
		cz /= threat.hostiles.size();
		double awayAngle = Math.atan2(npc.z - cz, npc.x - cx);
		for (double offset : new double[]{0.0, 0.6, -0.6, 1.2, -1.2}) {
			double angle = awayAngle + offset;
			int x = MathHelper.floor(npc.x + Math.cos(angle) * FLEE_DISTANCE);
			int z = MathHelper.floor(npc.z + Math.sin(angle) * FLEE_DISTANCE);
			if (npc.navigator.moveTo(x, MathHelper.floor(npc.y), z)) {
				return;
			}
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
		npc.setRunning(false);
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
