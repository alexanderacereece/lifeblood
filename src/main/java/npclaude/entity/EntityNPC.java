package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.Random;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.IArmorWearing;
import net.minecraft.core.entity.IItemHolding;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBrain;
import npclaude.ai.NpcNavigator;
import npclaude.dialogue.NpcDialogue;
import npclaude.interaction.NpcInteractions;
import npclaude.inventory.NpcInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A general-purpose NPC. Its look comes from a skin file, its actions from the behaviors in its {@link NpcBrain}.
 * The display name is the vanilla nickname, which BTA already draws as a name tag.
 */
public class EntityNPC extends MobPathfinder implements IArmorWearing<HumanArmorShape>, IItemHolding {
	/** Bump when the saved format changes, and convert older data in {@link #readAdditionalSaveData}. */
	public static final int DATA_VERSION = 1;
	private static final String SAVE_KEY = "NPClaude";
	private static final int DATA_SKIN = 20;
	private static final int DATA_SKIN_MODEL = 21;
	/** A conversation ends if the player walks further away than this. */
	private static final float TALK_DISTANCE = 8.0F;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	public final NpcInventory inventory = new NpcInventory(this);
	private boolean invulnerable = false;
	private double homeX;
	private double homeY;
	private double homeZ;
	@Nullable
	private Entity lookTarget;
	private int lookTicks;
	@Nullable
	private Player talkingTo;

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
		if (this.talkingTo != null && (this.talkingTo.removed || this.talkingTo.distanceTo(this) > TALK_DISTANCE)) {
			this.talkingTo = null;
		}

		// Mid-conversation the NPC stands still and faces the player; its behaviors resume afterwards.
		if (this.talkingTo != null) {
			this.navigator.stop();
			this.setLookTarget(this.talkingTo, 5);
		} else {
			this.brain.tick();
		}

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

	// Interaction

	@Override
	public boolean interact(@NotNull Player player) {
		ItemStack held = player.inventory.getCurrentItem();
		if (held != null && held.itemID == Items.LABEL.id) {
			return super.interact(player);
		}

		NpcInteractions.interact(this, player);
		return true;
	}

	/** The conversation offered to this player when they right-click the NPC. */
	public NpcDialogue getDialogue(Player player) {
		return NpcDialogue.placeholder();
	}

	@Nullable
	public Player getTalkingTo() {
		return this.talkingTo;
	}

	public void setTalkingTo(@Nullable Player player) {
		this.talkingTo = player;
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

	// Equipment

	@Nullable
	@Override
	public ItemStack getItemInArmorSlot(@NotNull HumanArmorShape slot) {
		return this.inventory.getItem(NpcInventory.ARMOR_START + slot.getSlotIndex());
	}

	@Override
	public void setItemInArmorSlot(@NotNull HumanArmorShape slot, @Nullable ItemStack item) {
		if (slot == HumanArmorShape.HEAD || this.canItemGoInArmorSlot(slot, item)) {
			this.inventory.setItem(NpcInventory.ARMOR_START + slot.getSlotIndex(), item);
		}
	}

	@Override
	public int getNumArmorSlots() {
		return NpcInventory.ARMOR_SIZE;
	}

	@Nullable
	@Override
	public HumanArmorShape getArmorSlotByIndex(int index) {
		return index >= 0 && index < HumanArmorShape.values().length ? HumanArmorShape.values()[index] : null;
	}

	@Nullable
	@Override
	public ItemStack getHeldItem() {
		return this.inventory.getItem(NpcInventory.HAND_SLOT);
	}

	@Override
	public void setHeldItem(@Nullable ItemStack item) {
		this.inventory.setItem(NpcInventory.HAND_SLOT, item);
	}

	@Override
	public boolean isLeftHanded() {
		return false;
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

	/** Armor reduces damage and wears down exactly as it does for players. */
	@Override
	protected void damageEntity(int damage, DamageType damageType) {
		float protection = 1.0F;
		if (damageType != null) {
			protection -= this.getTotalProtectionAmount(damageType);
		}

		protection = Math.max(protection, 0.01F);
		double reduced = damage * protection;
		int newDamage = (int) (this.random.nextFloat() > 0.5F ? Math.floor(reduced) : Math.ceil(reduced));
		if (damageType != null && damageType.shouldDamageArmor()) {
			this.damageArmor((int) Math.ceil((damage - newDamage) / 4.0));
		}

		super.damageEntity(newDamage, damageType);
	}

	@Override
	protected void dropDeathItems() {
		this.inventory.dropAll();
		super.dropDeathItems();
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
		npc.putList("Inventory", this.inventory.save());
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

		if (npc.containsKey("Inventory")) {
			this.inventory.load(npc.getList("Inventory"));
		}
	}
}
