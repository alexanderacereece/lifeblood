package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.IArmorWearing;
import net.minecraft.core.entity.IItemHolding;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.monster.MobSlime;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.enums.Difficulty;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBrain;
import npclaude.ai.NpcNavigator;
import npclaude.ai.behavior.RecoverBehavior;
import npclaude.ai.behavior.StayNearParentBehavior;
import npclaude.ai.routine.NpcRoutine;
import npclaude.appearance.Appearance;
import npclaude.appearance.PartCatalog;
import npclaude.combat.BrawlBehavior;
import npclaude.combat.CombatBehavior;
import npclaude.combat.CombatStyle;
import npclaude.combat.FactionRelation;
import npclaude.combat.MonsterContact;
import npclaude.combat.NpcFactions;
import npclaude.combat.NpcRelations;
import npclaude.dialogue.NpcDialogue;
import npclaude.interaction.NpcInteractions;
import npclaude.inventory.NpcInventory;
import npclaude.people.PeopleRegistry;
import npclaude.perf.NpcProfiler;
import npclaude.personality.NpcPersonality;
import npclaude.save.SaveUpgrades;
import npclaude.social.Brawl;
import npclaude.social.MemoryType;
import npclaude.social.NpcMemories;
import npclaude.social.Opinions;
import npclaude.speech.NpcVoice;
import npclaude.world.WorldSystems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * A general-purpose NPC. The entity itself handles what every mob must (moving, fighting, being hurt, equipment,
 * saving); everything else lives in its parts: who it is (personality, memories, voice), what it does (brain,
 * navigator, routine), and its {@link NpcPart}s for life, health, eating, following, brawling, awareness and social ties.
 * The display name is the vanilla nickname, which BTA already draws as a name tag.
 */
public class EntityNPC extends MobPathfinder implements IArmorWearing<HumanArmorShape>, IItemHolding {
	/** Bump when the saved format changes, and convert older data in {@link SaveUpgrades}. */
	public static final int DATA_VERSION = 2;
	private static final String SAVE_KEY = "NPClaude";
	static final int DATA_SKIN = 20;
	static final int DATA_SKIN_MODEL = 21;
	static final int DATA_DOWNED = 22;
	/** The NPC's look built from parts (see Appearance), as an encoded string; empty for none. */
	static final int DATA_APPEARANCE = 23;
	/** How grown up it is, 0 (newborn) to 100 (adult). */
	static final int DATA_GROWTH = 24;
	/** A conversation ends if the player walks further away than this. */
	private static final float TALK_DISTANCE = 8.0F;
	/** Ticks between melee hits; players can click faster, monsters wait 20. */
	private static final int MELEE_COOLDOWN = 15;
	private static final double MELEE_REACH = 2.5;
	private static final int SWING_DURATION = 8;
	/** How often (ticks) the distance-based thinking rate is recalculated. */
	private static final int THINK_RATE_REFRESH = 20;
	/** The forward input of a player holding the walk key; NPCs walk at exactly player speed. */
	private static final float WALK_INPUT = 1.0F;
	/** Below this share of health, the NPC is badly hurt: it says so, and killing its attacker saves its life. */
	private static final float BADLY_HURT_HEALTH = 0.3F;
	/** At least this long between "not now, I'm fighting" lines when clicked mid-fight (1.5 seconds). */
	private static final int REFUSAL_COOLDOWN = 30;
	private static final int MEMORY_PRUNE_INTERVAL = 1200;
	/** How often it checks in with the register of people: still here, and where (5 seconds). */
	private static final int CHECK_IN_INTERVAL = 100;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	public final NpcInventory inventory = new NpcInventory(this);
	public final NpcPersonality personality = new NpcPersonality();
	/** Debug overrides of how the NPC regards individual players, set by /npc relation; they beat opinions and factions. */
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
	public final NpcRoutine routine = new NpcRoutine(this);
	/** The parts, in the order they tick. */
	private final List<NpcPart> parts = List.of(this.life, this.health, this.eating, this.social, this.awareness, this.following, this.brawling,
		this.routine);
	/** Who this NPC is, for others' memories of it; kept for life. */
	private UUID uuid = UUID.randomUUID();
	private int lastRefusalTick = -REFUSAL_COOLDOWN;
	/** Whether it's been given a look from parts yet this session, if it needed one. */
	private boolean appearanceChecked;
	/** Set while a brawl punch (or a friend's shove) lands, so it can't take the NPC below 1 health. */
	private boolean takingBrawlHit;
	/** Whatever is hurting the NPC right now (during {@link #hurt}), so a knockdown can fall away from it. */
	@Nullable
	private Entity incomingDamageSource;
	private double homeX;
	private double homeY;
	private double homeZ;
	@Nullable
	private Entity lookTarget;
	private int lookTicks;
	@Nullable
	private Player talkingTo;
	/** Null when unaffiliated. */
	@Nullable
	private String faction;
	private NpcGender gender = NpcGender.MALE;
	private CombatStyle combatStyle = CombatStyle.AUTO;
	@Nullable
	private Entity lastAttacker;
	private int lastAttackedTick = Integer.MIN_VALUE / 2;
	private boolean swinging;
	private int swingTicks;
	private int thinkInterval;
	/** When it last ticked (by the world systems' steady count), so the register of people can tell it's still loaded. */
	private long lastTickTime;

