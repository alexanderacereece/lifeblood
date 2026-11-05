package lifeblood.family;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import lifeblood.people.NamePool;
import lifeblood.personality.Surroundings;
import lifeblood.social.MemoryType;
import lifeblood.world.Calendar;

/**
 * An NPC's life story so far: its family name, when it was born, how grown up it is, its birthdays, its graying
 * hair, and where it's from.
 */
public final class NpcLife implements NpcPart {
	/** How often it checks its age: growing up, and whether it's its birthday (10 seconds). */
	private static final int CHECK_INTERVAL = 200;
	/** A birth time not decided yet (a brand new NPC, or one from before ages). */
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

	private final EntityNPC npc;
	/** When it was born (world time; long before the world began, for most adults). */
	private long birthTime = UNKNOWN_BIRTH;
	/** The last year it celebrated its birthday, so it happens once a year. */
	private long lastBirthdayYear = Long.MIN_VALUE;
	/** Where it's from: the biome it was first seen in (or born in), and how warm that is (NaN until known). */
	private String homeBiome = "";
	private float homeWarmth = Float.NaN;
	/** The family name it was born with, kept after marriage changes its name; "" until it has one. */
	private String birthFamilyName = "";
	/** The size its hitbox was last set for, so it's only resized when growth changes. */
	private int sizedForGrowth = -1;

	public NpcLife(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		if ((this.npc.tickCount + this.npc.id) % CHECK_INTERVAL == 0) {
			this.ensureBirth();
			this.ensureFamilyName();
			this.npc.looks.ageHair(this.getAgeYears());
			if (this.isChild()) {
				this.grow();
			}

			this.checkBirthday();
		}
	}

	// Family name

	/** Its family name ("Thorne"), the one it goes by now; "" until it has one. */
	public String getFamilyName() {
		return this.npc.getEntityData().getString(EntityNPC.DATA_FAMILY_NAME);
	}

	/** The family name it was born with: the same as now unless marriage changed it. */
	public String getBirthFamilyName() {
		return this.birthFamilyName.isEmpty() ? this.getFamilyName() : this.birthFamilyName;
	}

	/** Its given and family name together: "Ada Thorne". */
	public String getFullName() {
		String family = this.getFamilyName();
		return family.isEmpty() ? this.npc.nickname : this.npc.nickname + " " + family;
	}

	/** Its family name from birth (a child's from its parents, see FamilyNames). */
	public void setBirthFamilyName(String name) {
		this.birthFamilyName = name;
		this.npc.getEntityData().set(EntityNPC.DATA_FAMILY_NAME, name);
	}

	/** Takes a new family name, as on marrying; the one it was born with is remembered. */
	public void changeFamilyName(String name) {
		this.birthFamilyName = this.getBirthFamilyName();
		this.npc.getEntityData().set(EntityNPC.DATA_FAMILY_NAME, name);
	}

	/** Gives an NPC with no family name one from the name list: its family comes from somewhere outside this world. */
	public void ensureFamilyName() {
		if (this.getFamilyName().isEmpty()) {
			this.setBirthFamilyName(NamePool.pickFamilyName(this.npc.getRandom()));
		}
	}

	// Growing up

	/** 0 for a newborn, 1 for an adult. */
	public float getGrowth() {
		return this.npc.getEntityData().getByte(EntityNPC.DATA_GROWTH) / 100.0F;
	}

	/** Where it is in growing up (baby, little kid, young teen, older teen, adult), for how it talks. */
	public AgeStage getStage() {
		return AgeStage.of(this.getGrowth());
	}

	public boolean isChild() {
		return this.npc.getEntityData().getByte(EntityNPC.DATA_GROWTH) < 100;
	}

	/** Makes it this grown (0 newborn, 1 adult of 18), counting its birth back from now to match. */
	public void setGrowth(float growth) {
		growth = Math.max(0.0F, Math.min(1.0F, growth));
		this.npc.getEntityData().set(EntityNPC.DATA_GROWTH, (byte) Math.round(growth * 100.0F));
		this.birthTime = this.npc.world.getWorldTime() - (long) (growth * Calendar.growUpTicks(this.npc.world));
	}

	/** How big it's drawn and how big its hitbox is: half size as a newborn, full as an adult. */
	public float getSizeScale() {
		return 0.5F + 0.5F * this.getGrowth();
	}

	/** Grows a little older; on becoming an adult, it says so (and men may grow a beard). */
	private void grow() {
		float growth = (this.npc.world.getWorldTime() - this.birthTime) / (float) Calendar.growUpTicks(this.npc.world);
		if (growth >= 1.0F) {
			this.setGrowth(1.0F);
			Children.cameOfAge(this.npc);
		} else {
			this.npc.getEntityData().set(EntityNPC.DATA_GROWTH, (byte) Math.max(0, Math.round(growth * 100.0F)));
		}
	}

