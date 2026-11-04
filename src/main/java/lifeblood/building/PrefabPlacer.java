package lifeblood.building;

import com.mojang.nbt.tags.CompoundTag;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.block.entity.TileEntityDispatcher;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.ownership.BlockOwners;
import org.jetbrains.annotations.Nullable;

/**
 * Puts a {@link Prefab} into the world all at once, turned and with its materials swapped. This is how the world
 * itself places a build (generated homesteads, the /lb prefab command); NPCs building by hand will place block by
 * block under the player's rules instead (see docs/building.md).
 *
 * Blocks are written without neighbor updates, so nothing falls, pops off or reshapes while the build is half placed,
 * then tile entities are filled in from the saved data and every block is marked for redrawing.
 */
public final class PrefabPlacer {
	private PrefabPlacer() {
	}

	/** What placing did: how many blocks were written, and names of saved blocks that don't exist any more (left as air). */
	public record Result(int placed, Set<String> missing) {
	}

	/** How big the box is once turned: {width, depth}. */
	public static int[] turnedSize(Prefab prefab, int turns) {
		return (Turns.normalize(turns) & 1) == 1 ? new int[] {prefab.depth, prefab.width} : new int[] {prefab.width, prefab.depth};
	}

	/**
	 * Places a prefab with the turned box's lowest corner at the given position. Every placed block is recorded as the
	 * owner's (none if null); spots that end up as air own nothing.
	 */
	public static Result place(World world, Prefab prefab, int originX, int originY, int originZ, int turns, Swaps swaps, @Nullable UUID owner) {
		Set<String> missing = new LinkedHashSet<>();
		Block<?>[] blocks = new Block<?>[prefab.palette().size()];
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = PrefabCapture.blockNamed(prefab.palette().get(i));
			if (blocks[i] == null) {
				missing.add(prefab.palette().get(i));
			}
		}

		int placed = 0;
		TilePos pos = new TilePos();
		for (int y = 0; y < prefab.height; y++) {
			for (int z = 0; z < prefab.depth; z++) {
				for (int x = 0; x < prefab.width; x++) {
					spotPosition(prefab, x, y, z, originX, originY, originZ, turns, pos);
					if (!world.isBlockLoaded(pos)) {
						continue;
					}

					Block<?> saved = blocks[prefab.paletteIndexAt(x, y, z)];
					WoodType.Placed placing = saved != null ? swaps.apply(saved, BlockTurns.turn(saved, prefab.metaAt(x, y, z), turns))
						: new WoodType.Placed(Blocks.AIR, 0);
					Block<?> block = placing.block();
					int meta = placing.meta();
					if (world.getTileEntity(pos) != null) {
						world.removeTileEntity(pos);
					}

					world.setBlockTypeDataRaw(pos, block, meta);
					BlockOwners.setOwner(world, pos.x, pos.y, pos.z, block == Blocks.AIR ? null : owner);
					placed++;
				}
			}
		}

		// Tile entities go in once every block is there, from the saved data moved to the new spot.
		for (var entry : prefab.tileEntities().entrySet()) {
			int spot = entry.getKey();
			int x = spot % prefab.width;
			int z = spot / prefab.width % prefab.depth;
			int y = spot / (prefab.width * prefab.depth);
			spotPosition(prefab, x, y, z, originX, originY, originZ, turns, pos);
			if (!world.isBlockLoaded(pos) || world.getBlockType(pos) == Blocks.AIR) {
				continue;
			}

			CompoundTag data = (CompoundTag) entry.getValue().clone();
			data.putInt("x", pos.x);
			data.putInt("y", pos.y);
			data.putInt("z", pos.z);
			TileEntity tileEntity = TileEntityDispatcher.createAndLoadEntity(data);
			if (tileEntity != null) {
				world.setTileEntity(pos, tileEntity);
			}
		}

		int[] size = turnedSize(prefab, turns);
		for (int y = 0; y < prefab.height; y++) {
			for (int dz = 0; dz < size[1]; dz++) {
				for (int dx = 0; dx < size[0]; dx++) {
					world.markBlockNeedsUpdate(pos.set(originX + dx, originY + y, originZ + dz));
				}
			}
		}

		return new Result(placed, missing);
	}

	/** Where a prefab's spot lands in the world. */
	public static TilePos spotPosition(Prefab prefab, int x, int y, int z, int originX, int originY, int originZ, int turns, TilePos into) {
		int[] turned = Turns.turnSpot(x, z, prefab.width, prefab.depth, turns);
		return into.set(originX + turned[0], originY + y, originZ + turned[1]);
	}
}
