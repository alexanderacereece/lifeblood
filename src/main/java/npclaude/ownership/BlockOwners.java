package npclaude.ownership;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.pos.TilePos;
import org.jetbrains.annotations.Nullable;

/**
 * Who placed each block. A block someone places is theirs; anything that clears it (breaking, burning, an explosion)
 * clears the record, and changes in place (a furnace lighting up, dirt turning to grass, a door's state) keep it.
 * Natural terrain belongs to nobody.
 *
 * Placing is tracked through whoever is acting: while a player uses an item on a block, or an NPC places one, every
 * block that appears because of it (both halves of a door, a tree grown with bone meal) is theirs.
 */
public final class BlockOwners {
	/** Who is placing blocks on this thread right now, innermost last. */
	private static final ThreadLocal<Deque<UUID>> PLACERS = ThreadLocal.withInitial(ArrayDeque::new);

	private BlockOwners() {
	}

	/** Starts attributing placed blocks to this owner; pair with {@link #endPlacing()}. */
	public static void beginPlacing(UUID owner) {
		PLACERS.get().push(owner);
	}

	public static void endPlacing() {
		PLACERS.get().poll();
	}

	/** Runs an action whose placed blocks belong to this owner. */
	public static <T> T placingAs(UUID owner, Supplier<T> action) {
		beginPlacing(owner);
		try {
			return action.get();
		} finally {
			endPlacing();
		}
	}

	/** Forgets any placer left over by an action that never finished; called once a tick. */
	public static void resetPlacers() {
		PLACERS.get().clear();
	}

	/** Called by a chunk just before one of its blocks changes. */
	public static void onBlockChanging(Chunk chunk, int x, int y, int z, int oldId, int newId) {
		if (chunk.world.isClientSide) {
			return;
		}

		ChunkOwners owners = ((OwnedChunk) chunk).npclaude$owners();
		UUID placer = PLACERS.get().peek();
		if (newId == 0) {
			owners.set(ChunkOwners.index(x, y, z), null);
		} else if (placer != null && newId != oldId) {
			owners.set(ChunkOwners.index(x, y, z), placer);
		}
	}

	/** Who placed the block here, or null for natural terrain, unplaced blocks and unloaded areas. */
	@Nullable
	public static UUID ownerAt(World world, int x, int y, int z) {
		TilePos pos = new TilePos(x, y, z);
		if (!world.isBlockLoaded(pos)) {
			return null;
		}

		return ((OwnedChunk) world.getChunk(pos)).npclaude$owners().get(ChunkOwners.index(x, y, z));
	}

	/** Records an owner directly, for blocks placed by generation rather than by someone's hand. */
	public static void setOwner(World world, int x, int y, int z, @Nullable UUID owner) {
		TilePos pos = new TilePos(x, y, z);
		if (world.isBlockLoaded(pos)) {
			((OwnedChunk) world.getChunk(pos)).npclaude$owners().set(ChunkOwners.index(x, y, z), owner);
		}
	}

	/** Whether someone may break or take from the block here: it's nobody's, or it's theirs or shared with them. */
	public static boolean mayUse(World world, int x, int y, int z, UUID user) {
		UUID owner = ownerAt(world, x, y, z);
		return owner == null || Ownership.shares(world, owner, user);
	}
}
