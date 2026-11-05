package lifeblood.entity;

import com.mojang.nbt.tags.CompoundTag;

/**
 * A self-contained piece of an NPC (its life, health, eating...): its own state, its own upkeep, and its own section
 * of the save. Each part saves into a compound of its own, named by {@link #saveKey}, so parts can never overwrite each
 * other's data, and a part can name its keys however it likes.
 */
public interface NpcPart {
	/** Regular upkeep, run every tick on the side that runs the game (never just to draw). */
	default void tick() {
	}

	/** The name of this part's section in the NPC's save ("Health", "Life"). Unique among parts; never change it lightly. */
	String saveKey();

	/** Writes this part's state into its own, empty section. */
	void save(CompoundTag tag);

	/** Reads this part's state from its section; an NPC saved before the part existed gets an empty one. */
	void load(CompoundTag tag);
}
