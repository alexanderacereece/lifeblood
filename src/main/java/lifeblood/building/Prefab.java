package lifeblood.building;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.util.helper.Direction;

/**
 * A saved build: every block in a box, exactly as it was. Each spot holds a block (by its name, so saves survive block
 * ids changing), its metadata (facing, halves, color...) and, for chests, signs, furnaces and the like, its tile entity
 * data. Air is saved too, so placing a prefab clears what was inside its box.
 *
 * Spots are numbered x first, then z, then y: see {@link #index}. The front is the side the build faces as saved
 * (usually where the door is); placing turns it to face wherever it's wanted.
 *
 * This class is only data, with no world access, so it can be tested on its own. {@link PrefabCapture} makes one from
 * the world and {@link PrefabPlacer} puts one into it.
 */
public final class Prefab {
	/** Bump when the saved format changes. */
	public static final int FORMAT = 1;

	public final String name;
	public final int width;
	public final int height;
	public final int depth;
	public final Direction front;
	/** Block names, each once; spots refer to them by position in this list. */
	private final List<String> palette;
	private final short[] blocks;
	private final short[] meta;
	/** Tile entity data by spot. */
	private final Map<Integer, CompoundTag> tileEntities;

	public Prefab(String name, int width, int height, int depth, Direction front, List<String> palette, short[] blocks, short[] meta,
		Map<Integer, CompoundTag> tileEntities) {
		if (width <= 0 || height <= 0 || depth <= 0 || blocks.length != width * height * depth || meta.length != blocks.length) {
			throw new IllegalArgumentException("Prefab " + name + " has the wrong number of blocks for its size");
		}

		this.name = name;
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.front = front;
		this.palette = List.copyOf(palette);
		this.blocks = blocks;
		this.meta = meta;
		this.tileEntities = new LinkedHashMap<>(tileEntities);
	}

	public int volume() {
		return this.blocks.length;
	}

	/** The number of a spot in the box. */
	public int index(int x, int y, int z) {
		return (y * this.depth + z) * this.width + x;
	}

	/** The block name at a spot. */
	public String blockAt(int x, int y, int z) {
		return this.palette.get(this.blocks[this.index(x, y, z)]);
	}

	/** The block at a spot, as its position in {@link #palette()}. */
	public int paletteIndexAt(int x, int y, int z) {
		return this.blocks[this.index(x, y, z)];
	}

	public int metaAt(int x, int y, int z) {
		return this.meta[this.index(x, y, z)] & 0xFFFF;
	}

	/** The tile entity data saved at a spot, or null. Its position fields are those of where it was saved. */
	public CompoundTag tileEntityAt(int x, int y, int z) {
		return this.tileEntities.get(this.index(x, y, z));
	}

	public List<String> palette() {
		return this.palette;
	}

	public Map<Integer, CompoundTag> tileEntities() {
		return Collections.unmodifiableMap(this.tileEntities);
	}

	/** How many spots hold each block name. */
	public Map<String, Integer> blockCounts() {
		int[] counts = new int[this.palette.size()];
		for (short block : this.blocks) {
			counts[block]++;
		}

		Map<String, Integer> result = new LinkedHashMap<>();
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				result.put(this.palette.get(i), counts[i]);
			}
		}

		return result;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Format", FORMAT);
		tag.putString("Name", this.name);
		tag.putInt("Width", this.width);
		tag.putInt("Height", this.height);
		tag.putInt("Depth", this.depth);
		tag.putString("Front", this.front.name());
		ListTag paletteList = new ListTag();
		for (String block : this.palette) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Block", block);
			paletteList.addTag(entry);
		}

		tag.putList("Palette", paletteList);
		tag.putShortArray("Blocks", this.blocks);
		tag.putShortArray("Meta", this.meta);
		ListTag tileList = new ListTag();
		for (Map.Entry<Integer, CompoundTag> entry : this.tileEntities.entrySet()) {
			CompoundTag wrapped = new CompoundTag();
			wrapped.putInt("Spot", entry.getKey());
			wrapped.putCompound("Data", entry.getValue());
			tileList.addTag(wrapped);
		}

		tag.putList("TileEntities", tileList);
		return tag;
	}

	/** Reads a saved prefab; throws IllegalArgumentException if the data doesn't make one. */
	public static Prefab load(CompoundTag tag) {
		List<String> palette = new ArrayList<>();
		for (Tag<?> entry : tag.getListOrDefault("Palette", new ListTag())) {
			if (entry instanceof CompoundTag block) {
				palette.add(block.getString("Block"));
			}
		}

		short[] blocks = tag.getShortArrayOrDefault("Blocks", new short[0]);
		for (short block : blocks) {
			if (block < 0 || block >= palette.size()) {
				throw new IllegalArgumentException("Prefab refers to a block missing from its palette");
			}
		}

		Map<Integer, CompoundTag> tileEntities = new LinkedHashMap<>();
		for (Tag<?> entry : tag.getListOrDefault("TileEntities", new ListTag())) {
			if (entry instanceof CompoundTag wrapped) {
				tileEntities.put(wrapped.getInteger("Spot"), wrapped.getCompound("Data"));
			}
		}

		Direction front;
		try {
			front = Direction.valueOf(tag.getStringOrDefault("Front", "SOUTH"));
		} catch (IllegalArgumentException e) {
			front = Direction.SOUTH;
		}

		return new Prefab(tag.getStringOrDefault("Name", "unnamed"), tag.getInteger("Width"), tag.getInteger("Height"), tag.getInteger("Depth"),
			Turns.indexOf(front) < 0 ? Direction.SOUTH : front, palette, blocks, tag.getShortArrayOrDefault("Meta", new short[0]), tileEntities);
	}
}
