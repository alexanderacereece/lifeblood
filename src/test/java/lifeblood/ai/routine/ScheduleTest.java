package lifeblood.ai.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import lifeblood.Systems;
import lifeblood.world.Calendar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScheduleTest {
	@BeforeAll
	static void registerSystems() {
		Systems.registerAll();
	}

	@Test
	void blocksRunUntilTheNextOneStarts() {
		Schedule farmer = new Schedule("farmer", new Schedule.Block(22, "sleep"), new Schedule.Block(6, "work"), new Schedule.Block(18, "socialize"));
		assertEquals("work", farmer.activityAt(6));
		assertEquals("work", farmer.activityAt(17));
		assertEquals("socialize", farmer.activityAt(18));
		assertEquals("sleep", farmer.activityAt(23));
		// Past midnight, the last block of the day carries on.
		assertEquals("sleep", farmer.activityAt(0));
		assertEquals("sleep", farmer.activityAt(5));
	}

	@Test
	void theDefaultScheduleWandersAllDay() {
		Schedule schedule = Routines.schedule(Routines.DEFAULT_SCHEDULE);
		for (int hour = 0; hour < 24; hour++) {
			assertEquals(Routines.WANDER, schedule.activityAt(hour));
		}

		assertEquals(schedule, Routines.schedule("no such schedule"));
		assertNotNull(Routines.startActivity(Routines.WANDER));
	}

	@Test
	void hoursFollowTheDayCycle() {
		assertEquals(6, Calendar.hourOfDay(0));
		assertEquals(12, Calendar.hourOfDay(6000));
		assertEquals(0, Calendar.hourOfDay(18000));
		assertEquals(5, Calendar.hourOfDay(23999));
		assertEquals(6, Calendar.hourOfDay(24000));
	}
}
