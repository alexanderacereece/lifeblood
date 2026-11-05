package lifeblood.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.IArmorWearing;
import net.minecraft.core.entity.IItemHolding;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.monster.MobSlime;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.enums.Difficulty;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import lifeblood.ClientHooks;
import lifeblood.ai.NpcBehavior;
import lifeblood.ai.NpcBehaviors;
import lifeblood.ai.NpcBrain;
import lifeblood.ai.NpcHome;
import lifeblood.ai.routine.NpcRoutine;
import lifeblood.appearance.NpcLooks;
import lifeblood.appearance.SkinModel;
import lifeblood.brawl.Brawl;
import lifeblood.brawl.NpcBrawling;
import lifeblood.combat.CombatBehavior;
import lifeblood.combat.CombatStyle;
import lifeblood.combat.MonsterContact;
import lifeblood.combat.NpcAttackers;
import lifeblood.combat.NpcMelee;
import lifeblood.dialogue.NpcConversation;
import lifeblood.dialogue.NpcDialogue;
import lifeblood.faction.NpcFactions;
import lifeblood.faction.NpcRelations;
import lifeblood.family.NpcLife;
import lifeblood.following.NpcFollowing;
import lifeblood.health.HealthMode;
import lifeblood.health.NpcEating;
import lifeblood.health.NpcHealth;
import lifeblood.inventory.NpcInventory;
import lifeblood.nav.NpcNavigator;
import lifeblood.people.PeopleRegistry;
import lifeblood.perf.NpcProfiler;
import lifeblood.personality.NpcAwareness;
import lifeblood.personality.NpcPersonality;
import lifeblood.social.MemoryType;
import lifeblood.social.NpcMemories;
import lifeblood.social.NpcSocial;
import lifeblood.speech.NpcVoice;
import lifeblood.world.WorldSystems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A general-purpose NPC. The entity itself only does what every mob must (moving, being hurt, equipment, synced data,
 * saving) and holds the rest as fields: the brain, navigator, conversation and attackers, and the {@link NpcPart}s,
 * each in its own system's package, which tick, save and load themselves (personality, voice, memories, inventory,
 * looks, home, life, health, eating, social ties, awareness, following, brawling, melee, routine). A new per-NPC
 * system is a new part: a field here and an entry in {@code parts}. See docs/architecture.md, "The NPC".
 * The display name is the vanilla nickname, which BTA already draws as a name tag.
 */
