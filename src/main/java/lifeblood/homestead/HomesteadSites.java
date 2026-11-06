package lifeblood.homestead;

import java.util.Random;

/**
 * Where homesteads may go: the overworld is split into square cells, about 400 blocks across, and each cell has a few
 * chunks picked from the world seed where a homestead is tried. The first of those chunks to be generated with flat,
 * dry ground gets one, so a cell holds one homestead at most. Rare on purpose: towns and cities will fill the land later.
 */
public final class HomesteadSites {
	/** How many chunks across a cell is: 25 chunks, 400 blocks. */
	public static final int CELL_CHUNKS = 25;
	/** How many chunks in each cell get a try, in case the first ones are water or hills. */
	public static final int TRIES_PER_CELL = 8;
	/** Chunks this close to a cell's edge aren't tried, so homesteads in neighboring cells aren't side by side. */
	private static final int EDGE_CHUNKS = 3;
	private static final long CELL_X_MIX = 0x5DEECE66DL;
	private static final long CELL_Z_MIX = 0x2545F4914F6CDD1DL;

	private HomesteadSites() {
	}

	/** The cell a chunk is in, as one number. */
	public static long cellOf(int chunkX, int chunkZ) {
		return ((long) Math.floorDiv(chunkX, CELL_CHUNKS) << 32) ^ (Math.floorDiv(chunkZ, CELL_CHUNKS) & 0xFFFFFFFFL);
	}

	/** Whether a homestead is tried in this chunk: it's one of its cell's picked chunks. */
	public static boolean isTried(long seed, int chunkX, int chunkZ) {
		int cellX = Math.floorDiv(chunkX, CELL_CHUNKS);
		int cellZ = Math.floorDiv(chunkZ, CELL_CHUNKS);
		Random random = random(seed, cellX, cellZ);
		int inside = CELL_CHUNKS - 2 * EDGE_CHUNKS;
		for (int i = 0; i < TRIES_PER_CELL; i++) {
			int x = cellX * CELL_CHUNKS + EDGE_CHUNKS + random.nextInt(inside);
			int z = cellZ * CELL_CHUNKS + EDGE_CHUNKS + random.nextInt(inside);
			if (x == chunkX && z == chunkZ) {
				return true;
			}
		}

		return false;
	}

	/** The random numbers for one homestead in this chunk: the same for the same seed, so a world is repeatable. */
	public static Random random(long seed, int chunkX, int chunkZ) {
		// Mixed well, since Random's first numbers barely differ for seeds that are close together.
		long mixed = (seed ^ chunkX * CELL_X_MIX ^ chunkZ * CELL_Z_MIX) * 0x9E3779B97F4A7C15L;
		return new Random(mixed ^ (mixed >>> 31));
	}
}
