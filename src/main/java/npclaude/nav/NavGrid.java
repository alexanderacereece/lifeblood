package npclaude.nav;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicDoor;
import net.minecraft.core.block.BlockLogicFenceGate;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;

/**
 * The world as the pathfinder sees it: for each block space, whether someone with their feet in it could stand there,
 * swim, climb, pass through in mid-air, or not fit at all. Shapes come from each block's own collision boxes,
 * so slabs, fences, stairs and other partial blocks are judged exactly as the physics will treat them.
 *
 * Results are cached for the current tick and shared by every NPC that searches during it.
 */
public final class NavGrid {
	/** Body size of an NPC (the same as a player's). */
	public static final double HALF_WIDTH = 0.3;
	public static final double BODY_HEIGHT = 1.8;
	/** Highest ledge stepped onto without jumping (Mob.footSize). */
	public static final double STEP_HEIGHT = 0.5;
	/** Highest ledge reachable by jumping from standing. */
	public static final double JUMP_HEIGHT = 1.2;
	/** Longest fall taken on purpose: falling damage starts beyond 3 blocks. */
	public static final int SAFE_DROP = 3;
	private static final double EPSILON = 1.0E-3;
	/** Footing narrower than this (fence tops, walls) counts as precarious. */
	private static final double NARROW_FOOTING = 0.5;

	public enum Kind {
		/** Something solid fills the space a body would need. */
		BLOCKED,
		/** Room for a body but nothing to stand on: falls through. */
		OPEN,
		STAND,
		SWIM,
		CLIMB
	}

	/** What a block space means for someone whose feet are in it. */
	public static final class Cell {
		public final Kind kind;
		/** Height the feet rest at when standing (absolute); the block's bottom otherwise. */
		public final double feetY;
		/** A closed wooden door or gate stands here and must be opened to pass. */
		public final boolean door;
		/** Hurts to be here (fire, lava, cactus beside it...). */
		public final boolean hazard;
		/** Standing in shallow water: passable but slow. */
		public final boolean wet;
		/** A ladder (or vine) is here: can be climbed from this spot, even when standing at its foot. */
		public final boolean climbable;
		/** Standing on something narrow, like the top of a fence: possible but precarious. */
		public final boolean narrow;

		Cell(Kind kind, double feetY, boolean door, boolean hazard, boolean wet, boolean climbable, boolean narrow) {
			this.kind = kind;
			this.feetY = feetY;
			this.door = door;
			this.hazard = hazard;
			this.wet = wet;
			this.climbable = climbable;
			this.narrow = narrow;
		}

		public boolean isPassable() {
			return this.kind != Kind.BLOCKED;
		}

		/** Somewhere the NPC can be on purpose (not mid-fall). */
		public boolean isFooting() {
			return this.kind == Kind.STAND || this.kind == Kind.SWIM || this.kind == Kind.CLIMB;
		}
	}

	private static final Map<World, NavGrid> GRIDS = new WeakHashMap<>();

	private final World world;
	private long tick = Long.MIN_VALUE;
	private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
	private final List<AABBdc> boxes = new ArrayList<>();
	private final TilePos pos = new TilePos();
	private final AABBd query = new AABBd();

	private NavGrid(World world) {
		this.world = world;
	}

	public static NavGrid of(World world) {
		NavGrid grid = GRIDS.computeIfAbsent(world, NavGrid::new);
		long now = world.getTotalWorldTime();
		if (grid.tick != now) {
			grid.tick = now;
			grid.cells.clear();
		}

		return grid;
	}

	private static long key(int x, int y, int z) {
		return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | y & 0xFFFL;
	}

	public Cell cell(int x, int y, int z) {
		long key = key(x, y, z);
		Cell cell = this.cells.get(key);
		if (cell == null) {
			cell = this.compute(x, y, z);
			this.cells.put(key, cell);
		}

		return cell;
	}

