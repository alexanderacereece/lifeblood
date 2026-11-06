package lifeblood.homestead;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lifeblood.homestead.HouseholdPlan.Kind;
import lifeblood.homestead.HouseholdPlan.LateSpouse;
import lifeblood.homestead.HouseholdPlan.Member;
import lifeblood.homestead.HouseholdPlan.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Decides who lives in a homestead from its beds: a double bed is a couple (or a widow or widower who still sleeps on
 * their side), a single bed alone is one person. A house with a double bed is a family's; one single bed is someone
 * on their own; several singles are friends, brothers and sisters, or a widowed parent with children. Children are
 * young enough for their parents to have had them, and those younger than the homestead were born in it.
 */
public final class HouseholdPlanner {
	/** Why people come out to live on their own land, as NpcLife.getCameFor says it. */
	static final String[] REASONS = {"fresh_start", "faith", "land", "quiet", "running"};
	/** Families mostly come for land or a fresh start; running from something is for those on their own. */
	private static final String[] FAMILY_REASONS = {"fresh_start", "faith", "land", "land", "quiet"};
	public static final String CAME_WITH_FAMILY = "family";
	public static final String BORN_HERE = "born_here";

	/** The chance a house with a double bed holds a widow or widower instead of a couple. */
	private static final float WIDOWED_CHANCE = 0.15F;
	/** The chance each spare single bed in a family's house holds a child. */
	private static final float CHILD_CHANCE = 0.75F;
	/** The chance a second double bed holds another couple, friends of the first. */
	private static final float FRIEND_COUPLE_CHANCE = 0.5F;
	/** With several single beds and no double: the chance of a widowed parent with children, then of siblings. */
	private static final float WIDOWED_PARENT_CHANCE = 0.3F;
	private static final float SIBLINGS_CHANCE = 0.35F;
	/** The chance friends share one reason for coming (a shared faith, say) rather than each having their own. */
	private static final float SHARED_REASON_CHANCE = 0.5F;
	/** The youngest and oldest a parent can be when a child is born, in years. */
	static final int MIN_PARENT_AGE = 18;
	private static final int MAX_MOTHER_AGE = 42;
	private static final int MAX_CHILD_AGE = 17;
	/** How many years ago a homestead was settled, at most. */
	private static final int MAX_YEARS_HERE = 8;

	private HouseholdPlanner() {
	}

	/** The household for a house with these beds, or null if it has none. */
	@Nullable
	public static HouseholdPlan plan(int doubles, int singles, Random random) {
		if (doubles > 0) {
			return family(doubles, singles, random);
		} else if (singles == 1) {
			return lone(random);
		} else if (singles > 1) {
			float roll = random.nextFloat();
			if (roll < WIDOWED_PARENT_CHANCE) {
				return widowed(false, singles, random);
			}

			return roll < WIDOWED_PARENT_CHANCE + SIBLINGS_CHANCE ? together(Kind.SIBLINGS, singles, random) : together(Kind.FRIENDS, singles, random);
		}

		return null;
	}

	private static HouseholdPlan family(int doubles, int singles, Random random) {
		if (random.nextFloat() < WIDOWED_CHANCE) {
			return widowed(true, singles, random);
		}

		List<Member> members = new ArrayList<>();
		int yearsHere = 1 + random.nextInt(MAX_YEARS_HERE);
		String reason = pick(FAMILY_REASONS, random);
		boolean headFemale = random.nextBoolean();
		int headAge = adultAge(random, 20, 58);
		int spouseAge = clamp(headAge + random.nextInt(9) - 4, 18, 85);
		members.add(new Member(Role.HEAD, headFemale, headAge, random.nextFloat(), reason, true, 0, 1));
		members.add(new Member(Role.SPOUSE, !headFemale, spouseAge, random.nextFloat(), reason, true, 0, 0));
		int motherAge = headFemale ? headAge : spouseAge;
		int fatherAge = headFemale ? spouseAge : headAge;
		// The oldest a child can be: born once both were grown. The youngest: born before the mother was too old.
		int oldest = Math.min(MAX_CHILD_AGE, Math.min(motherAge, fatherAge) - MIN_PARENT_AGE);
		int youngest = Math.max(0, motherAge - MAX_MOTHER_AGE);
		addChildren(members, singles, youngest, oldest, yearsHere, random);
		for (int extra = 1; extra < doubles; extra++) {
			if (random.nextFloat() < FRIEND_COUPLE_CHANCE) {
				boolean female = random.nextBoolean();
				int index = members.size();
				int age = adultAge(random, 20, 60);
				members.add(new Member(Role.FRIEND, female, age, random.nextFloat(), reason, true, extra, index + 1));
				members.add(new Member(Role.FRIEND, !female, clamp(age + random.nextInt(9) - 4, 18, 85), random.nextFloat(), reason, true, extra, index));
			}
		}

		return new HouseholdPlan(Kind.FAMILY, members, yearsHere, null, false);
	}

