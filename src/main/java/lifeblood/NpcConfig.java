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
		config = new TomlConfigHandler(Mod.ID, defaults);
	}

	public static int guardianAmuletId() {
		return config != null ? config.getInt("Items.guardianAmulet") : DEFAULT_GUARDIAN_AMULET_ID;
	}

	public static int wordBookId() {
		return config != null ? config.getInt("Items.wordBook") : DEFAULT_WORD_BOOK_ID;
	}

	public static int yearsToGrowUp() {
		return Math.max(1, config != null ? config.getInt("Game.yearsToGrowUp") : DEFAULT_YEARS_TO_GROW_UP);
	}
}