	private Cell compute(int x, int y, int z) {
		boolean hazard = isHazardAt(this.world, x, y, z);
		Block<?> feetBlock = this.block(x, y, z);
		boolean water = feetBlock.getMaterial() == Materials.WATER;
		boolean climbable = feetBlock.isClimbable(this.world, this.pos.set(x, y, z));

		// Collision boxes in the body's column, from the block below (support) to two above (head room).
		this.boxes.clear();
		boolean door = false;
		for (int by = y - 1; by <= y + 2; by++) {
			door |= this.collectBoxes(x, by, z);
		}

		double floor = Double.NaN;
		double floorWidth = 1.0;
		for (AABBdc box : this.boxes) {
			double top = box.maxY();
			if (top >= y - EPSILON && top <= y + STEP_HEIGHT + EPSILON && (Double.isNaN(floor) || top > floor)) {
				floor = top;
				floorWidth = Math.min(box.maxX() - box.minX(), box.maxZ() - box.minZ());
			}
		}

		double feet = Double.isNaN(floor) ? y : floor;
		if (!this.bodyFits(feet)) {
			return new Cell(Kind.BLOCKED, feet, door, hazard, false, climbable, false);
		}

		if (!Double.isNaN(floor)) {
			return new Cell(Kind.STAND, floor, door, hazard, water, climbable, floorWidth < NARROW_FOOTING);
		}

		if (climbable) {
			return new Cell(Kind.CLIMB, y, door, hazard, false, true, false);
		}

		if (water) {
			return new Cell(Kind.SWIM, y, door, hazard, true, false, false);
		}

		return new Cell(Kind.OPEN, y, door, hazard, false, false, false);
	}

	/**
	 * Whether a body fits with its feet at the given height, centered anywhere (not just on a block's center).
	 * Used for squeezing diagonally between thin obstacles like fence posts.
	 */
	public boolean bodyFitsAt(double centerX, double centerZ, double feet) {
		this.boxes.clear();
		int minX = MathHelper.floor(centerX - HALF_WIDTH);
		int maxX = MathHelper.floor(centerX + HALF_WIDTH);
		int minZ = MathHelper.floor(centerZ - HALF_WIDTH);
		int maxZ = MathHelper.floor(centerZ + HALF_WIDTH);
		this.query.setMin(centerX - HALF_WIDTH, feet, centerZ - HALF_WIDTH).setMax(centerX + HALF_WIDTH, feet + BODY_HEIGHT, centerZ + HALF_WIDTH);
		for (int bx = minX; bx <= maxX; bx++) {
			for (int bz = minZ; bz <= maxZ; bz++) {
				for (int by = MathHelper.floor(feet) - 1; by <= MathHelper.floor(feet + BODY_HEIGHT); by++) {
					Block<?> block = this.block(bx, by, bz);
					if (block != Blocks.AIR && !isOpenable(block) && !block.isClimbable(this.world, this.pos)) {
						block.getCollisionAABBs(this.world, this.pos, this.query, this.boxes);
					}
				}
			}
		}

		return this.bodyFits(feet);
	}

	/**
	 * Is the block ahead a staircase rising away from us: its near half at most a step above our feet and its
	 * far half another step up? Then it can be walked up (the physics steps up twice) instead of jumped.
	 */
	public boolean isStairAscent(int x, int y, int z, int dx, int dz, double feet) {
		this.boxes.clear();
		this.pos.set(x, y, z);
		Block<?> block = this.block(x, y, z);
		if (block == Blocks.AIR) {
			return false;
		}

		this.query.setMin(x, y, z).setMax(x + 1.0, y + 1.0, z + 1.0);
		block.getCollisionAABBs(this.world, this.pos, this.query, this.boxes);
		double nearTop = y;
		double farTop = y;
		for (AABBdc box : this.boxes) {
			// Split the block along the direction of travel into the half we reach first and the half behind it.
			double start = dx != 0 ? (dx > 0 ? box.minX() - x : x + 1.0 - box.maxX()) : (dz > 0 ? box.minZ() - z : z + 1.0 - box.maxZ());
			double end = dx != 0 ? (dx > 0 ? box.maxX() - x : x + 1.0 - box.minX()) : (dz > 0 ? box.maxZ() - z : z + 1.0 - box.minZ());
			if (start < 0.5 - EPSILON) {
				nearTop = Math.max(nearTop, box.maxY());
			}

			if (end > 0.5 + EPSILON) {
				farTop = Math.max(farTop, box.maxY());
			}
		}

		return nearTop - feet <= STEP_HEIGHT + EPSILON && farTop - nearTop <= STEP_HEIGHT + EPSILON && farTop > nearTop + EPSILON;
	}

