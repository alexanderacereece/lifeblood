package lifeblood.social;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.animal.AmbientCreature;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.animal.MobWaterAnimal;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.combat.CombatBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import lifeblood.family.Kinship;
import lifeblood.people.PeopleRegistry;
import lifeblood.personality.Quirk;
import lifeblood.speech.Word;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/** How NPCs nearby react to a death: thanks for help, rescues, grief, and grudges against killers. */
public final class SocialEvents {
	/** NPCs this close to a death notice it. */
	private static final double WITNESS_RANGE = 24.0;
	/** Close enough to see it happen. */
	private static final double SEE_RANGE = 16.0;
	/** Chance someone who isn't an animal lover says something about an animal being killed. */
	private static final float ANIMAL_REMARK_CHANCE = 0.2F;
	/** How recently an attacker must have struck to count as who the NPC was fighting (30 seconds). */
	private static final int RECENT_FIGHT_TICKS = 600;

	private SocialEvents() {
	}

	/** Called whenever any mob dies (players included), before it's removed. */
	public static void onDeath(Mob victim, @Nullable Entity killedBy) {
		if (victim.world.isClientSide) {
			return;
		}

		if (victim instanceof Player player) {
			stopFollowingDeadLeader(player);
		}

		mournFamily(victim, killedBy instanceof Projectile projectile && projectile.owner != null ? projectile.owner : killedBy);

		Entity killer = killedBy instanceof Projectile projectile && projectile.owner != null ? projectile.owner : killedBy;
		boolean person = isPerson(victim);
		AABBd area = MathHelper.aabbGrow(victim.bb, WITNESS_RANGE, 8.0, WITNESS_RANGE, new AABBd());
		List<EntityNPC> witnesses = victim.world.getEntitiesWithinAABB(EntityNPC.class, area);
		for (EntityNPC npc : witnesses) {
			if (npc == victim || !npc.isAlive()) {
				continue;
			}

			boolean enemy = wasFighting(npc, victim) || person && NpcFactions.regard(npc, victim) == FactionRelation.HOSTILE;
			if (enemy) {
				onEnemyDied(npc, victim, killer);
			} else if (person) {
				onPersonDied(npc, victim, killer);
			} else if (isAnimal(victim) && killer != null && killer != npc && isPerson(killer) && npc.canEntityBeSeen(victim)) {
				onAnimalKilled(npc, victim, killer);
			}
		}
	}

	/**
	 * Word of a death reaches the family wherever they are: relatives grieve (most for a spouse, child or parent)
	 * and remember who did it. An NPC's death is also recorded, ending any partnership.
	 */
	private static void mournFamily(Mob victim, @Nullable Entity killer) {
		UUID id = Opinions.idOf(victim);
		if (id == null) {
			return;
		}

		PeopleRegistry family = PeopleRegistry.get(victim.world);
		for (Entity entity : victim.world.entities) {
			if (!(entity instanceof EntityNPC npc) || npc == victim || !npc.isAlive()) {
				continue;
			}

			Kinship kin = Kinship.of(family, npc.getUuid(), id);
			if (kin == null || kin == Kinship.LATE_SPOUSE) {
				continue;
			}

			boolean closest = kin == Kinship.SPOUSE || kin == Kinship.CHILD || kin == Kinship.FIANCE;
			float closeness = closest || kin == Kinship.PARENT ? 1.0F : kin == Kinship.SIBLING || kin == Kinship.PARTNER ? 0.8F : 0.4F;
			npc.remember(MemoryType.FAMILY_DIED, null, closeness);
			if (killer != null && killer != npc && isPerson(killer)) {
				npc.remember(closest ? MemoryType.KILLED_MY_SPOUSE_OR_CHILD : MemoryType.KILLED_FAMILY, killer, closest ? 1.0F : closeness);
			}
		}

		if (victim instanceof EntityNPC) {
			family.died(id);
		}
	}

