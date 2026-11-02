package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
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
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemSoup;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import npclaude.ai.NpcBrain;
import npclaude.appearance.Appearance;
import npclaude.appearance.PartCatalog;
import npclaude.ai.NpcNavigator;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.ai.behavior.RecoverBehavior;
import npclaude.ai.behavior.StayNearParentBehavior;
import npclaude.ai.behavior.WanderBehavior;
import npclaude.combat.BrawlBehavior;
import npclaude.combat.CombatBehavior;
import npclaude.combat.CombatStyle;
import npclaude.combat.FactionRelation;
import npclaude.combat.MonsterContact;
import npclaude.combat.NpcFactions;
import npclaude.combat.NpcRelations;
import npclaude.dialogue.NpcDialogue;
import npclaude.family.Calendar;
import npclaude.family.Children;
import npclaude.family.Romance;
import npclaude.interaction.NpcInteractions;
import npclaude.inventory.NpcInventory;
import npclaude.perf.NpcProfiler;
import npclaude.personality.ItemDefaults;
import npclaude.personality.Surroundings;
import npclaude.personality.TasteCategory;
import npclaude.personality.Tastes;
import npclaude.personality.NpcPersonality;
import npclaude.personality.Quirk;
import npclaude.personality.TraitAxis;
import npclaude.save.SaveUpgrades;
import npclaude.social.Brawl;
import npclaude.social.Memory;
import npclaude.social.MemoryType;
import npclaude.social.NpcMemories;
import npclaude.social.Opinions;
import npclaude.speech.NpcVoice;
import npclaude.speech.SpeechContext;
import npclaude.speech.Word;
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
	private static final int DATA_DOWNED = 22;
	/** The NPC's look built from parts (see Appearance), as an encoded string; empty for none. */
	private static final int DATA_APPEARANCE = 23;
	/** How grown up it is, 0 (newborn) to 100 (adult). */
	private static final int DATA_GROWTH = 24;
	/** How often it checks its age: growing up, and whether it's its birthday (10 seconds). */
	private static final int GROWTH_CHECK_INTERVAL = 200;
	/** A birth time not decided yet (an NPC from before ages, or a brand new one). */
	private static final long UNKNOWN_BIRTH = Long.MIN_VALUE;
	/**
	 * Adults without a known birth get an age around this, most between their twenties and forties: a bell curve,
	 * tighter below the peak than above it, so there are a fair few older people and nobody under 18.
	 */
	private static final float TYPICAL_ADULT_AGE = 27.0F;
	private static final float YOUNGER_SPREAD = 4.0F;
	private static final float OLDER_SPREAD = 12.0F;
	private static final int MIN_ADULT_AGE = 18;
	private static final int MAX_ADULT_AGE = 85;
	private static final float ADULT_WIDTH = 0.6F;
	private static final float ADULT_HEIGHT = 1.825F;
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
	/** How long an invincible NPC stays down after a blow that would have killed them. */
	private static final int DOWNED_DURATION = 100;
	/** How long falling over (and getting back up) takes, in ticks. */
	private static final int FALL_TICKS = 8;
	/** After being downed, the NPC goes home and stays out of trouble this long (5 minutes). */
	private static final int RECOVERY_DURATION = 6000;
	private static final byte DOWNED_ON_BACK = 1;
	private static final byte DOWNED_ON_FRONT = 2;
	/** Below this share of health, someone who kills its attacker has saved its life (if it lives a while longer). */
	private static final float RESCUE_HEALTH = 0.3F;
	/** How long an NPC must survive after being rescued for it to count (10 seconds). */
	private static final int RESCUE_CONFIRM_TICKS = 200;
	/** How often the NPC notices a change of weather, biome or season (2 seconds). */
	private static final int SURROUNDINGS_CHECK_INTERVAL = 40;
	/** How often the weather and the place it's in affect its mood (30 seconds). */
	private static final int SURROUNDINGS_MOOD_INTERVAL = 600;
	/** How often a follower checks whether it still wants to follow (5 seconds). */
	private static final int FOLLOW_CHECK_INTERVAL = 100;
	/** A follower whose opinion of its leader sinks this low leaves. */
	private static final float QUIT_FOLLOWING_OPINION = -15.0F;
	/** How often the NPC tends its love life: a relationship to keep up, or someone to ask out (1 minute). */
	private static final int ROMANCE_INTERVAL = 1200;
	/** How often the NPC considers picking a fight with someone it can't stand (10 seconds). */
	private static final int BRAWL_CHECK_INTERVAL = 200;
	/** Hitting someone this soon after a brawl with them ended is a cheap shot, and starts a real fight (30 seconds). */
	private static final int CHEAP_SHOT_TICKS = 600;
	/** Hits in the first second after a brawl are ignored, so a punch already on its way doesn't escalate it. */
	private static final int BRAWL_GRACE_TICKS = 20;
	/** Within this long of a first punch, a second one from a friend starts a brawl (5 seconds). */
	private static final int SECOND_PUNCH_TICKS = 100;
	/** How often a follower looks at what its leader is up to (2 seconds). */
	private static final int LEADER_WATCH_INTERVAL = 40;
	/** This far above sea level, the air feels thin. */
	private static final int HIGH_UP_ABOVE_SEA = 70;
	/** At least this long between "not now, I'm fighting" lines when clicked mid-fight (1.5 seconds). */
	private static final int REFUSAL_COOLDOWN = 30;
	/** At least this long between remarks about a new biome, so walking along a border doesn't set it off (2 minutes). */
	private static final int BIOME_REMARK_COOLDOWN = 2400;
	private static final int MEMORY_PRUNE_INTERVAL = 1200;

	public final NpcBrain brain = new NpcBrain(this);
	public final NpcNavigator navigator = new NpcNavigator(this);
	public final NpcInventory inventory = new NpcInventory(this);
	public final NpcPersonality personality = new NpcPersonality();
	/** Debug overrides of how the NPC regards individual players, set by /npc relation; they beat opinions and factions. */
	public final NpcRelations relations = new NpcRelations();
	public final NpcMemories memories = new NpcMemories();
	public final NpcVoice voice = new NpcVoice(this);
	/** Who this NPC is, for others' memories of it; kept for life. */
	private UUID uuid = UUID.randomUUID();
	/** Someone who just killed what was about to kill this NPC; becomes "saved my life" if it survives a while. */
	@Nullable
	private Entity rescuer;
	private int rescueTick;
	/** The surroundings last noticed, so changes can be remarked on. Not saved, so nothing fires on load. */
	@Nullable
	private String lastWeather;
	@Nullable
	private String lastBiome;
	@Nullable
	private String lastSeason;
	/** A remark about new surroundings waiting to be made (after a short, random pause), and when. */
	@Nullable
	private String pendingRemark;
	private int pendingRemarkTick;
	private int lastBiomeRemarkTick = -BIOME_REMARK_COOLDOWN;
	private int lastRefusalTick = -REFUSAL_COOLDOWN;
	/** Whether it's been given a look from parts yet this session, if it needed one. */
	private boolean appearanceChecked;
	/** When it was born (world time; long before the world began, for most adults). */
	private long birthTime = UNKNOWN_BIRTH;
	/** The last year it celebrated its birthday, so it happens once a year. */
	private long lastBirthdayYear = Long.MIN_VALUE;
	/** Where it's from: the biome it was first seen in (or born in), and how warm that is (NaN until known). */
	private String homeBiome = "";
	private float homeWarmth = Float.NaN;
	/** The size its hitbox was last set for, so it's only resized when growth changes. */
	private int sizedForGrowth = -1;
	/** Something it wants to ask a player next time they talk (out on a date, or to marry), and which player. */
	@Nullable
	private Romance.Request romanceRequest;
	@Nullable
	private UUID romanceRequestPlayer;
	/** The in-game day each player last gave it a gift, for the one-gift-a-day limit. */
	private final Map<UUID, Long> lastGiftDays = new HashMap<>();
	/** The fistfight it's in, if any. Not saved: a brawl doesn't survive leaving the world. */
	@Nullable
	private Brawl brawl;
	/** Set while a brawl punch lands, so it can't take the NPC below 1 health. */
	private boolean takingBrawlHit;
	/** Who its last brawl was with, and when it ended, to recognize a cheap shot afterwards. */
	@Nullable
	private Entity lastBrawlOpponent;
	private int lastBrawlEndTick;
	private int lastFriendlyPunchTick = -SECOND_PUNCH_TICKS;
	/** When each kind of remark about the leader can next be made, by event. */
	private final Map<String, Integer> remarkReady = new HashMap<>();
	/** How many valuables (and of this NPC's favorite thing) its leader carried at the last look; null before the first. */
	@Nullable
	private Map<Item, Integer> leaderValuables;
	private HealthMode healthMode = HealthMode.MORTAL;
	/** Ticks left lying down after a would-be killing blow; 0 when standing. */
	private int downedTicks;
	/** Ticks left recovering at home after being downed; 0 when recovered. */
	private int recoveringTicks;
	/** How far through falling over the NPC is, 0 (standing) to FALL_TICKS (lying); for drawing. */
	private int fallProgress;
	private int prevFallProgress;
	/** Which way the NPC last fell, remembered so getting up plays the same way in reverse. */
	private boolean fellOnFront;
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
	private FollowDistance followDistance = FollowDistance.MEDIUM;
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
		this.prevFallProgress = this.fallProgress;
		if (this.isDowned()) {
			this.fellOnFront = this.entityData.getByte(DATA_DOWNED) == DOWNED_ON_FRONT;
		}

		this.fallProgress = this.isDowned() ? Math.min(this.fallProgress + 1, FALL_TICKS) : Math.max(this.fallProgress - 1, 0);
		if (this.recoveringTicks > 0 && !this.isDowned()) {
			this.recoveringTicks--;
		}

		if (this.swinging && ++this.swingTicks >= SWING_DURATION) {
			this.swinging = false;
			this.swingTicks = 0;
		}

		this.swingProgress = this.swingTicks / (float) SWING_DURATION;
	}

	/** Player-equivalent upkeep: regenerating on Peaceful, being hurt by slimes on contact, and eating when injured. */
	@Override
	public void onLivingUpdate() {
		if (this.downedTicks > 0 && --this.downedTicks == 0) {
			this.getUp();
		}

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

		this.updateSize();
		if (!this.world.isClientSide) {
			this.socialUpdate();
		}
	}

	/** Speaking up, confirming rescues, and noticing the weather and where it is. */
	private void socialUpdate() {
		if ((this.tickCount + this.id) % GROWTH_CHECK_INTERVAL == 0) {
			this.ensureBirth();
			if (this.isChild()) {
				this.grow();
			}

			this.checkBirthday();
		}

		if (!this.appearanceChecked) {
			this.appearanceChecked = true;
			this.rollAppearanceIfNeeded();
		}

		this.voice.tick();
		if (this.rescuer != null && this.tickCount - this.rescueTick >= RESCUE_CONFIRM_TICKS) {
			Entity saviour = this.rescuer;
			this.rescuer = null;
			if (this.isAlive() && !saviour.removed) {
				this.remember(MemoryType.SAVED_MY_LIFE, saviour, 1.0F);
				this.voice.bark("thanks_saved", saviour, null, true);
			}
		}

		int offset = this.tickCount + this.id;
		if (offset % SURROUNDINGS_CHECK_INTERVAL == 0) {
			this.noticeSurroundings();
		}

		if (offset % FOLLOW_CHECK_INTERVAL == 0) {
			this.reconsiderFollowing();
		}

		if (offset % LEADER_WATCH_INTERVAL == 0) {
			this.watchLeader();
		}

		if (offset % BRAWL_CHECK_INTERVAL == 0) {
			this.considerBrawl();
		}

		if (offset % ROMANCE_INTERVAL == 0) {
			Romance.tick(this);
		}

		if (offset % SURROUNDINGS_MOOD_INTERVAL == 0) {
			this.feelSurroundings();
		}

		if (this.pendingRemark != null && this.tickCount >= this.pendingRemarkTick) {
			String remark = this.pendingRemark;
			this.pendingRemark = null;
			if (!this.isBusy()) {
				this.voice.bark(remark, this.getLeader(), null, false);
			}
		}

		if (offset % MEMORY_PRUNE_INTERVAL == 0) {
			this.memories.prune(this.world.getWorldTime());
		}
	}

	/**
	 * Notices when the weather turns, the season changes, or it walks into a new biome, and maybe says something
	 * about it a moment later. The stronger its feelings about the new surroundings, the likelier it speaks up.
	 */
	private void noticeSurroundings() {
		String weather = Surroundings.weatherAt(this.world, this.x, this.y + this.getHeadHeight(), this.z);
		String biome = Surroundings.placeAt(this.world, this.x, this.y, this.z);
		String season = Surroundings.seasonOf(this.world);
		if (this.lastWeather != null && !weather.equals(this.lastWeather)) {
			this.considerRemark("weather_change", Surroundings.Kind.WEATHER, weather, 300);
		} else if (this.lastSeason != null && season != null && !season.equals(this.lastSeason)) {
			this.considerRemark("season_change", Surroundings.Kind.SEASON, season, 600);
		} else if (this.lastBiome != null && biome != null && !biome.equals(this.lastBiome)
			&& this.tickCount - this.lastBiomeRemarkTick >= BIOME_REMARK_COOLDOWN) {
			this.lastBiomeRemarkTick = this.tickCount;
			this.considerRemark("biome_enter", Surroundings.Kind.BIOME, biome, 60);
		}

		this.lastWeather = weather;
		this.lastBiome = biome;
		this.lastSeason = season;
	}

	private void considerRemark(String event, Surroundings.Kind kind, String key, int maxDelay) {
		float like = this.personality.tastes.like(kind, key);
		if (this.random.nextFloat() < 0.25F + 0.6F * Math.abs(like)) {
			this.pendingRemark = event;
			this.pendingRemarkTick = this.tickCount + 20 + this.random.nextInt(maxDelay);
		}
	}

	/** Weather it likes cheers it up while it's out in it; living somewhere it loves (or hates) colors its mood. */
	private void feelSurroundings() {
		if (this.world.canBlockSeeSky(new TilePos(MathHelper.floor(this.x), MathHelper.floor(this.y + this.getHeadHeight()), MathHelper.floor(this.z)))) {
			float like = this.personality.tastes.like(Surroundings.Kind.WEATHER,
				Surroundings.weatherAt(this.world, this.x, this.y + this.getHeadHeight(), this.z));
			if (like >= 0.3F) {
				this.remember(MemoryType.ENJOYED_THE_WEATHER, null, like);
			} else if (like <= -0.3F) {
				this.remember(MemoryType.MISERABLE_WEATHER, null, -like);
			}
		}

		String biome = Surroundings.placeAt(this.world, this.x, this.y, this.z);
		if (biome != null) {
			float like = this.personality.tastes.like(Surroundings.Kind.BIOME, biome);
			if (like >= 0.3F) {
				this.remember(MemoryType.LIKES_IT_HERE, null, like);
			} else if (like <= -0.3F) {
				this.remember(MemoryType.HATES_IT_HERE, null, -like);
			}
		}
	}

	// Social life

	public UUID getUuid() {
		return this.uuid;
	}

	@Nullable
	public Romance.Request getRomanceRequest() {
		return this.romanceRequest;
	}

	/** What it wants to ask this player, if anything. */
	@Nullable
	public Romance.Request getRomanceRequest(Player player) {
		return this.romanceRequest != null && player.uuid.equals(this.romanceRequestPlayer) ? this.romanceRequest : null;
	}

	public void setRomanceRequest(@Nullable Romance.Request request, @Nullable Player player) {
		this.romanceRequest = request;
		this.romanceRequestPlayer = player != null ? player.uuid : null;
	}

	/** The in-game day this player last gave it a gift; -1 if never. */
	public long getLastGiftDay(UUID player) {
		return this.lastGiftDays.getOrDefault(player, -1L);
	}

	public void setLastGiftDay(UUID player, long day) {
		this.lastGiftDays.put(player, day);
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

	/** Someone killed whatever was attacking this badly hurt NPC; if it lives another ten seconds, they saved its life. */
	public void noticeRescue(Entity rescuer) {
		this.rescuer = rescuer;
		this.rescueTick = this.tickCount;
	}

	public boolean isBadlyHurt() {
		return this.getHealth() < this.getMaxHealth() * RESCUE_HEALTH;
	}

	/** Too occupied to start a conversation: fighting, brawling, talking to someone, or lying on the ground. */
	public boolean isBusy() {
		return this.talkingTo != null || this.isDowned() || this.brawl != null || this.brain.getActive() instanceof CombatBehavior;
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

		int slot = this.findFavoriteFood();
		if (slot < 0) {
			return;
		}

		this.inventory.swapWithHand(slot);
		this.eatReturnSlot = slot == NpcInventory.HAND_SLOT ? -1 : slot;
		this.eatTicks = EAT_HOLD_TICKS;
	}

	/** The slot holding the carried food this NPC likes most, or -1 if it has none. */
	private int findFavoriteFood() {
		int best = -1;
		float bestLike = Float.NEGATIVE_INFINITY;
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (stack != null && Tastes.isFood(stack.getItem()) && ((ItemFood) stack.getItem()).getHealAmount(stack) > 0) {
				float like = this.personality.tastes.like(stack.getItem());
				if (like > bestLike) {
					best = slot;
					bestLike = like;
				}
			}
		}

		return best;
	}

	/** Good food lifts the mood and bad food sours it, twice as much for gluttons; sometimes the NPC says so. */
	private void reactToFood(ItemFood food) {
		float like = this.personality.tastes.like(food);
		float strength = Math.abs(like) * (this.personality.has(Quirk.GLUTTON) ? 2.0F : 1.0F);
		if (like >= 0.2F) {
			this.remember(MemoryType.ATE_FOOD_I_LIKE, null, strength);
		} else if (like <= -0.2F) {
			this.remember(MemoryType.ATE_FOOD_I_HATE, null, strength);
		}

		if (this.random.nextFloat() < 0.25F) {
			this.voice.bark("eat", null, context -> context.word("food", Word.ofFood(food)).fact("taste", tasteOf(like)), false);
		}
	}

	/** How a liking sounds in speech files: love, like, meh, dislike or hate. */
	public static String tasteOf(float like) {
		if (like >= 0.6F) {
			return "love";
		} else if (like >= 0.2F) {
			return "like";
		} else if (like > -0.2F) {
			return "meh";
		}

		return like > -0.6F ? "dislike" : "hate";
	}

	/** Eats one of the held food (soup leaves its bowl behind), then switches back to what was held before. */
	private void finishEating() {
		ItemStack stack = this.getHeldItem();
		if (stack != null && stack.getItem() instanceof ItemFood food && this.wantsFood() && stack.consumeItem(null)) {
			this.eatFood(stack);
			this.reactToFood(food);
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

		// No chatting mid-fight, or with someone it means to fight: it says so instead.
		CombatBehavior combat = this.brain.getActive() instanceof CombatBehavior active ? active : null;
		boolean hostile = NpcFactions.regard(this, player) == FactionRelation.HOSTILE;
		if (combat != null || hostile || this.brawl != null) {
			if (this.tickCount - this.lastRefusalTick >= REFUSAL_COOLDOWN) {
				this.lastRefusalTick = this.tickCount;
				boolean fightingYou = combat != null && combat.getCurrentTarget() == player || this.isBrawlingWith(player);
				this.voice.bark("busy_fighting", player, context -> context.fact("fighting_you", fightingYou ? "yes" : "no")
					.fact("in_combat", combat != null || this.brawl != null ? "yes" : "no")
					.fact("brawling", this.brawl != null ? "yes" : "no"), true);
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

	/**
	 * A bare-fisted punch, like a player's with an empty hand, for brawls. Never takes a player below 1 health, and
	 * a player beaten down to their yield point loses.
	 */
	public boolean tryPunch(Mob target) {
		if (this.attackTime > 0 || this.isEating() || target.distanceToSqr(this) > MELEE_REACH * MELEE_REACH
			|| target.bb.maxY <= this.bb.minY || target.bb.minY >= this.bb.maxY) {
			return false;
		}

		this.attackTime = MELEE_COOLDOWN;
		this.swingArm();
		if (target instanceof Player && target.getHealth() <= 1) {
			return false;
		}

		boolean hit = target.hurt(this, 1, DamageType.COMBAT);
		if (hit && this.brawl != null && target instanceof Player) {
			this.brawl.checkYield(target);
		}

		return hit;
	}

	/** Moves a held weapon into the pack (if there's room), like putting it away for a fistfight. */
	public void putAwayWeapon() {
		ItemStack held = this.getHeldItem();
		if (held != null && (held.getDamageVsEntity(this) > 1 || held.getItem() instanceof ItemBow) && this.inventory.addToPack(held)) {
			this.setHeldItem(null);
		}
	}

	@Nullable
	public Brawl getBrawl() {
		return this.brawl;
	}

	public boolean isBrawlingWith(Entity entity) {
		return this.brawl != null && this.brawl.involves(entity);
	}

	public void joinBrawl(Brawl brawl) {
		this.brawl = brawl;
		this.talkingTo = null;
	}

	/** Leaves the brawl; punches from the forgiven opponent no longer count as a reason to fight them for real. */
	public void leaveBrawl(Brawl brawl, @Nullable Entity forgiven) {
		if (this.brawl == brawl) {
			this.brawl = null;
			this.lastBrawlOpponent = brawl.opponentOf(this);
			this.lastBrawlEndTick = this.tickCount;
		}

		if (forgiven != null && this.lastAttacker == forgiven) {
			this.lastAttacker = null;
		}
	}

	/** Whether this NPC still wants to fight back against someone who hit it recently (not counting brawl punches). */
	public boolean holdsGrudge(Entity mob, int grudgeTicks) {
		return mob == this.lastAttacker && this.ticksSinceAttacked() < grudgeTicks && !this.isBrawlingWith(mob);
	}

	/**
	 * Picks a fight, now and then, with someone nearby it can't stand: anyone it dislikes if it's a hothead,
	 * otherwise only someone who hates it right back. Never when hurt, busy, or outmatched by its own nerves.
	 */
	private void considerBrawl() {
		if (this.brawl != null || this.isBusy() || this.isRecovering() || this.isChild() || this.getHealth() < this.getMaxHealth() * 0.75F) {
			return;
		}

		boolean hothead = this.personality.has(Quirk.HOTHEAD);
		float threshold = hothead ? -20.0F : -40.0F;
		AABBd area = MathHelper.aabbGrow(this.bb, 8.0, 3.0, 8.0, new AABBd());
		for (Entity entity : this.world.getEntitiesWithinAABBExcludingEntity(this, area)) {
			if (!(entity instanceof Player) && !(entity instanceof EntityNPC) || !this.canEntityBeSeen(entity)
				|| NpcFactions.regard(this, entity) == FactionRelation.HOSTILE) {
				continue;
			}

			Mob other = (Mob) entity;
			float opinion = Opinions.of(this, other);
			if (opinion > threshold) {
				continue;
			}

			if (other instanceof EntityNPC npc && (npc.isBusy() || npc.getHealth() < npc.getMaxHealth() * 0.75F
				|| !hothead && Opinions.of(npc, this) > -40.0F)) {
				continue;
			}

			if (other instanceof Player && other.getHealth() <= Brawl.PLAYER_YIELD_HEALTH + 2) {
				continue;
			}

			float chance = 0.04F + (threshold - opinion) / 250.0F;
			chance *= hothead ? 2.0F : 1.0F;
			chance *= this.personality.has(Quirk.ZEN) ? 0.3F : 1.0F;
			chance *= this.personality.get(TraitAxis.COURAGE) <= -0.3F ? 0.3F : 1.0F;
			if (this.random.nextFloat() < chance) {
				if (Brawl.start(this, other, this) != null) {
					this.voice.bark("brawl_challenge", other, null, true);
				}

				return;
			}
		}
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

	// Growing up

	/** 0 for a newborn, 1 for an adult. */
	public float getGrowth() {
		return this.entityData.getByte(DATA_GROWTH) / 100.0F;
	}

	public boolean isChild() {
		return this.entityData.getByte(DATA_GROWTH) < 100;
	}

	/** Makes it this grown (0 newborn, 1 adult of 18), counting its birth back from now to match. */
	public void setGrowth(float growth) {
		growth = Math.max(0.0F, Math.min(1.0F, growth));
		this.entityData.set(DATA_GROWTH, (byte) Math.round(growth * 100.0F));
		this.birthTime = this.world.getWorldTime() - (long) (growth * Calendar.growUpTicks(this.world));
	}

	/** Gives an NPC with no birth date one: an adult between 18 and 60, or a child as grown as it was saved. */
	private void ensureBirth() {
		if (this.birthTime != UNKNOWN_BIRTH) {
			return;
		}

		if (this.isChild()) {
			this.setGrowth(this.getGrowth());
			return;
		}

		float spread = (float) this.random.nextGaussian();
		float age = TYPICAL_ADULT_AGE + spread * (spread < 0.0F ? YOUNGER_SPREAD : OLDER_SPREAD);
		int years = Math.max(MIN_ADULT_AGE, Math.min(MAX_ADULT_AGE, (int) age));
		long ticks = years * Calendar.ticksPerYear(this.world) + (long) (this.random.nextFloat() * Calendar.ticksPerYear(this.world));
		this.birthTime = this.world.getWorldTime() - ticks;
	}

	/** Its age in whole years. */
	public int getAgeYears() {
		this.ensureBirth();
		return Calendar.ageInYears(this.world, this.birthTime, this.world.getWorldTime());
	}

	/** Its birthday, like "Summer 5". */
	public String getBirthday() {
		this.ensureBirth();
		return Calendar.describeDate(this.world, this.birthTime);
	}

	/** Its birthday is today (but not the day it was born). */
	public boolean isBirthday() {
		this.ensureBirth();
		long now = this.world.getWorldTime();
		return Calendar.dayOfYear(this.world, this.birthTime) == Calendar.dayOfYear(this.world, now) && Calendar.day(this.birthTime) != Calendar.day(now);
	}

	/** Once a year, on the day: a good mood, and it tells whoever's around. */
	private void checkBirthday() {
		long year = Calendar.year(this.world, this.world.getWorldTime());
		if (this.isBirthday() && year != this.lastBirthdayYear) {
			this.lastBirthdayYear = year;
			this.remember(MemoryType.MY_BIRTHDAY, null, 1.0F);
			this.voice.bark("birthday", null, null, false);
		}
	}

	/** How big it's drawn and how big its hitbox is: half size as a newborn, full as an adult. */
	public float getSizeScale() {
		return 0.5F + 0.5F * this.getGrowth();
	}

	/** Grows a little older; on becoming an adult, it says so (and men may grow a beard). */
	private void grow() {
		float growth = (this.world.getWorldTime() - this.birthTime) / (float) Calendar.growUpTicks(this.world);
		if (growth >= 1.0F) {
			this.setGrowth(1.0F);
			Children.cameOfAge(this);
		} else {
			this.entityData.set(DATA_GROWTH, (byte) Math.max(0, Math.round(growth * 100.0F)));
		}
	}

	/** Keeps the hitbox matching its size as it grows. */
	private void updateSize() {
		int growth = this.entityData.getByte(DATA_GROWTH);
		if (growth != this.sizedForGrowth) {
			this.sizedForGrowth = growth;
			float scale = this.getSizeScale();
			this.setSize(ADULT_WIDTH * scale, ADULT_HEIGHT * scale);
			this.setPos(this.x, this.y, this.z);
		}
	}

	/** The look built from appearance parts, encoded; shown when the NPC has no skin file set. */
	public String getAppearance() {
		return this.entityData.getString(DATA_APPEARANCE);
	}

	public void setAppearance(Appearance appearance) {
		this.entityData.set(DATA_APPEARANCE, appearance.encode());
	}

	/**
	 * Gives an NPC with no skin file and no look yet a random one from the parts available (if there are any).
	 * Runs once after spawning or loading, so NPCs from before parts existed get a look once parts are added.
	 */
	public void rollAppearanceIfNeeded() {
		this.ensureHomeland();
		if (this.getSkin().isEmpty() && this.getAppearance().isEmpty()) {
			this.setAppearance(Appearance.random(PartCatalog.get(), this.gender, this.random, this.homeWarmth));
		}
	}

	/** Decides where it's from, if that isn't known yet: wherever it is now. */
	private void ensureHomeland() {
		if (!Float.isNaN(this.homeWarmth)) {
			return;
		}

		String biome = Surroundings.biomeAt(this.world, this.x, this.y, this.z);
		float warmth = Surroundings.warmthAt(this.world, this.x, this.y, this.z);
		if (biome != null && !Float.isNaN(warmth)) {
			this.homeBiome = biome;
			this.homeWarmth = warmth;
		}
	}

	/** The biome it comes from ("tundra"), or "" if unknown. */
	public String getHomeBiome() {
		return this.homeBiome;
	}

	/** How warm the place it comes from is, 0 cold to 1 hot; NaN if unknown. */
	public float getHomeWarmth() {
		return this.homeWarmth;
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

	/** The player this NPC is following, if it's following someone who is here (not while recovering at home). */
	@Nullable
	public Player getLeader() {
		if (this.isRecovering()) {
			return null;
		}

		FollowPlayerBehavior follow = this.brain.findBehavior(FollowPlayerBehavior.class);
		return follow != null ? follow.getPlayer(this) : null;
	}

	/**
	 * Whether it would come along if this player asked: always for allies (its own faction, or a close friend),
	 * never for enemies or while recovering, otherwise if it likes them enough. Outgoing and loyal NPCs need less
	 * convincing, lazy ones more.
	 */
	public boolean willFollow(Player player) {
		if (this.isRecovering()) {
			return false;
		}

		FactionRelation relation = NpcFactions.regard(this, player);
		if (relation != FactionRelation.NEUTRAL) {
			return relation == FactionRelation.ALLY;
		}

		float threshold = 25.0F - 10.0F * this.personality.get(TraitAxis.SOCIABILITY) - 10.0F * this.personality.get(TraitAxis.WORK_ETHIC)
			- (this.personality.has(Quirk.LOYAL) ? 5.0F : 0.0F);
		return Opinions.of(this, player) >= Math.max(10.0F, Math.min(45.0F, threshold));
	}

	/** Whether it lets this player see its things and tell it how to fight: its leader, an ally, or a good friend. */
	public boolean trusts(Player player) {
		return this.getLeader() == player || NpcFactions.regard(this, player) == FactionRelation.ALLY
			|| Opinions.of(this, player) >= Opinions.FRIEND_THRESHOLD;
	}

	public void startFollowing(Player player) {
		this.brain.set(new FollowPlayerBehavior(player.uuid));
	}

	/** Goes back to wandering around home. */
	public void stopFollowing() {
		this.brain.set(new WanderBehavior());
	}

	/**
	 * A follower keeps an eye on its leader and comments now and then: going for a swim, climbing somewhere the air
	 * is thin, getting badly hurt, or picking up something valuable (or something this NPC loves).
	 */
	private void watchLeader() {
		Player leader = this.getLeader();
		if (leader == null) {
			this.leaderValuables = null;
			return;
		}

		if (this.isBusy()) {
			return;
		}

		if (leader.isInWater()) {
			this.remarkOnLeader("remark_swim", leader, 3000, 0.6F, null);
		} else if (leader.y > this.world.getWorldType().getOceanY() + HIGH_UP_ABOVE_SEA) {
			this.remarkOnLeader("remark_high_up", leader, 6000, 0.6F, null);
		}

		if (leader.getHealth() > 0 && leader.getHealth() < leader.getMaxHealth() * RESCUE_HEALTH) {
			this.remarkOnLeader("remark_leader_hurt", leader, 1200, 0.8F, null);
		}

		Map<Item, Integer> valuables = this.countValuables(leader);
		if (this.leaderValuables != null) {
			for (Map.Entry<Item, Integer> entry : valuables.entrySet()) {
				if (entry.getValue() > this.leaderValuables.getOrDefault(entry.getKey(), 0)) {
					Item item = entry.getKey();
					float like = this.personality.tastes.like(item);
					boolean favorite = item == this.personality.tastes.favoriteThing();
					this.remarkOnLeader("remark_leader_found", leader, 600, favorite ? 1.0F : 0.7F, context -> context
						.word("item", Word.ofThing(item)).fact("taste", tasteOf(like)).fact("is_favorite", favorite ? "yes" : "no"));
					break;
				}
			}
		}

		this.leaderValuables = valuables;
	}

	private Map<Item, Integer> countValuables(Player player) {
		Map<Item, Integer> counts = new HashMap<>();
		Item favorite = this.personality.tastes.favoriteThing();
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack != null && (ItemDefaults.of(stack.getItem()).is(TasteCategory.VALUABLE) || stack.getItem() == favorite)) {
				counts.merge(stack.getItem(), stack.stackSize, Integer::sum);
			}
		}

		return counts;
	}

	/** Says something about the leader, if this kind of remark isn't on cooldown and the chance comes up. */
	private void remarkOnLeader(String event, Player leader, int cooldown, float chance, @Nullable Consumer<SpeechContext> extras) {
		if (this.tickCount < this.remarkReady.getOrDefault(event, 0)) {
			return;
		}

		this.remarkReady.put(event, this.tickCount + cooldown);
		if (this.random.nextFloat() < chance) {
			this.voice.bark(event, leader, extras, false);
		}
	}

	/** A follower that's fallen out with its leader says so and leaves. */
	private void reconsiderFollowing() {
		Player leader = this.getLeader();
		if (leader == null) {
			return;
		}

		FactionRelation relation = NpcFactions.regard(this, leader);
		if (relation != FactionRelation.ALLY && (relation == FactionRelation.HOSTILE || Opinions.of(this, leader) <= QUIT_FOLLOWING_OPINION)) {
			this.voice.bark("follow_quit", leader, null, true);
			this.stopFollowing();
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

	public HealthMode getHealthMode() {
		return this.healthMode;
	}

	/** The health mode that actually applies: a mortal NPC carrying a guardian amulet is invincible. */
	public HealthMode getEffectiveHealthMode() {
		// Children are kept safe the same way an amulet keeps adults safe.
		if (this.healthMode == HealthMode.MORTAL && (this.inventory.hasAmulet() || this.isChild())) {
			return HealthMode.INVINCIBLE;
		}

		return this.healthMode;
	}

	public void setHealthMode(HealthMode healthMode) {
		this.healthMode = healthMode;
		if (healthMode == HealthMode.MORTAL && this.isDowned()) {
			this.getUp();
			this.stopRecovering();
		}
	}

	// Being knocked down (invincible NPCs)

	/** True while lying down after a blow that would have killed this invincible NPC. */
	public boolean isDowned() {
		return this.entityData.getByte(DATA_DOWNED) != 0;
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
		this.setHealthRaw(this.getMaxHealth());
		if (this.isDowned()) {
			this.getUp();
		}

		this.stopRecovering();
	}

	/**
	 * Instead of dying, the NPC drops to the ground for a few seconds. Lying there, it counts as out of the fight:
	 * enemies lose interest (it reports itself as not alive), and it can't be hurt further or act.
	 */
	private void knockDown() {
		this.downedTicks = DOWNED_DURATION;
		this.recoveringTicks = RECOVERY_DURATION;
		this.entityData.set(DATA_DOWNED, this.isPushedForward(this.incomingDamageSource) ? DOWNED_ON_FRONT : DOWNED_ON_BACK);
		this.talkingTo = null;
		this.navigator.stop();
		this.eatTicks = 0;
		this.eatReturnSlot = -1;
		this.remainingFireTicks = 0;
		this.rescuer = null;
		this.remember(MemoryType.NEARLY_DIED, null, 1.0F);
		this.voice.bark("downed", null, null, true);
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
			pushX = this.x - source.x;
			pushZ = this.z - source.z;
		}

		if (pushX * pushX + pushZ * pushZ < 1.0E-4) {
			return this.random.nextBoolean();
		}

		double yaw = Math.toRadians(this.yRot);
		return pushX * -Math.sin(yaw) + pushZ * Math.cos(yaw) > 0.0;
	}

	/** Back on its feet with whatever health it has left. If it went down somewhere deadly, it wakes up back home. */
	private void getUp() {
		this.downedTicks = 0;
		this.entityData.set(DATA_DOWNED, (byte) 0);
		if (this.isInLava() || this.isInWall() || this.isUnderAcidOrWater() || this.y < 0.0) {
			this.teleportTo(this.homeX, this.homeY, this.homeZ);
		}

		if (!this.world.isClientSide && this.isRecovering()) {
			this.voice.bark("get_up", null, null, true);
		}
	}

	@Override
	public boolean isAlive() {
		return super.isAlive() && !this.isDowned();
	}

	@Override
	protected boolean isMovementBlocked() {
		return super.isMovementBlocked() || this.isDowned();
	}

	/** Falling out of the world can't kill an invincible NPC either: it's brought home. */
	@Override
	public void outOfWorld() {
		if (this.getEffectiveHealthMode() == HealthMode.MORTAL) {
			super.outOfWorld();
		} else {
			this.teleportTo(this.homeX, this.homeY, this.homeZ);
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

	// Vanilla hooks

	@Override
	public boolean hurt(Entity attacker, int damage, DamageType type) {
		if (this.healthMode == HealthMode.INVULNERABLE || this.isDowned()) {
			return false;
		}

		boolean wasBadlyHurt = this.isBadlyHurt();
		Entity source = attacker instanceof Projectile projectile && projectile.owner != null ? projectile.owner : attacker;
		boolean person = source instanceof Player || source instanceof EntityNPC;
		boolean armed = attacker instanceof Projectile || isArmed(source);
		boolean brawlHit = false;
		boolean friendlyPunch = false;
		// A punch landing in the moment right after a brawl ends is a swing already thrown: it doesn't count.
		if (this.brawl == null && source != null && source == this.lastBrawlOpponent
			&& this.tickCount - this.lastBrawlEndTick < BRAWL_GRACE_TICKS) {
			return false;
		}

		// Hitting it again once a brawl is over isn't part of the brawl: that's a real attack.
		boolean cheapShot = this.brawl == null && source != null && source == this.lastBrawlOpponent
			&& this.tickCount - this.lastBrawlEndTick < CHEAP_SHOT_TICKS;
		if (cheapShot && type == DamageType.COMBAT) {
			this.lastBrawlOpponent = null;
			this.remember(MemoryType.HIT_ME, source, 1.0F);
			this.voice.bark("brawl_cheap_shot", source, null, true);
		} else if (person && source != this && type == DamageType.COMBAT) {
			if (this.isBrawlingWith(source)) {
				// Fists only: reaching for a weapon turns the brawl into a real fight.
				if (armed) {
					this.brawl.escalate((Mob) source);
				} else {
					brawlHit = true;
				}
			} else if (this.brawl == null && !armed && NpcFactions.regard(this, source) != FactionRelation.HOSTILE
				&& !(this.brain.getActive() instanceof CombatBehavior) && !this.isRecovering()) {
				// A punch from someone it isn't fighting. A friend gets one warning; otherwise it's a brawl.
				if (Opinions.of(this, source) >= 20.0F && this.tickCount - this.lastFriendlyPunchTick > SECOND_PUNCH_TICKS) {
					this.lastFriendlyPunchTick = this.tickCount;
					friendlyPunch = true;
				} else if (Brawl.start(this, (Mob) source, (Mob) source) != null) {
					brawlHit = true;
					this.voice.bark("brawl_accept", source, null, true);
				}
			}
		}

		this.incomingDamageSource = attacker;
		this.takingBrawlHit = brawlHit || friendlyPunch;
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
		if (brawlHit) {
			if (this.brawl != null) {
				this.brawl.checkYield(this);
			}
		} else if (friendlyPunch) {
			this.remember(MemoryType.HIT_ME, source, 0.3F);
			this.voice.bark("hit_by_friend", source, null, false);
		} else if (source != null && source != this) {
			this.lastAttacker = source;
			this.lastAttackedTick = this.tickCount;
			this.reactToHit(source);
		}

		if (!wasBadlyHurt && this.isBadlyHurt() && !this.isDowned()) {
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

		if (this.getEffectiveHealthMode() == HealthMode.INVINCIBLE && newDamage >= this.getHealth()) {
			newDamage = this.getHealth() - 1;
			this.knockDown();
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
		npc.putString("Appearance", this.getAppearance());
		npc.putLong("BirthTime", this.birthTime);
		npc.putLong("LastBirthday", this.lastBirthdayYear);
		npc.putString("HomeBiome", this.homeBiome);
		npc.putFloat("HomeWarmth", this.homeWarmth);
		npc.putByte("Growth", this.entityData.getByte(DATA_GROWTH));
		npc.putByte("SkinModel", (byte) this.getSkinModel().ordinal());
		npc.putString("HealthMode", this.healthMode.name());
		npc.putInt("DownedTicks", this.downedTicks);
		npc.putInt("RecoveringTicks", this.recoveringTicks);
		npc.putCompound("Personality", this.personality.save());
		npc.putString("Faction", this.faction != null ? this.faction : "");
		npc.putByte("Gender", (byte) this.gender.ordinal());
		npc.putByte("FollowDistance", (byte) this.followDistance.ordinal());
		npc.putByte("CombatStyle", (byte) this.combatStyle.ordinal());
		npc.putDouble("HomeX", this.homeX);
		npc.putDouble("HomeY", this.homeY);
		npc.putDouble("HomeZ", this.homeZ);
		npc.putList("Behaviors", this.brain.save());
		npc.putList("Inventory", this.inventory.save());
		npc.putList("PlayerRelations", this.relations.save());
		npc.putString("Uuid", this.uuid.toString());
		npc.putList("Memories", this.memories.save());
		npc.putCompound("Voice", this.voice.save());
		CompoundTag giftTag = new CompoundTag();
		for (Map.Entry<UUID, Long> entry : this.lastGiftDays.entrySet()) {
			giftTag.putLong(entry.getKey().toString(), entry.getValue());
		}

		npc.putCompound("GiftDays", giftTag);
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
		this.setSkin(npc.getString("Skin"));
		this.entityData.set(DATA_APPEARANCE, npc.getStringOrDefault("Appearance", ""));
		this.birthTime = npc.getLongOrDefault("BirthTime", UNKNOWN_BIRTH);
		this.lastBirthdayYear = npc.getLongOrDefault("LastBirthday", Long.MIN_VALUE);
		this.homeBiome = npc.getStringOrDefault("HomeBiome", "");
		this.homeWarmth = npc.containsKey("HomeWarmth") ? npc.getFloat("HomeWarmth") : Float.NaN;
		this.entityData.set(DATA_GROWTH, npc.containsKey("Growth") ? npc.getByte("Growth") : (byte) 100);
		this.setSkinModel(SkinModel.byId(npc.getByte("SkinModel")));
		HealthMode mode = HealthMode.byName(npc.getStringOrDefault("HealthMode", ""));
		this.healthMode = mode != null ? mode : HealthMode.MORTAL;
		this.downedTicks = npc.getIntegerOrDefault("DownedTicks", 0);
		this.recoveringTicks = npc.getIntegerOrDefault("RecoveringTicks", 0);
		this.entityData.set(DATA_DOWNED, this.downedTicks > 0 ? DOWNED_ON_BACK : 0);
		this.fallProgress = this.prevFallProgress = this.downedTicks > 0 ? FALL_TICKS : 0;
		if (npc.containsKey("Personality")) {
			this.personality.load(npc.getCompound("Personality"));
		}

		this.faction = NpcFactions.normalize(npc.getStringOrDefault("Faction", ""));
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

		try {
			if (npc.containsKey("Uuid")) {
				this.uuid = UUID.fromString(npc.getString("Uuid"));
			}
		} catch (IllegalArgumentException ignored) {
			// Keep the fresh id rather than fail to load.
		}

		if (npc.containsKey("Memories")) {
			this.memories.load(npc.getList("Memories"));
		}

		this.lastGiftDays.clear();
		if (npc.containsKey("GiftDays")) {
			CompoundTag giftTag = npc.getCompound("GiftDays");
			for (String key : giftTag.getValue().keySet()) {
				try {
					this.lastGiftDays.put(UUID.fromString(key), giftTag.getLong(key));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}

		if (npc.containsKey("Voice")) {
			this.voice.load(npc.getCompound("Voice"));
		}
	}
}