	/** Keeps the hitbox matching its size as it grows (on both sides, since the client moves it too). */
	public void updateSize() {
		int growth = this.npc.getEntityData().getByte(EntityNPC.DATA_GROWTH);
		if (growth != this.sizedForGrowth) {
			this.sizedForGrowth = growth;
			float scale = this.getSizeScale();
			this.npc.resize(ADULT_WIDTH * scale, ADULT_HEIGHT * scale);
		}
	}

	// Age and birthdays

	/** Gives an NPC with no birth date one: an adult on the age curve, or a child as grown as it was saved. */
	private void ensureBirth() {
		if (this.birthTime != UNKNOWN_BIRTH) {
			return;
		}

		if (this.isChild()) {
			this.setGrowth(this.getGrowth());
			return;
		}

		float spread = (float) this.npc.getRandom().nextGaussian();
		float age = TYPICAL_ADULT_AGE + spread * (spread < 0.0F ? YOUNGER_SPREAD : OLDER_SPREAD);
		int years = Math.max(MIN_ADULT_AGE, Math.min(MAX_ADULT_AGE, (int) age));
		long year = Calendar.ticksPerYear(this.npc.world);
		this.birthTime = this.npc.world.getWorldTime() - years * year - (long) (this.npc.getRandom().nextFloat() * year);
	}

	/** When it was born, in world time. */
	public long getBirthTime() {
		this.ensureBirth();
		return this.birthTime;
	}

	/** Its age in whole years. */
	public int getAgeYears() {
		return Calendar.ageInYears(this.npc.world, this.getBirthTime(), this.npc.world.getWorldTime());
	}

	/** Its birthday, like "Summer 5". */
	public String getBirthday() {
		return Calendar.describeDate(this.npc.world, this.getBirthTime());
	}

	/** Its birthday is today (but not the day it was born). */
	public boolean isBirthday() {
		long born = this.getBirthTime();
		long now = this.npc.world.getWorldTime();
		return Calendar.dayOfYear(this.npc.world, born) == Calendar.dayOfYear(this.npc.world, now) && Calendar.day(born) != Calendar.day(now);
	}

	/** Once a year, on the day: a good mood, and it tells whoever's around. */
	private void checkBirthday() {
		long year = Calendar.year(this.npc.world, this.npc.world.getWorldTime());
		if (this.isBirthday() && year != this.lastBirthdayYear) {
			this.lastBirthdayYear = year;
			this.npc.remember(MemoryType.MY_BIRTHDAY, null, 1.0F);
			this.npc.voice.bark("birthday", null, null, false);
		}
	}

	// Homeland

	/** Decides where it's from, if that isn't known yet: wherever it is now. */
	public void ensureHomeland() {
		if (!Float.isNaN(this.homeWarmth)) {
			return;
		}

		String biome = Surroundings.biomeAt(this.npc.world, this.npc.x, this.npc.y, this.npc.z);
		float warmth = Surroundings.warmthAt(this.npc.world, this.npc.x, this.npc.y, this.npc.z);
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

	@Override
	public String saveKey() {
		return "Life";
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putString("FamilyName", this.getFamilyName());
		tag.putString("BirthFamilyName", this.birthFamilyName);
		tag.putLong("BirthTime", this.birthTime);
		tag.putLong("LastBirthday", this.lastBirthdayYear);
		tag.putString("HomeBiome", this.homeBiome);
		tag.putFloat("HomeWarmth", this.homeWarmth);
		tag.putByte("Growth", this.npc.getEntityData().getByte(EntityNPC.DATA_GROWTH));
	}

	@Override
	public void load(CompoundTag tag) {
		this.npc.getEntityData().set(EntityNPC.DATA_FAMILY_NAME, tag.getStringOrDefault("FamilyName", ""));
		this.birthFamilyName = tag.getStringOrDefault("BirthFamilyName", "");
		this.birthTime = tag.getLongOrDefault("BirthTime", UNKNOWN_BIRTH);
		this.lastBirthdayYear = tag.getLongOrDefault("LastBirthday", Long.MIN_VALUE);
		this.homeBiome = tag.getStringOrDefault("HomeBiome", "");
		this.homeWarmth = tag.containsKey("HomeWarmth") ? tag.getFloat("HomeWarmth") : Float.NaN;
		this.npc.getEntityData().set(EntityNPC.DATA_GROWTH, tag.containsKey("Growth") ? tag.getByte("Growth") : (byte) 100);
	}
}
