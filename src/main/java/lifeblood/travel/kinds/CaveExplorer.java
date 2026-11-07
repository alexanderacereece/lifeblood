package lifeblood.travel.kinds;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntityChest;
import net.minecraft.core.enums.LightLayer;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.tool.ItemToolPickaxe;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.ai.NpcBehavior;
import lifeblood.building.PrefabCapture;
import lifeblood.building.PrefabCheck;
import lifeblood.entity.EntityNPC;
import lifeblood.ownership.BlockOwners;
import lifeblood.travel.Outing;
import lifeblood.travel.Supplies;
import lifeblood.work.Approach;
import lifeblood.work.BlockBreaking;
import lifeblood.work.BlockUse;
import lifeblood.work.Containers;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Time underground on an adventure, by the player's rules: finds a way into a cave near where they've come to (or digs a
 * short stair down to one), explores it lighting the way with the torches they carry, mines ore they come across with
 * their pickaxe, empties any chest nobody owns (dungeons), and fights what's down there (the fight-or-flight instinct
 * sees to that). They turn back when out of torches, badly hurt, short of food, loaded down, or the stay is up, and
 * climb out (digging a stair up if they must).
 */
public final class CaveExplorer implements NpcBehavior {
	public static final String ID = "cave_explorer";
	/** How far around they look for a way into a cave, and how far ahead for the next spot to explore, in blocks. */
	private static final int ENTRANCE_SEARCH = 24;
	private static final int EXPLORE_RANGE = 10;
	/** How deep below the surface counts as underground, in blocks. */
	private static final int UNDERGROUND = 6;
	/** The most steps of stair they'll dig, down or up: a little digging, no more. */
	private static final int MAX_STAIR_STEPS = 16;
	/** How many walks toward a way in before they dig one instead. */
	private static final int MAX_APPROACHES = 3;
	/** Darker than this at their feet, they put down a torch. */
	private static final int TORCH_BELOW = 7;
	/** How far they look for ore and chests, in blocks. */
	private static final int ORE_RANGE = 3;
	private static final int CHEST_RANGE = 6;
	/** Health below this share means they've had enough. */
	private static final float HURT_SHARE = 0.5F;
	/** How many free pack slots they want to keep; fewer and they head out. */
	private static final int SLOTS_SPARE = 3;

	private static final Predicate<ItemStack> TORCH = stack -> stack.itemID == Blocks.TORCH_COAL.id();
	private static final Predicate<ItemStack> PICKAXE = stack -> stack.getItem() instanceof ItemToolPickaxe;

	private enum Phase {
		FIND_WAY_IN, STAIR_DOWN, EXPLORE, MINE, LOOT, CLIMB_OUT, DONE
	}

	private final Outing outing;
	private final Approach approach = new Approach();
	@Nullable
	private BlockBreaking breaking;
	private Phase phase = Phase.FIND_WAY_IN;
	@Nullable
	private int[] target;
	private int stairSteps;
	/** How many times they've walked toward a way in without getting underground. */
	private int approaches;
	private int stairDirX;
	private int stairDirZ;
	private final Set<Long> visited = new HashSet<>();
	private long nextLook;

