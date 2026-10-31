package npclaude.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicDoor;
import net.minecraft.core.block.BlockLogicFenceGate;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import npclaude.entity.EntityNPC;
import npclaude.nav.NavBudget;
import npclaude.nav.NavGrid;
import npclaude.nav.NavMove;
import npclaude.nav.NavPath;
import npclaude.nav.NavSearch;
import npclaude.nav.NavStep;
import npclaude.perf.NpcProfiler;
import org.jetbrains.annotations.Nullable;

/**
 * Movement for NPCs. Behaviors ask to go somewhere (or away from something); this plans a route with {@link NavSearch}
 * and walks it: steering like a player holding forward, jumping up ledges and across gaps, swimming, climbing ladders,
 * opening doors and gates on the way (and closing them behind), and recovering when knocked off course or stuck.
 *
 * Long trips are planned in stretches: when a search runs out of budget the NPC walks to the closest point found and
 * plans the next stretch from there. All NPCs share a per-tick search budget, so mass replanning is spread out.
 */
public class NpcNavigator {
	private static final int SEARCH_NODES = 1500;
	private static final int FLEE_SEARCH_NODES = 1000;
	/** Within this many steps of the end of a partial route, plan the next stretch. */
	private static final int CONTINUE_AT_REMAINING = 3;
	/** A chased target that has moved less than this (squared) since the route was planned doesn't need a new route. */
	private static final double CHASE_REPLAN_DISTANCE_SQR = 1.5 * 1.5;
	private static final double ARRIVE_RADIUS_SQR = 0.35 * 0.35;
	/** No progress toward the next step for this many ticks: try a jump. */
	private static final int STUCK_JUMP_TICKS = 10;
	/** Still none: plan again from here. */
	private static final int STUCK_REPLAN_TICKS = 30;
	/** Further than this (squared) from the next step means the NPC was knocked off its route. */
	private static final double OFF_ROUTE_SQR = 3.0 * 3.0;
	private static final double DOOR_REACH_SQR = 2.5 * 2.5;
	/** Doors are closed again as soon as the body has cleared the doorway (horizontal distance, squared). */
	private static final double DOOR_CLOSE_SQR = 1.0 * 1.0;
	/** Fleeing routes avoid coming within this distance of attackers. */
	private static final double FLEE_DANGER_RADIUS = 8.0;
	private static final double FLEE_DANGER_COST = 0.8;
	/** Within this distance (squared) of a landing spot while in the air, stop pushing forward. */
	private static final double AIR_BRAKE_RADIUS_SQR = 0.45 * 0.45;

	private final EntityNPC npc;
	@Nullable
	private NavPath path;
	@Nullable
	private NavSearch.Goal goal;
	@Nullable
	private NavSearch.CostField goalField;
	private int goalNodes;
	private boolean pending;
	@Nullable
	private Entity chaseTarget;
	private double chaseX;
	private double chaseY;
	private double chaseZ;
	private final List<TilePos> openedDoors = new ArrayList<>();
	private double bestProgress;
	private int ticksWithoutProgress;
	/** Direction of a straight run (radians), and ticks left on it; 0 when not running straight. */
	private double directAngle;
	private int directTicks;

	public NpcNavigator(EntityNPC npc) {
		this.npc = npc;
	}

	// Requests

	/** Heads for a moving target. Cheap to call repeatedly: the route is only re-planned once the target has moved. */
	public boolean moveTo(Entity target) {
		if (this.path != null && this.chaseTarget == target && target.distanceToSqr(this.chaseX, this.chaseY, this.chaseZ) < CHASE_REPLAN_DISTANCE_SQR) {
			return true;
		}

		this.chaseTarget = target;
		this.chaseX = target.x;
		this.chaseY = target.y;
		this.chaseZ = target.z;
		return this.request(NavSearch.near(target.x, target.bb.minY(), target.z, 1.5), null, SEARCH_NODES);
	}

	public boolean moveTo(int x, int y, int z) {
		this.chaseTarget = null;
		return this.request(NavSearch.near(x + 0.5, y, z + 0.5, 1.0), null, SEARCH_NODES);
	}

	/** Runs about this far away from a group of attackers, preferring routes that keep clear of them. */
	public boolean fleeFrom(List<? extends Entity> threats, double distance) {
		if (threats.isEmpty()) {
			return false;
		}

		double cx = 0.0;
		double cz = 0.0;
		for (Entity threat : threats) {
			cx += threat.x;
			cz += threat.z;
		}

		cx /= threats.size();
		cz /= threats.size();
		double angle = Math.atan2(this.npc.z - cz, this.npc.x - cx);
		double goalX = this.npc.x + Math.cos(angle) * distance;
		double goalZ = this.npc.z + Math.sin(angle) * distance;
		this.chaseTarget = null;
		return this.request(NavSearch.near(goalX, this.npc.y, goalZ, 3.0), dangerField(threats), FLEE_SEARCH_NODES);
	}

