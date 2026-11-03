package npclaude.ownership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.nbt.tags.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkOwnersTest {
	@Test
	void recordsSurviveSavingAndLoading() {
		UUID ann = UUID.randomUUID();
		UUID tom = UUID.randomUUID();
		ChunkOwners owners = new ChunkOwners();
		owners.set(ChunkOwners.index(0, 0, 0), ann);
		owners.set(ChunkOwners.index(15, 255, 15), ann);
		owners.set(ChunkOwners.index(3, 64, 9), tom);
		owners.set(ChunkOwners.index(4, 64, 9), tom);
		owners.set(ChunkOwners.index(4, 64, 9), null);

		CompoundTag tag = new CompoundTag();
		owners.save(tag);
		ChunkOwners loaded = new ChunkOwners();
		loaded.load(tag);

		assertEquals(3, loaded.count());
		assertEquals(ann, loaded.get(ChunkOwners.index(0, 0, 0)));
		assertEquals(ann, loaded.get(ChunkOwners.index(15, 255, 15)));
		assertEquals(tom, loaded.get(ChunkOwners.index(3, 64, 9)));
		assertNull(loaded.get(ChunkOwners.index(4, 64, 9)));
		assertEquals(2, loaded.countOf(ann));
	}

	@Test
	void anEmptyChunkSavesNothing() {
		CompoundTag tag = new CompoundTag();
		new ChunkOwners().save(tag);
		ChunkOwners loaded = new ChunkOwners();
		loaded.load(tag);
		assertEquals(0, loaded.count());
		assertEquals(false, tag.containsKey("NPClaudeOwners"));
	}

	@Test
	void indexesAreDistinctWithinAChunk() {
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int y = 0; y < 256; y += 17) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					seen.add(ChunkOwners.index(x, y, z));
				}
			}
		}

		assertEquals(16 * 16 * 16, seen.size());
	}
}