	public EntityNPC(World world) {
		super(world);
		this.doRandomWalk = false;
		this.brain.addInstinct(new CombatBehavior());
		this.brain.addInstinct(new BrawlBehavior());
		this.brain.addInstinct(new RecoverBehavior());
		this.brain.addInstinct(new StayNearParentBehavior());
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
			this.rollAppearanceIfNeeded();
			PeopleRegistry.get(this.world).seen(this);
		} else if ((this.tickCount + this.id) % CHECK_IN_INTERVAL == 0) {
			PeopleRegistry.get(this.world).seen(this);
		}

		this.voice.tick();
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
		if (this.world.isClientSide) {
			return;
		}

		UUID id = subject != null ? Opinions.idOf(subject) : null;
		if (type.isAboutSomeone() && id == null) {
			return;
		}

		boolean bond = subject != null && (this.faction != null && this.faction.equals(NpcFactions.affiliation(this.world, subject))
			|| Opinions.of(this, subject) >= 50.0F);
		this.memories.add(type, id, subject != null ? Opinions.nameOf(subject) : "", scale, this.personality, bond, this.world.getWorldTime());
	}

	public boolean isBadlyHurt() {
		return this.getHealth() < this.getMaxHealth() * BADLY_HURT_HEALTH;
	}

	/** Too occupied to start a conversation: fighting, brawling, talking to someone, or lying on the ground. */
	public boolean isBusy() {
		return this.talkingTo != null || this.health.isDowned() || this.brawling.isBrawling() || this.brain.getActive() instanceof CombatBehavior;
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
	void resize(float width, float height) {
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
		CombatBehavior combat = this.brain.getActive() instanceof CombatBehavior active ? active : null;
		boolean hostile = NpcFactions.regard(this, player) == FactionRelation.HOSTILE;
		boolean brawling = this.brawling.isBrawling();
		if (combat != null || hostile || brawling) {
			if (this.tickCount - this.lastRefusalTick >= REFUSAL_COOLDOWN) {
				this.lastRefusalTick = this.tickCount;
				boolean fightingYou = combat != null && combat.getCurrentTarget() == player || this.brawling.isBrawlingWith(player);
				this.voice.bark("busy_fighting", player, context -> context.fact("fighting_you", fightingYou ? "yes" : "no")
					.fact("in_combat", combat != null || brawling ? "yes" : "no")
					.fact("brawling", brawling ? "yes" : "no"), true);
			}

			return true;
		}

		NpcInteractions.interact(this, player);
		return true;
	}

	/** The conversation offered to this player when they right-click the NPC. */
	public NpcDialogue getDialogue(Player player) {
		return NpcDialogue.main(this, player);
	}

	@Nullable
	public Player getTalkingTo() {
		return this.talkingTo;
	}

	public void setTalkingTo(@Nullable Player player) {
		this.talkingTo = player;
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

	@Nullable
	public Entity getLastAttacker() {
		return this.lastAttacker;
	}

	public int ticksSinceAttacked() {
		return this.tickCount - this.lastAttackedTick;
	}

	/** Lets go of wanting to fight back against this attacker (after a brawl with them ends fairly). */
	void forgiveAttacker(Entity attacker) {
		if (this.lastAttacker == attacker) {
			this.lastAttacker = null;
		}
	}

	/** Whether this NPC still wants to fight back against someone who hit it recently (not counting brawl punches). */
	public boolean holdsGrudge(Entity mob, int grudgeTicks) {
		return mob == this.lastAttacker && this.ticksSinceAttacked() < grudgeTicks && !this.brawling.isBrawlingWith(mob);
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
		if (this.eating.isEating()) {
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

	/** Moves a held weapon into the pack (if there's room), like putting it away for a fistfight. */
	public void putAwayWeapon() {
		ItemStack held = this.getHeldItem();
		if (held != null && (held.getDamageVsEntity(this) > 1 || held.getItem() instanceof ItemBow) && this.inventory.addToPack(held)) {
			this.setHeldItem(null);
		}
	}

	private boolean canReach(Entity target) {
		return target.distanceToSqr(this) <= MELEE_REACH * MELEE_REACH && target.bb.maxY > this.bb.minY && target.bb.minY < this.bb.maxY;
	}

	/**
	 * A bare-fisted punch, like a player's with an empty hand, for brawls. Never takes a player below 1 health, and
	 * a player beaten down to their yield point loses.
	 */
	public boolean tryPunch(Mob target) {
		if (this.attackTime > 0 || this.eating.isEating() || !this.canReach(target)) {
			return false;
		}

		this.attackTime = MELEE_COOLDOWN;
		this.swingArm();
		if (target instanceof Player && target.getHealth() <= 1) {
			return false;
		}

		boolean hit = target.hurt(this, 1, DamageType.COMBAT);
		Brawl brawl = this.brawling.getBrawl();
		if (hit && brawl != null && target instanceof Player) {
			brawl.checkYield(target);
		}

		return hit;
	}

	/** Hits the target with the held item, like a player's attack, if it's in reach and the cooldown is over. */
	public boolean tryMeleeAttack(Entity target) {
		if (this.attackTime > 0 || this.eating.isEating() || !this.canReach(target)) {
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

	/** The look built from appearance parts, encoded; shown when the NPC has no skin file set. */
	public String getAppearance() {
		return this.entityData.getString(DATA_APPEARANCE);
	}

	public void setAppearance(Appearance appearance) {
		this.entityData.set(DATA_APPEARANCE, appearance.encode());
	}

	/**
	 * Gives an NPC with no skin file and no look yet a random one from the parts available (if there are any),
	 * suited to where it's from. Runs once after spawning or loading, so NPCs get a look once parts are added.
	 */
	public void rollAppearanceIfNeeded() {
		this.life.ensureHomeland();
		if (this.getSkin().isEmpty() && this.getAppearance().isEmpty()) {
			this.setAppearance(Appearance.random(PartCatalog.get(), this.gender, this.random, this.life.getHomeWarmth()));
		}
	}

	public NpcGender getGender() {
		return this.gender;
	}

	public void setGender(NpcGender gender) {
		this.gender = gender;
	}

	/** Voiced like a player of the same gender. */
	@Override
	protected String getHurtSound() {
		return this.gender.hurtSound;
	}

	@Override
	protected String getDeathSound() {
		return this.gender.deathSound;
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
			this.goHome();
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

	/** Teleports home, for getting out of somewhere deadly. */
	void goHome() {
		this.teleportTo(this.homeX, this.homeY, this.homeZ);
	}

	// Vanilla hooks

	@Override
	public boolean hurt(Entity attacker, int damage, DamageType type) {
		if (this.health.getMode() == HealthMode.INVULNERABLE || this.health.isDowned()) {
			return false;
		}

		boolean wasBadlyHurt = this.isBadlyHurt();
		Entity source = attacker instanceof Projectile projectile && projectile.owner != null ? projectile.owner : attacker;
		boolean armed = attacker instanceof Projectile || this.isArmed(source);
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
		this.talkingTo = null;
		if (hit == NpcBrawling.Hit.BRAWL) {
			Brawl brawl = this.brawling.getBrawl();
			if (brawl != null) {
				brawl.checkYield(this);
			}
		} else if (hit == NpcBrawling.Hit.FRIENDLY) {
			this.remember(MemoryType.HIT_ME, source, 0.3F);
			this.voice.bark("hit_by_friend", source, null, false);
		} else if (source != null && source != this) {
			this.lastAttacker = source;
			this.lastAttackedTick = this.tickCount;
			this.reactToHit(source);
		}

		if (!wasBadlyHurt && this.isBadlyHurt() && !this.health.isDowned()) {
			this.voice.bark("low_health", null, null, true);
		}

		return true;
	}

	/** Whether this person is holding something that hits harder than a fist. */
	private boolean isArmed(@Nullable Entity person) {
		ItemStack held = person instanceof Player player ? player.inventory.getCurrentItem() : person instanceof EntityNPC npc ? npc.getHeldItem() : null;
		return held != null && held.getDamageVsEntity(this) > 1;
	}

	/** Being hit by a person is remembered; being hit by someone it isn't fighting gets a complaint. */
	private void reactToHit(Entity source) {
		if (!(source instanceof Player) && !(source instanceof EntityNPC)) {
			return;
		}

		boolean wasHostile = NpcFactions.regard(this, source) == FactionRelation.HOSTILE;
		this.remember(MemoryType.HIT_ME, source, 1.0F);
		if (!wasHostile && !(this.brain.getActive() instanceof CombatBehavior)) {
			this.voice.bark("hit_by_friend", source, null, false);
		}
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
		npc.putString("Skin", this.getSkin());
		npc.putString("Appearance", this.getAppearance());
		npc.putByte("SkinModel", (byte) this.getSkinModel().ordinal());
		npc.putString("Faction", this.faction != null ? this.faction : "");
		npc.putByte("Gender", (byte) this.gender.ordinal());
		npc.putByte("CombatStyle", (byte) this.combatStyle.ordinal());
		npc.putDouble("HomeX", this.homeX);
		npc.putDouble("HomeY", this.homeY);
		npc.putDouble("HomeZ", this.homeZ);
		npc.putCompound("Personality", this.personality.save());
		npc.putList("Behaviors", this.brain.save());
		npc.putList("Inventory", this.inventory.save());
		npc.putList("PlayerRelations", this.relations.save());
		npc.putList("Memories", this.memories.save());
		npc.putCompound("Voice", this.voice.save());
		for (NpcPart part : this.parts) {
			part.save(npc);
		}

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
		SaveUpgrades.npc(npc);
		try {
			if (npc.containsKey("Uuid")) {
				this.uuid = UUID.fromString(npc.getString("Uuid"));
			}
		} catch (IllegalArgumentException ignored) {
			// Keep the fresh id rather than fail to load.
		}

		this.setSkin(npc.getString("Skin"));
		this.entityData.set(DATA_APPEARANCE, npc.getStringOrDefault("Appearance", ""));
		this.setSkinModel(SkinModel.byId(npc.getByte("SkinModel")));
		this.faction = NpcFactions.normalize(npc.getStringOrDefault("Faction", ""));
		this.gender = NpcGender.byId(npc.getByte("Gender"));
		this.combatStyle = CombatStyle.byId(npc.getByte("CombatStyle"));
		this.setHome(npc.getDouble("HomeX"), npc.getDouble("HomeY"), npc.getDouble("HomeZ"));
		if (npc.containsKey("Personality")) {
			this.personality.load(npc.getCompound("Personality"));
		}

		if (npc.containsKey("Behaviors")) {
			this.brain.load(npc.getList("Behaviors"));
		}

		if (npc.containsKey("Inventory")) {
			this.inventory.load(npc.getList("Inventory"));
		}

		if (npc.containsKey("PlayerRelations")) {
			this.relations.load(npc.getList("PlayerRelations"));
		}

		if (npc.containsKey("Memories")) {
			this.memories.load(npc.getList("Memories"));
		}

		if (npc.containsKey("Voice")) {
			this.voice.load(npc.getCompound("Voice"));
		}

		for (NpcPart part : this.parts) {
			part.load(npc);
		}
	}
}
