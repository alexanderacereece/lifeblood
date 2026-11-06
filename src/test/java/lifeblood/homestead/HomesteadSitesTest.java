package lifeblood.homestead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HomesteadSitesTest {
	@Test
	void eachCellHasAFewTriedChunksAwayFromItsEdges() {
		long seed = 12345L;
		for (int cellX = -3; cellX <= 3; cellX++) {
			for (int cellZ = -3; cellZ <= 3; cellZ++) {
				int tried = 0;
				for (int dx = 0; dx < HomesteadSites.CELL_CHUNKS; dx++) {
					for (int dz = 0; dz < HomesteadSites.CELL_CHUNKS; dz++) {
						int chunkX = cellX * HomesteadSites.CELL_CHUNKS + dx;
						int chunkZ = cellZ * HomesteadSites.CELL_CHUNKS + dz;
						if (HomesteadSites.isTried(seed, chunkX, chunkZ)) {
							tried++;
							assertTrue(dx >= 3 && dx < HomesteadSites.CELL_CHUNKS - 3 && dz >= 3 && dz < HomesteadSites.CELL_CHUNKS - 3);
							assertEquals(HomesteadSites.cellOf(cellX * HomesteadSites.CELL_CHUNKS, cellZ * HomesteadSites.CELL_CHUNKS),
								HomesteadSites.cellOf(chunkX, chunkZ));
						}
					}
				}

				// Two picks can land on the same chunk, so up to the number of tries.
				assertTrue(tried >= 1 && tried <= HomesteadSites.TRIES_PER_CELL, "tried " + tried);
			}
		}
	}

	@Test
	void theSameSeedTriesTheSameChunks() {
		assertEquals(HomesteadSites.isTried(7L, 10, 10), HomesteadSites.isTried(7L, 10, 10));
	}
}
