package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;

/**
 * A self-contained piece of an NPC (its life, health, eating...): its own state, its own upkeep, and its own share
 * of the save. Parts write their values straight into the NPC's save compound, each with its own keys.
 */
public interface NpcPart {
	/** Regular upkeep, run every tick on the side that runs the game (never just to draw). */
	default void tick() {
	}

	void save(CompoundTag tag);

	void load(CompoundTag tag);
}
