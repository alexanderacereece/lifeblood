package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.combat.CombatBehavior;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.personality.Quirk;
import npclaude.personality.TraitAxis;
import npclaude.social.Brawl;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * Fistfights: picking them, being in one, and telling a brawl punch apart from a real attack (a friend's shove, a
 * cheap shot after the brawl is over, someone reaching for a weapon).
 */
public final class NpcBrawling implements NpcPart {
	/** How often the NPC considers picking a fight with someone it can't stand (10 seconds). */
	private static final int CHECK_INTERVAL = 200;
	/** Hitting someone this soon after a brawl with them ended is a cheap shot, and starts a real fight (30 seconds). */
	private static final int CHEAP_SHOT_TICKS = 600;
	/** Hits in the first second after a brawl are ignored, so a punch already on its way doesn't escalate it. */
	private static final int GRACE_TICKS = 20;
	/** Within this long of a first punch, a second one from a friend starts a brawl (5 seconds). */
	private static final int SECOND_PUNCH_TICKS = 100;

	/** What a blow from a person turns out to be. */
	enum Hit {
		/** A swing thrown just before the brawl ended: it doesn't land at all. */
		IGNORED,
		/** Part of a brawl: it can't knock the NPC below 1 health, and may make it yield. */
		BRAWL,
		/** A friend's first shove: forgiven, mostly. */
		FRIENDLY,
		/** A real attack. */
		REAL
	}

	private final EntityNPC npc;
	/** The fistfight it's in, if any. Not saved: a brawl doesn't survive leaving the world. */
	@Nullable
	private Brawl brawl;
	/** Who its last brawl was with, and when it ended, to recognize a cheap shot afterwards. */
	@Nullable
	private Entity lastOpponent;
	private int lastEndTick;
	private int lastFriendlyPunchTick = -SECOND_PUNCH_TICKS;

	NpcBrawling(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		if ((this.npc.tickCount + this.npc.id) % CHECK_INTERVAL == 0) {
			this.considerPickingOne();
		}
	}

	@Nullable
	public Brawl getBrawl() {
		return this.brawl;
	}

	public boolean isBrawling() {
		return this.brawl != null;
	}

	public boolean isBrawlingWith(Entity entity) {
		return this.brawl != null && this.brawl.involves(entity);
	}

	public void join(Brawl brawl) {
		this.brawl = brawl;
		this.npc.setTalkingTo(null);
	}

	/** Leaves the brawl; punches from the forgiven opponent no longer count as a reason to fight them for real. */
	public void leave(Brawl brawl, @Nullable Entity forgiven) {
		if (this.brawl == brawl) {
			this.brawl = null;
			this.lastOpponent = brawl.opponentOf(this.npc);
			this.lastEndTick = this.npc.tickCount;
		}

		if (forgiven != null) {
			this.npc.forgiveAttacker(forgiven);
		}
	}

	/**
	 * Works out what a blow from someone is, before it lands: part of a brawl, the start of one, a friend's shove,
	 * a cheap shot, or a real attack. Starting or escalating a brawl happens here too.
	 */
	Hit classify(@Nullable Entity source, boolean armed, DamageType type) {
		boolean person = source instanceof Player || source instanceof EntityNPC;
		if (source == null || source == this.npc || !person || type != DamageType.COMBAT) {
			return Hit.REAL;
		}

		boolean justEnded = this.brawl == null && source == this.lastOpponent;
		// A punch landing in the moment right after a brawl ends is a swing already thrown: it doesn't count.
		if (justEnded && this.npc.tickCount - this.lastEndTick < GRACE_TICKS) {
			return Hit.IGNORED;
		}

		// Hitting it again once a brawl is over isn't part of the brawl: that's a real attack.
		if (justEnded && this.npc.tickCount - this.lastEndTick < CHEAP_SHOT_TICKS) {
			this.lastOpponent = null;
			this.npc.remember(MemoryType.HIT_ME, source, 1.0F);
			this.npc.voice.bark("brawl_cheap_shot", source, null, true);
			return Hit.REAL;
		}

		if (this.isBrawlingWith(source)) {
			// Fists only: reaching for a weapon turns the brawl into a real fight.
			if (armed) {
				this.brawl.escalate((Mob) source);
				return Hit.REAL;
			}

			return Hit.BRAWL;
		}

		if (this.brawl == null && !armed && NpcFactions.regard(this.npc, source) != FactionRelation.HOSTILE
			&& !(this.npc.brain.getActive() instanceof CombatBehavior) && !this.npc.health.isRecovering()) {
			// A punch from someone it isn't fighting. A friend gets one warning; otherwise it's a brawl.
			if (Opinions.of(this.npc, source) >= 20.0F && this.npc.tickCount - this.lastFriendlyPunchTick > SECOND_PUNCH_TICKS) {
				this.lastFriendlyPunchTick = this.npc.tickCount;
				return Hit.FRIENDLY;
			}

			Brawl brawl = Brawl.start(this.npc, (Mob) source, (Mob) source);
			if (brawl != null) {
				this.npc.voice.bark("brawl_accept", source, context -> context.fact("unprovoked", brawl.isUnprovoked() ? "yes" : "no"), true);
				return Hit.BRAWL;
			}
		}

		return Hit.REAL;
	}

	/**
	 * Picks a fight, now and then, with someone nearby it can't stand: anyone it dislikes if it's a hothead,
	 * otherwise only someone who hates it right back. Never when hurt, busy, or outmatched by its own nerves.
	 */
	private void considerPickingOne() {
		if (this.brawl != null || this.npc.isBusy() || this.npc.health.isRecovering() || this.npc.life.isChild()
			|| this.npc.getHealth() < this.npc.getMaxHealth() * 0.75F) {
			return;
		}

		boolean hothead = this.npc.personality.has(Quirk.HOTHEAD);
		float threshold = hothead ? -20.0F : -40.0F;
		AABBd area = MathHelper.aabbGrow(this.npc.bb, 8.0, 3.0, 8.0, new AABBd());
		for (Entity entity : this.npc.world.getEntitiesWithinAABBExcludingEntity(this.npc, area)) {
			if (!(entity instanceof Player) && !(entity instanceof EntityNPC) || !this.npc.canEntityBeSeen(entity)
				|| NpcFactions.regard(this.npc, entity) == FactionRelation.HOSTILE) {
				continue;
			}

			Mob other = (Mob) entity;
			float opinion = Opinions.of(this.npc, other);
			if (opinion > threshold) {
				continue;
			}

			if (other instanceof EntityNPC npc && (npc.isBusy() || npc.getHealth() < npc.getMaxHealth() * 0.75F
				|| !hothead && Opinions.of(npc, this.npc) > -40.0F)) {
				continue;
			}

			if (other instanceof Player && other.getHealth() <= Brawl.PLAYER_YIELD_HEALTH + 2) {
				continue;
			}

			float chance = 0.04F + (threshold - opinion) / 250.0F;
			chance *= hothead ? 2.0F : 1.0F;
			chance *= this.npc.personality.has(Quirk.ZEN) ? 0.3F : 1.0F;
			chance *= this.npc.personality.get(TraitAxis.COURAGE) <= -0.3F ? 0.3F : 1.0F;
			if (this.npc.getRandom().nextFloat() < chance) {
				if (Brawl.start(this.npc, other, this.npc) != null) {
					this.npc.voice.bark("brawl_challenge", other, null, true);
				}

				return;
			}
		}
	}

	/** Brawls aren't saved. */
	@Override
	public void save(CompoundTag tag) {
	}

	@Override
	public void load(CompoundTag tag) {
	}
}
