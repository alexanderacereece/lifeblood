package lifeblood.work;

import lifeblood.entity.EntityNPC;

/**
 * Walking over to a block until it's within reach, for anything worked on by hand: a tree to chop, a chest to open, a
 * field to till. Tries standing spots around the block, and gives up if it can't get there in reasonable time.
 */
public final class Approach {
	/** How long to keep trying to get within reach before giving up, in ticks (30 seconds). */
	private static final int GIVE_UP_TICKS = 600;
	/** How many thinking ticks to wait before planning a new path when the last one ended out of reach. */
	private static final int REPATH_TICKS = 4;
	/** Standing spots tried around the block: beside it at its level, one below, one above, then on top. */
	private static final int[][] SPOTS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
		{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {0, 1, 0}};

	/** How it's going. */
	public enum Status {
		/** Within reach now. */
		REACHED,
		/** On the way. */
		WALKING,
		/** No way there was found in time. */
		STUCK
	}

	private int targetX = Integer.MIN_VALUE;
	private int targetY;
	private int targetZ;
	/** When it started on this block (world time). */
	private long started;
	private int repath;

	/** One tick of getting within reach of a block; starts over for a different block. */
	public Status toward(EntityNPC npc, int x, int y, int z) {
		if (x != this.targetX || y != this.targetY || z != this.targetZ) {
			this.targetX = x;
			this.targetY = y;
			this.targetZ = z;
			this.started = npc.world.getWorldTime();
			this.repath = 0;
		}

		if (Reach.canReach(npc, x, y, z)) {
			npc.navigator.stop();
			return Status.REACHED;
		}

		if (npc.world.getWorldTime() - this.started > GIVE_UP_TICKS) {
			npc.navigator.stop();
			return Status.STUCK;
		}

		if (!npc.navigator.isMoving() && --this.repath <= 0) {
			this.repath = REPATH_TICKS;
			for (int[] spot : SPOTS) {
				if (npc.navigator.moveTo(x + spot[0], y + spot[1], z + spot[2])) {
					break;
				}
			}
		}

		return Status.WALKING;
	}

	/** Forgets the block, so the next one starts fresh. */
	public void reset() {
		this.targetX = Integer.MIN_VALUE;
	}
}
