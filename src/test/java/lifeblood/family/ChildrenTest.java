package lifeblood.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import lifeblood.entity.NpcGender;
import lifeblood.people.PeopleRegistry;
import lifeblood.world.Calendar;
import org.junit.jupiter.api.Test;

/** Children come at a real-life pace: about one every two years, the youngest at least a year and a half old, four at most. */
class ChildrenTest {
	private static final int YEAR_DAYS = Calendar.DEFAULT_YEAR_DAYS;
	private static final long YEAR = Calendar.ticksPerYear(YEAR_DAYS);

	@Test
	void aChildComesAboutEveryTwoYears() {
		float checksPerYear = YEAR / (float) Romance.CHECK_INTERVAL;
		float childrenPerYear = Children.birthChance(YEAR_DAYS) * checksPerYear;
		assertEquals(0.5F, childrenPerYear, 0.001F);
	}

	@Test
	void theYoungestMustBeAYearAndAHalf() {
		PeopleRegistry family = new PeopleRegistry(PeopleRegistry.DATA_ID);
		UUID mother = UUID.randomUUID();
		UUID father = UUID.randomUUID();
		assertTrue(Children.canHaveChild(YEAR_DAYS, family, mother, father, 0L));
		long born = 10 * YEAR;
		addChild(family, mother, father, born);
		assertFalse(Children.canHaveChild(YEAR_DAYS, family, mother, father, born + YEAR));
		assertTrue(Children.canHaveChild(YEAR_DAYS, family, mother, father, born + (long) (1.5 * YEAR)));
	}

	@Test
	void fourAtMost() {
		PeopleRegistry family = new PeopleRegistry(PeopleRegistry.DATA_ID);
		UUID mother = UUID.randomUUID();
		UUID father = UUID.randomUUID();
		for (int i = 0; i < 4; i++) {
			addChild(family, mother, father, i * 2 * YEAR);
		}

		assertFalse(Children.canHaveChild(YEAR_DAYS, family, mother, father, 100 * YEAR));
	}

	private static void addChild(PeopleRegistry family, UUID mother, UUID father, long born) {
		UUID child = UUID.randomUUID();
		family.person(child, "Kid", NpcGender.FEMALE, false).born = born;
		family.addParent(child, mother);
		family.addParent(child, father);
	}
}
