package lifeblood.combat;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The factions of one world and how they regard others, saved with the world (BTA SavedData).
 *
 * Anyone, NPC or player, belongs to at most one faction; without one they are unaffiliated. How a faction's members
 * regard someone is decided in layers:
 * 1. members of the same faction are always allies;
 * 2. an explicit relation between two factions (a war, an alliance) applies both ways;
 * 3. otherwise the faction's default stance applies, toward other factions and unaffiliated people alike.
 * Unaffiliated people have no allegiances and regard everyone as neutral.
 *
 * New worlds start with settlers (default neutral) and raiders (default hostile: they attack anyone not of their own),
 * with an explicit war between the two. Factions can be added at any time, by commands now and by world generation later.
 */
public class FactionRegistry extends SavedData {
	public static final String DATA_ID = "lifeblood_factions";
	public static final String SETTLERS = "settlers";
	public static final String RAIDERS = "raiders";
	private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]{1,32}");

	/** Each faction and its default stance toward anyone without an explicit relation. */
	private final Map<String, FactionRelation> factions = new LinkedHashMap<>();
	/** Explicit relations between two different factions, keyed by both ids in alphabetical order. */
	private final Map<String, FactionRelation> relations = new LinkedHashMap<>();
	/** Which faction each player has joined. */
	private final Map<UUID, String> players = new LinkedHashMap<>();

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
		this.factions.put(SETTLERS, FactionRelation.NEUTRAL);
		this.factions.put(RAIDERS, FactionRelation.HOSTILE);
		this.relations.put(key(SETTLERS, RAIDERS), FactionRelation.HOSTILE);
		this.setDirty();
	}

	public static boolean isValidId(String id) {
		return VALID_ID.matcher(id).matches();
	}

	public boolean exists(String faction) {
		return this.factions.containsKey(faction);
	}

	public Collection<String> all() {
		return Collections.unmodifiableSet(this.factions.keySet());
	}

	/** Adds a faction with the given default stance; returns false if the name is taken or invalid. */
	public boolean create(String faction, FactionRelation defaultStance) {
		if (!isValidId(faction) || this.factions.containsKey(faction)) {
			return false;
		}

		this.factions.put(faction, defaultStance);
		this.setDirty();
		return true;
	}

	/** Removes a faction, its relations and its players' membership. NPCs still carrying its name keep each other as allies. */
	public boolean remove(String faction) {
		if (this.factions.remove(faction) == null) {
			return false;
		}

		this.relations.keySet().removeIf(key -> key.startsWith(faction + "|") || key.endsWith("|" + faction));
		this.players.values().removeIf(faction::equals);
		this.setDirty();
		return true;
	}

	public FactionRelation getDefault(String faction) {
		return this.factions.getOrDefault(faction, FactionRelation.NEUTRAL);
	}

	public void setDefault(String faction, FactionRelation stance) {
		if (this.factions.containsKey(faction)) {
			this.factions.put(faction, stance);
			this.setDirty();
		}
	}

	/** The explicit relation between two factions, or null if their defaults apply. */
	@Nullable
	public FactionRelation getExplicit(String a, String b) {
		return this.relations.get(key(a, b));
	}

	/** Sets (or with null, clears) the explicit relation between two different factions. */
	public void setExplicit(String a, String b, @Nullable FactionRelation relation) {
		if (a.equals(b)) {
			return;
		}

		if (relation == null) {
			this.relations.remove(key(a, b));
		} else {
			this.relations.put(key(a, b), relation);
		}

		this.setDirty();
	}

	/** How a member of one faction regards a member of another; null means unaffiliated. */
	public FactionRelation regard(@Nullable String from, @Nullable String to) {
		if (from == null) {
			return FactionRelation.NEUTRAL;
		}

		if (from.equals(to)) {
			return FactionRelation.ALLY;
		}

		if (to != null) {
			FactionRelation explicit = this.relations.get(key(from, to));
			if (explicit != null) {
				return explicit;
			}
		}

		return this.getDefault(from);
	}

	@Nullable
	public String getPlayerFaction(UUID player) {
		return this.players.get(player);
	}

	/** Puts a player in a faction, or with null makes them unaffiliated. */
	public void setPlayerFaction(UUID player, @Nullable String faction) {
		if (faction == null) {
			this.players.remove(player);
		} else {
			this.players.put(player, faction);
		}

		this.setDirty();
	}

	public int countPlayers(String faction) {
		int count = 0;
		for (String joined : this.players.values()) {
			if (joined.equals(faction)) {
				count++;
			}
		}

		return count;
	}

	/** Explicit relations involving this faction, by the other faction's name. */
	public Map<String, FactionRelation> explicitRelationsOf(String faction) {
		Map<String, FactionRelation> result = new LinkedHashMap<>();
		for (Map.Entry<String, FactionRelation> entry : this.relations.entrySet()) {
			String key = entry.getKey();
			int separator = key.indexOf('|');
			String a = key.substring(0, separator);
			String b = key.substring(separator + 1);
			if (a.equals(faction)) {
				result.put(b, entry.getValue());
			} else if (b.equals(faction)) {
				result.put(a, entry.getValue());
			}
		}

		return result;
	}

	private static String key(String a, String b) {
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}

	@Override
	public void load(CompoundTag tag) {
		this.factions.clear();
		this.relations.clear();
		this.players.clear();
		for (Tag<?> entry : tag.getListOrDefault("Factions", new ListTag())) {
			if (entry instanceof CompoundTag faction) {
				String id = faction.getString("Id");
				FactionRelation stance = FactionRelation.byName(faction.getString("Default"));
				if (stance == null) {
					// Saved before default stances existed: raiders were the aggressive faction.
					stance = id.equals(RAIDERS) ? FactionRelation.HOSTILE : FactionRelation.NEUTRAL;
				}

				this.factions.put(id, stance);
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

		for (Tag<?> entry : tag.getListOrDefault("Players", new ListTag())) {
			if (entry instanceof CompoundTag player) {
				try {
					this.players.put(UUID.fromString(player.getString("Player")), player.getString("Faction"));
				} catch (IllegalArgumentException ignored) {
					// Skip a malformed id rather than lose the rest.
				}
			}
		}
	}

	@Override
	public void save(CompoundTag tag) {
		ListTag factionList = new ListTag();
		for (Map.Entry<String, FactionRelation> faction : this.factions.entrySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Id", faction.getKey());
			entry.putString("Default", faction.getValue().id());
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

		ListTag playerList = new ListTag();
		for (Map.Entry<UUID, String> player : this.players.entrySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Player", player.getKey().toString());
			entry.putString("Faction", player.getValue());
			playerList.addTag(entry);
		}

		tag.putList("Factions", factionList);
		tag.putList("Relations", relationList);
		tag.putList("Players", playerList);
	}
}
