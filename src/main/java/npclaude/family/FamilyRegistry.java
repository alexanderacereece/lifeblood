package npclaude.family;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import npclaude.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone's family, saved with the world: each person (NPC or player) and their parents, plus partnerships
 * (dating, engaged, married). Only these direct links are stored; siblings, grandparents, cousins and in-laws are
 * worked out from them (see {@link Kinship}). Kept at world level rather than on each NPC so relatives who are
 * far away, unloaded or dead still count.
 */
public class FamilyRegistry extends SavedData {
	public static final String DATA_ID = "npclaude_family";

	public enum Stage {
		DATING, ENGAGED, MARRIED
	}

	/** A person: name and gender (null for players, who don't set one), whether alive, and their parents. */
	public static final class Person {
		public final UUID id;
		public String name;
		@Nullable
		public NpcGender gender;
		public boolean player;
		public boolean alive = true;
		public final List<UUID> parents = new ArrayList<>();
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
	/** Each person's partnership, if they're in one (both partners map to the same object). */
	private final Map<UUID, Partnership> partnerships = new HashMap<>();

	/** Called by BTA through reflection when loading saved data. */
	public FamilyRegistry(String id) {
		super(id);
	}

	public static FamilyRegistry get(World world) {
		FamilyRegistry registry = (FamilyRegistry) world.getSavedData(FamilyRegistry.class, DATA_ID);
		if (registry == null) {
			registry = new FamilyRegistry(DATA_ID);
			world.setSavedData(DATA_ID, registry);
		}

		return registry;
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

	public String nameOf(UUID id) {
		Person person = this.people.get(id);
		return person != null && person.name != null ? person.name : "someone";
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
		return true;
	}

	/** Moves a couple to the next stage (or straight to one, by command), restarting the clock. */
	public void setStage(UUID id, Stage stage, long now) {
		Partnership partnership = this.partnerships.get(id);
		if (partnership != null) {
			partnership.stage = stage;
			partnership.since = now;
			this.setDirty();
		}
	}

	/** Ends someone's partnership, whatever its stage. */
	public void breakUp(UUID id) {
		Partnership partnership = this.partnerships.get(id);
		if (partnership != null) {
			this.partnerships.remove(partnership.first);
			this.partnerships.remove(partnership.second);
			this.setDirty();
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
				person.gender = personTag.containsKey("Gender") ? NpcGender.byName(personTag.getString("Gender")) : null;
				person.player = personTag.getBoolean("Player");
				person.alive = !personTag.getBoolean("Dead");
				for (String parent : personTag.getStringOrDefault("Parents", "").split(",")) {
					if (!parent.isBlank()) {
						person.parents.add(UUID.fromString(parent));
					}
				}

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
