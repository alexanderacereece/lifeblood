package lifeblood.homestead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import lifeblood.homestead.HouseholdPlan.Kind;
import lifeblood.homestead.HouseholdPlan.Member;
import lifeblood.homestead.HouseholdPlan.Role;
import org.junit.jupiter.api.Test;

class HouseholdPlannerTest {
	private static final int ROLLS = 2000;

	/** Spread-out seeds: Random's first numbers barely differ for seeds that are close together. */
	private static Random random(int seed) {
		return new Random(seed * 0x9E3779B97F4A7C15L);
	}

	@Test
	void noBedsNoHousehold() {
		assertNull(HouseholdPlanner.plan(0, 0, new Random(1)));
	}

	@Test
	void oneSingleBedIsSomeoneOnTheirOwn() {
		for (int seed = 0; seed < ROLLS; seed++) {
			HouseholdPlan plan = HouseholdPlanner.plan(0, 1, random(seed));
			assertNotNull(plan);
			assertEquals(Kind.LONE, plan.kind());
			assertEquals(1, plan.members().size());
			assertTrue(plan.members().get(0).age() >= 18);
		}
	}

	@Test
	void nobodyIsWithoutABedAndNoBedIsShared() {
		for (int doubles = 0; doubles <= 2; doubles++) {
			for (int singles = 0; singles <= 4; singles++) {
				for (int seed = 0; seed < ROLLS / 4; seed++) {
					HouseholdPlan plan = HouseholdPlanner.plan(doubles, singles, random(seed));
					if (plan == null) {
						continue;
					}

					Set<String> singleBeds = new HashSet<>();
					int[] doubleUse = new int[Math.max(1, doubles)];
					for (Member member : plan.members()) {
						if (member.inDouble()) {
							assertTrue(member.bed() < doubles, "double bed " + member.bed() + " of " + doubles);
							assertTrue(++doubleUse[member.bed()] <= 2, "three in a double bed");
						} else {
							assertTrue(member.bed() < singles, "single bed " + member.bed() + " of " + singles);
							assertTrue(singleBeds.add("s" + member.bed()), "two in a single bed");
						}
					}
				}
			}
		}
	}

	@Test
	void childrenAreYoungEnoughForTheirParents() {
		for (int seed = 0; seed < ROLLS; seed++) {
			HouseholdPlan plan = HouseholdPlanner.plan(1, 3, random(seed));
			assertNotNull(plan);
			int youngestParent = Integer.MAX_VALUE;
			for (Member member : plan.members()) {
				if (member.role() == Role.HEAD || member.role() == Role.SPOUSE) {
					youngestParent = Math.min(youngestParent, member.age());
				}
			}

			if (plan.lateSpouse() != null) {
				youngestParent = Math.min(youngestParent, plan.lateSpouse().ageAtDeath() + plan.lateSpouse().yearsAgo());
			}

			for (Member member : plan.members()) {
				if (member.role() == Role.CHILD) {
					assertTrue(member.age() <= 17, "a child of " + member.age());
					assertTrue(youngestParent - member.age() >= HouseholdPlanner.MIN_PARENT_AGE, "parent " + youngestParent + ", child " + member.age());
					if (plan.lateSpouse() != null) {
						assertTrue(member.age() >= plan.lateSpouse().yearsAgo(), "born after their parent died");
					}

					String expected = member.age() < plan.yearsHere() ? HouseholdPlanner.BORN_HERE : HouseholdPlanner.CAME_WITH_FAMILY;
					assertEquals(expected, member.cameFor());
				}
			}
		}
	}

	@Test
	void aDoubleBedIsACoupleOrAWidowedParent() {
		boolean sawCouple = false;
		boolean sawWidowed = false;
		for (int seed = 0; seed < ROLLS; seed++) {
			HouseholdPlan plan = HouseholdPlanner.plan(1, 0, random(seed));
			assertNotNull(plan);
			if (plan.kind() == Kind.FAMILY) {
				sawCouple = true;
				Member head = plan.members().get(0);
				Member spouse = plan.members().get(head.partnerOf());
				assertEquals(0, spouse.partnerOf());
				assertTrue(head.female() != spouse.female());
			} else {
				assertEquals(Kind.WIDOWED, plan.kind());
				assertNotNull(plan.lateSpouse());
				sawWidowed = true;
			}
		}

		assertTrue(sawCouple && sawWidowed);
	}

	@Test
	void severalSinglesAreFriendsSiblingsOrAWidowedParent() {
		Set<Kind> kinds = new HashSet<>();
		for (int seed = 0; seed < ROLLS; seed++) {
			HouseholdPlan plan = HouseholdPlanner.plan(0, 3, random(seed));
			assertNotNull(plan);
			kinds.add(plan.kind());
			assertEquals(plan.kind() == Kind.SIBLINGS, plan.parentsBackHome());
		}

		assertEquals(Set.of(Kind.FRIENDS, Kind.SIBLINGS, Kind.WIDOWED), kinds);
	}
}