public class EntityNPC extends MobPathfinder implements IArmorWearing<HumanArmorShape>, IItemHolding {
	/**
	 * Saved with every NPC so a future change of format can tell old data apart. Bump it when the format changes; until
	 * long-term worlds exist, old development saves are simply not converted (see CLAUDE.md).
	 */
	public static final int DATA_VERSION = 4;
	private static final String SAVE_KEY = "Lifeblood";
	public static final int DATA_SKIN = 20;
	public static final int DATA_SKIN_MODEL = 21;
	public static final int DATA_DOWNED = 22;
	/** The NPC's look built from parts (see Appearance), as an encoded string; empty for none. */
	public static final int DATA_APPEARANCE = 23;
	/** How grown up it is, 0 (newborn) to 100 (adult). */
	public static final int DATA_GROWTH = 24;
	static final int DATA_GENDER = 25;
	private static final int SWING_DURATION = 8;
	/** The forward input of a player holding the walk key; NPCs walk at exactly player speed. */
	private static final float WALK_INPUT = 1.0F;
	/** Below this share of health, the NPC is badly hurt: it says so, and killing its attacker saves its life. */
	private static final float BADLY_HURT_HEALTH = 0.3F;
	private static final int MEMORY_PRUNE_INTERVAL = 1200;
	/** How often it checks in with the register of people: still here, and where (5 seconds). */
	private static final int CHECK_IN_INTERVAL = 100;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	public final NpcInventory inventory = new NpcInventory(this);
	public final NpcPersonality personality = new NpcPersonality();
	/** Debug overrides of how the NPC regards individual players, set by /lb social stance; they beat opinions and factions. */
	public final NpcRelations relations = new NpcRelations();
	public final NpcMemories memories = new NpcMemories();
	public final NpcVoice voice = new NpcVoice(this);
	public final NpcLife life = new NpcLife(this);
	public final NpcHealth health = new NpcHealth(this);
	public final NpcEating eating = new NpcEating(this);
	public final NpcSocial social = new NpcSocial(this);
	public final NpcAwareness awareness = new NpcAwareness(this);
	public final NpcFollowing following = new NpcFollowing(this);
	public final NpcBrawling brawling = new NpcBrawling(this);
	public final NpcMelee melee = new NpcMelee(this);
	public final NpcHome home = new NpcHome(this);
	public final NpcLooks looks = new NpcLooks(this);
	public final NpcAttackers attackers = new NpcAttackers(this);
	public final NpcConversation conversation = new NpcConversation(this);
	public final NpcRoutine routine = new NpcRoutine(this);
	/** The parts, in the order they tick and load: who it is first, then what it does. */
	private final List<NpcPart> parts = List.of(this.personality, this.voice, this.memories, this.relations, this.inventory,
		this.looks, this.home, this.life, this.health, this.eating, this.social, this.awareness, this.following,
		this.brawling, this.melee, this.routine);
	/** Who this NPC is, for others' memories of it; kept for life. */
	private UUID uuid = UUID.randomUUID();
	/** Whether it's been given a look from parts yet this session, if it needed one. */
	private boolean appearanceChecked;
	/** Set while a brawl punch (or a friend's shove) lands, so it can't take the NPC below 1 health. */
	private boolean takingBrawlHit;
	/** Whatever is hurting the NPC right now (during {@link #hurt}), so a knockdown can fall away from it. */
	@Nullable
	private Entity incomingDamageSource;
	@Nullable
	private Entity lookTarget;
	private int lookTicks;
	/** Null when unaffiliated. */
	@Nullable
	private String faction;
	private CombatStyle combatStyle = CombatStyle.AUTO;
	private boolean swinging;
	private int swingTicks;
	/** When it last ticked (by the world systems' steady count), so the register of people can tell it's still loaded. */
	private long lastTickTime;

	public EntityNPC(World world) {
		super(world);
		this.doRandomWalk = false;
		for (NpcBehavior instinct : NpcBehaviors.createInstincts()) {
			this.brain.addInstinct(instinct);
		}
		// Every NPC starts as someone; loading a saved NPC replaces this with who it was.
		this.personality.randomize(this.random);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(DATA_SKIN, "", String.class);
		this.entityData.define(DATA_SKIN_MODEL, (byte) SkinModel.AUTO.ordinal(), Byte.class);
		this.entityData.define(DATA_DOWNED, (byte) 0, Byte.class);
		this.entityData.define(DATA_APPEARANCE, "", String.class);
		this.entityData.define(DATA_GROWTH, (byte) 100, Byte.class);
		this.entityData.define(DATA_GENDER, (byte) NpcGender.MALE.ordinal(), Byte.class);
	}