	/** A player who dies loses their followers: they head home rather than waiting for them to come back. */
	private static void stopFollowingDeadLeader(Player player) {
		for (Entity entity : player.world.entities) {
			if (entity instanceof EntityNPC npc && npc.isAlive() && npc.following.getLeader() == player) {
				npc.voice.bark("leader_died", player, null, true);
				npc.following.stop();
			}
		}
	}

	private static boolean isAnimal(Mob mob) {
		return mob instanceof MobAnimal || mob instanceof MobWaterAnimal || mob instanceof AmbientCreature;
	}

	/**
	 * Someone killed an animal in front of it. Animal lovers are upset and think less of whoever did it; anyone else
	 * might pass comment (a glutton sees dinner).
	 */
	private static void onAnimalKilled(EntityNPC npc, Mob animal, Entity killer) {
		boolean lover = npc.personality.has(Quirk.ANIMAL_LOVER);
		if (lover) {
			npc.remember(MemoryType.KILLED_AN_ANIMAL, killer, 1.0F);
			npc.remember(MemoryType.SAW_AN_ANIMAL_DIE, null, 1.0F);
		}

		if (lover || npc.getRandom().nextFloat() < ANIMAL_REMARK_CHANCE) {
			npc.voice.bark("animal_killed", killer, context -> context.word("animal", wordFor(animal)), lover);
		}
	}

	private static void onEnemyDied(EntityNPC npc, Mob victim, @Nullable Entity killer) {
		if (killer == npc) {
			npc.remember(MemoryType.WON_A_FIGHT, null, 1.0F);
			npc.voice.bark("victory", victim, context -> context.word("enemy", wordFor(victim)), false);
		} else if (killer != null && isPerson(killer) && wasFighting(npc, victim)) {
			if (npc.isBadlyHurt()) {
				npc.social.noticeRescue(killer);
			} else {
				npc.remember(MemoryType.FOUGHT_BESIDE_ME, killer, 1.0F);
			}
		}
	}

	private static void onPersonDied(EntityNPC npc, Mob victim, @Nullable Entity killer) {
		boolean saw = npc.distanceTo(victim) <= SEE_RANGE && npc.canEntityBeSeen(victim);
		float opinion = Opinions.of(npc, victim);
		if (opinion >= 50.0F) {
			npc.remember(MemoryType.A_FRIEND_DIED, null, 1.0F);
			if (killer != null && killer != npc && isPerson(killer)) {
				npc.remember(MemoryType.KILLED_A_FRIEND, killer, 1.0F);
			}

			npc.voice.bark("grief", victim, context -> context.fact("victim", Opinions.nameOf(victim)), true);
		} else if (saw) {
			npc.remember(MemoryType.SAW_A_DEATH, null, 1.0F);
		}
	}

	/** Whether this NPC was fighting the mob: it was the target, or recently hit the NPC, or was hunting it. */
	private static boolean wasFighting(EntityNPC npc, Mob mob) {
		if (npc.brain.getActive() instanceof CombatBehavior combat && combat.getCurrentTarget() == mob) {
			return true;
		}

		if (npc.attackers.getLastAttacker() == mob && npc.attackers.ticksSinceAttacked() < RECENT_FIGHT_TICKS) {
			return true;
		}

		return mob instanceof MobPathfinder hunter && hunter.getTarget() == npc;
	}

	private static boolean isPerson(Entity entity) {
		return entity instanceof Player || entity instanceof EntityNPC;
	}

	/** What to call a mob in speech: a person by name, anything else by its kind ("zombie", "cave spider"). */
	public static Word wordFor(Entity entity) {
		if (isPerson(entity)) {
			return Word.name(Opinions.nameOf(entity));
		}

		String name = entity.getClass().getSimpleName().replaceFirst("^Mob", "").replaceFirst("^Entity", "");
		return Word.one(name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT));
	}
}
