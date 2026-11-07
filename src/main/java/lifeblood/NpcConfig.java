package lifeblood;

import turniplabs.halplibe.util.TomlConfigHandler;
import turniplabs.halplibe.util.toml.Toml;

/**
 * The mod's settings, in config/lifeblood.cfg: item ids (changeable if they clash with another mod's) and game
 * settings. Before the file is loaded (as in tests), the defaults apply.
 */
public final class NpcConfig {
	public static final int DEFAULT_GUARDIAN_AMULET_ID = 25000;
	public static final int DEFAULT_WORD_BOOK_ID = 25001;
	public static final int DEFAULT_YEARS_TO_GROW_UP = 18;
	/** How many trips away (journeys, adventures, visits) an adult who's neither homebody nor wanderer takes a year. */
	public static final double DEFAULT_TRIPS_PER_YEAR = 6.0;
	/** How many strangers pass near each player in an in-game day, on average. */
	public static final double DEFAULT_STRANGERS_PER_DAY = 0.75;

	private static TomlConfigHandler config;

	private NpcConfig() {
	}

	/** Reads the config file, writing one with the defaults if there isn't one yet. */
	public static void load() {
		Toml defaults = new Toml("Lifeblood settings");
		defaults.addCategory("Item ids, if they clash with another mod's", "Items");
		defaults.addEntry("Items.guardianAmulet", DEFAULT_GUARDIAN_AMULET_ID);
		defaults.addEntry("Items.wordBook", DEFAULT_WORD_BOOK_ID);
		defaults.addCategory("Game settings", "Game");
		defaults.addEntry("Game.yearsToGrowUp", "How many in-game years children take to grow up (a year is 56 days)", DEFAULT_YEARS_TO_GROW_UP);
		defaults.addEntry("Game.tripsPerYear", "How many trips away (journeys, adventures, visits) a typical adult takes a year; wanderers more, homebodies hardly any", DEFAULT_TRIPS_PER_YEAR);
		defaults.addEntry("Game.strangersPerDay", "How many strangers pass near each player in an in-game day, on average", DEFAULT_STRANGERS_PER_DAY);
		config = new TomlConfigHandler(Mod.ID, defaults);
	}

	public static int guardianAmuletId() {
		return config != null ? config.getInt("Items.guardianAmulet") : DEFAULT_GUARDIAN_AMULET_ID;
	}

	public static int wordBookId() {
		return config != null ? config.getInt("Items.wordBook") : DEFAULT_WORD_BOOK_ID;
	}

	public static double tripsPerYear() {
		return Math.max(0.0, config != null ? config.getDouble("Game.tripsPerYear") : DEFAULT_TRIPS_PER_YEAR);
	}

	public static double strangersPerDay() {
		return Math.max(0.0, config != null ? config.getDouble("Game.strangersPerDay") : DEFAULT_STRANGERS_PER_DAY);
	}

	public static int yearsToGrowUp() {
		return Math.max(1, config != null ? config.getInt("Game.yearsToGrowUp") : DEFAULT_YEARS_TO_GROW_UP);
	}
}
