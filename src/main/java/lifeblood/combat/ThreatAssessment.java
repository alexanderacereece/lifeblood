package lifeblood.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.IArmorWearing;
import net.minecraft.core.entity.IItemHolding;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.monster.Enemy;
import net.minecraft.core.entity.monster.MobCreeper;
import net.minecraft.core.entity.monster.MobGhast;
import net.minecraft.core.entity.monster.MobHuman;
import net.minecraft.core.entity.monster.MobScorpion;
import net.minecraft.core.entity.monster.MobSkeleton;
import net.minecraft.core.entity.monster.MobSnowman;
import net.minecraft.core.entity.monster.MobSpider;
import net.minecraft.core.entity.monster.MobZombieArmored;
import net.minecraft.core.entity.monster.MobZombiePig;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.HealthMode;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A snapshot of how dangerous an NPC's surroundings are: the hostiles nearby,
 * weighed against the NPC's own fighting strength plus that of its allies: NPCs its faction is allied with, and players
 * it is personally allied with. Players it is neutral toward don't count either way.
 * Everyone's strength uses the same formula, so an unarmed player helps no more than an unarmed NPC.
 *
 * danger = threat / strength. Around 1 means an even fight; the NPC's bravery decides how much it will accept.
 */
public final class ThreatAssessment {
	public static final double SCAN_RADIUS = 12.0;
	/** Hostiles this close count fully; further ones count less, since they may not engage. */
	private static final double CLOSE_RANGE = 6.0;
	private static final double FAR_WEIGHT = 0.6;
	/** How far from the player it follows an NPC will engage enemies. */
	public static final double LEASH_RADIUS = 12.0;
	/** How long the NPC remembers who attacked it, in ticks. */
	public static final int GRUDGE_TICKS = 200;

	public final List<Mob> hostiles = new ArrayList<>();
	public final List<Entity> allies = new ArrayList<>();
	public double threat;
	public double strength;
	@Nullable
	public Mob nearestHostile;

	private ThreatAssessment() {
	}

	public boolean hasHostiles() {
		return !this.hostiles.isEmpty();
	}

	public double danger() {
		return this.strength <= 0.0 ? Double.MAX_VALUE : this.threat / this.strength;
	}

	public static ThreatAssessment of(EntityNPC npc) {
		return of(npc, SCAN_RADIUS, null);
	}

	/**
	 * A wider radius keeps a fight going once it has started. The engaged target (the one the NPC was last fighting)
	 * stays hostile even if it breaks line of sight for a moment.
	 */
	public static ThreatAssessment of(EntityNPC npc, double radius, @Nullable Mob engaged) {
		ThreatAssessment result = new ThreatAssessment();
		AABBd area = new AABBd(npc.x, npc.y, npc.z, npc.x, npc.y, npc.z);
		MathHelper.aabbGrow(area, radius, 4.0, radius, area);
		List<Entity> nearby = npc.world.getEntitiesWithinAABBExcludingEntity(npc, area);

		result.strength = strengthOf(npc);
		for (Entity entity : nearby) {
			if (entity instanceof Mob mob && mob.isAlive() && relationTo(npc, mob) == FactionRelation.ALLY) {
				result.allies.add(mob);
				result.strength += strengthOf(mob);
			}
		}

		// An NPC following a player stays at their side: it ignores fights more than a short way from them,
		// unless the enemy is coming for it or for the player.
		Player leader = npc.following.getLeader();
		double nearestDistance = Double.MAX_VALUE;
		for (Entity entity : nearby) {
			if (!(entity instanceof Mob mob) || !(mob == engaged && mob.isAlive() || isHostile(npc, mob, result.allies))) {
				continue;
			}

			if (leader != null && mob.distanceTo(leader) > LEASH_RADIUS && !isComingFor(mob, npc, leader)) {
				continue;
			}

			double distance = Math.sqrt(mob.distanceToSqr(npc));
			result.hostiles.add(mob);
			boolean inReach = distance <= CLOSE_RANGE || isRanged(mob);
			result.threat += threatOf(mob) * (inReach ? 1.0 : FAR_WEIGHT);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				result.nearestHostile = mob;
			}
		}

