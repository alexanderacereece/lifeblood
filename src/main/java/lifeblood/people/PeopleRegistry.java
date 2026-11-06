package lifeblood.people;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.event.LifeEvents;
import lifeblood.event.PartnershipEvent;
import lifeblood.world.WorldSystem;
import lifeblood.world.WorldSystems;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone who has lived in this world, saved with it: a record of each person (NPC or player) that outlasts their
 * entity, so people who are far away, unloaded or dead still count. Each record holds who they are (name, gender,
 * birth, homeland, faction), where they were last seen, and their family: parents and partnerships (dating,
 * engaged, married). Siblings, grandparents, cousins and in-laws are worked out from those (see family/Kinship).
 *
 * It also knows which NPCs are loaded right now, to find one by id without searching the world.
 */
public class PeopleRegistry extends SavedData {
	public static final String DATA_ID = "lifeblood_people";
	/** How long an NPC can go without ticking before it's taken to be unloaded. */
	private static final long LOADED_GRACE_TICKS = 40L;

	public enum Stage {
		DATING, ENGAGED, MARRIED
	}

	/** A person: who they are, where they were last seen, and their parents. Gender is null for players. */
	public static final class Person {
		public final UUID id;
		/** Their given name ("Ada"). */
		public String name;
		/** Their family name ("Thorne"); "" for players and anyone without one. */
		public String familyName = "";
		@Nullable
		public NpcGender gender;
		public boolean player;
		public boolean alive = true;
		public final List<UUID> parents = new ArrayList<>();
		/** When they were born (world time), if born in this world; -1 otherwise. */
		public long born = -1L;
		/** Their birth date (world time, usually before the world began); Long.MIN_VALUE if unknown. */
		public long birthTime = Long.MIN_VALUE;
		/** The biome they come from; "" if unknown. */
		public String homeBiome = "";
		/** Their faction; null if unaffiliated. */
		@Nullable
		public String faction;
		/** Where and when (world time) they were last seen; lastSeen is -1 if never. */
		public double lastX;
		public double lastY;
		public double lastZ;
		public int lastDimension;
		public long lastSeen = -1L;
		/** A spouse who died, remembered as such. */
		@Nullable
		public UUID lateSpouse;

		Person(UUID id) {
			this.id = id;
		}
	}

	/** Two people together, since when (world time), and how far along. */
	public static final class Partnership {
		public final UUID first;
		public final UUID second;
		public Stage stage;
		public long since;

		Partnership(UUID first, UUID second, Stage stage, long since) {
			this.first = first;
			this.second = second;
			this.stage = stage;
			this.since = since;
		}

		public UUID other(UUID id) {
			return id.equals(this.first) ? this.second : this.first;
		}
	}

	private final Map<UUID, Person> people = new LinkedHashMap<>();
	/** NPCs loaded right now, by id. Not saved; NPCs add themselves as they tick (see {@link #seen}). */
	private final Map<UUID, EntityNPC> loaded = new HashMap<>();
	/** The world this register belongs to, for the events it posts. Not saved; set whenever it's fetched. */
	@Nullable
	private World world;
	/** Each person's partnership, if they're in one (both partners map to the same object). */
	private final Map<UUID, Partnership> partnerships = new HashMap<>();

	/** Called by BTA through reflection when loading saved data. */
	public PeopleRegistry(String id) {
		super(id);
	}

	public static PeopleRegistry get(World world) {
		PeopleRegistry registry = (PeopleRegistry) world.getSavedData(PeopleRegistry.class, DATA_ID);
		if (registry == null) {
			registry = new PeopleRegistry(DATA_ID);
			world.setSavedData(DATA_ID, registry);
			registry.setDirty();
		}

		registry.world = world;
		return registry;
	}

	// Who's loaded, and keeping records current

	/**
	 * An NPC checking in: it's loaded, and its record is brought up to date (who it is, where it is). Called by
	 * every NPC as it ticks, now and then.
	 */
	public void seen(EntityNPC npc) {
		this.loaded.put(npc.getUuid(), npc);
		Person person = this.person(npc.getUuid(), npc.nickname, npc.getGender(), false);
		person.familyName = npc.life.getFamilyName();
		person.birthTime = npc.life.getBirthTime();
		person.homeBiome = npc.life.getHomeBiome();
		person.faction = npc.getFaction();
		person.lastX = npc.x;
		person.lastY = npc.y;
		person.lastZ = npc.z;
		person.lastDimension = npc.world.dimension.id;
		person.lastSeen = npc.world.getWorldTime();
		this.setDirty();
	}

	/** Every minute, NPCs that have stopped ticking (unloaded with their chunk) are let go of. */
	public static final WorldSystem UPKEEP = new WorldSystem() {
		@Override
		public int interval() {
			return 1200;
		}

		@Override
		public void tick(World world) {
			get(world).loaded.values().removeIf(npc -> !isStillLoaded(world, npc));
		}
	};

