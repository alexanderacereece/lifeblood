package npclaude.ownership;

import com.mojang.nbt.tags.CompoundTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Who owns the placed blocks in one chunk, saved with the chunk. Only placed blocks have an entry; natural terrain
 * belongs to nobody. Chunks are saved on a background thread, so access is synchronized.
 */
public final class ChunkOwners {
	private static final String TAG = "NPClaudeOwners";

	/** Block index (see {@link #index}) to owner. Made on the first placed block, since most chunks have none. */
	@Nullable
	private Int2ObjectMap<UUID> owners;

	/** A block's index within its chunk, from chunk-local coordinates. */
	public static int index(int x, int y, int z) {
		return (y & 0xFF) << 8 | (z & 15) << 4 | (x & 15);
	}

	@Nullable
	public synchronized UUID get(int index) {
		return this.owners != null ? this.owners.get(index) : null;
	}

	public synchronized void set(int index, @Nullable UUID owner) {
		if (owner == null) {
			if (this.owners != null) {
				this.owners.remove(index);
			}

			return;
		}

		if (this.owners == null) {
			this.owners = new Int2ObjectOpenHashMap<>();
		}

		this.owners.put(index, owner);
	}

	public synchronized int count() {
		return this.owners != null ? this.owners.size() : 0;
	}

	/** How many blocks here this owner placed. */
	public synchronized int countOf(UUID owner) {
		if (this.owners == null) {
			return 0;
		}

		int count = 0;
		for (UUID each : this.owners.values()) {
			if (each.equals(owner)) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Writes the records into the chunk's tag: each owner once, as two longs, and each block as one long holding its
	 * index and which owner it is.
	 */
	public synchronized void save(CompoundTag chunkTag) {
		if (this.owners == null || this.owners.isEmpty()) {
			return;
		}

		Map<UUID, Integer> numbers = new HashMap<>();
		List<UUID> table = new ArrayList<>();
		long[] blocks = new long[this.owners.size()];
		int i = 0;
		for (Int2ObjectMap.Entry<UUID> entry : this.owners.int2ObjectEntrySet()) {
			int number = numbers.computeIfAbsent(entry.getValue(), owner -> {
				table.add(owner);
				return table.size() - 1;
			});
			blocks[i++] = (long) entry.getIntKey() << 32 | number;
		}

		long[] ids = new long[table.size() * 2];
		for (int n = 0; n < table.size(); n++) {
			ids[n * 2] = table.get(n).getMostSignificantBits();
			ids[n * 2 + 1] = table.get(n).getLeastSignificantBits();
		}

		CompoundTag tag = new CompoundTag();
		tag.putLongArray("Owners", ids);
		tag.putLongArray("Blocks", blocks);
		chunkTag.putCompound(TAG, tag);
	}

	public synchronized void load(CompoundTag chunkTag) {
		this.owners = null;
		if (!chunkTag.containsKey(TAG)) {
			return;
		}

		CompoundTag tag = chunkTag.getCompound(TAG);
		long[] ids = tag.getLongArrayOrDefault("Owners", new long[0]);
		long[] blocks = tag.getLongArrayOrDefault("Blocks", new long[0]);
		UUID[] table = new UUID[ids.length / 2];
		for (int n = 0; n < table.length; n++) {
			table[n] = new UUID(ids[n * 2], ids[n * 2 + 1]);
		}

		for (long block : blocks) {
			int number = (int) block;
			if (number >= 0 && number < table.length) {
				this.set((int) (block >>> 32), table[number]);
			}
		}
	}
}
