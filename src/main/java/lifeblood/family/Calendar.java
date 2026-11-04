package lifeblood.family;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.world.World;
import net.minecraft.core.world.season.Season;
import net.minecraft.core.world.season.SeasonManagerCycle;
import lifeblood.NpcConfig;
import lifeblood.social.Memory;

/**
 * Days, years and dates for ages and birthdays, following the world's own seasons: an overworld year is four
 * 14-day seasons, 56 days. Worlds without a season cycle use the same 56-day year.
 */
public final class Calendar {
	public static final int DEFAULT_YEAR_DAYS = 56;
	private static final String[] DEFAULT_SEASONS = {"Spring", "Summer", "Fall", "Winter"};

	private Calendar() {
	}

	public static long day(long worldTime) {
		return Math.floorDiv(worldTime, Memory.TICKS_PER_DAY);
	}

	/** The hour of the day, 0 to 23; world time 0 is 6 in the morning. */
	public static int hourOfDay(long worldTime) {
		return (int) (Math.floorMod(worldTime + 6000L, Memory.TICKS_PER_DAY) / 1000L);
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
		return dayOfYear(yearDays(world), worldTime);
	}

	public static int dayOfYear(int yearDays, long worldTime) {
		return (int) Math.floorMod(day(worldTime), yearDays);
	}

	public static long year(World world, long worldTime) {
		return year(yearDays(world), worldTime);
	}

	public static long year(int yearDays, long worldTime) {
		return Math.floorDiv(day(worldTime), yearDays);
	}

	public static long ticksPerYear(World world) {
		return (long) yearDays(world) * Memory.TICKS_PER_DAY;
	}

	public static long growUpTicks(World world) {
		// 18 years by default (1008 days), set in the config.
		return NpcConfig.yearsToGrowUp() * ticksPerYear(world);
	}

	/** Whole years between a birth and now. */
	public static int ageInYears(World world, long born, long now) {
		return ageInYears(yearDays(world), born, now);
	}

	public static int ageInYears(int yearDays, long born, long now) {
		return (int) Math.max(0, Math.floorDiv(day(now) - day(born), yearDays));
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

		return describeDefaultDate(dayOfYear);
	}

	/** A day of the default 56-day year as "Summer 5". */
	public static String describeDefaultDate(int dayOfYear) {
		int length = DEFAULT_YEAR_DAYS / DEFAULT_SEASONS.length;
		return DEFAULT_SEASONS[Math.min(DEFAULT_SEASONS.length - 1, dayOfYear / length)] + " " + (dayOfYear % length + 1);
	}

	private static String seasonName(Season season) {
		String id = season.getId();
		String name = id.substring(id.lastIndexOf('.') + 1).replace('_', ' ');
		return name.isEmpty() ? "Day" : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}
}
