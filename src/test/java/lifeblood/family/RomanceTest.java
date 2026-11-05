package lifeblood.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import lifeblood.people.PeopleRegistry;
import lifeblood.world.Calendar;
import org.junit.jupiter.api.Test;

/** Courtship takes real time: a year and a half of dating, then a year engaged, counted in in-game years. */
class RomanceTest {
	private static final int YEAR_DAYS = Calendar.DEFAULT_YEAR_DAYS;
	private static final long DAY = Calendar.TICKS_PER_DAY;

	private static PeopleRegistry.Partnership couple(long since) {
		PeopleRegistry people = new PeopleRegistry(PeopleRegistry.DATA_ID);
		UUID first = UUID.randomUUID();
		assertTrue(people.startDating(first, UUID.randomUUID(), since));
		return people.partnershipOf(first);
	}

	@Test
	void datingTakesAYearAndAHalf() {
		PeopleRegistry.Partnership partnership = couple(1000L);
		long ready = 1000L + 84 * DAY;
		assertFalse(Romance.datedLongEnough(partnership, YEAR_DAYS, ready - 1));
		assertTrue(Romance.datedLongEnough(partnership, YEAR_DAYS, ready));
		assertEquals(1.5F, Romance.yearsTogether(partnership, YEAR_DAYS, ready), 0.001F);
	}

	@Test
	void engagementsLastAYear() {
		PeopleRegistry.Partnership partnership = couple(0L);
		assertFalse(Romance.engagedLongEnough(partnership, YEAR_DAYS, 55 * DAY));
		assertTrue(Romance.engagedLongEnough(partnership, YEAR_DAYS, 56 * DAY));
	}

	@Test
	void timesFollowTheYearLength() {
		// A world with longer years takes longer, day for day.
		PeopleRegistry.Partnership partnership = couple(0L);
		assertFalse(Romance.datedLongEnough(partnership, 112, 84 * DAY));
		assertTrue(Romance.datedLongEnough(partnership, 112, 168 * DAY));
	}
}