	public CaveExplorer(Outing outing) {
		this.outing = outing;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public boolean shouldContinue(EntityNPC npc) {
		return this.phase != Phase.DONE;
	}

	@Override
	public void tick(EntityNPC npc) {
		if (this.breaking == null) {
			this.breaking = new BlockBreaking(npc);
		}

		ItemPickup.pickUpNearby(npc);
		if (this.phase != Phase.CLIMB_OUT && this.timeToLeave(npc)) {
			this.phase = Phase.CLIMB_OUT;
			this.stairSteps = 0;
		}

		switch (this.phase) {
			case FIND_WAY_IN -> this.findWayIn(npc);
			case STAIR_DOWN -> this.stair(npc, -1);
			case EXPLORE -> this.explore(npc);
			case MINE, LOOT -> this.reachTarget(npc);
			case CLIMB_OUT -> this.climbOut(npc);
			case DONE -> {
			}
		}
	}

	/** Whether they've had enough: hurt, out of light or food, laden, or the stay is nearly up. */
	private boolean timeToLeave(EntityNPC npc) {
		boolean hurt = npc.getHealth() < npc.getMaxHealth() * HURT_SHARE;
		if (hurt) {
			this.outing.gotHurt = true;
		}

		boolean underground = this.isUnderground(npc);
		boolean noLight = underground && Supplies.count(npc, TORCH) == 0 && npc.world.getBlockLightValue(new TilePos(npc)) < TORCH_BELOW;
		boolean noFood = Supplies.foodCarried(npc) == 0 && npc.getHealth() < npc.getMaxHealth() * 0.8F;
		boolean late = npc.world.getWorldTime() - this.outing.arrived > this.outing.stay * 0.9;
		return hurt || noLight || noFood || late || freeSlots(npc) < SLOTS_SPARE;
	}

	/** Looks for open, dark space below the surface near here that they can walk to; failing that, digs a stair down. */
	private void findWayIn(EntityNPC npc) {
		if (this.isUnderground(npc)) {
			this.phase = Phase.EXPLORE;
			return;
		}

		if (npc.navigator.isMoving()) {
			return;
		}

		// A way in that the paths only lead near, not into: after a few tries, they dig their own way down.
		World world = npc.world;
		for (int attempt = 0; attempt < 12 && this.approaches < MAX_APPROACHES; attempt++) {
			int x = (int) Math.floor(npc.x) + npc.getRandom().nextInt(ENTRANCE_SEARCH * 2 + 1) - ENTRANCE_SEARCH;
			int z = (int) Math.floor(npc.z) + npc.getRandom().nextInt(ENTRANCE_SEARCH * 2 + 1) - ENTRANCE_SEARCH;
			int[] space = darkSpaceBelow(world, x, z);
			if (space != null && npc.navigator.moveTo(space[0], space[1], space[2])) {
				this.target = space;
				this.approaches++;
				return;
			}
		}

		// No way in to be walked: a short stair down, as a player digs one.
		double angle = npc.getRandom().nextInt(4) * Math.PI / 2.0;
		this.stairDirX = (int) Math.round(Math.cos(angle));
		this.stairDirZ = (int) Math.round(Math.sin(angle));
		this.stairSteps = 0;
		this.phase = Phase.STAIR_DOWN;
	}

	/** One step of a stair, down (-1) or up (+1): clears the blocks of the next step and walks onto it. */
	private void stair(EntityNPC npc, int dy) {
		if (dy < 0 && this.isUnderground(npc) && this.stairSteps > 2 && this.inOpenSpace(npc)) {
			this.phase = Phase.EXPLORE;
			return;
		}

		if (this.stairSteps >= MAX_STAIR_STEPS) {
			this.phase = dy < 0 ? Phase.CLIMB_OUT : Phase.DONE;
			return;
		}

		int x = (int) Math.floor(npc.x) + this.stairDirX;
		int y = (int) Math.floor(npc.y) + dy;
		int z = (int) Math.floor(npc.z) + this.stairDirZ;
		BlockUse.holdItem(npc, PICKAXE);
		// The step's feet and head space, and the space above (for headroom going down, or the climb going up).
		for (int up = 0; up <= 2; up++) {
			TilePos pos = new TilePos(x, y + up, z);
			Block<?> block = npc.world.getBlockType(pos);
			if (block.getMaterial().isLiquid()) {
				// Water or lava in the way: not this way.
				this.phase = dy < 0 ? Phase.CLIMB_OUT : Phase.DONE;
				return;
			}

			if (block.getMaterial().blocksMotion()) {
				// Someone's block is never dug through: the stair turns the other way.
				if (BlockOwners.ownerAt(npc.world, x, y + up, z) != null
					|| this.breaking.dig(x, y + up, z) == BlockBreaking.Result.FAILED) {
					this.stairDirX = -this.stairDirX;
					this.stairDirZ = -this.stairDirZ;
					// A turn counts as a try, so a stair boxed in both ways gives up.
					this.stairSteps++;
				}

				return;
			}
		}

		// Each try at the next step counts, walked or not, so a stair that won't go doesn't go on for ever.
		if (!npc.navigator.isMoving()) {
			npc.navigator.moveTo(x, y, z);
			this.stairSteps++;
		}
	}

	/** Lights the way, looks for ore and chests now and then, and moves on to somewhere not yet seen. */
	private void explore(EntityNPC npc) {
		World world = npc.world;
		this.lightTheWay(npc);
		long now = world.getWorldTime();
		if (now >= this.nextLook) {
			this.nextLook = now + 20;
			int[] chest = this.findChest(npc);
			if (chest != null) {
				this.target = chest;
				this.phase = Phase.LOOT;
				return;
			}

			int[] ore = findOre(npc);
			if (ore != null && BlockUse.holdItem(npc, PICKAXE)) {
				this.target = ore;
				this.phase = Phase.MINE;
				return;
			}
		}

		if (npc.navigator.isMoving()) {
			return;
		}

		this.visited.add(cell(npc.x, npc.y, npc.z));
		for (int attempt = 0; attempt < 16; attempt++) {
			int x = (int) Math.floor(npc.x) + npc.getRandom().nextInt(EXPLORE_RANGE * 2 + 1) - EXPLORE_RANGE;
			int y = (int) Math.floor(npc.y) + npc.getRandom().nextInt(7) - 4;
			int z = (int) Math.floor(npc.z) + npc.getRandom().nextInt(EXPLORE_RANGE * 2 + 1) - EXPLORE_RANGE;
			if (!this.visited.contains(cell(x, y, z)) && isDarkSpace(world, x, y, z) && npc.navigator.moveTo(x, y, z)) {
				return;
			}
		}
	}

	/** Walks to an ore or a chest and takes what's there. */
	private void reachTarget(EntityNPC npc) {
		int[] spot = this.target;
		if (spot == null) {
			this.phase = Phase.EXPLORE;
			return;
		}

		Approach.Status status = this.approach.toward(npc, spot[0], spot[1], spot[2]);
		if (status == Approach.Status.WALKING) {
			return;
		}

		if (status == Approach.Status.REACHED) {
			if (this.phase == Phase.MINE) {
				if (this.breaking.dig(spot[0], spot[1], spot[2]) == BlockBreaking.Result.DIGGING) {
					return;
				}
			} else if (npc.world.getTileEntity(new TilePos(spot[0], spot[1], spot[2])) instanceof TileEntityChest chest) {
				npc.swingArm();
				if (Containers.take(npc, chest, stack -> true, Integer.MAX_VALUE) > 0) {
					this.outing.foundTreasure = true;
					npc.voice.bark("trip_found_treasure", null, null, false);
				}
			}
		}

		this.target = null;
		this.phase = Phase.EXPLORE;
	}

	/** Back to the surface: walks to the way they came in, else digs a stair up. */
	private void climbOut(EntityNPC npc) {
		if (!this.isUnderground(npc)) {
			this.phase = Phase.DONE;
			return;
		}

		if (npc.navigator.isMoving()) {
			return;
		}

		int entranceX = this.outing.destX;
		int entranceZ = this.outing.destZ;
		int surface = npc.world.getHeightValue(entranceX, entranceZ);
		if (this.stairSteps == 0 && npc.navigator.moveTo(entranceX, surface, entranceZ)) {
			return;
		}

		if (this.stairSteps == 0) {
			this.stairDirX = npc.getRandom().nextBoolean() ? 1 : -1;
			this.stairDirZ = 0;
		}

		this.stair(npc, 1);
		if (this.phase == Phase.DONE && this.isUnderground(npc)) {
			// Couldn't dig out this way either: try another way the next time round.
			this.phase = Phase.CLIMB_OUT;
			this.stairSteps = 0;
		}
	}

	/** A torch at their feet when it's dark, if they have one. */
	private void lightTheWay(EntityNPC npc) {
		TilePos feet = new TilePos(npc);
		if (npc.world.getBlockLightValue(feet) >= TORCH_BELOW || npc.world.getBlockType(feet) != Blocks.AIR
			|| !npc.world.getBlockType(new TilePos(feet.x, feet.y - 1, feet.z)).getMaterial().blocksMotion()) {
			return;
		}

		ItemStack held = npc.getHeldItem();
		if (BlockUse.holdItem(npc, TORCH)) {
			BlockUse.useOnTop(npc, feet.x, feet.y - 1, feet.z);
			// Back to what they were holding, the pickaxe or weapon.
			if (held != null) {
				BlockUse.holdItem(npc, held::isItemEqual);
			}
		}
	}

	/** An unowned chest near, as in a dungeon; null if none. */
	@Nullable
	private int[] findChest(EntityNPC npc) {
		TilePos pos = new TilePos();
		int cx = (int) Math.floor(npc.x);
		int cy = (int) Math.floor(npc.y);
		int cz = (int) Math.floor(npc.z);
		for (int dx = -CHEST_RANGE; dx <= CHEST_RANGE; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -CHEST_RANGE; dz <= CHEST_RANGE; dz++) {
					pos.set(cx + dx, cy + dy, cz + dz);
					if (npc.world.getTileEntity(pos) instanceof TileEntityChest chest && BlockOwners.ownerAt(npc.world, pos.x, pos.y, pos.z) == null
						&& Containers.count(chest, stack -> true) > 0) {
						return new int[] {pos.x, pos.y, pos.z};
					}
				}
			}
		}

