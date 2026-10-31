package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.List;
import java.util.Random;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.IArmorWearing;
import net.minecraft.core.entity.IItemHolding;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.monster.MobSlime;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.enums.Difficulty;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemSoup;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBrain;
import npclaude.ai.NpcNavigator;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.combat.CombatBehavior;
import npclaude.combat.CombatStyle;
import npclaude.combat.MonsterContact;
import npclaude.combat.NpcFactions;
import npclaude.combat.NpcRelations;
import npclaude.dialogue.NpcDialogue;
import npclaude.interaction.NpcInteractions;
import npclaude.inventory.NpcInventory;
import npclaude.perf.NpcProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A general-purpose NPC. Its look comes from a skin file, its actions from the behaviors in its {@link NpcBrain}.
 * The display name is the vanilla nickname, which BTA already draws as a name tag.
 */
public class EntityNPC extends MobPathfinder implements IArmorWearing<HumanArmorShape>, IItemHolding {
	/** Bump when the saved format changes, and convert older data in {@link #readAdditionalSaveData}. */
	public static final int DATA_VERSION = 2;
	private static final String SAVE_KEY = "NPClaude";
	private static final int DATA_SKIN = 20;
	private static final int DATA_SKIN_MODEL = 21;
	/** A conversation ends if the player walks further away than this. */
	private static final float TALK_DISTANCE = 8.0F;
	/** How often the NPC checks whether it should eat, in ticks. */
	private static final int EAT_CHECK_INTERVAL = 10;
	/** How long food is held before it's eaten, so the switch is visible. */
	private static final int EAT_HOLD_TICKS = 20;
	/** Ticks between melee hits; players can click faster, monsters wait 20. */
	private static final int MELEE_COOLDOWN = 15;
	private static final double MELEE_REACH = 2.5;
	private static final int SWING_DURATION = 8;
	/** How often (ticks) the distance-based thinking rate is recalculated. */
	private static final int THINK_RATE_REFRESH = 20;
	/** The forward input of a player holding the walk key; NPCs walk at exactly player speed. */
	private static final float WALK_INPUT = 1.0F;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	public final NpcInventory inventory = new NpcInventory(this);
	public final NpcPersonality personality = new NpcPersonality();
	/** Personal standing with individual players, separate from faction relations. */
	public final NpcRelations relations = new NpcRelations();
	private boolean invulnerable = false;
	private double homeX;
	private double homeY;
	private double homeZ;
	@Nullable
	private Entity lookTarget;
	private int lookTicks;
	@Nullable
	private Player talkingTo;
	private String faction = NpcFactions.DEFAULT;
	private NpcGender gender = NpcGender.MALE;
	private FollowDistance followDistance = FollowDistance.SHORT;
	private CombatStyle combatStyle = CombatStyle.AUTO;
	@Nullable
	private Entity lastAttacker;
	private int lastAttackedTick = Integer.MIN_VALUE / 2;
	private boolean swinging;
	private int swingTicks;
	private int thinkInterval;
	/** Ticks left before the held food is eaten; 0 when not eating. */
	private int eatTicks;
	/** Where the item held before eating went, to switch back to it afterwards; -1 if none. */
	private int eatReturnSlot = -1;