		return result;
	}

	/** Is this enemy attacking (or hunting) the NPC or the player it follows? */
	private static boolean isComingFor(Mob mob, EntityNPC npc, Player leader) {
		if (npc.holdsGrudge(mob, GRUDGE_TICKS)) {
			return true;
		}

		return mob instanceof MobPathfinder hunter && (hunter.getTarget() == npc || hunter.getTarget() == leader);
	}

	/**
	 * How the NPC regards a person (another NPC or a player): personal relation, then factions.
	 * Monsters and animals have no relation and are judged separately.
	 */
	@Nullable
	public static FactionRelation relationTo(EntityNPC npc, Mob mob) {
		return mob instanceof EntityNPC || mob instanceof Player ? NpcFactions.regard(npc, mob) : null;
	}

	/**
	 * Hostile: NPCs of an enemy faction, players the NPC is hostile toward, anything hunting this NPC or its allies,
	 * whoever recently hurt it (unless an ally), and naturally aggressive monsters it can see.
	 */
	private static boolean isHostile(EntityNPC npc, Mob mob, List<Entity> allies) {
		if (!mob.isAlive() || mob instanceof Player player && !player.getGamemode().hasHostileMobs()) {
			return false;
		}

		// A brawl opponent is someone to punch, not to kill.
		if (npc.brawling.isBrawlingWith(mob)) {
			return false;
		}

		FactionRelation relation = relationTo(npc, mob);
		if (relation != null) {
			if (relation == FactionRelation.ALLY) {
				return false;
			}

			// Neutral people are left alone unless they start something.
			boolean grudge = npc.holdsGrudge(mob, GRUDGE_TICKS);
			return grudge || relation == FactionRelation.HOSTILE && npc.canEntityBeSeen(mob);
		}

		if (npc.holdsGrudge(mob, GRUDGE_TICKS)) {
			return true;
		}

		if (mob instanceof MobPathfinder hunter && hunter.getTarget() != null
			&& (hunter.getTarget() == npc || allies.contains(hunter.getTarget()))) {
			return true;
		}

		// Neutral until provoked: zombie pigmen, plain humans, and spiders/scorpions (who only hunt in the dark, caught above).
		if (mob instanceof MobZombiePig || mob instanceof MobHuman || mob instanceof MobSpider || mob instanceof MobScorpion) {
			return false;
		}

		return mob instanceof Enemy && npc.canEntityBeSeen(mob);
	}

	/** Mobs that hurt from a distance are as dangerous far away as up close. */
	private static boolean isRanged(Mob mob) {
		return mob instanceof MobSkeleton || mob instanceof MobGhast || mob instanceof MobSnowman
			|| mob instanceof EntityNPC npc && NpcArchery.canShoot(npc);
	}

	/** Enemy NPCs and players are rated like allies are: by their fighting strength. */
	private static double threatOf(Mob mob) {
		if (mob instanceof EntityNPC || mob instanceof Player) {
			return strengthOf(mob);
		}

		double health = Math.max(0.25, Math.min(2.0, mob.getHealth() / 20.0));
		double kind;
		if (mob instanceof MobCreeper || mob instanceof MobZombieArmored) {
			kind = 1.5;
		} else if (mob instanceof MobSkeleton) {
			kind = 1.2;
		} else {
			kind = 1.0;
		}

		return health * kind;
	}

	/**
	 * How well someone can fight, NPC or player alike: health x weapon x armor. Unarmed at full health is 0.25,
	 * an iron sword makes it about 1.5, and full iron armor adds roughly half again.
	 */
	public static double strengthOf(Mob fighter) {
		if (fighter instanceof EntityNPC npc && npc.health.getMode() == HealthMode.INVULNERABLE) {
			return 100.0;
		}

		double health = fighter.getHealth() / (double) fighter.getMaxHealth();
		ItemStack held = fighter instanceof IItemHolding holder ? holder.getHeldItem() : null;
		int damage = held != null ? held.getDamageVsEntity(fighter) : 1;
		double attack = Math.max(0.25, Math.min(2.5, damage / 4.0));
		double armor = fighter instanceof IArmorWearing<?> wearer ? wearer.getTotalProtectionAmount(DamageType.COMBAT) : 0.0;
		return health * attack * (1.0 + armor * 1.5);
	}
}