		return null;
	}

	/** An ore block within reach with open space beside it; null if none. */
	@Nullable
	private static int[] findOre(EntityNPC npc) {
		TilePos pos = new TilePos();
		int cx = (int) Math.floor(npc.x);
		int cy = (int) Math.floor(npc.y);
		int cz = (int) Math.floor(npc.z);
		for (int dx = -ORE_RANGE; dx <= ORE_RANGE; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				for (int dz = -ORE_RANGE; dz <= ORE_RANGE; dz++) {
					Block<?> block = npc.world.getBlockType(pos.set(cx + dx, cy + dy, cz + dz));
					if (isOre(block) && exposed(npc.world, pos.x, pos.y, pos.z) && BlockOwners.ownerAt(npc.world, pos.x, pos.y, pos.z) == null) {
						return new int[] {pos.x, pos.y, pos.z};
					}
				}
			}
		}

		return null;
	}

	static boolean isOre(Block<?> block) {
		return block != Blocks.AIR && PrefabCheck.shortName(PrefabCapture.nameOf(block)).startsWith("ore_");
	}

	private static boolean exposed(World world, int x, int y, int z) {
		int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		TilePos pos = new TilePos();
		for (int[] side : sides) {
			if (world.getBlockType(pos.set(x + side[0], y + side[1], z + side[2])) == Blocks.AIR) {
				return true;
			}
		}

		return false;
	}

	/** Open, dark space under the surface in a column (feet room with floor below), or null. */
	@Nullable
	private static int[] darkSpaceBelow(World world, int x, int z) {
		TilePos pos = new TilePos(x, 0, z);
		if (!world.isBlockLoaded(pos)) {
			return null;
		}

		int surface = world.getHeightValue(x, z);
		for (int y = surface - UNDERGROUND; y > 8; y--) {
			if (isDarkSpace(world, x, y, z)) {
				return new int[] {x, y, z};
			}
		}

		return null;
	}

	/** Room to stand, with a floor, where no sky reaches. */
	private static boolean isDarkSpace(World world, int x, int y, int z) {
		TilePos pos = new TilePos(x, y, z);
		return world.getBlockType(pos) == Blocks.AIR && world.getBlockType(pos.set(x, y + 1, z)) == Blocks.AIR
			&& world.getBlockType(pos.set(x, y - 1, z)).getMaterial().blocksMotion() && world.getSavedLightValue(LightLayer.Sky, pos.set(x, y, z)) == 0;
	}

	private boolean isUnderground(EntityNPC npc) {
		TilePos feet = new TilePos(npc);
		return npc.world.getSavedLightValue(LightLayer.Sky, feet) == 0 && npc.y < npc.world.getHeightValue(feet.x, feet.z) - 2;
	}

	/** Standing somewhere already open (a cave), not just at the bottom of their own stair. */
	private boolean inOpenSpace(EntityNPC npc) {
		int x = (int) Math.floor(npc.x);
		int y = (int) Math.floor(npc.y);
		int z = (int) Math.floor(npc.z);
		int open = 0;
		TilePos pos = new TilePos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (npc.world.getBlockType(pos.set(x + dx, y, z + dz)) == Blocks.AIR) {
					open++;
				}
			}
		}

		return open >= 8;
	}

	private static int freeSlots(EntityNPC npc) {
		int free = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			if (npc.inventory.getItem(slot) == null) {
				free++;
			}
		}

		return free;
	}

	private static long cell(double x, double y, double z) {
		return ((long) Math.floor(x / 4) << 40) ^ ((long) Math.floor(y / 4) << 20) ^ (long) Math.floor(z / 4);
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
		if (this.breaking != null) {
			this.breaking.stop();
		}
	}

	@Override
	public String describe() {
		return switch (this.phase) {
			case FIND_WAY_IN -> "looking for a way into a cave";
			case STAIR_DOWN -> "digging down into a cave";
			case EXPLORE -> "exploring a cave";
			case MINE -> "mining ore";
			case LOOT -> "looting a chest";
			case CLIMB_OUT -> "climbing out of a cave";
			case DONE -> "out of the cave";
		};
	}
}
