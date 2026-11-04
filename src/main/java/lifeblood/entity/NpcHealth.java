package lifeblood.entity;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.projectile.Projectile;
import lifeblood.social.MemoryType;
import org.jetbrains.annotations.Nullable;

/**
 * How an NPC takes a beating: its health mode, and for invincible NPCs, being knocked down instead of killed, lying
 * there a few seconds, then going home to recover.
 */
public final class NpcHealth implements NpcPart {
	/** How long an invincible NPC stays down after a blow that would have killed them. */
	private static final int DOWNED_DURATION = 100;
	/** How long falling over (and getting back up) takes, in ticks. */
	private static final int FALL_TICKS = 8;
	/** After being downed, the NPC goes home and stays out of trouble this long (5 minutes). */
	private static final int RECOVERY_DURATION = 6000;
	static final byte DOWNED_ON_BACK = 1;
	static final byte DOWNED_ON_FRONT = 2;

	private final EntityNPC npc;
	private HealthMode mode = HealthMode.MORTAL;
	/** Ticks left lying down after a would-be killing blow; 0 when standing. */
	private int downedTicks;
	/** Ticks left recovering at home after being downed; 0 when recovered. */
	private int recoveringTicks;
	/** How far through falling over the NPC is, 0 (standing) to FALL_TICKS (lying); for drawing. */
	private int fallProgress;
	private int prevFallProgress;
	/** Which way the NPC last fell, remembered so getting up plays the same way in reverse. */
	private boolean fellOnFront;

	NpcHealth(EntityNPC npc) {
		this.npc = npc;
	}

	/** Falling and getting up, on both sides (the client draws it), and the recovery countdown. */
	void animate() {
		this.prevFallProgress = this.fallProgress;
		if (this.isDowned()) {
			this.fellOnFront = this.npc.getEntityData().getByte(EntityNPC.DATA_DOWNED) == DOWNED_ON_FRONT;
		}

		this.fallProgress = this.isDowned() ? Math.min(this.fallProgress + 1, FALL_TICKS) : Math.max(this.fallProgress - 1, 0);
		if (this.recoveringTicks > 0 && !this.isDowned()) {
			this.recoveringTicks--;
		}
	}

	@Override
	public void tick() {
		if (this.downedTicks > 0 && --this.downedTicks == 0) {
			this.getUp();
		}
	}

	public HealthMode getMode() {
		return this.mode;
	}

	/** The health mode that actually applies: a mortal child, or a mortal NPC with a guardian amulet, is invincible. */
	public HealthMode getEffectiveMode() {
		if (this.mode == HealthMode.MORTAL && (this.npc.inventory.hasAmulet() || this.npc.life.isChild())) {
			return HealthMode.INVINCIBLE;
		}

		return this.mode;
	}

	public void setMode(HealthMode mode) {
		this.mode = mode;
		if (mode == HealthMode.MORTAL && this.isDowned()) {
			this.getUp();
			this.stopRecovering();
		}
	}

	/** True while lying down after a blow that would have killed this invincible NPC. */
	public boolean isDowned() {
		return this.npc.getEntityData().getByte(EntityNPC.DATA_DOWNED) != 0;
	}

	/** True if the NPC fell (or is falling, or getting up) face down rather than on its back. */
	public boolean isDownedOnFront() {
		return this.fellOnFront;
	}

	/** How far over the NPC has fallen: 0 standing, 1 lying flat, smoothly in between. */
	public float getFallAmount(float partialTick) {
		float progress = (this.prevFallProgress + (this.fallProgress - this.prevFallProgress) * partialTick) / FALL_TICKS;
		// Slow to tip, fast to land, like falling over.
		return progress * progress;
	}

	/** True for a while after being downed: the NPC goes home, follows no one and avoids fights. */
	public boolean isRecovering() {
		return this.recoveringTicks > 0;
	}

	public void stopRecovering() {
		this.recoveringTicks = 0;
	}

	/** Debug: full health, back on its feet, and done recovering. */
	public void healFully() {
		this.npc.setHealthRaw(this.npc.getMaxHealth());
		if (this.isDowned()) {
			this.getUp();
		}

		this.stopRecovering();
	}

	/**
	 * Instead of dying, the NPC drops to the ground for a few seconds. Lying there, it counts as out of the fight:
	 * enemies lose interest (it reports itself as not alive), and it can't be hurt further or act.
	 */
	void knockDown(@Nullable Entity source) {
		this.downedTicks = DOWNED_DURATION;
		this.recoveringTicks = RECOVERY_DURATION;
		this.npc.getEntityData().set(EntityNPC.DATA_DOWNED, this.isPushedForward(source) ? DOWNED_ON_FRONT : DOWNED_ON_BACK);
		this.npc.setTalkingTo(null);
		this.npc.navigator.stop();
		this.npc.eating.stop();
		this.npc.remainingFireTicks = 0;
		this.npc.social.forgetRescue();
		this.npc.remember(MemoryType.NEARLY_DIED, null, 1.0F);
		this.npc.voice.bark("downed", null, null, true);
	}

	/**
	 * Whether a blow from this source pushes the NPC forward (so it falls on its face) rather than backward.
	 * A projectile pushes along its flight, anything else away from where it stands. Without a source
	 * (lava, falling, drowning) it's a coin toss.
	 */
	private boolean isPushedForward(@Nullable Entity source) {
		double pushX = 0.0;
		double pushZ = 0.0;
		if (source instanceof Projectile) {
			pushX = source.xd;
			pushZ = source.zd;
		}

		if (source != null && pushX * pushX + pushZ * pushZ < 1.0E-4) {
			pushX = this.npc.x - source.x;
			pushZ = this.npc.z - source.z;
		}

		if (pushX * pushX + pushZ * pushZ < 1.0E-4) {
			return this.npc.getRandom().nextBoolean();
		}

		double yaw = Math.toRadians(this.npc.yRot);
		return pushX * -Math.sin(yaw) + pushZ * Math.cos(yaw) > 0.0;
	}

	/** Back on its feet with whatever health it has left. If it went down somewhere deadly, it wakes up back home. */
	private void getUp() {
		this.downedTicks = 0;
		this.npc.getEntityData().set(EntityNPC.DATA_DOWNED, (byte) 0);
		if (this.npc.isInLava() || this.npc.isInWall() || this.npc.isUnderAcidOrWater() || this.npc.y < 0.0) {
			this.npc.goHome();
		}

		if (!this.npc.world.isClientSide && this.isRecovering()) {
			this.npc.voice.bark("get_up", null, null, true);
		}
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putString("HealthMode", this.mode.name());
		tag.putInt("DownedTicks", this.downedTicks);
		tag.putInt("RecoveringTicks", this.recoveringTicks);
	}

	@Override
	public void load(CompoundTag tag) {
		HealthMode mode = HealthMode.byName(tag.getStringOrDefault("HealthMode", ""));
		this.mode = mode != null ? mode : HealthMode.MORTAL;
		this.downedTicks = tag.getIntegerOrDefault("DownedTicks", 0);
		this.recoveringTicks = tag.getIntegerOrDefault("RecoveringTicks", 0);
		this.npc.getEntityData().set(EntityNPC.DATA_DOWNED, this.downedTicks > 0 ? DOWNED_ON_BACK : 0);
		this.fallProgress = this.prevFallProgress = this.downedTicks > 0 ? FALL_TICKS : 0;
	}
}
