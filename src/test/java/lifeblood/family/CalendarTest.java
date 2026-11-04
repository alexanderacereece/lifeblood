package lifeblood.family;

import static org.junit.jupiter.api.Assertions.assertEquals;

import lifeblood.social.Memory;
import org.junit.jupiter.api.Test;

class CalendarTest {
	private static final long DAY = Memory.TICKS_PER_DAY;
	private static final int YEAR = Calendar.DEFAULT_YEAR_DAYS;

	@Test
	void daysCountFromWorldTime() {
		assertEquals(0, Calendar.day(0));
		assertEquals(0, Calendar.day(DAY - 1));
		assertEquals(1, Calendar.day(DAY));
		// Births long before the world began are negative times, and still land on whole days.
		assertEquals(-1, Calendar.day(-1));
	}

	@Test
	void dayOfYearWrapsEachYear() {
		assertEquals(0, Calendar.dayOfYear(YEAR, 0));
		assertEquals(5, Calendar.dayOfYear(YEAR, 5 * DAY));
		assertEquals(5, Calendar.dayOfYear(YEAR, (YEAR + 5) * DAY));
		assertEquals(YEAR - 1, Calendar.dayOfYear(YEAR, -DAY));
	}

	@Test
	void agesCountWholeYears() {
		long born = 3 * DAY;
		assertEquals(0, Calendar.ageInYears(YEAR, born, born));
		assertEquals(0, Calendar.ageInYears(YEAR, born, born + (YEAR - 1) * DAY));
		assertEquals(1, Calendar.ageInYears(YEAR, born, born + YEAR * DAY));
		assertEquals(27, Calendar.ageInYears(YEAR, born - 27L * YEAR * DAY, born));
		assertEquals(0, Calendar.ageInYears(YEAR, born, 0));
	}

	@Test
	void defaultDatesNameTheSeason() {
		assertEquals("Spring 1", Calendar.describeDefaultDate(0));
		assertEquals("Spring 14", Calendar.describeDefaultDate(13));
		assertEquals("Summer 1", Calendar.describeDefaultDate(14));
		assertEquals("Winter 14", Calendar.describeDefaultDate(55));
	}
}
