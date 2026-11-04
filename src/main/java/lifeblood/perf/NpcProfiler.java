package lifeblood.perf;

import java.util.Locale;

/**
 * Measures how much time NPCs take, in one-second windows, for /lb debug perf.
 * AI time includes everything an NPC decides in a tick; path search time is the part of it spent planning routes.
 */
public final class NpcProfiler {
	private static final long WINDOW_NANOS = 1_000_000_000L;

	private static long windowStart = System.nanoTime();
	private static long aiNanos;
	private static int aiTicks;
	private static long searchNanos;
	private static int searches;
	private static long nodes;
	private static int stuck;
	private static String lastReport = "No NPC activity measured yet.";

	private NpcProfiler() {
	}

	public static void recordAi(long nanos) {
		roll();
		aiNanos += nanos;
		aiTicks++;
	}

	public static void recordSearch(long nanos, int expanded) {
		roll();
		searchNanos += nanos;
		searches++;
		nodes += expanded;
	}

	public static void recordStuck() {
		roll();
		stuck++;
	}

	/** Closes the window once a second has passed and keeps its summary. */
	private static void roll() {
		long now = System.nanoTime();
		long elapsed = now - windowStart;
		if (elapsed < WINDOW_NANOS) {
			return;
		}

		double seconds = elapsed / 1.0E9;
		double aiMsPerSecond = aiNanos / 1.0E6 / seconds;
		double searchMsPerSecond = searchNanos / 1.0E6 / seconds;
		lastReport = String.format(Locale.ROOT,
			"NPC AI: %.2f ms/tick avg (%.1f%% of a 50 ms tick), %d NPC updates/s. "
				+ "Pathfinding: %.2f ms/tick, %d searches/s, %d nodes/s, %.2f ms per search. Stuck recoveries: %d/s.",
			aiMsPerSecond / 20.0, aiMsPerSecond / 20.0 / 50.0 * 100.0, Math.round(aiTicks / seconds),
			searchMsPerSecond / 20.0, Math.round(searches / seconds), Math.round(nodes / seconds),
			searches > 0 ? searchNanos / 1.0E6 / searches : 0.0, Math.round(stuck / seconds));
		windowStart = now;
		aiNanos = 0;
		aiTicks = 0;
		searchNanos = 0;
		searches = 0;
		nodes = 0;
		stuck = 0;
	}

	/** The summary of the last full second. */
	public static String report() {
		roll();
		return lastReport;
	}
}