	/** Runs to someone for safety (e.g. the player it follows), by a route that keeps clear of the attackers. */
	public boolean fleeToward(Entity refuge, List<? extends Entity> threats) {
		this.chaseTarget = null;
		return this.request(NavSearch.near(refuge.x, refuge.bb.minY(), refuge.z, 2.0), dangerField(threats), FLEE_SEARCH_NODES);
	}

	/** Extra route cost for passing near attackers, growing the closer the route comes to one. */
	private static NavSearch.CostField dangerField(List<? extends Entity> threats) {
		List<Entity> dangers = new ArrayList<>(threats);
		return (x, y, z) -> {
			double cost = 0.0;
			for (Entity threat : dangers) {
				double dx = x + 0.5 - threat.x;
				double dz = z + 0.5 - threat.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < FLEE_DANGER_RADIUS) {
					cost += (FLEE_DANGER_RADIUS - d) * FLEE_DANGER_COST;
				}
			}

			return cost;
		};
	}

	/** Runs straight along a heading (radians) for a while, for when no route can be found. Stops at hazards. */
	public void runDirect(double angle, int ticks) {
		this.clearRoute();
		this.directAngle = angle;
		this.directTicks = ticks;
	}

	public void stop() {
		this.clearRoute();
		this.directTicks = 0;
	}

	private void clearRoute() {
		this.path = null;
		this.goal = null;
		this.goalField = null;
		this.pending = false;
		this.chaseTarget = null;
	}

	public boolean isMoving() {
		return this.path != null || this.pending || this.directTicks > 0;
	}

	/** Plans now if the shared budget allows, otherwise next tick. Returns false only if no route exists at all. */
	private boolean request(NavSearch.Goal goal, @Nullable NavSearch.CostField field, int nodes) {
		this.goal = goal;
		this.goalField = field;
		this.goalNodes = nodes;
		this.directTicks = 0;
		if (NavBudget.available(this.npc.world) < NavBudget.MIN_TO_START) {
			this.pending = true;
			return true;
		}

		return this.plan();
	}

	private boolean plan() {
		this.pending = false;
		if (this.goal == null) {
			return false;
		}

		World world = this.npc.world;
		long start = System.nanoTime();
		int budget = Math.min(this.goalNodes, NavBudget.available(world));
		NavPath route = NavSearch.find(world, this.npc.x, this.npc.y, this.npc.z, this.goal, this.goalField, budget);
		NavBudget.spend(world, NavSearch.lastExpanded);
		NpcProfiler.recordSearch(System.nanoTime() - start, NavSearch.lastExpanded);
		if (route != null) {
			// The first step is where the NPC already stands; keep it as the "previous" step.
			route.advance();
			if (route.isDone()) {
				route = null;
			}
		}

		this.path = route;
		this.resetProgress();
		return route != null;
	}

	// Following

	/** Called every tick, after the vanilla AI, to steer along the route. */
	public void tick() {
		if (this.pending && NavBudget.available(this.npc.world) >= NavBudget.MIN_TO_START) {
			this.plan();
		}

		this.closeDoorsBehind();
		if (this.directTicks > 0) {
			this.tickDirectRun();
			return;
		}

		if (this.path == null) {
			return;
		}

		NavStep step = this.path.current();
		while (step != null && this.hasReached(step)) {
			this.path.advance();
			step = this.path.current();
			this.resetProgress();
		}

		if (step == null || !this.path.complete && this.path.remaining() <= CONTINUE_AT_REMAINING) {
			// End of a stretch on a long trip: plan the next one. Otherwise the trip is over.
			boolean continues = !this.path.complete && this.goal != null && this.plan();
			if (!continues && step == null) {
				this.path = null;
				return;
			}

			if (this.path == null) {
				return;
			}

			step = this.path.current();
			if (step == null) {
				return;
			}
		}

		this.openDoorsAhead(step);
		this.steer(step);
		this.brakeInAir(step);
		this.checkProgress(step);
	}

	private boolean hasReached(NavStep step) {
		double dx = step.centerX() - this.npc.x;
		double dz = step.centerZ() - this.npc.z;
		double horizontal = dx * dx + dz * dz;
		return switch (step.move()) {
			case CLIMB -> horizontal < 0.25 && this.npc.y >= step.feetY() - 0.05;
			case DESCEND -> this.npc.y <= step.feetY() + 0.2;
			case DROP -> horizontal < ARRIVE_RADIUS_SQR && this.npc.y < step.feetY() + 3.5;
			default -> horizontal < ARRIVE_RADIUS_SQR && Math.abs(this.npc.y - step.feetY()) < 1.1;
		};
	}

	private void steer(NavStep step) {
		boolean inWater = this.npc.isInWater();
		switch (step.move()) {
			case CLIMB -> {
				// On a ladder, holding jump climbs; in water it swims up.
				this.steerGently(step);
				this.npc.setJumping(true);
			}
			case DESCEND -> this.steerGently(step);
			case JUMP_UP -> {
				this.npc.steerToward(step.centerX(), step.centerZ());
				double dx = step.centerX() - this.npc.x;
				double dz = step.centerZ() - this.npc.z;
				// Jump only while still below the ledge; once up, just walk to the step's center.
				boolean below = this.npc.y < step.feetY() - 0.4;
				if (below && (this.npc.onGround || inWater) && dx * dx + dz * dz < 1.4 * 1.4) {
					this.npc.setJumping(true);
				}
			}
			case GAP_JUMP -> {
				this.npc.steerToward(step.centerX(), step.centerZ());
				NavStep from = this.path.previous();
				if (from != null && this.npc.onGround) {
					// Take off at the edge of the starting block.
					double dirX = Integer.signum(step.x() - from.x());
					double dirZ = Integer.signum(step.z() - from.z());
					double along = (this.npc.x - from.centerX()) * dirX + (this.npc.z - from.centerZ()) * dirZ;
					if (along > 0.3) {
						this.npc.setJumping(true);
					}
				}
			}
			case SWIM -> {
				this.npc.steerToward(step.centerX(), step.centerZ());
				this.npc.setJumping(inWater && step.feetY() >= this.npc.y - 0.3);
			}
			default -> {
				this.npc.steerToward(step.centerX(), step.centerZ());
				if (inWater && step.feetY() >= this.npc.y - 0.3) {
					this.npc.setJumping(true);
				} else if (this.npc.onGround && this.npc.horizontalCollision && step.feetY() > this.npc.y + 0.3) {
					this.npc.setJumping(true);
				}
			}
		}
	}

	/**
	 * Once airborne over the landing spot, let go of forward (as a player does) so momentum doesn't carry the NPC
	 * past narrow landings like fence tops.
	 */
	private void brakeInAir(NavStep step) {
		if (this.npc.onGround || this.npc.isInWater() || step.move() == NavMove.CLIMB || step.move() == NavMove.DESCEND) {
			return;
		}

		double dx = step.centerX() - this.npc.x;
		double dz = step.centerZ() - this.npc.z;
		if (dx * dx + dz * dz < AIR_BRAKE_RADIUS_SQR) {
			this.npc.stopWalking();
		}
	}

	/** Lines up over the step's center without overshooting, for vertical moves. */
	private void steerGently(NavStep step) {
		double dx = step.centerX() - this.npc.x;
		double dz = step.centerZ() - this.npc.z;
		if (dx * dx + dz * dz > 0.15 * 0.15) {
			this.npc.steerToward(step.centerX(), step.centerZ());
		}
	}

	/** Jumps if progress stalls, then re-plans from where it is if it's still stuck or was knocked off course. */
	private void checkProgress(NavStep step) {
		double dx = step.centerX() - this.npc.x;
		double dz = step.centerZ() - this.npc.z;
		double distance = dx * dx + dz * dz + Math.abs(step.feetY() - this.npc.y);
		if (distance > OFF_ROUTE_SQR && step.move() != NavMove.DROP) {
			this.replanOrStop();
			return;
		}

		if (distance < this.bestProgress - 0.01) {
			this.bestProgress = distance;
			this.ticksWithoutProgress = 0;
			return;
		}

		this.ticksWithoutProgress++;
		if (this.ticksWithoutProgress == STUCK_JUMP_TICKS) {
			this.npc.setJumping(true);
		} else if (this.ticksWithoutProgress >= STUCK_REPLAN_TICKS) {
			NpcProfiler.recordStuck();
			this.replanOrStop();
		}
	}

	private void replanOrStop() {
		if (this.goal == null || !this.plan()) {
			this.path = null;
		}
	}

	private void resetProgress() {
		this.bestProgress = Double.MAX_VALUE;
		this.ticksWithoutProgress = 0;
	}

	// Doors

	/** Opens a closed door or gate on the next step or two once it's within reach, like a player clicking it. */
	private void openDoorsAhead(NavStep step) {
		for (int ahead = 0; ahead <= 1; ahead++) {
			NavStep next = ahead == 0 ? step : this.path.peek(1);
			if (next == null || !next.door()) {
				continue;
			}

			for (int dy = 0; dy <= 1; dy++) {
				TilePos pos = new TilePos(next.x(), next.y() + dy, next.z());
				Block<?> block = this.npc.world.getBlockType(pos);
				if (NavGrid.isOpenable(block) && !NavGrid.isOpen(block, this.npc.world.getBlockData(pos))
					&& this.npc.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= DOOR_REACH_SQR) {
					this.toggle(block, pos);
					this.openedDoors.add(pos);
					break;
				}
			}
		}
	}

	/** Closes doors this NPC opened as soon as its body has cleared the doorway, unless its route leads back through. */
	private void closeDoorsBehind() {
		Iterator<TilePos> doors = this.openedDoors.iterator();
		while (doors.hasNext()) {
			TilePos pos = doors.next();
			Block<?> block = this.npc.world.getBlockType(pos);
			if (!NavGrid.isOpenable(block) || !NavGrid.isOpen(block, this.npc.world.getBlockData(pos))) {
				doors.remove();
				continue;
			}

			double dx = pos.x + 0.5 - this.npc.x;
			double dz = pos.z + 0.5 - this.npc.z;
			if (dx * dx + dz * dz > DOOR_CLOSE_SQR && !this.isOnUpcomingSteps(pos)) {
				this.toggle(block, pos);
				doors.remove();
			}
		}
	}

	private boolean isOnUpcomingSteps(TilePos pos) {
		if (this.path == null) {
			return false;
		}

		for (int ahead = 0; ahead <= 1; ahead++) {
			NavStep step = this.path.peek(ahead);
			if (step != null && step.x() == pos.x && step.z() == pos.z && Math.abs(step.y() - pos.y) <= 1) {
				return true;
			}
		}

		return false;
	}

	private void toggle(Block<?> block, TilePos pos) {
		World world = this.npc.world;
		if (block.getLogic() instanceof BlockLogicDoor door) {
			door.onInteracted(world, pos, null, null, 0.5, 0.5);
		} else if (block.getLogic() instanceof BlockLogicFenceGate) {
			// Same as BlockLogicFenceGate.onInteracted, but facing the NPC: a gate opening toward its opener
			// swings the other way, just as it does for players.
			int data = world.getBlockData(pos);
			if (BlockLogicFenceGate.isOpen(data)) {
				world.setBlockDataNotify(pos, data & -5);
			} else {
				int facing = (MathHelper.floor(this.npc.yRot * 4.0F / 360.0F + 0.5) & 3) % 4;
				if (BlockLogicFenceGate.getDirection(data) == (facing + 2) % 4) {
					data = data & 240 | facing;
				}

				world.setBlockDataNotify(pos, data | 4);
			}

			world.playBlockEvent(null, pos, 1003, 0);
		}
	}

	// Straight runs and teleports

	private void tickDirectRun() {
		this.directTicks--;
		int aheadX = MathHelper.floor(this.npc.x + Math.cos(this.directAngle));
		int aheadZ = MathHelper.floor(this.npc.z + Math.sin(this.directAngle));
		if (NavGrid.isHazardAt(this.npc.world, aheadX, MathHelper.floor(this.npc.y), aheadZ)) {
			this.directTicks = 0;
			return;
		}

		this.npc.steerToward(this.npc.x + Math.cos(this.directAngle) * 4.0, this.npc.z + Math.sin(this.directAngle) * 4.0);
		if (this.npc.horizontalCollision) {
			this.npc.setJumping(true);
		}
	}

	/** Teleports to an open spot 2 blocks from the target, like a tamed wolf does. */
	public boolean teleportNear(Entity target) {
		int targetX = MathHelper.floor(target.x);
		int targetY = MathHelper.floor(target.bb.minY());
		int targetZ = MathHelper.floor(target.z);
		NavGrid grid = NavGrid.of(this.npc.world);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					continue;
				}

				NavGrid.Cell cell = grid.cell(targetX + dx, targetY, targetZ + dz);
				if (cell.kind == NavGrid.Kind.STAND && !cell.hazard) {
					this.stop();
					this.npc.teleportTo(targetX + dx + 0.5, cell.feetY, targetZ + dz + 0.5);
					return true;
				}
			}
		}

		return false;
	}

	/** True unless the current route only gets partway (the goal couldn't be reached within the search budget). */
	public boolean isRouteComplete() {
		return this.path == null || this.pending || this.path.complete;
	}

	/** Steps left on the current route, for debugging. */
	public int remainingSteps() {
		return this.path != null ? this.path.remaining() : 0;
	}
}
