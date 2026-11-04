package lifeblood.building;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.util.helper.Direction;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.Nullable;

/** Copies a box of the world into a {@link Prefab}, and finds blocks again by the names prefabs save them under. */
public final class PrefabCapture {
	/** The largest box that can be saved, in blocks, so a typo in the corners can't freeze the game. */
	public static final int MAX_VOLUME = 64 * 64 * 64;

	private static Map<String, Block<?>> byName;

	private PrefabCapture() {
	}

	/** Copies every block between two corners (both included). The front is the side the build faces. */
	public static Prefab capture(World world, String name, int x1, int y1, int z1, int x2, int y2, int z2, Direction front) {
		int minX = Math.min(x1, x2);
		int minY = Math.min(y1, y2);
		int minZ = Math.min(z1, z2);
		int width = Math.abs(x1 - x2) + 1;
		int height = Math.abs(y1 - y2) + 1;
		int depth = Math.abs(z1 - z2) + 1;
		if ((long) width * height * depth > MAX_VOLUME) {
			throw new IllegalArgumentException("That box is too big to save (" + width + "x" + height + "x" + depth + ").");
		}

		List<String> palette = new ArrayList<>();
		Map<String, Short> paletteIndex = new HashMap<>();
		short[] blocks = new short[width * height * depth];
		short[] meta = new short[blocks.length];
		Map<Integer, CompoundTag> tileEntities = new LinkedHashMap<>();
		TilePos pos = new TilePos();
		int spot = 0;
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < depth; z++) {
				for (int x = 0; x < width; x++, spot++) {
					pos.set(minX + x, minY + y, minZ + z);
					Block<?> block = world.getBlockType(pos);
					String key = nameOf(block);
					Short index = paletteIndex.get(key);
					if (index == null) {
						index = (short) palette.size();
						palette.add(key);
						paletteIndex.put(key, index);
					}

					blocks[spot] = index;
					meta[spot] = (short) world.getBlockData(pos);
					TileEntity tileEntity = world.getTileEntity(pos);
					if (tileEntity != null) {
						CompoundTag data = new CompoundTag();
						tileEntity.writeToNBT(data);
						tileEntities.put(spot, data);
					}
				}
			}
		}

		return new Prefab(name, width, height, depth, front, palette, blocks, meta, tileEntities);
	}

	/** The name a block is saved under. */
	public static String nameOf(Block<?> block) {
		return block.namespaceId().toString();
	}

	/** The block saved under this name, or null if no such block exists (a block from a mod no longer installed). */
	@Nullable
	public static Block<?> blockNamed(String name) {
		if (byName == null) {
			Map<String, Block<?>> map = new HashMap<>();
			for (Block<?> block : Blocks.blocksList) {
				if (block != null) {
					map.put(nameOf(block), block);
				}
			}

			byName = map;
		}

		return byName.get(name);
	}
}
