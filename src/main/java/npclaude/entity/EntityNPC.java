package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.Random;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBrain;
import npclaude.ai.NpcNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A general-purpose NPC. Its look comes from a skin file, its actions from the behaviors in its {@link NpcBrain}.
 * The display name is the vanilla nickname, which BTA already draws as a name tag.
 */
public class EntityNPC extends MobPathfinder {
	/** Bump when the saved format changes, and convert older data in {@link #readAdditionalSaveData}. */
	public static final int DATA_VERSION = 1;
	private static final String SAVE_KEY = "NPClaude";
	private static final int DATA_SKIN = 20;
	private static final int DATA_SKIN_MODEL = 21;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	private boolean invulnerable = false;
	private double homeX;
	private double homeY;
	private double homeZ;
	@Nullable
	private Entity lookTarget;
	private int lookTicks;

	public EntityNPC(World world) {
		super(world);
		this.doRandomWalk = false;
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_SKIN, "", String.class);
		this.entityData.define(DATA_SKIN_MODEL, (byte) SkinModel.AUTO.ordinal(), Byte.class);
	}

	@Override
	protected void updateAI() {
		this.brain.tick();
		super.updateAI();
		// Applied after the vanilla idle AI, which would otherwise spin the head randomly.
		if (this.lookTarget != null && !this.hasPath()) {
			if (this.lookTicks-- > 0 && !this.lookTarget.removed) {
				this.lookAt(this.lookTarget, 10.0F, this.getLookingTilt());
				this.randomYawVelocity = 0.0F;
			} else {
				this.lookTarget = null;
			}
		}
	}

	public void setLookTarget(@Nullable Entity target, int ticks) {
		this.lookTarget = target;
		this.lookTicks = ticks;
	}

	public void teleportTo(double x, double y, double z) {
		this.moveTo(x, y, z, this.yRot, this.xRot);
		this.fallDistance = 0.0F;
	}

	public Random getRandom() {
		return this.random;
	}

	// Identity and appearance

	public String getSkin() {
		return this.entityData.getString(DATA_SKIN);
	}

	public void setSkin(String skin) {
		this.entityData.set(DATA_SKIN, skin);
	}

	public SkinModel getSkinModel() {
		return SkinModel.byId(this.entityData.getByte(DATA_SKIN_MODEL));
	}

	public void setSkinModel(SkinModel model) {
		this.entityData.set(DATA_SKIN_MODEL, (byte) model.ordinal());
	}

	public boolean isInvulnerable() {
		return this.invulnerable;
	}

	public void setInvulnerable(boolean invulnerable) {
		this.invulnerable = invulnerable;
	}

	// Home: the spot that "stay" and "wander" are anchored to

	public void setHome(double x, double y, double z) {
		this.homeX = x;
		this.homeY = y;
		this.homeZ = z;
	}

	public void setHomeHere() {
		this.setHome(this.x, this.y, this.z);
	}

	public double getHomeX() {
		return this.homeX;
	}

	public double getHomeY() {
		return this.homeY;
	}

	public double getHomeZ() {
		return this.homeZ;
	}

	public double distanceToHomeSqr() {
		double dx = this.x - this.homeX;
		double dy = this.y - this.homeY;
		double dz = this.z - this.homeZ;
		return dx * dx + dy * dy + dz * dz;
	}

	// Vanilla hooks

	@Override
	public boolean hurt(Entity attacker, int damage, DamageType type) {
		return !this.invulnerable && super.hurt(attacker, damage, type);
	}

	@Override
	protected boolean canDespawn() {
		return false;
	}

	@Override
	public boolean isSpawnCapExempt() {
		return true;
	}

	@Override
	public void addAdditionalSaveData(@NotNull CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		CompoundTag npc = new CompoundTag();
		npc.putInt("DataVersion", DATA_VERSION);
		npc.putString("Skin", this.getSkin());
		npc.putByte("SkinModel", (byte) this.getSkinModel().ordinal());
		npc.putBoolean("Invulnerable", this.invulnerable);
		npc.putDouble("HomeX", this.homeX);
		npc.putDouble("HomeY", this.homeY);
		npc.putDouble("HomeZ", this.homeZ);
		npc.putList("Behaviors", this.brain.save());
		tag.putCompound(SAVE_KEY, npc);
	}

	@Override
	public void readAdditionalSaveData(@NotNull CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (!tag.containsKey(SAVE_KEY)) {
			this.setHomeHere();
			return;
		}

		CompoundTag npc = tag.getCompound(SAVE_KEY);
		this.setSkin(npc.getString("Skin"));
		this.setSkinModel(SkinModel.byId(npc.getByte("SkinModel")));
		this.invulnerable = npc.getBoolean("Invulnerable");
		this.setHome(npc.getDouble("HomeX"), npc.getDouble("HomeY"), npc.getDouble("HomeZ"));
		if (npc.containsKey("Behaviors")) {
			this.brain.load(npc.getList("Behaviors"));
		}
	}
}
