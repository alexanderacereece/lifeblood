package npclaude.personality;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.biome.Biome;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.season.Season;
import net.minecraft.core.world.weather.Weather;
import org.jetbrains.annotations.Nullable;

/**
 * The weather, biome and season an NPC is in, as short keys ("storm", "desert", "winter"), with how much people
 * like each before personality comes into it. Unknown keys (other mods' biomes) still work, starting at 0.
 */
public final class Surroundings {
	public enum Kind {
		WEATHER, BIOME, SEASON;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Map<String, Float> WEATHER = ordered(
		"clear", 0.35F, "rain", -0.15F, "snow", 0.1F, "storm", -0.35F, "fog", -0.15F, "blood_rain", -0.8F);
	public static final Map<String, Float> BIOMES = ordered(
		"plains", 0.25F, "meadow", 0.3F, "forest", 0.25F, "birch_forest", 0.25F, "seasonal_forest", 0.3F, "grasslands", 0.2F,
		"shrubland", 0.1F, "rainforest", 0.1F, "swamp", -0.25F, "muddy_swamp", -0.35F, "desert", -0.1F, "outback", -0.1F,
		"grassy_outback", 0.0F, "caatinga", 0.0F, "caatinga_plains", 0.05F, "taiga", 0.05F, "boreal_forest", 0.1F, "tundra", -0.15F,
		"glacier", -0.3F, "retro", 0.1F, "hell", -0.6F, "crag", -0.5F, "volcanic_islands", -0.5F, "sulfur_pools", -0.6F,
		"crystal_plains", -0.2F, "crystal_forest", -0.2F, "old_world", -0.3F, "old_world_desert", -0.4F, "shelf", -0.4F, "drift", -0.2F,
		"cave", -0.15F, "ocean", 0.15F);
	public static final Map<String, Float> SEASONS = ordered("spring", 0.25F, "summer", 0.15F, "fall", 0.15F, "winter", -0.1F);

	/** Biome names that read as plural: "the plains are...". */
	private static final Set<String> PLURAL_NAMES = Set.of("plains", "grasslands", "volcanic_islands", "sulfur_pools", "crystal_plains",
		"caatinga_plains");

	private Surroundings() {
	}

	public static Map<String, Float> table(Kind kind) {
		return switch (kind) {
			case WEATHER -> WEATHER;
			case BIOME -> BIOMES;
			case SEASON -> SEASONS;
		};
	}

	public static float base(Kind kind, String key) {
		return table(kind).getOrDefault(key, 0.0F);
	}

	/** The weather where this spot is: a biome that never gets rain (a desert) stays clear while it rains elsewhere. */
	public static String weatherAt(World world, double x, double y, double z) {
		Weather weather = world.getCurrentWeather();
		if (weather == null) {
			return "clear";
		}

		Biome biome = biomeObjectAt(world, x, y, z);
		if (biome != null && biome.blockedWeathers.contains(weather)) {
			return "clear";
		}

		String key = weather.getLanguageKey();
		if (key.contains("blood")) {
			return "blood_rain";
		} else if (key.contains("storm")) {
			return "storm";
		} else if (key.contains("snow")) {
			return "snow";
		} else if (key.contains("rain")) {
			return "rain";
		} else if (key.contains("fog")) {
			return "fog";
		}

		return "clear";
	}

	/** Below this many blocks under sea level with no sky overhead counts as a cave. */
	private static final int CAVE_DEPTH = 6;
	private static final int OCEAN_SAMPLE_RADIUS = 10;
	private static final int OCEAN_SAMPLES = 12;

	/**
	 * Where someone is, as far as feelings go: "cave" underground, "ocean" by a big stretch of open water, otherwise
	 * the biome.
	 */
	@Nullable
	public static String placeAt(World world, double x, double y, double z) {
		int seaLevel = world.getWorldType().getOceanY();
		TilePos head = new TilePos(MathHelper.floor(x), MathHelper.floor(y + 1.5), MathHelper.floor(z));
		boolean sky = world.canBlockSeeSky(head);
		if (!sky && y < seaLevel - CAVE_DEPTH) {
			return "cave";
		}

		if (sky && y > seaLevel - 4 && y < seaLevel + 12 && isByOpenWater(world, x, z, seaLevel)) {
			return "ocean";
		}

		return biomeAt(world, x, y, z);
	}

	/** Most of a ring around the spot is water at sea level. */
	private static boolean isByOpenWater(World world, double x, double z, int seaLevel) {
		int water = 0;
		for (int i = 0; i < OCEAN_SAMPLES; i++) {
			double angle = Math.PI * 2.0 * i / OCEAN_SAMPLES;
			TilePos pos = new TilePos(MathHelper.floor(x + Math.cos(angle) * OCEAN_SAMPLE_RADIUS), seaLevel - 1,
				MathHelper.floor(z + Math.sin(angle) * OCEAN_SAMPLE_RADIUS));
			if (world.isBlockLoaded(pos) && world.getBlockMaterial(pos) == Materials.WATER) {
				water++;
			}
		}

		return water >= OCEAN_SAMPLES * 3 / 4;
	}

	@Nullable
	public static String biomeAt(World world, double x, double y, double z) {
		Biome biome = biomeObjectAt(world, x, y, z);
		if (biome == null || biome.getRegistryKey() == null) {
			return null;
		}

		String key = biome.getRegistryKey();
		key = key.substring(key.indexOf(':') + 1);
		for (String prefix : new String[] {"overworld.", "nether.", "drift."}) {
			if (key.startsWith(prefix)) {
				key = key.substring(prefix.length());
			}
		}

		key = key.replace("legacy_", "");
		return switch (key) {
			case "swampland" -> "swamp";
			case "swampland_muddy" -> "muddy_swamp";
			case "outback_grassy" -> "grassy_outback";
			case "savanna" -> "grasslands";
			case "legacy_hell" -> "hell";
			default -> key;
		};
	}

	@Nullable
	private static Biome biomeObjectAt(World world, double x, double y, double z) {
		try {
			return world.getBlockBiome(new TilePos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The season, if this world has the usual four. */
	@Nullable
	public static String seasonOf(World world) {
		if (world.getSeasonManager() == null) {
			return null;
		}

		Season season = world.getSeasonManager().getCurrentSeason();
		if (season == null) {
			return null;
		}

		String id = season.getId();
		for (String key : SEASONS.keySet()) {
			if (id.endsWith("." + key) || id.equals(key) || id.endsWith("." + key + "_endless")) {
				return key;
			}
		}

		return null;
	}

	/** How a key reads in a sentence: "birch_forest" is "birch forest". */
	public static String displayName(String key) {
		return switch (key) {
			case "clear" -> "clear skies";
			case "blood_rain" -> "blood rain";
			case "retro" -> "old green country";
			default -> key.replace('_', ' ');
		};
	}

	/** Whether a key's name is plural ("plains", "clear skies", "storms" are; "rain" isn't). */
	public static boolean isPluralName(String key) {
		return PLURAL_NAMES.contains(key) || key.equals("clear");
	}

	private static Map<String, Float> ordered(Object... pairs) {
		Map<String, Float> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Float) pairs[i + 1]);
		}

		return map;
	}
}
