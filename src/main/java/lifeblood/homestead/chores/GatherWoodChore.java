package lifeblood.homestead.chores;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicLeavesBase;
import net.minecraft.core.block.BlockLogicLog;
import net.minecraft.core.block.BlockLogicSaplingBase;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.ownership.BlockOwners;
import lifeblood.work.Approach;
import lifeblood.work.BlockBreaking;
import lifeblood.work.BlockUse;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Gathering wood: takes an axe from home if there is one (bare hands work too, slowly, as for a player), finds a natural
 * tree nearby (one nobody placed, with leaves on it), chops its trunk from the bottom up as far as they can reach,
 * gathers what falls (logs, saplings, apples), plants a sapling where the tree stood, and brings it all home.
 */
public final class GatherWoodChore implements Chore {
	/** How far from home they look for a tree, in blocks. */
	private static final int SEARCH_RADIUS = 48;
	/** How many columns the tree search looks at each tick, so a search spreads over a few ticks. */
	private static final int COLUMNS_PER_TICK = 256;
	/** The tallest trunk they'll take on, in logs. */
	private static final int MAX_TRUNK = 12;
	/** How long they stay gathering what falls once the trunk is down, in ticks (15 seconds). */
	private static final int GATHER_TICKS = 300;
	/** How far from the tree they go for fallen things, in blocks. */
	private static final double GATHER_RADIUS = 8.0;

	private static final Predicate<ItemStack> AXE = stack -> stack.getItem() instanceof ItemToolAxe;
	private static final Predicate<ItemStack> SAPLING = stack -> stack.getItem() instanceof ItemBlock<?> block
		&& block.getBlock().getLogic() instanceof BlockLogicSaplingBase;
	/** What they bring home from a tree, axe included so others can use it. */
	private static final Predicate<ItemStack> HAUL = stack -> AXE.test(stack) || SAPLING.test(stack) || stack.getItem() == Items.FOOD_APPLE
		|| stack.getItem() == Items.STICK || stack.getItem() instanceof ItemBlock<?> block && block.getBlock().getLogic() instanceof BlockLogicLog;

	private enum Step {
		FETCH_AXE, FIND_TREE, CHOP, GATHER, REPLANT, STORE
	}

	private final Approach approach = new Approach();
	private final BlockBreaking breaking;
	private Step step = Step.FETCH_AXE;
	/** How far the tree search has got: the ring around home, and the position in it. */
	private int searchRing;
	private int searchIndex;
	@Nullable
	private int[] base;
	/** Whether the search came up empty: no natural tree near home. */
	private boolean noTrees;
	private final List<int[]> trunk = new ArrayList<>();
	/** When they started gathering what fell (world time); -1 until then. */
	private long gatherStart = -1L;

	public GatherWoodChore(EntityNPC npc) {
		this.breaking = new BlockBreaking(npc);
	}

