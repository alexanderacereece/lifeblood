package npclaude.social;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.combat.CombatBehavior;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.entity.EntityNPC;
import npclaude.speech.Word;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/** How NPCs nearby react to a death: thanks for help, rescues, grief, and grudges against killers. */
public final class SocialEvents {
	/** NPCs this close to a death notice it. */
	private static final double WITNESS_RANGE = 24.0;
	/** Close enough to see it happen. */
	private static final double SEE_RANGE = 16.0;
	/** How recently an attacker must have struck to count as who the NPC was fighting (30 seconds). */
	private static final int RECENT_FIGHT_TICKS = 600;

	private SocialEvents() {
	}

	/** Called whenever any mob dies (players included), before it's removed. */
	public static void onDeath(Mob victim, @Nullable Entity killedBy) {
		if (victim.world.isClientSide) {
			return;
		}

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
			}
		}
	}

	private static void onEnemyDied(EntityNPC npc, Mob victim, @Nullable Entity killer) {
		if (killer == npc) {
			npc.remember(MemoryType.WON_A_FIGHT, null, 1.0F);
			npc.voice.bark("victory", victim, context -> context.word("enemy", wordFor(victim)), false);
		} else if (killer != null && isPerson(killer) && wasFighting(npc, victim)) {
			if (npc.isBadlyHurt()) {
				npc.noticeRescue(killer);
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

		if (npc.getLastAttacker() == mob && npc.ticksSinceAttacked() < RECENT_FIGHT_TICKS) {
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
