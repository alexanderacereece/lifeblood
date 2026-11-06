package lifeblood.world;

/**
 * How many game ticks a dedicated server runs for each real one, for testing: at 10, a minute of real time is ten
 * minutes of game time, and everything (NPCs, crops, day and night) moves along together. Set by /lb debug speed;
 * not saved, so a restart is back to normal. Single-player isn't sped up.
 */
public final class GameSpeed {
	/** The fastest the game can be run, in game ticks per real tick. */
	public static final int MAX = 20;

	private static volatile int ticksPerTick = 1;

	private GameSpeed() {
	}

	public static int ticksPerTick() {
		return ticksPerTick;
	}

	public static void set(int ticks) {
		ticksPerTick = Math.max(1, Math.min(MAX, ticks));
	}
}
