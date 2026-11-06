package lifeblood.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.Nullable;

/**
 * Fitting a prefab to the land the way the world places one: finding fairly flat, dry ground, sinking the floor layer
 * into it so the door is at ground level, filling any gap under the floor with the local ground so the land looks
 * leveled rather than built up, and clearing trees left hanging over the roof. Only loaded blocks are touched.
 */
public final class TerrainFit {
	/**
	 * The most the ground under a house may rise and fall, in blocks, for the site to count as fairly flat. Better than
	 * Adventure's land is rarely flatter than this over a house's width; the floor goes in at the middle height, so the
	 * house is cut into the high side and built up on the low side.
	 */
	public static final int MAX_UNEVENNESS = 4;
	/** How far below the floor a gap is filled, at most, in blocks. */
	private static final int MAX_FILL = 6;
	/** How far above the box hanging trees are cleared, in blocks. */
	private static final int CLEAR_ABOVE = 16;
	/** How far apart the spots tried within an area are, in blocks. */
	private static final int SEARCH_STEP = 2;

	private TerrainFit() {
	}

	/**
	 * A place for a prefab: the turned box's lowest corner, with y the floor level (the height of the ground block it
	 * replaces), how many quarter turns, and the block to fill gaps under the floor with.
	 */
	public record Site(int x, int y, int z, int turns, Block<?> ground) {
	}

	/**
	 * The best fairly flat, dry site for a prefab with its turned box inside the area (inclusive), or null if there's
	 * none: the flattest found, every column on solid ground with no water, ice or lava.
	 */
	@Nullable
	public static Site find(World world, Prefab prefab, int turns, int minX, int minZ, int maxX, int maxZ) {
		int[] size = PrefabPlacer.turnedSize(prefab, turns);
		Map<Long, Integer> surfaces = new HashMap<>();
		Site best = null;
		int bestSpread = Integer.MAX_VALUE;
		for (int x = minX; x + size[0] - 1 <= maxX; x += SEARCH_STEP) {
			for (int z = minZ; z + size[1] - 1 <= maxZ; z += SEARCH_STEP) {
				int[] heights = footprint(world, surfaces, x, z, size[0], size[1]);
				if (heights == null) {
					continue;
				}

				int spread = heights[1] - heights[0];
				if (spread <= MAX_UNEVENNESS && spread < bestSpread) {
					bestSpread = spread;
					best = new Site(x, heights[2], z, turns, groundBelow(world, x + size[0] / 2, heights[2], z + size[1] / 2));
				}
			}
		}

		return best;
	}

	/** The lowest, highest and middle ground height under a footprint, or null if any column isn't dry ground. */
	@Nullable
	private static int[] footprint(World world, Map<Long, Integer> surfaces, int x, int z, int width, int depth) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		List<Integer> heights = new ArrayList<>();
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				int y = surfaces.computeIfAbsent(((long) (x + dx) << 32) ^ ((z + dz) & 0xFFFFFFFFL), key -> surfaceY(world, (int) (key >> 32), (int) (long) key));
				if (y < 0) {
					return null;
				}

				min = Math.min(min, y);
				max = Math.max(max, y);
				heights.add(y);
			}
		}

		// The middle height: as much ground cut away on the high side as is built up on the low side.
		heights.sort(null);
		return new int[] {min, max, heights.get(heights.size() / 2)};
	}

	/**
	 * The height of the ground block in a column: the top solid block, looking through plants, snow, leaves and tree
	 * trunks. -1 if the column isn't loaded or the ground is under water, ice or lava.
	 */
	public static int surfaceY(World world, int x, int z) {
		TilePos pos = new TilePos(x, 0, z);
		if (!world.isBlockLoaded(pos)) {
			return -1;
		}

		for (int y = world.getHeightValue(x, z); y > 0; y--) {
			Material material = world.getBlockType(pos.set(x, y, z)).getMaterial();
			if (material.isLiquid() || material == Materials.ICE) {
				return -1;
			}

			if (material.isSolidBlocking() && material != Materials.WOOD) {
				return y;
			}
		}

		return -1;
	}

	/** What the ground is made of just under the surface (dirt under grass, sand under sand); dirt if that's not ground. */
	private static Block<?> groundBelow(World world, int x, int surfaceY, int z) {
		Block<?> below = world.getBlockType(new TilePos(x, surfaceY - 1, z));
		Material material = below.getMaterial();
		boolean ground = material == Materials.DIRT || material == Materials.SAND || material == Materials.GRASS || material.isStone();
		return ground && below != Blocks.GRASS ? below : Blocks.DIRT;
	}

	/**
	 * Readies the land around a placed prefab: fills any gap under its floor with the local ground, and clears logs,
	 * leaves and snow left hanging over its roof. Call it after placing.
	 */
	public static void settle(World world, Prefab prefab, Site site) {
		TilePos pos = new TilePos();
		for (int z = 0; z < prefab.depth; z++) {
			for (int x = 0; x < prefab.width; x++) {
				PrefabPlacer.spotPosition(prefab, x, 0, z, site.x(), site.y(), site.z(), site.turns(), pos);
				int columnX = pos.x;
				int columnZ = pos.z;
				if (!world.isBlockLoaded(pos)) {
					continue;
				}

				// Under the floor's own blocks, the ground is built up to meet them; air in the floor layer keeps the terrain.
				if (!"minecraft:block/air".equals(prefab.blockAt(x, 0, z))) {
					for (int y = site.y() - 1; y > site.y() - 1 - MAX_FILL && y > 0; y--) {
						Material material = world.getBlockType(pos.set(columnX, y, columnZ)).getMaterial();
						if (material.isSolidBlocking() && material != Materials.WOOD) {
							break;
						}

						world.setBlockTypeDataRaw(pos, site.ground(), 0);
					}
				}

				for (int y = site.y() + prefab.height; y < site.y() + prefab.height + CLEAR_ABOVE; y++) {
					Material material = world.getBlockType(pos.set(columnX, y, columnZ)).getMaterial();
					if (material == Materials.WOOD || material == Materials.LEAVES || material == Materials.TOP_SNOW) {
						world.setBlockTypeDataRaw(pos, Blocks.AIR, 0);
						world.markBlockNeedsUpdate(pos);
					}
				}
			}
		}
	}
}