	public EntityNPC(World world) {
		super(world);
		this.doRandomWalk = false;
		this.brain.addInstinct(new CombatBehavior());
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_SKIN, "", String.class);
		this.entityData.define(DATA_SKIN_MODEL, (byte) SkinModel.AUTO.ordinal(), Byte.class);
	}

	@Override
	protected void updateAI() {
		long start = System.nanoTime();
		if (this.talkingTo != null && (this.talkingTo.removed || this.talkingTo.distanceTo(this) > TALK_DISTANCE)) {
			this.talkingTo = null;
		}

		// Mid-conversation the NPC stands still and faces the player; its behaviors resume afterwards.
		if (this.talkingTo != null) {
			this.navigator.stop();
			this.setLookTarget(this.talkingTo, 5);
		} else if (this.isThinkTick()) {
			this.brain.tick();
		}

		super.updateAI();
		this.navigator.tick();
		// Applied after the vanilla idle AI, which would otherwise spin the head randomly.
		if (this.lookTarget != null && !this.navigator.isMoving()) {
			if (this.lookTicks-- > 0 && !this.lookTarget.removed) {
				this.lookAt(this.lookTarget, 10.0F, this.getLookingTilt());
				this.randomYawVelocity = 0.0F;
			} else {
				this.lookTarget = null;
			}
		}

		NpcProfiler.recordAi(System.nanoTime() - start);
	}

	/**
	 * NPCs far from every player decide what to do less often (their movement stays smooth every tick):
	 * every tick within 32 blocks, every 2nd within 64, every 5th within 128, every 10th beyond.
	 * Each NPC thinks on its own offset tick so the work is spread out.
	 */
	private boolean isThinkTick() {
		if (this.tickCount % THINK_RATE_REFRESH == 0 || this.thinkInterval == 0) {
			Player nearest = this.world.getClosestPlayerToEntity(this, -1.0);
			double distance = nearest != null ? nearest.distanceTo(this) : Double.MAX_VALUE;
			this.thinkInterval = distance < 32.0 ? 1 : distance < 64.0 ? 2 : distance < 128.0 ? 5 : 10;
		}

		return (this.tickCount + this.id) % this.thinkInterval == 0;
	}

	public void setLookTarget(@Nullable Entity target, int ticks) {
		this.lookTarget = target;
		this.lookTicks = ticks;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.swinging && ++this.swingTicks >= SWING_DURATION) {
			this.swinging = false;
			this.swingTicks = 0;
		}

		this.swingProgress = this.swingTicks / (float) SWING_DURATION;
	}

	/** Player-equivalent upkeep: regenerating on Peaceful, being hurt by slimes on contact, and eating when injured. */
	@Override
	public void onLivingUpdate() {
		if (this.world.getDifficulty() == Difficulty.PEACEFUL && this.getHealth() < this.getMaxHealth() && this.tickCount % 20 * 12 == 0) {
			this.heal(1);
		}

		super.onLivingUpdate();
		if (this.getHealth() > 0) {
			List<MobSlime> slimes = this.world.getEntitiesWithinAABB(MobSlime.class, MathHelper.aabbGrow(this.bb, 1.0, 0.5, 1.0, new AABBd()));
			for (MobSlime slime : slimes) {
				if (!slime.removed) {
					MonsterContact.slimeTouch(slime, this);
				}
			}
		}

		if (this.eatTicks > 0) {
			if (--this.eatTicks == 0) {
				this.finishEating();
			}
		} else if (this.tickCount % EAT_CHECK_INTERVAL == 0) {
			this.startEating();
		}
	}

	/** The player's rule for eating: only while health plus the healing still queued from earlier food is below max. */
	private boolean wantsFood() {
		return this.getHealth() > 0 && this.getHealth() + this.getTotalHealingRemaining() < this.getMaxHealth();
	}

	public boolean isEating() {
		return this.eatTicks > 0;
	}

	/** Switches food into the hand; it's eaten a second later, like a player picking the food then using it. */
	private void startEating() {
		if (!this.wantsFood()) {
			return;
		}

		int slot = this.inventory.findSlot(stack -> stack.getItem() instanceof ItemFood);
		if (slot < 0) {
			return;
		}

		this.inventory.swapWithHand(slot);
		this.eatReturnSlot = slot == NpcInventory.HAND_SLOT ? -1 : slot;
		this.eatTicks = EAT_HOLD_TICKS;
	}

	/** Eats one of the held food (soup leaves its bowl behind), then switches back to what was held before. */
	private void finishEating() {
		ItemStack stack = this.getHeldItem();
		if (stack != null && stack.getItem() instanceof ItemFood food && this.wantsFood() && stack.consumeItem(null)) {
			this.eatFood(stack);
			this.world.playSoundAtEntity(null, this, food.getTicksPerHeal(stack) >= 10 ? "random.bite_extended" : "random.bite",
				0.5F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F, 1.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
			if (stack.stackSize <= 0) {
				this.setHeldItem(food instanceof ItemSoup ? Items.BOWL.getDefaultStack() : null);
			}
		}

		if (this.eatReturnSlot >= 0) {
			this.inventory.swapWithHand(this.eatReturnSlot);
			this.eatReturnSlot = -1;
		}
	}

	/** Walks straight toward a point at player speed, turning at most 30 degrees a tick like vanilla path-following. */
	public void steerToward(double x, double z) {
		float targetYaw = (float) (Math.atan2(z - this.z, x - this.x) * 180.0 / Math.PI) - 90.0F;
		float turn = MathHelper.wrapDegrees(targetYaw - this.yRot);
		this.yRot += Math.max(-30.0F, Math.min(30.0F, turn));
		this.moveForward = WALK_INPUT;
	}

	public void stopWalking() {
		this.moveForward = 0.0F;
	}

	public void setJumping(boolean jumping) {
		this.isJumping = jumping;
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
		return NpcDialogue.placeholder(this);
	}

	@Nullable
	public Player getTalkingTo() {
		return this.talkingTo;
	}

	public void setTalkingTo(@Nullable Player player) {
		this.talkingTo = player;
	}

	// Combat

	public String getFaction() {
		return this.faction;
	}

	public void setFaction(String faction) {
		this.faction = faction;
	}

	public boolean isAlliedWith(EntityNPC other) {
		return NpcFactions.areAllies(this, other);
	}

	/** The style chosen for this NPC; AUTO means it decides from its equipment. */
	public CombatStyle getCombatStyle() {
		return this.combatStyle;
	}

	public void setCombatStyle(CombatStyle combatStyle) {
		this.combatStyle = combatStyle;
	}

	@Nullable
	public Entity getLastAttacker() {
		return this.lastAttacker;
	}

	public int ticksSinceAttacked() {
		return this.tickCount - this.lastAttackedTick;
	}

	public void swingArm() {
		this.swinging = true;
		this.swingTicks = 0;
	}

	/**
	 * Moves the hardest-hitting item from the inventory into the hand, swapping places with what was held,
	 * like a player switching to their sword. Does nothing if the hand already holds the best option.
	 */
	public void readyMeleeWeapon() {
		if (this.isEating()) {
			return;
		}

		ItemStack held = this.getHeldItem();
		int bestDamage = held != null ? held.getDamageVsEntity(this) : 1;
		int bestSlot = -1;
		for (int i = 0; i < NpcInventory.PACK_SIZE; i++) {
			ItemStack stack = this.inventory.getItem(NpcInventory.PACK_START + i);
			if (stack != null && !(stack.getItem() instanceof ItemBow) && stack.getDamageVsEntity(this) > bestDamage) {
				bestDamage = stack.getDamageVsEntity(this);
				bestSlot = NpcInventory.PACK_START + i;
			}
		}

		if (bestSlot >= 0) {
			this.inventory.swapWithHand(bestSlot);
		}
	}

	/** True if anything carried hits harder than a bare fist (bows don't count). */
	public boolean hasMeleeWeapon() {
		return this.inventory.findSlot(stack -> !(stack.getItem() instanceof ItemBow) && stack.getDamageVsEntity(this) > 1) >= 0;
	}

	/** Hits the target with the held item, like a player's attack, if it's in reach and the cooldown is over. */
	public boolean tryMeleeAttack(Entity target) {
		if (this.attackTime > 0 || this.isEating() || target.distanceToSqr(this) > MELEE_REACH * MELEE_REACH
			|| target.bb.maxY <= this.bb.minY || target.bb.minY >= this.bb.maxY) {
			return false;
		}

		this.attackTime = MELEE_COOLDOWN;
		this.swingArm();
		ItemStack weapon = this.getHeldItem();
		int damage = weapon != null ? weapon.getDamageVsEntity(target) : 1;
		boolean hit = target.hurt(this, damage, DamageType.COMBAT);
		if (hit && weapon != null && weapon.isItemStackDamageable()) {
			weapon.damageItem(1, this);
			if (weapon.stackSize <= 0) {
				this.setHeldItem(null);
			}
		}

		return hit;
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

	public FollowDistance getFollowDistance() {
		return this.followDistance;
	}

	public void setFollowDistance(FollowDistance followDistance) {
		this.followDistance = followDistance;
	}

	/** The player this NPC is following, if it's following someone who is here. */
	@Nullable
	public Player getLeader() {
		FollowPlayerBehavior follow = this.brain.findBehavior(FollowPlayerBehavior.class);
		return follow != null ? follow.getPlayer(this) : null;
	}

	public NpcGender getGender() {
		return this.gender;
	}

	public void setGender(NpcGender gender) {
		this.gender = gender;
	}

	/** Female NPCs' hurt and death sounds are pitched up. */
	@Override
	protected float getPitchModifier() {
		return super.getPitchModifier() + this.gender.pitchOffset;
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
		if (this.invulnerable || !super.hurt(attacker, damage, type)) {
			return false;
		}

		// Being hurt breaks off any conversation, and the NPC remembers who did it (the archer, not the arrow).
		this.talkingTo = null;
		Entity source = attacker instanceof Projectile projectile && projectile.owner != null ? projectile.owner : attacker;
		if (source != null && source != this) {
			this.lastAttacker = source;
			this.lastAttackedTick = this.tickCount;
		}

		return true;
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

	/** Same as a player (10 hearts); the vanilla mob default is only 10. */
	@Override
	public int getMaxHealth() {
		return 20;
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
		npc.putCompound("Personality", this.personality.save());
		npc.putString("Faction", this.faction);
		npc.putByte("Gender", (byte) this.gender.ordinal());
		npc.putByte("FollowDistance", (byte) this.followDistance.ordinal());
		npc.putByte("CombatStyle", (byte) this.combatStyle.ordinal());
		npc.putDouble("HomeX", this.homeX);
		npc.putDouble("HomeY", this.homeY);
		npc.putDouble("HomeZ", this.homeZ);
		npc.putList("Behaviors", this.brain.save());
		npc.putList("Inventory", this.inventory.save());
		npc.putList("PlayerRelations", this.relations.save());
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
		if (npc.containsKey("Personality")) {
			this.personality.load(npc.getCompound("Personality"));
		} else if (npc.containsKey("Bravery")) {
			// Version 1 stored bravery directly.
			this.personality.setBravery(npc.getFloat("Bravery"));
		}

		this.faction = NpcFactions.normalize(npc.getStringOrDefault("Faction", NpcFactions.DEFAULT));
		this.gender = NpcGender.byId(npc.getByte("Gender"));
		this.followDistance = FollowDistance.byId(npc.getByte("FollowDistance"));
		this.combatStyle = CombatStyle.byId(npc.getByte("CombatStyle"));
		this.setHome(npc.getDouble("HomeX"), npc.getDouble("HomeY"), npc.getDouble("HomeZ"));
		if (npc.containsKey("Behaviors")) {
			this.brain.load(npc.getList("Behaviors"));
		}

		if (npc.containsKey("Inventory")) {
			this.inventory.load(npc.getList("Inventory"));
		}

		if (npc.containsKey("PlayerRelations")) {
			this.relations.load(npc.getList("PlayerRelations"));
		}
	}
}
