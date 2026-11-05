package lifeblood.brawl;

import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import lifeblood.social.MemoryType;
import lifeblood.social.Opinions;
import lifeblood.speech.Word;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A non-lethal fistfight between two people (NPCs or a player and an NPC). Fists only, and nobody goes below
 * 1 health. It ends when one side drops to their yield point (10 health; 15 if timid; 5 if proud or competitive),
 * walks away, or a real fight interrupts it. Afterwards the loser remembers the beating, the winner feels good,
 * and both have usually let off some steam. See docs/personality.md, "Brawls".
 */
public final class Brawl {
	/** Health at which an ordinary fighter gives up. */
	private static final int YIELD_HEALTH = 10;
	private static final int TIMID_YIELD_HEALTH = 15;
	private static final int STUBBORN_YIELD_HEALTH = 5;
	/** Players give up at the same point as an ordinary NPC. */
	public static final int PLAYER_YIELD_HEALTH = YIELD_HEALTH;
	/** Walking this far away from a brawl forfeits it. */
	public static final double FORFEIT_DISTANCE = 16.0;
	private static final double BYSTANDER_RANGE = 16.0;
	private static final float BYSTANDER_REMARK_CHANCE = 0.4F;
	/** Someone picked on by a person they thought this well of (or better) didn't have it coming. */
	private static final float UNPROVOKED_OPINION = -20.0F;
	/** How much more an unprovoked fight counts against whoever started it. */
	private static final float UNPROVOKED_GRUDGE = 3.0F;

	private final Mob first;
	private final Mob second;
	/** Whoever started it, if anyone did on purpose; they get the blame. */
	@Nullable
	private final Mob instigator;
	/** The one who was picked on, if anyone was; null when nobody started it on purpose. */
	@Nullable
	private final Mob challenged;
	/** Whether the challenged side had nothing against the instigator: a fight out of nowhere. */
	private final boolean unprovoked;
	private boolean over;

	private Brawl(Mob first, Mob second, @Nullable Mob instigator) {
		this.first = first;
		this.second = second;
		this.instigator = instigator;
		this.challenged = instigator == first ? second : instigator == second ? first : null;
		this.unprovoked = this.challenged instanceof EntityNPC npc && Opinions.of(npc, instigator) > UNPROVOKED_OPINION;
	}

	/** Whether the challenged side had nothing against whoever started it. */
	public boolean isUnprovoked() {
		return this.unprovoked;
	}

	/**
	 * Starts a brawl between two people, if both are free to fight. The instigator (if any) is who threw the first
	 * punch or the challenge.
	 */
	@Nullable
	public static Brawl start(Mob first, Mob second, @Nullable Mob instigator) {
		if (!canBrawl(first) || !canBrawl(second) || first == second) {
			return null;
		}

		Brawl brawl = new Brawl(first, second, instigator);
		for (Mob side : new Mob[] {first, second}) {
			if (side instanceof EntityNPC npc) {
				npc.brawling.join(brawl);
			}
		}

		if (brawl.challenged instanceof EntityNPC npc) {
			npc.remember(MemoryType.PICKED_A_FIGHT_WITH_ME, instigator, brawl.unprovoked ? UNPROVOKED_GRUDGE : 1.0F);
		}

		brawl.alertBystanders();
		return brawl;
	}

	private static boolean canBrawl(Mob mob) {
		if (mob instanceof EntityNPC npc) {
			return npc.isAlive() && npc.brawling.getBrawl() == null && !npc.health.isRecovering() && !npc.health.isDowned() && !npc.life.isChild();
		}

		return mob instanceof Player player && player.isAlive() && player.getGamemode().hasHostileMobs();
	}

	public boolean involves(Entity entity) {
		return !this.over && (entity == this.first || entity == this.second);
	}

	public Mob opponentOf(Entity entity) {
		return entity == this.first ? this.second : this.first;
	}

	/** The health at which this person gives up. Timid and proud cancel out. */
	public static int yieldHealth(Mob mob) {
		if (!(mob instanceof EntityNPC npc)) {
			return PLAYER_YIELD_HEALTH;
		}

		boolean timid = npc.personality.get(TraitAxis.COURAGE) <= -0.3F;
		boolean stubborn = npc.personality.has(Quirk.PROUD) || npc.personality.has(Quirk.COMPETITIVE);
		if (timid == stubborn) {
			return YIELD_HEALTH;
		}

		return timid ? TIMID_YIELD_HEALTH : STUBBORN_YIELD_HEALTH;
	}

