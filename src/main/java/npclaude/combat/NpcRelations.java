package npclaude.combat;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;

/**
 * One NPC's personal standing with individual players, separate from its faction's relations.
 * Anyone without an entry is neutral: the NPC neither counts on them nor attacks them, unless they attack first.
 */
public class NpcRelations {
	private final Map<UUID, FactionRelation> players = new LinkedHashMap<>();

	public FactionRelation get(Player player) {
		return player.uuid != null ? this.players.getOrDefault(player.uuid, FactionRelation.NEUTRAL) : FactionRelation.NEUTRAL;
	}

	public void set(Player player, FactionRelation relation) {
		if (player.uuid == null) {
			return;
		}

		if (relation == FactionRelation.NEUTRAL) {
			this.players.remove(player.uuid);
		} else {
			this.players.put(player.uuid, relation);
		}
	}

	public Map<UUID, FactionRelation> all() {
		return Collections.unmodifiableMap(this.players);
	}

	public ListTag save() {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, FactionRelation> entry : this.players.entrySet()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Player", entry.getKey().toString());
			tag.putString("Relation", entry.getValue().id());
			list.addTag(tag);
		}

		return list;
	}

	public void load(ListTag list) {
		this.players.clear();
		for (Tag<?> entry : list) {
			if (!(entry instanceof CompoundTag tag)) {
				continue;
			}

			FactionRelation relation = FactionRelation.byName(tag.getString("Relation"));
			try {
				if (relation != null && relation != FactionRelation.NEUTRAL) {
					this.players.put(UUID.fromString(tag.getString("Player")), relation);
				}
			} catch (IllegalArgumentException ignored) {
				// Skip malformed ids rather than lose the rest.
			}
		}
	}
}
