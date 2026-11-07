package lifeblood.travel;

import net.minecraft.core.block.material.Materials;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;

/**
 * Walking somewhere far, by the player's rules: the path search only plans so far ahead, so the trip goes in legs of
 * about 24 blocks toward the goal, each walked with the navigator. A leg that won't plan (a cliff, a lake) is tried
 * bending left and right of the straight line; one that won't plan at all for a while means they're lost for now.
 */
public final class LongWalk {
	/** How long each leg is, in blocks. */
	private static final int LEG = 24;
	/** How close to the goal counts as there, in blocks across the ground. */
	public static final double ARRIVED = 8.0;
	/** The bends tried when the straight way won't plan, in degrees. */
	private static final int[] BENDS = {0, 30, -30, 60, -60, 90, -90};
	/** How many legs in a row may fail before they're lost. */
	private static final int LOST_AFTER = 12;

	/** How it's going. */
	public enum Status {
		WALKING, ARRIVED, LOST
	}

	private int failures;
	private double lastDistance = Double.MAX_VALUE;

	/** One tick of walking toward a spot on the map. */
	public Status toward(EntityNPC npc, int goalX, int goalZ) {
		double dx = goalX + 0.5 - npc.x;
		double dz = goalZ + 0.5 - npc.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= ARRIVED) {
			npc.navigator.stop();
			return Status.ARRIVED;
		}

		if (npc.navigator.isMoving()) {
			return Status.WALKING;
		}

		// A leg that didn't get them any closer counts against them too.
		if (distance > this.lastDistance - 2.0) {
			this.failures++;
		} else {
			this.failures = 0;
		}

		this.lastDistance = distance;
		if (this.failures > LOST_AFTER) {
			return Status.LOST;
		}

		double angle = Math.atan2(dz, dx);
		double leg = Math.min(LEG, distance);
		for (int bend : BENDS) {
			double turned = angle + Math.toRadians(bend);
			int x = (int) Math.floor(npc.x + Math.cos(turned) * leg);
			int z = (int) Math.floor(npc.z + Math.sin(turned) * leg);
			int y = standingY(npc.world, x, z);
			if (y >= 0 && npc.navigator.moveTo(x, y, z)) {
				return Status.WALKING;
			}
		}

		this.failures++;
		return Status.WALKING;
	}

	/** Forgets how the walk has gone, for a new goal. */
	public void reset() {
		this.failures = 0;
		this.lastDistance = Double.MAX_VALUE;
	}

	/** Where feet would stand on the ground of a column, or -1 if it isn't loaded or is water or lava. */
	static int standingY(World world, int x, int z) {
		TilePos pos = new TilePos(x, 0, z);
		if (!world.isBlockLoaded(pos)) {
			return -1;
		}

		int y = world.getHeightValue(x, z);
		var material = world.getBlockType(pos.set(x, y - 1, z)).getMaterial();
		return material.isLiquid() || material == Materials.ICE ? -1 : y;
	}
}
