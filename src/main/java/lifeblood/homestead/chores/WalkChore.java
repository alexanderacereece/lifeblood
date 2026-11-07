package lifeblood.homestead.chores;

import lifeblood.entity.EntityNPC;

/**
 * A walk for the pleasure of it: out to somewhere a fair way from home, a while to look around, then done (the next
 * chore brings them back). The curious go farther. Children's version is playing close to home.
 */
public final class WalkChore implements Chore {
	/** How far out a walk goes, in blocks; the curious go up to twice as far. */
	private static final int MIN_DISTANCE = 12;
	private static final int MAX_DISTANCE = 28;
	/** How long they look around once there, in ticks (10 seconds). */
	private static final int LINGER_TICKS = 200;
	/** How long a walk may take before they give up on getting there, in ticks (1 minute). */
	private static final int MAX_TICKS = 1200;

	private final int distanceMin;
	private final int distanceMax;
	/** When the walk began, and when they got there (world time); -1 until then. */
	private long began = -1L;
	private long arrived = -1L;
	private boolean started;

	/** A walk out between these distances from home. */
	public WalkChore(int distanceMin, int distanceMax) {
		this.distanceMin = distanceMin;
		this.distanceMax = distanceMax;
	}

	/** A grown-up's walk; farther for the curious. */
	public static WalkChore stroll(boolean curious) {
		return new WalkChore(MIN_DISTANCE, curious ? MAX_DISTANCE * 2 : MAX_DISTANCE);
	}

	@Override
	public boolean tick(EntityNPC npc) {
		long now = npc.world.getWorldTime();
		if (this.began < 0L) {
			this.began = now;
		}

		if (now - this.began > MAX_TICKS) {
			return true;
		}

		if (!this.started) {
			this.started = this.setOut(npc);
			return false;
		}

		if (npc.navigator.isMoving()) {
			return false;
		}

		if (this.arrived < 0L) {
			this.arrived = now;
		}

		return now - this.arrived > LINGER_TICKS;
	}

	/**
	 * Picks a spot in a random direction and heads for it, coming closer if no way that far is found (hills and water
	 * get in the way); returns whether a way somewhere was found.
	 */
	private boolean setOut(EntityNPC npc) {
		double angle = npc.getRandom().nextDouble() * Math.PI * 2.0;
		int distance = this.distanceMin + npc.getRandom().nextInt(Math.max(1, this.distanceMax - this.distanceMin));
		for (; distance >= 2; distance /= 2) {
			int x = (int) Math.floor(npc.home.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(npc.home.getZ() + Math.sin(angle) * distance);
			if (npc.navigator.moveTo(x, npc.world.getHeightValue(x, z), z)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String doing() {
		return this.distanceMax <= MIN_DISTANCE ? "playing" : "walking";
	}

	@Override
	public String describe() {
		return this.distanceMax <= MIN_DISTANCE ? "playing near home" : "out for a walk";
	}
}
