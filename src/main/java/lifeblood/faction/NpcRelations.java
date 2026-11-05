package lifeblood.faction;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.NpcPart;
import org.jetbrains.annotations.Nullable;

/**
 * One NPC's personal feelings toward individual players. These override the faction's view of that player;
 * players without an entry are judged by faction alone.
 */
public class NpcRelations implements NpcPart {
	private final Map<UUID, FactionRelation> players = new LinkedHashMap<>();

	/** The personal relation with this player, or null if none is set (the faction decides). */
	@Nullable
	public FactionRelation get(Player player) {
		return player.uuid != null ? this.players.get(player.uuid) : null;
	}

	/** Sets a personal relation, or with null clears it so the faction decides again. */
	public void set(Player player, @Nullable FactionRelation relation) {
		if (player.uuid == null) {
			return;
		}

		if (relation == null) {
			this.players.remove(player.uuid);
		} else {
			this.players.put(player.uuid, relation);
		}
	}

	public Map<UUID, FactionRelation> all() {
		return Collections.unmodifiableMap(this.players);
	}

	private ListTag toTag() {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, FactionRelation> entry : this.players.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Player", entry.getKey().toString());
			tag.putString("Relation", entry.getValue().id());
			list.addTag(tag);
		}

		return list;
	}

	private void fromTag(ListTag list) {
		this.players.clear();
		for (Tag<?> entry : list) {
			if (!(entry instanceof CompoundTag tag)) {
				continue;
			}

			FactionRelation relation = FactionRelation.byName(tag.getString("Relation"));
			try {
				if (relation != null) {
					this.players.put(UUID.fromString(tag.getString("Player")), relation);
				}
			} catch (IllegalArgumentException ignored) {
				// Skip malformed ids rather than lose the rest.
			}
		}
	}

	/** Saved in the NPC's compound under "PlayerRelations". */
	@Override
	public void save(CompoundTag tag) {
		tag.putList("PlayerRelations", this.toTag());
	}

	@Override
	public void load(CompoundTag tag) {
		if (tag.containsKey("PlayerRelations")) {
			this.fromTag(tag.getList("PlayerRelations"));
		}
	}
}