	/** A widow or widower, in a double bed or a single, with children if there are beds for them. */
	private static HouseholdPlan widowed(boolean inDouble, int singles, Random random) {
		List<Member> members = new ArrayList<>();
		int yearsHere = 1 + random.nextInt(MAX_YEARS_HERE);
		boolean female = random.nextBoolean();
		int age = adultAge(random, 26, 70);
		members.add(new Member(Role.HEAD, female, age, random.nextFloat(), pick(FAMILY_REASONS, random), inDouble, 0, -1));
		int yearsAgo = 1 + random.nextInt(Math.max(1, Math.min(10, age - MIN_PARENT_AGE - 2)));
		LateSpouse late = new LateSpouse(!female, clamp(age - yearsAgo + random.nextInt(9) - 4, MIN_PARENT_AGE + 1, 85), yearsAgo);
		// The mother's age now, had she lived: what limits how young her children can be.
		int motherAge = female ? age : late.ageAtDeath() + yearsAgo;
		// Children were born while both parents were alive, so they're at least as old as the years since the death.
		int oldest = Math.min(MAX_CHILD_AGE, Math.min(age, late.ageAtDeath() + yearsAgo) - MIN_PARENT_AGE);
		int youngest = Math.max(yearsAgo, Math.max(0, motherAge - MAX_MOTHER_AGE));
		// A single-bed widow has her own bed among the singles, so one fewer for children.
		addChildren(members, inDouble ? singles : singles - 1, youngest, oldest, yearsHere, random);
		if (!inDouble) {
			// Children in single beds after the parent's, so beds stay one each.
			List<Member> renumbered = new ArrayList<>();
			renumbered.add(members.get(0));
			for (int i = 1; i < members.size(); i++) {
				Member child = members.get(i);
				renumbered.add(new Member(child.role(), child.female(), child.age(), child.intoYear(), child.cameFor(), false, child.bed() + 1, -1));
			}

			members = renumbered;
		}

		return new HouseholdPlan(Kind.WIDOWED, members, yearsHere, late, false);
	}

	private static HouseholdPlan lone(Random random) {
		int age = adultAge(random, 18, 70);
		Member member = new Member(Role.HEAD, random.nextBoolean(), age, random.nextFloat(), pick(REASONS, random), false, 0, -1);
		return new HouseholdPlan(Kind.LONE, List.of(member), 1 + random.nextInt(MAX_YEARS_HERE), null, false);
	}

	/** Friends or siblings, two or more, one to a single bed. */
	private static HouseholdPlan together(Kind kind, int singles, Random random) {
		int count = 2 + random.nextInt(singles - 1);
		boolean siblings = kind == Kind.SIBLINGS;
		String shared = siblings || random.nextFloat() < SHARED_REASON_CHANCE ? pick(REASONS, random) : null;
		int eldest = adultAge(random, siblings ? 20 : 18, siblings ? 45 : 60);
		List<Member> members = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			// Siblings are within a few years of the eldest; friends can be any age.
			int age = siblings ? Math.max(18, eldest - random.nextInt(8)) : i == 0 ? eldest : adultAge(random, 18, 60);
			Role role = i == 0 ? Role.HEAD : siblings ? Role.SIBLING : Role.FRIEND;
			members.add(new Member(role, random.nextBoolean(), age, random.nextFloat(), shared != null ? shared : pick(REASONS, random), false, i, -1));
		}

		return new HouseholdPlan(kind, members, 1 + random.nextInt(MAX_YEARS_HERE), null, siblings);
	}

	/** Children in single beds, each bed likely filled, as old as the parents allow. Those younger than the homestead were born here. */
	private static void addChildren(List<Member> members, int beds, int youngest, int oldest, int yearsHere, Random random) {
		if (oldest < youngest) {
			return;
		}

		int bed = 0;
		for (int i = 0; i < beds; i++) {
			if (random.nextFloat() < CHILD_CHANCE) {
				int age = youngest + random.nextInt(oldest - youngest + 1);
				members.add(new Member(Role.CHILD, random.nextBoolean(), age, random.nextFloat(), age < yearsHere ? BORN_HERE : CAME_WITH_FAMILY, false, bed++, -1));
			}
		}
	}

	/** An adult's age, most often toward the younger end of the range. */
	private static int adultAge(Random random, int min, int max) {
		float roll = Math.min(random.nextFloat(), random.nextFloat());
		return min + (int) (roll * (max - min + 1));
	}

	private static String pick(String[] options, Random random) {
		return options[random.nextInt(options.length)];
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
