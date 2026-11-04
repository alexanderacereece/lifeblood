package lifeblood.nav;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A* search over {@link NavGrid}. From each spot it tries every move an NPC can physically make:
 * walking (including diagonals that don't clip corners), jumping up a block, dropping up to 3 blocks,
 * jumping a one-block gap, swimming, climbing ladders and passing through doors (opened on arrival).
 *
 * Costs favor dry, flat, direct routes; an optional {@link CostField} adds extra cost to places
 * (used to keep fleeing NPCs away from their attackers). If the goal can't be reached within the node budget,
 * the path leads to the closest point found.
 */
public final class NavSearch {
	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double DIAGONAL = Math.sqrt(2.0);
	private static final double COST_JUMP = 2.0;
	private static final double COST_DROP_PER_BLOCK = 0.5;
	private static final double COST_GAP = 3.0;
	private static final double COST_SWIM = 3.0;
	private static final double COST_WET = 1.0;
	private static final double COST_CLIMB = 1.5;
	private static final double COST_DOOR = 1.5;
	/** Walking along fence tops and other narrow footing is possible but avoided when there's another way. */
	private static final double COST_NARROW = 2.0;
	/** Walking up stairs costs a little more than flat ground, but far less than jumping. */
	private static final double COST_STAIRS = 1.3;
	/** Estimated cost per block of height still to climb, and to descend. Climbing is the slower of the two. */
	private static final double ESTIMATE_UP = 1.0;
	private static final double ESTIMATE_DOWN = 0.5;

	/** Where the search is trying to get. */
	public interface Goal {
		boolean isReached(int x, int y, int z);

		/** Optimistic estimate of the remaining cost; must not overestimate. */
		double estimate(int x, int y, int z);
	}

	/** Extra cost for being at a spot, e.g. near danger. */
	public interface CostField {
		double extraCost(int x, int y, int z);
	}

	/** Reach within a radius of a point. */
	public static Goal near(double gx, double gy, double gz, double radius) {
		return new Goal() {
			@Override
			public boolean isReached(int x, int y, int z) {
				double dx = x + 0.5 - gx;
				double dz = z + 0.5 - gz;
				return dx * dx + dz * dz <= radius * radius && Math.abs(y - gy) <= 1.5;
			}

			@Override
			public double estimate(int x, int y, int z) {
				double dx = Math.abs(x + 0.5 - gx);
				double dz = Math.abs(z + 0.5 - gz);
				double flat = Math.max(dx, dz) + (DIAGONAL - 1.0) * Math.min(dx, dz);
				double rise = gy - y;
				return Math.max(0.0, flat - radius) + (rise > 0 ? rise * ESTIMATE_UP : -rise * ESTIMATE_DOWN);
			}
		};
	}

	private static final class Node implements Comparable<Node> {
		final int x;
		final int y;
		final int z;
		final double feetY;
		final double g;
		final double f;
		final double h;
		@Nullable
		final Node parent;
		final NavMove move;
		final boolean door;

		Node(int x, int y, int z, double feetY, double g, double h, @Nullable Node parent, NavMove move, boolean door) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.feetY = feetY;
			this.g = g;
			this.h = h;
			this.f = g + h;
			this.parent = parent;
			this.move = move;
			this.door = door;
		}

		@Override
		public int compareTo(Node other) {
			return Double.compare(this.f, other.f);
		}
	}

	private final NavGrid grid;
	private final Goal goal;
	@Nullable
	private final CostField field;
	private final PriorityQueue<Node> open = new PriorityQueue<>();
	private final Long2ObjectOpenHashMap<Node> best = new Long2ObjectOpenHashMap<>();
	private Node closest;
	private int expanded;

	private NavSearch(World world, Goal goal, @Nullable CostField field) {
		this.grid = NavGrid.of(world);
		this.goal = goal;
		this.field = field;
	}

	/** How many nodes the last search expanded (for profiling). */
	public static int lastExpanded;

	/**
	 * Plans a path from a standing position. Returns null if no step at all can be taken toward the goal.
	 */
	@Nullable
	public static NavPath find(World world, double startX, double startY, double startZ, Goal goal, @Nullable CostField field, int maxNodes) {
		NavSearch search = new NavSearch(world, goal, field);
		NavPath path = search.run(startX, startY, startZ, maxNodes);
		lastExpanded = search.expanded;
		return path;
	}

	@Nullable
	private NavPath run(double startX, double startY, double startZ, int maxNodes) {
		int[] spot = this.findStart(startX, startY, startZ);
		if (spot == null) {
			return null;
		}

		int sx = spot[0];
		int sy = spot[1];
		int sz = spot[2];
		NavGrid.Cell start = this.grid.cell(sx, sy, sz);

		Node first = new Node(sx, sy, sz, start.feetY, 0.0, this.goal.estimate(sx, sy, sz), null, NavMove.WALK, false);
		this.closest = first;
		this.push(first);
		while (!this.open.isEmpty() && this.expanded < maxNodes) {
			Node node = this.open.poll();
			if (this.best.get(key(node.x, node.y, node.z)) != node) {
				continue;
			}

			if (this.goal.isReached(node.x, node.y, node.z)) {
				return this.build(node, true);
			}

			this.expanded++;
			if (node.h < this.closest.h) {
				this.closest = node;
			}

			this.expand(node);
		}

		return this.closest == first ? null : this.build(this.closest, false);
	}

	/**
	 * The block space the feet are really in. Usually the one under the body's center, but standing on an edge or
	 * pressed against a fence the center hangs over a space that can't be stood in; then it's whichever space the
	 * body overlaps that the feet are actually resting in.
	 */
	@Nullable
	private int[] findStart(double startX, double startY, double startZ) {
		int sy = MathHelper.floor(startY + 0.01);
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (double ox : new double[]{0.0, -NavGrid.HALF_WIDTH, NavGrid.HALF_WIDTH}) {
			for (double oz : new double[]{0.0, -NavGrid.HALF_WIDTH, NavGrid.HALF_WIDTH}) {
				int x = MathHelper.floor(startX + ox);
				int z = MathHelper.floor(startZ + oz);
				// Mid-jump or on a slab the feet may be a little above their resting spot.
				for (int dy = 0; dy <= 2; dy++) {
					NavGrid.Cell cell = this.grid.cell(x, sy - dy, z);
					if (!cell.isFooting()) {
						continue;
					}

					double cx = x + 0.5 - startX;
					double cz = z + 0.5 - startZ;
					double distance = cx * cx + cz * cz + Math.abs(cell.feetY - startY) * 2.0;
					if (distance < bestDistance) {
						bestDistance = distance;
						best = new int[]{x, sy - dy, z};
					}

					break;
				}
			}
		}

		return best;
	}

	private void expand(Node node) {
		NavGrid.Cell here = this.grid.cell(node.x, node.y, node.z);

		// Straight up or down: ladders (also from their foot) and swimming. At a ladder's top the NPC climbs
		// into the open space above it, then steps off onto the ledge beside it.
		if (here.climbable || here.kind == NavGrid.Kind.CLIMB || here.kind == NavGrid.Kind.SWIM) {
			NavGrid.Cell up = this.grid.cell(node.x, node.y + 1, node.z);
			if (up.isFooting() || here.climbable && up.kind == NavGrid.Kind.OPEN) {
				this.offer(node, node.x, node.y + 1, node.z, up, NavMove.CLIMB, COST_CLIMB);
			}

			NavGrid.Cell down = this.grid.cell(node.x, node.y - 1, node.z);
			if (down.kind == NavGrid.Kind.CLIMB || down.kind == NavGrid.Kind.SWIM) {
				this.offer(node, node.x, node.y - 1, node.z, down, NavMove.DESCEND, COST_CLIMB);
			}
		}

		for (int[] direction : DIRECTIONS) {
			int dx = direction[0];
			int dz = direction[1];
			boolean diagonal = dx != 0 && dz != 0;
			int nx = node.x + dx;
			int nz = node.z + dz;
			// Diagonals only where the body fits through the corner: both side squares are open, or (between thin
			// things like fence posts) there's room at the halfway point.
			if (diagonal && !(this.grid.cell(node.x + dx, node.y, node.z).isPassable() && this.grid.cell(node.x, node.y, node.z + dz).isPassable())
				&& !this.grid.bodyFitsAt(node.x + 0.5 + dx * 0.5, node.z + 0.5 + dz * 0.5, node.feetY)) {
				continue;
			}

			double flat = diagonal ? DIAGONAL : 1.0;
			NavGrid.Cell same = this.grid.cell(nx, node.y, nz);
			switch (same.kind) {
				case STAND -> {
					double rise = same.feetY - node.feetY;
					if (rise <= NavGrid.STEP_HEIGHT + 1.0E-3) {
						this.offer(node, nx, node.y, nz, same, NavMove.WALK, flat + (same.wet ? COST_WET : 0.0));
					} else if (!diagonal && rise <= NavGrid.JUMP_HEIGHT && this.hasJumpRoom(node)) {
						this.offer(node, nx, node.y, nz, same, NavMove.JUMP_UP, COST_JUMP);
					}
				}
				case SWIM -> this.offer(node, nx, node.y, nz, same, NavMove.SWIM, COST_SWIM * flat);
				case CLIMB -> this.offer(node, nx, node.y, nz, same, NavMove.WALK, flat + 0.2);
				case OPEN -> {
					// Both are offered: dropping into a narrow pit can be a dead end the jump across avoids.
					this.tryDrop(node, nx, nz, flat);
					if (!diagonal) {
						this.tryGapJump(node, dx, dz);
					}
				}
				case BLOCKED -> {
					// A full block in the way: maybe it can be jumped onto.
					NavGrid.Cell above = this.grid.cell(nx, node.y + 1, nz);
					if (diagonal || above.kind != NavGrid.Kind.STAND) {
						continue;
					}

					double rise = above.feetY - node.feetY;
					if (rise <= NavGrid.STEP_HEIGHT + 1.0E-3) {
						// From a slab, a full block ahead is only half a block up: a step, not a jump.
						this.offer(node, nx, node.y + 1, nz, above, NavMove.WALK, flat);
					} else if (rise <= 1.0 + 1.0E-3 && this.grid.isStairAscent(nx, node.y, nz, dx, dz, node.feetY)) {
						this.offer(node, nx, node.y + 1, nz, above, NavMove.WALK, COST_STAIRS);
					} else if (rise <= NavGrid.JUMP_HEIGHT && this.hasJumpRoom(node)) {
						this.offer(node, nx, node.y + 1, nz, above, NavMove.JUMP_UP, COST_JUMP);
					}
				}
			}
		}
	}

	/** Steps off a ledge and lands up to 3 blocks lower. */
	private void tryDrop(Node node, int nx, int nz, double flat) {
		for (int depth = 1; depth <= NavGrid.SAFE_DROP; depth++) {
			NavGrid.Cell below = this.grid.cell(nx, node.y - depth, nz);
			if (below.hazard || below.kind == NavGrid.Kind.BLOCKED) {
				return;
			}

			if (below.isFooting()) {
				this.offer(node, nx, node.y - depth, nz, below, NavMove.DROP, flat + COST_DROP_PER_BLOCK * depth);
				return;
			}
		}
	}

	/** Jumps a one-block gap to land two blocks away, level or one lower. */
	private void tryGapJump(Node node, int dx, int dz) {
		if (!this.hasJumpRoom(node) || !this.grid.isClearBetween(node.x + dx, node.z + dz, node.feetY + NavGrid.BODY_HEIGHT, node.feetY + NavGrid.BODY_HEIGHT + 1.0)) {
			return;
		}

		int lx = node.x + 2 * dx;
		int lz = node.z + 2 * dz;
		for (int dy = 0; dy >= -1; dy--) {
			NavGrid.Cell landing = this.grid.cell(lx, node.y + dy, lz);
			if (landing.kind == NavGrid.Kind.STAND && landing.feetY - node.feetY <= NavGrid.STEP_HEIGHT) {
				this.offer(node, lx, node.y + dy, lz, landing, NavMove.GAP_JUMP, COST_GAP);
				return;
			}
		}
	}

	/** Enough head room above the current spot to jump. */
	private boolean hasJumpRoom(Node node) {
		return this.grid.isClearBetween(node.x, node.z, node.feetY + NavGrid.BODY_HEIGHT, node.feetY + NavGrid.BODY_HEIGHT + NavGrid.JUMP_HEIGHT);
	}

	private void offer(Node from, int x, int y, int z, NavGrid.Cell cell, NavMove move, double cost) {
		if (cell.hazard) {
			return;
		}

		double g = from.g + cost + (cell.door ? COST_DOOR : 0.0) + (cell.narrow ? COST_NARROW : 0.0);
		if (this.field != null) {
			g += this.field.extraCost(x, y, z);
		}

		long key = key(x, y, z);
		Node existing = this.best.get(key);
		if (existing != null && existing.g <= g) {
			return;
		}

		this.push(new Node(x, y, z, cell.feetY, g, this.goal.estimate(x, y, z), from, move, cell.door));
	}

	private void push(Node node) {
		this.best.put(key(node.x, node.y, node.z), node);
		this.open.add(node);
	}

	private NavPath build(Node end, boolean complete) {
		// Includes the starting spot as the first step, so every real step has a step before it.
		List<NavStep> steps = new ArrayList<>();
		for (Node node = end; node != null; node = node.parent) {
			steps.add(new NavStep(node.x, node.y, node.z, node.feetY, node.move, node.door));
		}

		Collections.reverse(steps);
		return new NavPath(steps, complete);
	}

	private static long key(int x, int y, int z) {
		return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | y & 0xFFFL;
	}
}