	@Override
	public boolean tick(EntityNPC npc) {
		switch (this.step) {
			case FETCH_AXE -> {
				if (Errands.fetchTool(npc, this.approach, AXE) != Errands.Trip.ON_THE_WAY) {
					this.step = Step.FIND_TREE;
				}
			}
			case FIND_TREE -> this.search(npc);
			case CHOP -> this.chop(npc);
			case GATHER -> this.gather(npc);
			case REPLANT -> this.replant(npc);
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	/** Looks at the next stretch of columns around home, ring by ring outward, for a natural tree. */
	private void search(EntityNPC npc) {
		int homeX = (int) Math.floor(npc.home.getX());
		int homeZ = (int) Math.floor(npc.home.getZ());
		for (int checked = 0; checked < COLUMNS_PER_TICK; checked++) {
			if (this.searchRing > SEARCH_RADIUS) {
				this.noTrees = true;
				this.step = Step.STORE;
				return;
			}

			int[] column = ringColumn(this.searchRing, this.searchIndex);
			if (++this.searchIndex >= Math.max(1, this.searchRing * 8)) {
				this.searchRing++;
				this.searchIndex = 0;
			}

			int[] found = treeBase(npc.world, homeX + column[0], homeZ + column[1]);
			if (found != null) {
				this.base = found;
				this.trunk.clear();
				for (int y = found[1]; y < found[1] + MAX_TRUNK && isLog(npc.world, found[0], y, found[2]); y++) {
					this.trunk.add(new int[] {found[0], y, found[2]});
				}

				this.step = Step.CHOP;
				return;
			}
		}
	}

	/** The column at a position along a square ring around home: ring 0 is home itself, ring r has 8r columns. */
	private static int[] ringColumn(int ring, int index) {
		if (ring == 0) {
			return new int[] {0, 0};
		}

		int side = index / (2 * ring);
		int along = index % (2 * ring) - ring;
		return switch (side) {
			case 0 -> new int[] {along, -ring};
			case 1 -> new int[] {ring, along};
			case 2 -> new int[] {-along, ring};
			default -> new int[] {-ring, -along};
		};
	}

	/** The bottom log of a natural tree in this column, or null: nobody placed it, it stands on soil, and it has leaves. */
	@Nullable
	private static int[] treeBase(World world, int x, int z) {
		TilePos pos = new TilePos(x, 0, z);
		if (!world.isBlockLoaded(pos)) {
			return null;
		}

		int top = world.getHeightValue(x, z);
		for (int y = top; y > top - MAX_TRUNK - 4 && y > 1; y--) {
			if (!isLog(world, x, y, z) || isLog(world, x, y - 1, z)) {
				continue;
			}

			Block<?> ground = world.getBlockType(pos.set(x, y - 1, z));
			boolean soil = ground.getMaterial() == Materials.DIRT || ground.getMaterial() == Materials.GRASS;
			if (soil && BlockOwners.ownerAt(world, x, y, z) == null && hasLeaves(world, x, y, z)) {
				return new int[] {x, y, z};
			}
		}

		return null;
	}

	private static boolean isLog(World world, int x, int y, int z) {
		return world.getBlockType(new TilePos(x, y, z)).getLogic() instanceof BlockLogicLog;
	}

	/** Leaves somewhere around the trunk's upper part, so it's a tree and not a lone log or a house's post. */
	private static boolean hasLeaves(World world, int x, int y, int z) {
		TilePos pos = new TilePos();
		for (int dy = 2; dy <= MAX_TRUNK; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (world.getBlockType(pos.set(x + dx, y + dy, z + dz)).getLogic() instanceof BlockLogicLeavesBase) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** Chops the trunk from the bottom up, each log once within reach; logs too high to reach are left. */
	private void chop(EntityNPC npc) {
		while (!this.trunk.isEmpty() && !isLog(npc.world, this.trunk.get(0)[0], this.trunk.get(0)[1], this.trunk.get(0)[2])) {
			this.trunk.remove(0);
		}

		if (this.trunk.isEmpty()) {
			this.step = Step.GATHER;
			npc.voice.bark("chore_tree_down", null, null, false);
			return;
		}

		int[] log = this.trunk.get(0);
		Approach.Status status = this.approach.toward(npc, log[0], log[1], log[2]);
		if (status == Approach.Status.STUCK) {
			// Out of reach even after trying: leave what's left up there.
			this.trunk.clear();
		} else if (status == Approach.Status.REACHED && this.breaking.dig(log[0], log[1], log[2]) == BlockBreaking.Result.FAILED) {
			this.trunk.clear();
		}
	}

	/** Walks over to what fell around the tree and picks it up, for a little while. */
	private void gather(EntityNPC npc) {
		ItemPickup.pickUpNearby(npc);
		if (this.gatherStart < 0L) {
			this.gatherStart = npc.world.getWorldTime();
		}

		if (npc.world.getWorldTime() - this.gatherStart > GATHER_TICKS) {
			this.step = Step.REPLANT;
			return;
		}

		EntityItem item = ItemPickup.nearest(npc, GATHER_RADIUS);
		if (item != null && !npc.navigator.isMoving()) {
			npc.navigator.moveTo(item);
		}
	}

	/** Plants a sapling where the tree stood, if they have one and the ground is clear. */
	private void replant(EntityNPC npc) {
		int[] spot = this.base;
		TilePos above = spot != null ? new TilePos(spot[0], spot[1], spot[2]) : null;
		if (spot == null || npc.world.getBlockType(above) != Blocks.AIR || !BlockUse.holdItem(npc, SAPLING)) {
			this.step = Step.STORE;
			return;
		}

		Approach.Status status = this.approach.toward(npc, spot[0], spot[1] - 1, spot[2]);
		if (status == Approach.Status.REACHED) {
			BlockUse.useOnTop(npc, spot[0], spot[1] - 1, spot[2]);
		}

		if (status != Approach.Status.WALKING) {
			this.step = Step.STORE;
		}
	}

	/** Whether this trip found no tree near home, so wood is better put off for a while. */
	public boolean foundNoTrees() {
		return this.noTrees;
	}

	@Override
	public void stop(EntityNPC npc) {
		this.breaking.stop();
		npc.navigator.stop();
	}

	@Override
	public String doing() {
		return "wood";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_AXE -> "fetching an axe";
			case FIND_TREE -> "looking for a tree";
			case CHOP -> "chopping a tree";
			case GATHER -> "gathering wood";
			case REPLANT -> "planting a sapling";
			case STORE -> "bringing wood home";
		};
	}
}