	/** The NPC with this id, if it's loaded in this world right now. */
	@Nullable
	public EntityNPC loaded(World world, UUID id) {
		EntityNPC npc = this.loaded.get(id);
		if (npc == null) {
			return null;
		}

		if (!isStillLoaded(world, npc)) {
			this.loaded.remove(id);
			return null;
		}

		return npc;
	}

	private static boolean isStillLoaded(World world, EntityNPC npc) {
		return !npc.removed && npc.world == world && WorldSystems.ticks(world) - npc.getLastTickTime() <= LOADED_GRACE_TICKS;
	}

	/** The person with this id, created if new; name and gender are kept up to date. */
	public Person person(UUID id, String name, @Nullable NpcGender gender, boolean player) {
		Person person = this.people.computeIfAbsent(id, Person::new);
		if (!name.equals(person.name) || person.gender != gender || person.player != player) {
			person.name = name;
			person.gender = gender;
			person.player = player;
			this.setDirty();
		}

		return person;
	}

	@Nullable
	public Person find(UUID id) {
		return this.people.get(id);
	}

	public Collection<Person> everyone() {
		return this.people.values();
	}

	/** Their given name, the one people call them by ("Ada"); "someone" if unknown. */
	public String nameOf(UUID id) {
		Person person = this.people.get(id);
		return person != null && person.name != null ? person.name : "someone";
	}

	/** Their given and family name ("Ada Thorne"), or just the given name if they have no family name. */
	public String fullNameOf(UUID id) {
		Person person = this.people.get(id);
		return person == null || person.familyName.isEmpty() ? this.nameOf(id) : this.nameOf(id) + " " + person.familyName;
	}

	// Parents and children

	/** Records that parent is a parent of child (at most two parents each). */
	public boolean addParent(UUID child, UUID parent) {
		Person person = this.people.get(child);
		if (person == null || child.equals(parent) || person.parents.contains(parent) || person.parents.size() >= 2) {
			return false;
		}

		person.parents.add(parent);
		this.setDirty();
		return true;
	}

	public void removeParent(UUID child, UUID parent) {
		Person person = this.people.get(child);
		if (person != null && person.parents.remove(parent)) {
			this.setDirty();
		}
	}

	public List<UUID> parentsOf(UUID id) {
		Person person = this.people.get(id);
		return person != null ? person.parents : List.of();
	}

	public List<UUID> childrenOf(UUID id) {
		List<UUID> children = new ArrayList<>();
		for (Person person : this.people.values()) {
			if (person.parents.contains(id)) {
				children.add(person.id);
			}
		}

		return children;
	}

	/** Brothers and sisters, including half-siblings: anyone sharing a parent. */
	public Set<UUID> siblingsOf(UUID id) {
		Set<UUID> siblings = new LinkedHashSet<>();
		for (UUID parent : this.parentsOf(id)) {
			siblings.addAll(this.childrenOf(parent));
		}

		siblings.remove(id);
		return siblings;
	}

	// Partnerships

	@Nullable
	public Partnership partnershipOf(UUID id) {
		return this.partnerships.get(id);
	}

	@Nullable
	public UUID partnerOf(UUID id) {
		Partnership partnership = this.partnerships.get(id);
		return partnership != null ? partnership.other(id) : null;
	}

	@Nullable
	public UUID spouseOf(UUID id) {
		Partnership partnership = this.partnerships.get(id);
		return partnership != null && partnership.stage == Stage.MARRIED ? partnership.other(id) : null;
	}

	/** Starts dating; both must be single. */
	public boolean startDating(UUID first, UUID second, long now) {
		if (first.equals(second) || this.partnerships.containsKey(first) || this.partnerships.containsKey(second)) {
			return false;
		}

		Partnership partnership = new Partnership(first, second, Stage.DATING, now);
		this.partnerships.put(first, partnership);
		this.partnerships.put(second, partnership);
		this.setDirty();
		LifeEvents.post(new PartnershipEvent(this.world, first, second, Stage.DATING.name().toLowerCase(Locale.ROOT)));
		return true;
	}

	/**
	 * Records a couple who were already together before anyone saw it happen (a generated household): no news, no
	 * reactions. Both must be single.
	 */
	public boolean recordPartnership(UUID first, UUID second, Stage stage, long since) {
		if (first.equals(second) || this.partnerships.containsKey(first) || this.partnerships.containsKey(second)) {
			return false;
		}

		Partnership partnership = new Partnership(first, second, stage, since);
		this.partnerships.put(first, partnership);
		this.partnerships.put(second, partnership);
		this.setDirty();
		return true;
	}

	/** Moves a couple to the next stage (or straight to one, by command), restarting the clock. */
	public void setStage(UUID id, Stage stage, long now) {
		Partnership partnership = this.partnerships.get(id);
		if (partnership != null) {
			partnership.stage = stage;
			partnership.since = now;
			this.setDirty();
			LifeEvents.post(new PartnershipEvent(this.world, partnership.first, partnership.second, stage.name().toLowerCase(Locale.ROOT)));
		}
	}

