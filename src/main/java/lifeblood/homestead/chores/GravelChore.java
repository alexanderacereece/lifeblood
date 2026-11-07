package lifeblood.homestead.chores;

import java.util.function.Predicate;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.tool.ItemToolShovel;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.ownership.BlockOwners;
import lifeblood.work.Approach;
import lifeblood.work.BlockBreaking;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Digging gravel for flint, for a household that makes its own arrows: takes the shovel from home if there is one,
 * finds loose gravel lying open near home (none anyone placed), digs it by the player's rules, and keeps what drops:
 * flint about one time in ten, as for a player, and the gravel itself. A few blocks, or two flint, makes a trip.
 */
public final class GravelChore implements Chore {
	/** How far from home they look for gravel, in blocks. */
	private static final int SEARCH_RADIUS = 32;
	/** How many blocks they dig per trip at most, and how much flint is enough. */
	private static final int BLOCKS_PER_TRIP = 8;
	private static final int FLINT_WANTED = 2;
	private static final double GATHER_RADIUS = 4.0;

	private static final Predicate<ItemStack> SHOVEL = stack -> stack.getItem() instanceof ItemToolShovel;
	private static final Predicate<ItemStack> HAUL = stack -> SHOVEL.test(stack) || FletchChore.FLINT.test(stack)
		|| stack.getItem().getKey().contains("gravel");

	private enum Step {
		FETCH_SHOVEL, DIG, STORE
	}

	private final Approach approach = new Approach();
	private final BlockBreaking breaking;
	private Step step = Step.FETCH_SHOVEL;
	@Nullable
	private int[] gravel;
	private int dug;
	private boolean noGravel;

	public GravelChore(EntityNPC npc) {
		this.breaking = new BlockBreaking(npc);
	}

	/** Whether this trip found no gravel near home, so it's better put off for a while. */
	public boolean foundNoGravel() {
		return this.noGravel;
	}

	@Override
	public boolean tick(EntityNPC npc) {
		switch (this.step) {
			case FETCH_SHOVEL -> {
				// Bare hands dig gravel too, slower, as for a player.
				if (Errands.fetchTool(npc, this.approach, SHOVEL) != Errands.Trip.ON_THE_WAY) {
					this.step = Step.DIG;
				}
			}
			case DIG -> this.dig(npc);
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	private void dig(EntityNPC npc) {
		ItemPickup.pickUpNearby(npc);
		if (this.dug >= BLOCKS_PER_TRIP || countFlint(npc) >= FLINT_WANTED) {
			this.step = Step.STORE;
			return;
		}

		if (this.gravel == null || npc.world.getBlockType(new TilePos(this.gravel[0], this.gravel[1], this.gravel[2])) != Blocks.GRAVEL) {
			// Gather what fell from the last one first.
			EntityItem item = ItemPickup.nearest(npc, GATHER_RADIUS);
			if (item != null) {
				if (!npc.navigator.isMoving()) {
					npc.navigator.moveTo(item);
				}

				return;
			}

			this.gravel = findGravel(npc);
			if (this.gravel == null) {
				this.noGravel = this.dug == 0;
				this.step = Step.STORE;
				return;
			}
		}

		int[] spot = this.gravel;
		Approach.Status status = this.approach.toward(npc, spot[0], spot[1], spot[2]);
		if (status == Approach.Status.STUCK) {
			this.gravel = null;
			this.step = Step.STORE;
		} else if (status == Approach.Status.REACHED) {
			BlockBreaking.Result result = this.breaking.dig(spot[0], spot[1], spot[2]);
			if (result != BlockBreaking.Result.DIGGING) {
				this.dug++;
				this.gravel = null;
			}
		}
	}

	private static int countFlint(EntityNPC npc) {
		int flint = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack != null && FletchChore.FLINT.test(stack)) {
				flint += stack.stackSize;
			}
		}

		return flint;
	}

	/** The nearest gravel lying open near home, the top block of its column with air above, that nobody placed. */
	@Nullable
	private static int[] findGravel(EntityNPC npc) {
		World world = npc.world;
		int homeX = (int) Math.floor(npc.home.getX());
		int homeZ = (int) Math.floor(npc.home.getZ());
		TilePos pos = new TilePos();
		for (int ring = 1; ring <= SEARCH_RADIUS; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !world.isBlockLoaded(pos.set(homeX + dx, 0, homeZ + dz))) {
						continue;
					}

					int x = homeX + dx;
					int z = homeZ + dz;
					int y = world.getHeightValue(x, z) - 1;
					if (world.getBlockType(pos.set(x, y, z)) == Blocks.GRAVEL && world.getBlockType(pos.set(x, y + 1, z)) == Blocks.AIR
						&& BlockOwners.ownerAt(world, x, y, z) == null) {
						return new int[] {x, y, z};
					}
				}
			}
		}

		return null;
	}

	@Override
	public void stop(EntityNPC npc) {
		this.breaking.stop();
		npc.navigator.stop();
	}

	@Override
	public String doing() {
		return "digging";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_SHOVEL -> "fetching a shovel";
			case DIG -> "digging gravel for flint";
			case STORE -> "bringing flint home";
		};
	}
}
