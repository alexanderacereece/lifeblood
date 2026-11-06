package lifeblood.homestead.life;

import lifeblood.ai.routine.Schedule;

/**
 * A homesteader's day, as a schedule: chores from 6, a meal at home at 12, chores or a walk from 13, home for the
 * evening at 18, and bed at 21. Its activities are registered from HomesteadSetup; generated homesteaders follow it.
 */
public final class HomesteadDay {
	public static final String SCHEDULE = "homestead";
	public static final String SLEEP = "homestead_sleep";
	public static final String CHORES = "homestead_chores";
	public static final String MEAL = "homestead_meal";
	public static final String EVENING = "homestead_evening";
	/** How far from home they potter about in the evening, in blocks. */
	public static final int EVENING_RADIUS = 6;

	private HomesteadDay() {
	}

	/** The day's plan, hour by hour. */
	public static Schedule schedule() {
		return new Schedule(SCHEDULE, new Schedule.Block(6, CHORES), new Schedule.Block(12, MEAL), new Schedule.Block(13, CHORES),
			new Schedule.Block(18, EVENING), new Schedule.Block(21, SLEEP));
	}
}