	/** True if no collected box overlaps a body standing at the given height. */
	private boolean bodyFits(double feet) {
		for (AABBdc box : this.boxes) {
			if (box.maxY() > feet + EPSILON && box.minY() < feet + BODY_HEIGHT - EPSILON) {
				return false;
			}
		}

		return true;
	}

	/** Is the space above a body (for a jump's arc) clear between these heights? */
	public boolean isClearBetween(int x, int z, double fromY, double toY) {
		this.boxes.clear();
		for (int by = (int) Math.floor(fromY); by <= (int) Math.floor(toY); by++) {
			this.collectBoxes(x, by, z);
		}

		for (AABBdc box : this.boxes) {
			if (box.maxY() > fromY + EPSILON && box.minY() < toY - EPSILON) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Adds the collision boxes of one block that fall inside the body's footprint. Closed wooden doors and gates
	 * are left out (the NPC opens them), as are ladders (climbed through). Returns true if a closed door/gate was found.
	 */
	private boolean collectBoxes(int x, int y, int z) {
		Block<?> block = this.block(x, y, z);
		if (block == Blocks.AIR) {
			return false;
		}

		this.pos.set(x, y, z);
		if (isOpenable(block)) {
			return !isOpen(block, this.world.getBlockData(this.pos));
		}

		if (block.isClimbable(this.world, this.pos)) {
			return false;
		}

		this.query.setMin(x + 0.5 - HALF_WIDTH, y - 1.0, z + 0.5 - HALF_WIDTH).setMax(x + 0.5 + HALF_WIDTH, y + 2.0, z + 0.5 + HALF_WIDTH);
		block.getCollisionAABBs(this.world, this.pos, this.query, this.boxes);
		return false;
	}

	private Block<?> block(int x, int y, int z) {
		return this.world.getBlockType(this.pos.set(x, y, z));
	}

	/** Wooden doors and fence gates: things an NPC can open by hand. Iron and steel doors need redstone. */
	public static boolean isOpenable(Block<?> block) {
		if (Block.hasLogicClass(block, BlockLogicFenceGate.class)) {
			return true;
		}

		return Block.hasLogicClass(block, BlockLogicDoor.class) && block.getMaterial() != Materials.METAL && block.getMaterial() != Materials.STEEL;
	}

	public static boolean isOpen(Block<?> block, int data) {
		return Block.hasLogicClass(block, BlockLogicFenceGate.class) ? BlockLogicFenceGate.isOpen(data) : BlockLogicDoor.isOpen(data);
	}

	/** Hurts to stand here: a hazard at feet or head height, magma underfoot, or a cactus right beside the feet. */
	public static boolean isHazardAt(World world, int x, int y, int z) {
		if (isHazardBlock(blockAt(world, x, y, z)) || isHazardBlock(blockAt(world, x, y + 1, z)) || blockAt(world, x, y - 1, z) == Blocks.MAGMA) {
			return true;
		}

		return blockAt(world, x + 1, y, z) == Blocks.CACTUS || blockAt(world, x - 1, y, z) == Blocks.CACTUS
			|| blockAt(world, x, y, z + 1) == Blocks.CACTUS || blockAt(world, x, y, z - 1) == Blocks.CACTUS;
	}

	private static Block<?> blockAt(World world, int x, int y, int z) {
		return world.getBlockType(new TilePos(x, y, z));
	}

	private static boolean isHazardBlock(Block<?> block) {
		return block == Blocks.CACTUS || block == Blocks.FIRE || block == Blocks.MAGMA || block == Blocks.COBWEB
			|| block == Blocks.FLUID_LAVA_FLOWING || block == Blocks.FLUID_LAVA_STILL;
	}
}