	/** Called after a brawl hit lands: whoever's at their yield point gives up. */
	public void checkYield(Mob hit) {
		if (!this.over && hit.getHealth() <= yieldHealth(hit)) {
			this.finish(hit, false);
		}
	}

	/** Whoever walked away loses. */
	public void forfeit(Mob quitter) {
		if (!this.over) {
			this.finish(quitter, true);
		}
	}

	/** Someone reached for a weapon: the brawl is over, and what follows is a real fight. */
	public void escalate(Mob cheater) {
		if (this.over) {
			return;
		}

		this.over = true;
		this.release(false);
		Mob other = this.opponentOf(cheater);
		if (other instanceof EntityNPC npc) {
			npc.remember(MemoryType.HIT_ME, cheater, 2.0F);
			npc.voice.bark("brawl_escalate", cheater, null, true);
		}
	}

	/** Called off without a winner: a monster showed up, someone vanished, someone went down another way. */
	public void callOff() {
		if (!this.over) {
			this.over = true;
			this.release(true);
		}
	}

	private void finish(Mob loser, boolean forfeit) {
		this.over = true;
		Mob winner = this.opponentOf(loser);
		this.release(true);
		if (loser instanceof EntityNPC npc) {
			npc.remember(MemoryType.BEAT_ME_IN_A_BRAWL, winner, 1.0F);
			npc.remember(MemoryType.LOST_A_FIGHT, null, 1.0F);
			if (npc.personality.has(Quirk.COMPETITIVE) && !forfeit) {
				npc.remember(MemoryType.BEAT_ME_FAIR_AND_SQUARE, winner, 1.0F);
			}

			npc.voice.bark("brawl_yield", winner, null, true);
		}

		if (winner instanceof EntityNPC npc) {
			npc.remember(MemoryType.WON_A_FIGHT, null, 1.0F);
			npc.voice.bark("brawl_win", loser, context -> context.fact("forfeit", forfeit ? "yes" : "no"), true);
		}

		// Getting it out of their system: the bad blood usually cools a little, more so for hotheads. Someone picked on
		// out of nowhere had no bad blood to clear.
		if (!forfeit) {
			for (Mob side : new Mob[] {loser, winner}) {
				if (side instanceof EntityNPC npc && !(this.unprovoked && side == this.challenged)) {
					npc.remember(MemoryType.CLEARED_THE_AIR, this.opponentOf(side), npc.personality.has(Quirk.HOTHEAD) ? 1.5F : 1.0F);
				}
			}
		}
	}

	/** Lets both sides go; with forgive, neither holds the brawl's punches against the other afterwards. */
	private void release(boolean forgive) {
		for (Mob side : new Mob[] {this.first, this.second}) {
			if (side instanceof EntityNPC npc) {
				npc.brawling.leave(this, forgive ? this.opponentOf(npc) : null);
			}
		}
	}

	/** Nearby NPCs notice: extroverts cheer, timid ones want someone to stop it, friends worry. */
	private void alertBystanders() {
		AABBd area = MathHelper.aabbGrow(this.first.bb, BYSTANDER_RANGE, 6.0, BYSTANDER_RANGE, new AABBd());
		List<EntityNPC> nearby = this.first.world.getEntitiesWithinAABB(EntityNPC.class, area);
		for (EntityNPC npc : nearby) {
			if (this.involves(npc) || !npc.isAlive() || npc.state.isBusy() || npc.getRandom().nextFloat() >= BYSTANDER_REMARK_CHANCE) {
				continue;
			}

			boolean friendOfFirst = Opinions.of(npc, this.first) >= Opinions.FRIEND_THRESHOLD;
			boolean friendOfSecond = Opinions.of(npc, this.second) >= Opinions.FRIEND_THRESHOLD;
			Mob friend = friendOfFirst ? this.first : friendOfSecond ? this.second : null;
			npc.voice.bark("brawl_watch", friend, context -> {
				context.word("brawler", Word.name(Opinions.nameOf(this.first)));
				context.word("other_brawler", Word.name(Opinions.nameOf(this.second)));
				context.fact("friend_fighting", friend != null ? "yes" : "no");
			}, false);
		}
	}
}
