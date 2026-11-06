package lifeblood.building;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.block.Block;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.Nullable;

/**
 * Finds out what a place is made of by looking at it, the way a settler would: which trees grow nearby and which stone
 * is underground. Only loaded blocks are looked at, so a survey never loads or generates chunks.
 */
public final class LocalMaterials {
	/** How far around the site to look for trees, in blocks. */
	public static final int TREE_RADIUS = 24;
	/** How far around the site to dig test holes for the stone, in blocks. */
	public static final int STONE_RADIUS = 12;
	/** How deep below the surface to look for stone, in blocks. */
	public static final int STONE_DEPTH = 48;
	/** Trees counted from a little under the site's ground to this many blocks above it. */
	private static final int TREE_HEIGHT = 20;
	private static final int TREE_STEP = 2;
	private static final int STONE_STEP = 4;

	private LocalMaterials() {
	}

	/** What the site at this ground position offers: the commonest trees and stone, or null for either if none was found. */
	public static Swaps survey(World world, int x, int groundY, int z) {
		return new Swaps(commonestWood(world, x, groundY, z), commonestStone(world, x, z), null, null);
	}

	/**
	 * The kind of tree growing most around the site, by its logs and leaves, or null if there are no trees. Leaves
	 * count too: they spread wide, so they're found where a sampled grid steps over a one-block trunk.
	 */
	@Nullable
	public static WoodType commonestWood(World world, int x, int groundY, int z) {
		Map<WoodType, Integer> counts = new EnumMap<>(WoodType.class);
		TilePos pos = new TilePos();
		for (int dx = -TREE_RADIUS; dx <= TREE_RADIUS; dx += TREE_STEP) {
			for (int dz = -TREE_RADIUS; dz <= TREE_RADIUS; dz += TREE_STEP) {
				pos.set(x + dx, groundY, z + dz);
				if (!world.isBlockLoaded(pos)) {
					continue;
				}

				for (int y = groundY - 4; y <= groundY + TREE_HEIGHT; y++) {
					pos.set(x + dx, y, z + dz);
					Block<?> block = world.getBlockType(pos);
					WoodType type = WoodType.of(block);
					if (type == null) {
						type = WoodType.ofLeaves(block);
					}

					if (type != null) {
						counts.merge(type, 1, Integer::sum);
					}
				}
			}
		}

		return commonest(counts);
	}

	/** The natural stone met most often going down from the surface around the site, or null if none was reached. */
	@Nullable
	public static StoneFamily commonestStone(World world, int x, int z) {
		Map<StoneFamily, Integer> counts = new EnumMap<>(StoneFamily.class);
		TilePos pos = new TilePos();
		for (int dx = -STONE_RADIUS; dx <= STONE_RADIUS; dx += STONE_STEP) {
			for (int dz = -STONE_RADIUS; dz <= STONE_RADIUS; dz += STONE_STEP) {
				pos.set(x + dx, 0, z + dz);
				if (!world.isBlockLoaded(pos)) {
					continue;
				}

				int top = world.getHeightValue(x + dx, z + dz);
				for (int y = top; y > Math.max(0, top - STONE_DEPTH); y--) {
					pos.set(x + dx, y, z + dz);
					Block<?> block = world.getBlockType(pos);
					StoneFamily family = StoneFamily.of(block);
					if (family != null && block == family.natural()) {
						counts.merge(family, 1, Integer::sum);
						break;
					}
				}
			}
		}

		return commonest(counts);
	}

	@Nullable
	private static <T> T commonest(Map<T, Integer> counts) {
		T best = null;
		int bestCount = 0;
		for (Map.Entry<T, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
			}
		}

		return best;
	}
}
