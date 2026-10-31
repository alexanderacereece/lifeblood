package npclaude.nav;

import net.minecraft.core.world.World;

/**
 * Caps how much path searching all NPCs together may do in one tick, so a big fight where everyone replans at once
 * spreads the work over a few ticks instead of stalling one. Requests over budget wait for the next tick.
 */
public final class NavBudget {
	/** Search nodes all NPCs may expand per tick. */
	public static final int NODES_PER_TICK = 6000;
	/** Don't start a search with less than this left; wait for the next tick instead. */
	public static final int MIN_TO_START = 400;

	private static long tick = Long.MIN_VALUE;
	private static int used;

	private NavBudget() {
	}

	private static void roll(World world) {
		long now = world.getTotalWorldTime();
		if (now != tick) {
			tick = now;
			used = 0;
		}
	}

	public static int available(World world) {
		roll(world);
		return Math.max(0, NODES_PER_TICK - used);
	}

	public static void spend(World world, int nodes) {
		roll(world);
		used += nodes;
	}
}
