package npclaude.family;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.world.World;
import net.minecraft.core.world.season.Season;
import net.minecraft.core.world.season.SeasonManagerCycle;
import npclaude.social.Memory;

/**
 * Days, years and dates for ages and birthdays, following the world's own seasons: an overworld year is four
 * 14-day seasons, 56 days. Worlds without a season cycle use the same 56-day year.
 */
public final class Calendar {
	public static final int DEFAULT_YEAR_DAYS = 56;
	/** Children grow up over 18 years. A future game setting; 1008 days in the default world. */
	public static final int YEARS_TO_GROW_UP = 18;
	private static final String[] DEFAULT_SEASONS = {"Spring", "Summer", "Fall", "Winter"};

	private Calendar() {
	}

	public static long day(long worldTime) {
		return Math.floorDiv(worldTime, Memory.TICKS_PER_DAY);
	}

	public static int yearDays(World world) {
		if (world.getSeasonManager() instanceof SeasonManagerCycle cycle) {
			int days = 0;
			for (Season season : cycle.getSeasons()) {
				days += cycle.getConfig().getSeasonLength(season);
			}

			if (days > 0) {
				return days;
			}
		}

		return DEFAULT_YEAR_DAYS;
	}

	public static int dayOfYear(World world, long worldTime) {
		return (int) Math.floorMod(day(worldTime), yearDays(world));
	}

	public static long year(World world, long worldTime) {
		return Math.floorDiv(day(worldTime), yearDays(world));
	}

	public static long ticksPerYear(World world) {
		return (long) yearDays(world) * Memory.TICKS_PER_DAY;
	}

	public static long growUpTicks(World world) {
		return YEARS_TO_GROW_UP * ticksPerYear(world);
	}

	/** Whole years between a birth and now. */
	public static int ageInYears(World world, long born, long now) {
		return (int) Math.max(0, Math.floorDiv(day(now) - day(born), yearDays(world)));
	}

	/** "Summer 5": the season and day within it. */
	public static String describeDate(World world, long worldTime) {
		int dayOfYear = dayOfYear(world, worldTime);
		if (world.getSeasonManager() instanceof SeasonManagerCycle cycle) {
			int start = 0;
			List<Season> seasons = cycle.getSeasons();
			for (Season season : seasons) {
				int length = cycle.getConfig().getSeasonLength(season);
				if (dayOfYear < start + length) {
					return seasonName(season) + " " + (dayOfYear - start + 1);
				}

				start += length;
			}
		}

		int length = DEFAULT_YEAR_DAYS / DEFAULT_SEASONS.length;
		return DEFAULT_SEASONS[Math.min(DEFAULT_SEASONS.length - 1, dayOfYear / length)] + " " + (dayOfYear % length + 1);
	}

	private static String seasonName(Season season) {
		String id = season.getId();
		String name = id.substring(id.lastIndexOf('.') + 1).replace('_', ' ');
		return name.isEmpty() ? "Day" : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}
}