	@Override
	protected void updateAI() {
		long start = System.nanoTime();
		this.conversation.update();
		// Mid-conversation the NPC stands still and faces the player; its behaviors resume afterwards.
		if (this.conversation.isTalking()) {
			this.navigator.stop();
			this.setLookTarget(this.conversation.getTalkingTo(), 5);
		} else {
			this.brain.think();
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

	public void setLookTarget(@Nullable Entity target, int ticks) {
		this.lookTarget = target;
		this.lookTicks = ticks;
	}

	@Override
	public void tick() {
		super.tick();
		this.health.animate();
		if (this.swinging && ++this.swingTicks >= SWING_DURATION) {
			this.swinging = false;
			this.swingTicks = 0;
		}

		this.swingProgress = this.swingTicks / (float) SWING_DURATION;
	}

	/** Player-equivalent upkeep (regenerating on Peaceful, being hurt by slimes on contact), then its parts. */
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

		this.life.updateSize();
		if (this.world.isClientSide) {
			return;
		}

		this.lastTickTime = WorldSystems.ticks(this.world);
		if (!this.appearanceChecked) {
			this.appearanceChecked = true;
			this.looks.rollIfNeeded();
			PeopleRegistry.get(this.world).seen(this);
		} else if ((this.tickCount + this.id) % CHECK_IN_INTERVAL == 0) {
			PeopleRegistry.get(this.world).seen(this);
		}

		for (NpcPart part : this.parts) {
			part.tick();
		}

		if ((this.tickCount + this.id) % MEMORY_PRUNE_INTERVAL == 0) {
			this.memories.prune(this.world.getWorldTime());
		}
	}

	// Social life

	public UUID getUuid() {
		return this.uuid;
	}

	/** When it last ticked, by {@link WorldSystems#ticks}. */
	public long getLastTickTime() {
		return this.lastTickTime;
	}

	/**
	 * Remembers something, about someone or (for mood-only memories) about nothing in particular.
	 * Scale multiplies its effects, e.g. by how much a food is liked.
	 */
	public void remember(MemoryType type, @Nullable Entity subject, float scale) {
		this.memories.remember(this, type, subject, scale);
	}

	public boolean isBadlyHurt() {
		return this.getHealth() < this.getMaxHealth() * BADLY_HURT_HEALTH;
	}

	/** Whether it's in a fight with this mob right now (its current target). */
	public boolean isFighting(Entity mob) {
		return this.brain.getActive() instanceof CombatBehavior combat && combat.getCurrentTarget() == mob;
	}

	/** Too occupied to start a conversation: fighting, brawling, talking to someone, or lying on the ground. */
	public boolean isBusy() {
		return this.conversation.isTalking() || this.health.isDowned() || this.brawling.isBrawling() || this.brain.getActive() instanceof CombatBehavior;
	}

	// Movement

	/** Walks straight toward a point at player speed, turning at most 30 degrees a tick like vanilla path-following. */
	public void steerToward(double x, double z) {
		float targetYaw = (float) (Math.atan2(z - this.z, x - this.x) * 180.0 / Math.PI) - 90.0F;
		float turn = MathHelper.wrapDegrees(targetYaw - this.yRot);
		this.yRot += Math.max(-30.0F, Math.min(30.0F, turn));
		this.moveForward = WALK_INPUT;
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

	/** Resizes the hitbox (for growing up), keeping it where it stands. */
	public void resize(float width, float height) {
		this.setSize(width, height);
		this.setPos(this.x, this.y, this.z);
	}

	// Interaction

	@Override
	public boolean interact(@NotNull Player player) {
		ItemStack held = player.inventory.getCurrentItem();
		if (held != null && held.itemID == Items.LABEL.id) {
			return super.interact(player);
		}

		// No chatting mid-fight, or with someone it means to fight: it says so instead.
		if (this.conversation.refuses(player)) {
			return true;
		}

		ClientHooks.interact(this, player);
		return true;
	}

	/** The conversation offered to this player when they right-click the NPC. */
	public NpcDialogue getDialogue(Player player) {
		return NpcDialogue.main(this, player);
	}

	// Combat

	/** The faction this NPC belongs to, or null if unaffiliated. */
	@Nullable
	public String getFaction() {
		return this.faction;
	}

	public void setFaction(@Nullable String faction) {
		this.faction = faction;
	}

	/** The style chosen for this NPC; AUTO means it decides from its equipment. */
	public CombatStyle getCombatStyle() {
		return this.combatStyle;
	}

	public void setCombatStyle(CombatStyle combatStyle) {
		this.combatStyle = combatStyle;
	}

	public void swingArm() {
		this.swinging = true;
		this.swingTicks = 0;
	}

	// Identity

	public NpcGender getGender() {
		return NpcGender.byId(this.entityData.getByte(DATA_GENDER));
	}

	public void setGender(NpcGender gender) {
		this.entityData.set(DATA_GENDER, (byte) gender.ordinal());
	}

	/** Voiced like a player of the same gender. */
	@Override
	protected String getHurtSound() {
		return this.getGender().hurtSound;
	}

	@Override
	protected String getDeathSound() {
		return this.getGender().deathSound;
	}

	@Override
	public boolean isAlive() {
		return super.isAlive() && !this.health.isDowned();
	}

	@Override
	protected boolean isMovementBlocked() {
		return super.isMovementBlocked() || this.health.isDowned();
	}

	/** Falling out of the world can't kill an invincible NPC either: it's brought home. */
	@Override
	public void outOfWorld() {
		if (this.health.getEffectiveMode() == HealthMode.MORTAL) {
			super.outOfWorld();
		} else {
			this.home.teleportHome();
		}
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

	// Vanilla hooks

	@Override
	public boolean hurt(Entity attacker, int damage, DamageType type) {
		if (this.health.getMode() == HealthMode.INVULNERABLE || this.health.isDowned()) {
			return false;
		}

		boolean wasBadlyHurt = this.isBadlyHurt();
		Entity source = Persons.behind(attacker);
		boolean armed = attacker instanceof Projectile || this.attackers.isArmed(source);
		NpcBrawling.Hit hit = this.brawling.classify(source, armed, type);
		if (hit == NpcBrawling.Hit.IGNORED) {
			return false;
		}

		this.incomingDamageSource = attacker;
		this.takingBrawlHit = hit == NpcBrawling.Hit.BRAWL || hit == NpcBrawling.Hit.FRIENDLY;
		boolean hurt;
		try {
			hurt = super.hurt(attacker, damage, type);
		} finally {
			this.incomingDamageSource = null;
			this.takingBrawlHit = false;
		}

		if (!hurt) {
			return false;
		}

		// Being hurt breaks off any conversation, and the NPC remembers who did it (the archer, not the arrow).
		// Brawl punches and a friend's first shove don't make it want to fight for real.
		this.conversation.end();
		if (hit == NpcBrawling.Hit.BRAWL) {
			Brawl brawl = this.brawling.getBrawl();
			if (brawl != null) {
				brawl.checkYield(this);
			}
		} else if (hit == NpcBrawling.Hit.FRIENDLY) {
			this.remember(MemoryType.HIT_ME, source, 0.3F);
			this.voice.bark("hit_by_friend", source, null, false);
		} else if (source != null && source != this) {
			this.attackers.attackedBy(source);
		}

		if (!wasBadlyHurt && this.isBadlyHurt() && !this.health.isDowned()) {
			this.voice.bark("low_health", null, null, true);
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

		if (this.takingBrawlHit) {
			newDamage = Math.min(newDamage, this.getHealth() - 1);
		}

		if (this.health.getEffectiveMode() == HealthMode.INVINCIBLE && newDamage >= this.getHealth()) {
			newDamage = this.getHealth() - 1;
			this.health.knockDown(this.incomingDamageSource);
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
		npc.putString("Uuid", this.uuid.toString());
		npc.putString("Faction", this.faction != null ? this.faction : "");
		npc.putByte("Gender", (byte) this.getGender().ordinal());
		npc.putByte("CombatStyle", (byte) this.combatStyle.ordinal());
		npc.putList("Behaviors", this.brain.save());
		CompoundTag sections = new CompoundTag();
		for (NpcPart part : this.parts) {
			if (sections.containsKey(part.saveKey())) {
				throw new IllegalStateException("Two NPC parts share the save key " + part.saveKey());
			}

			CompoundTag section = new CompoundTag();
			part.save(section);
			sections.putCompound(part.saveKey(), section);
		}

		npc.putCompound("Parts", sections);

		tag.putCompound(SAVE_KEY, npc);
	}

	@Override
	public void readAdditionalSaveData(@NotNull CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (!tag.containsKey(SAVE_KEY)) {
			this.home.setHere();
			return;
		}

		CompoundTag npc = tag.getCompound(SAVE_KEY);
		try {
			if (npc.containsKey("Uuid")) {
				this.uuid = UUID.fromString(npc.getString("Uuid"));
			}
		} catch (IllegalArgumentException ignored) {
			// Keep the fresh id rather than fail to load.
		}

		this.faction = NpcFactions.normalize(npc.getStringOrDefault("Faction", ""));
		this.setGender(NpcGender.byId(npc.getByte("Gender")));
		this.combatStyle = CombatStyle.byId(npc.getByte("CombatStyle"));
		if (npc.containsKey("Behaviors")) {
			this.brain.load(npc.getList("Behaviors"));
		}

		CompoundTag sections = npc.getCompound("Parts");
		for (NpcPart part : this.parts) {
			part.load(sections.containsKey(part.saveKey()) ? sections.getCompound(part.saveKey()) : new CompoundTag());
		}
	}
}