	/** Ends someone's partnership, whatever its stage. */
	public void breakUp(UUID id) {
		Partnership partnership = this.partnerships.get(id);
		if (partnership != null) {
			this.partnerships.remove(partnership.first);
			this.partnerships.remove(partnership.second);
			this.setDirty();
			LifeEvents.post(new PartnershipEvent(this.world, partnership.first, partnership.second, null));
		}
	}

	/** Someone died: their partnership ends, and a widowed spouse remembers them as their late husband or wife. */
	public void died(UUID id) {
		Person person = this.people.get(id);
		if (person == null) {
			return;
		}

		person.alive = false;
		Partnership partnership = this.partnerships.get(id);
		if (partnership != null) {
			Person survivor = this.people.get(partnership.other(id));
			if (survivor != null && partnership.stage == Stage.MARRIED) {
				survivor.lateSpouse = id;
			}

			this.breakUp(id);
		}

		this.setDirty();
	}

	@Override
	public void load(CompoundTag tag) {
		this.people.clear();
		this.partnerships.clear();
		for (Tag<?> entry : tag.getList("People")) {
			if (!(entry instanceof CompoundTag personTag)) {
				continue;
			}

			try {
				Person person = new Person(UUID.fromString(personTag.getString("Id")));
				person.name = personTag.getStringOrDefault("Name", "someone");
				person.familyName = personTag.getStringOrDefault("FamilyName", "");
				person.gender = personTag.containsKey("Gender") ? NpcGender.byName(personTag.getString("Gender")) : null;
				person.player = personTag.getBoolean("Player");
				person.alive = !personTag.getBoolean("Dead");
				for (String parent : personTag.getStringOrDefault("Parents", "").split(",")) {
					if (!parent.isBlank()) {
						person.parents.add(UUID.fromString(parent));
					}
				}

				person.born = personTag.getLongOrDefault("Born", -1L);
				person.birthTime = personTag.getLongOrDefault("BirthTime", Long.MIN_VALUE);
				person.homeBiome = personTag.getStringOrDefault("HomeBiome", "");
				String faction = personTag.getStringOrDefault("Faction", "");
				person.faction = faction.isEmpty() ? null : faction;
				person.lastX = personTag.getDouble("LastX");
				person.lastY = personTag.getDouble("LastY");
				person.lastZ = personTag.getDouble("LastZ");
				person.lastDimension = personTag.getInteger("LastDimension");
				person.lastSeen = personTag.getLongOrDefault("LastSeen", -1L);
				if (personTag.containsKey("LateSpouse")) {
					person.lateSpouse = UUID.fromString(personTag.getString("LateSpouse"));
				}

				this.people.put(person.id, person);
			} catch (IllegalArgumentException ignored) {
			}
		}

		for (Tag<?> entry : tag.getList("Couples")) {
			if (!(entry instanceof CompoundTag coupleTag)) {
				continue;
			}

			try {
				UUID first = UUID.fromString(coupleTag.getString("First"));
				UUID second = UUID.fromString(coupleTag.getString("Second"));
				Stage stage = Stage.valueOf(coupleTag.getStringOrDefault("Stage", "DATING"));
				Partnership partnership = new Partnership(first, second, stage, coupleTag.getLongOrDefault("Since", 0L));
				this.partnerships.put(first, partnership);
				this.partnerships.put(second, partnership);
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void save(CompoundTag tag) {
		ListTag people = new ListTag();
		for (Person person : this.people.values()) {
			CompoundTag personTag = new CompoundTag();
			personTag.putString("Id", person.id.toString());
			personTag.putString("Name", person.name != null ? person.name : "someone");
			personTag.putString("FamilyName", person.familyName);
			if (person.gender != null) {
				personTag.putString("Gender", person.gender.name());
			}

			personTag.putBoolean("Player", person.player);
			personTag.putBoolean("Dead", !person.alive);
			List<String> parents = new ArrayList<>();
			for (UUID parent : person.parents) {
				parents.add(parent.toString());
			}

			personTag.putString("Parents", String.join(",", parents));
			if (person.lateSpouse != null) {
				personTag.putString("LateSpouse", person.lateSpouse.toString());
			}

			personTag.putLong("Born", person.born);
			personTag.putLong("BirthTime", person.birthTime);
			personTag.putString("HomeBiome", person.homeBiome);
			personTag.putString("Faction", person.faction != null ? person.faction : "");
			personTag.putDouble("LastX", person.lastX);
			personTag.putDouble("LastY", person.lastY);
			personTag.putDouble("LastZ", person.lastZ);
			personTag.putInt("LastDimension", person.lastDimension);
			personTag.putLong("LastSeen", person.lastSeen);

			people.addTag(personTag);
		}

		ListTag couples = new ListTag();
		Set<Partnership> seen = new LinkedHashSet<>(this.partnerships.values());
		for (Partnership partnership : seen) {
			CompoundTag coupleTag = new CompoundTag();
			coupleTag.putString("First", partnership.first.toString());
			coupleTag.putString("Second", partnership.second.toString());
			coupleTag.putString("Stage", partnership.stage.name());
			coupleTag.putLong("Since", partnership.since);
			couples.addTag(coupleTag);
		}

		tag.putList("People", people);
		tag.putList("Couples", couples);
	}
}
