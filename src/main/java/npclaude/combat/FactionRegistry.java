package npclaude.combat;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;

/**
 * The factions that exist in one world and how they regard each other, saved with the world
 * (in its data folder, through BTA's SavedData). New worlds start with two factions, settlers and raiders,
 * hostile to each other. Factions can be added at any time, by commands now and by world generation later.
 *
 * Factions with no relation set are neutral to each other.
 */
public class FactionRegistry extends SavedData {
	public static final String DATA_ID = "npclaude_factions";
	public static final String SETTLERS = "settlers";
	public static final String RAIDERS = "raiders";
	public static final String DEFAULT = SETTLERS;
	private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]{1,32}");

	private final Set<String> factions = new LinkedHashSet<>();
	/** Relations between two different factions, keyed by both ids in alphabetical order. */
	private final Map<String, FactionRelation> relations = new LinkedHashMap<>();

	/** Called by BTA through reflection when loading saved data. */
	public FactionRegistry(String id) {
		super(id);
	}

	public static FactionRegistry get(World world) {
		FactionRegistry registry = (FactionRegistry) world.getSavedData(FactionRegistry.class, DATA_ID);
		if (registry == null) {
			registry = new FactionRegistry(DATA_ID);
			registry.addStartingFactions();
			world.setSavedData(DATA_ID, registry);
		}

		return registry;
	}

	private void addStartingFactions() {
		this.factions.add(SETTLERS);
		this.factions.add(RAIDERS);
		this.relations.put(key(SETTLERS, RAIDERS), FactionRelation.HOSTILE);
		this.setDirty();
	}

	public static boolean isValidId(String id) {
		return VALID_ID.matcher(id).matches();
	}

	public boolean exists(String faction) {
		return this.factions.contains(faction);
	}

	public Collection<String> all() {
		return Collections.unmodifiableSet(this.factions);
	}

	/** Adds a faction; returns false if it already exists. Its relations to everyone start out neutral. */
	public boolean create(String faction) {
		if (!isValidId(faction) || !this.factions.add(faction)) {
			return false;
		}

		this.setDirty();
		return true;
	}

	/** Removes a faction and its relations. NPCs still carrying its name behave as an unaligned, neutral group. */
	public boolean remove(String faction) {
		if (faction.equals(DEFAULT) || !this.factions.remove(faction)) {
			return false;
		}

		this.relations.keySet().removeIf(key -> key.startsWith(faction + "|") || key.endsWith("|" + faction));
		this.setDirty();
		return true;
	}

	public FactionRelation getRelation(String a, String b) {
		if (a.equals(b)) {
			return FactionRelation.ALLY;
		}

		return this.relations.getOrDefault(key(a, b), FactionRelation.NEUTRAL);
	}

	public void setRelation(String a, String b, FactionRelation relation) {
		if (a.equals(b)) {
			return;
		}

		if (relation == FactionRelation.NEUTRAL) {
			this.relations.remove(key(a, b));
		} else {
			this.relations.put(key(a, b), relation);
		}

		this.setDirty();
	}

	private static String key(String a, String b) {
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}

	@Override
	public void load(CompoundTag tag) {
		this.factions.clear();
		this.relations.clear();
		for (Tag<?> entry : tag.getListOrDefault("Factions", new ListTag())) {
			if (entry instanceof CompoundTag faction) {
				this.factions.add(faction.getString("Id"));
			}
		}

		for (Tag<?> entry : tag.getListOrDefault("Relations", new ListTag())) {
			if (entry instanceof CompoundTag relation) {
				FactionRelation value = FactionRelation.byName(relation.getString("Relation"));
				if (value != null) {
					this.relations.put(key(relation.getString("A"), relation.getString("B")), value);
				}
			}
		}

		if (!this.factions.contains(DEFAULT)) {
			this.factions.add(DEFAULT);
		}
	}

	@Override
	public void save(CompoundTag tag) {
		ListTag factionList = new ListTag();
		for (String faction : this.factions) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Id", faction);
			factionList.addTag(entry);
		}

		ListTag relationList = new ListTag();
		for (Map.Entry<String, FactionRelation> relation : this.relations.entrySet()) {
			String key = relation.getKey();
			int separator = key.indexOf('|');
			CompoundTag entry = new CompoundTag();
			entry.putString("A", key.substring(0, separator));
			entry.putString("B", key.substring(separator + 1));
			entry.putString("Relation", relation.getValue().id());
			relationList.addTag(entry);
		}

		tag.putList("Factions", factionList);
		tag.putList("Relations", relationList);
	}
}
