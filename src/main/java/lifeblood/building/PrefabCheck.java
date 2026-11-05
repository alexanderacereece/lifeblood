package lifeblood.building;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicBed;
import net.minecraft.core.block.BlockLogicChest;
import net.minecraft.core.block.BlockLogicDoor;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.util.helper.Direction;

/**
 * The checks run on a prefab when it's saved, so problems are fixed by whoever builds it rather than found later in the
 * world: parts of two-block things cut off by the box, blocks whose facing won't turn, and a summary of what will be
 * swapped where it's placed.
 *
 * The building checks for NPCs placing by hand (build order, standing spots, scaffolding) come with NPC building; see
 * docs/building.md.
 */
public final class PrefabCheck {
	private PrefabCheck() {
	}

	/** What the check found: problems worth fixing, and how many blocks of each kind of placeholder there are. */
	public record Report(List<String> warnings, Map<String, Integer> swappable) {
	}

	public static Report check(Prefab prefab) {
		Set<String> warnings = new LinkedHashSet<>();
		Map<String, Integer> swappable = new LinkedHashMap<>();
		for (int y = 0; y < prefab.height; y++) {
			for (int z = 0; z < prefab.depth; z++) {
				for (int x = 0; x < prefab.width; x++) {
					Block<?> block = PrefabCapture.blockNamed(prefab.blockAt(x, y, z));
					if (block == null || block == Blocks.AIR) {
						continue;
					}

					int meta = prefab.metaAt(x, y, z);
					countSwappable(block, meta, swappable);
					if (BlockTurns.hasUnknownFacing(block)) {
						warnings.add(block.getKey() + " won't turn with the prefab yet; it keeps its saved facing.");
					}

					String cut = cutOff(prefab, block, meta, x, y, z);
					if (cut != null) {
						warnings.add(cut + " at " + x + " " + y + " " + z + " (from the box's corner) is cut off by the box's edge.");
					}
				}
			}
		}

		return new Report(new ArrayList<>(warnings), swappable);
	}

	/**
	 * What a prefab is made of, most common first, by short block name ("log_oak"), leaving out air: so whoever saves
	 * one sees at once whether the box caught the build.
	 */
	public static Map<String, Integer> contents(Prefab prefab) {
		List<Map.Entry<String, Integer>> counts = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : prefab.blockCounts().entrySet()) {
			String name = shortName(entry.getKey());
			if (!name.equals("air")) {
				counts.add(Map.entry(name, entry.getValue()));
			}
		}

		counts.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
		Map<String, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : counts) {
			result.merge(entry.getKey(), entry.getValue(), Integer::sum);
		}

		return result;
	}

	/** A saved block name without its namespace: "minecraft:block/log_oak" is "log_oak". */
	public static String shortName(String name) {
		return name.substring(Math.max(name.lastIndexOf(':'), name.lastIndexOf('/')) + 1);
	}

	private static void countSwappable(Block<?> block, int meta, Map<String, Integer> counts) {
		if (WoodType.of(block) != null) {
			counts.merge("logs (local wood)", 1, Integer::sum);
		} else if (WoodType.isPlainPlanks(block)) {
			counts.merge("plain planks (local wood's planks)", 1, Integer::sum);
		} else if (StoneFamily.of(block) != null) {
			counts.merge("stone (local stone)", 1, Integer::sum);
		} else if (Swaps.isPlaceholder(block, meta)) {
			boolean primary = Swaps.colorOf(block, meta) == Swaps.PRIMARY_PLACEHOLDER;
			counts.merge(primary ? "gray (primary color)" : "light gray (secondary color)", 1, Integer::sum);
		}
	}

	/** The name of a two-block thing whose other half falls outside the box or isn't there, or null if it's whole. */
	private static String cutOff(Prefab prefab, Block<?> block, int meta, int x, int y, int z) {
		if (block.getLogic() instanceof BlockLogicDoor door) {
			int otherY = door.isTop ? y - 1 : y + 1;
			if (!holds(prefab, x, otherY, z, other -> other.getLogic() instanceof BlockLogicDoor otherDoor && otherDoor.isTop != door.isTop)) {
				return "Half a door";
			}
		} else if (block.getLogic() instanceof BlockLogicBed) {
			Direction toHead = BlockLogicBed.footToHeadMap[BlockLogicBed.DIRECTION.get(meta)].direction();
			int sign = BlockLogicBed.IS_HEAD.bool(meta) ? -1 : 1;
			if (!holds(prefab, x + sign * Turns.stepX(toHead), y, z + sign * Turns.stepZ(toHead), other -> other.getLogic() instanceof BlockLogicBed)) {
				return "Half a bed";
			}
		} else if (block.getLogic() instanceof BlockLogicChest && BlockLogicChest.getTypeFromMeta(meta) != BlockLogicChest.Type.SINGLE) {
			// The other half of a double chest sits to one side: one step clockwise or counterclockwise of its facing.
			Direction facing = BlockLogicChest.getDirectionFromMeta(meta);
			boolean left = BlockLogicChest.getTypeFromMeta(meta) == BlockLogicChest.Type.LEFT;
			Direction side = Turns.turn(facing, left ? 3 : 1);
			if (!holds(prefab, x + Turns.stepX(side), y, z + Turns.stepZ(side), other -> other.getLogic() instanceof BlockLogicChest)) {
				return "Half a double chest";
			}
		}

		return null;
	}

	private static boolean holds(Prefab prefab, int x, int y, int z, Predicate<Block<?>> test) {
		if (x < 0 || y < 0 || z < 0 || x >= prefab.width || y >= prefab.height || z >= prefab.depth) {
			return false;
		}

		Block<?> block = PrefabCapture.blockNamed(prefab.blockAt(x, y, z));
		return block != null && test.test(block);
	}
}
