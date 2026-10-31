package npclaude.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.monster.Enemy;
import net.minecraft.core.entity.monster.MobCreeper;
import net.minecraft.core.entity.monster.MobHuman;
import net.minecraft.core.entity.monster.MobScorpion;
import net.minecraft.core.entity.monster.MobSkeleton;
import net.minecraft.core.entity.monster.MobSpider;
import net.minecraft.core.entity.monster.MobZombieArmored;
import net.minecraft.core.entity.monster.MobZombiePig;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A snapshot of how dangerous an NPC's surroundings are: the hostiles nearby,
 * weighed against the NPC's own fighting strength plus that of its allies (other NPCs and players).
 *
 * danger = threat / strength. Around 1 means an even fight; the NPC's bravery decides how much it will accept.
 */
public final class ThreatAssessment {
	public static final double SCAN_RADIUS = 12.0;
	/** Hostiles this close count fully; further ones count less, since they may not engage. */
	private static final double CLOSE_RANGE = 6.0;
	private static final double FAR_WEIGHT = 0.6;
	/** How long the NPC remembers who attacked it, in ticks. */
	public static final int GRUDGE_TICKS = 200;
	private static final double PLAYER_STRENGTH = 1.5;

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
		ThreatAssessment result = new ThreatAssessment();
		AABBd area = new AABBd(npc.x, npc.y, npc.z, npc.x, npc.y, npc.z);
		MathHelper.aabbGrow(area, SCAN_RADIUS, 4.0, SCAN_RADIUS, area);
		List<Entity> nearby = npc.world.getEntitiesWithinAABBExcludingEntity(npc, area);

		result.strength = strengthOf(npc);
		for (Entity entity : nearby) {
			if (entity instanceof EntityNPC other && other.isAlive()) {
				result.allies.add(other);
				result.strength += strengthOf(other);
			} else if (entity instanceof Player player && player.isAlive()) {
				result.allies.add(player);
				result.strength += PLAYER_STRENGTH * player.getHealth() / (double) player.getMaxHealth();
			}
		}

		double nearestDistance = Double.MAX_VALUE;
		for (Entity entity : nearby) {
			if (!(entity instanceof Mob mob) || !isHostile(npc, mob, result.allies)) {
				continue;
			}

			double distance = Math.sqrt(mob.distanceToSqr(npc));
			result.hostiles.add(mob);
			result.threat += threatOf(mob) * (distance <= CLOSE_RANGE ? 1.0 : FAR_WEIGHT);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				result.nearestHostile = mob;
			}
		}

		return result;
	}

	/** Hostile: anything hunting this NPC or its allies, whoever recently hurt it, and naturally aggressive monsters it can see. */
	private static boolean isHostile(EntityNPC npc, Mob mob, List<Entity> allies) {
		if (!mob.isAlive() || mob instanceof Player || mob instanceof EntityNPC) {
			return false;
		}

		if (mob == npc.getLastAttacker() && npc.ticksSinceAttacked() < GRUDGE_TICKS) {
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

	private static double threatOf(Mob mob) {
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
	 * How well an NPC can fight: health x weapon x armor. An unarmed NPC at full health is 0.25,
	 * an iron sword makes it about 1.5, and full iron armor adds roughly half again.
	 */
	public static double strengthOf(EntityNPC npc) {
		if (npc.isInvulnerable()) {
			return 100.0;
		}

		double health = npc.getHealth() / (double) npc.getMaxHealth();
		ItemStack held = npc.getHeldItem();
		int damage = held != null ? held.getDamageVsEntity(npc) : 1;
		double attack = Math.max(0.25, Math.min(2.5, damage / 4.0));
		double armor = npc.getTotalProtectionAmount(DamageType.COMBAT);
		return health * attack * (1.0 + armor * 1.5);
	}
}
