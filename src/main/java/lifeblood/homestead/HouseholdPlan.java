package lifeblood.homestead;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Who lives in a homestead, decided before anyone is made: each member's place in the household, gender, age and bed,
 * how long they've been there, and anyone missing from it (a late husband or wife, parents back home).
 *
 * @param kind      what sort of household it is
 * @param members   the people living there, the head first
 * @param yearsHere how many years since they settled; children younger than that were born here
 * @param lateSpouse the head's husband or wife who died, for a widowed household; null otherwise
 * @param parentsBackHome whether the members are siblings whose parents still live back home
 */
public record HouseholdPlan(Kind kind, List<Member> members, int yearsHere, @Nullable LateSpouse lateSpouse, boolean parentsBackHome) {
	/** What sort of household it is. */
	public enum Kind {
		/** A married couple, often with children, maybe with friends sharing a second double bed. */
		FAMILY,
		/** A widow or widower, sometimes with children. */
		WIDOWED,
		/** Someone on their own. */
		LONE,
		/** Friends or like-minded people who came out together. */
		FRIENDS,
		/** Brothers and sisters who came out together. */
		SIBLINGS
	}

	/** Someone's place in the household. */
	public enum Role {
		HEAD, SPOUSE, CHILD, FRIEND, SIBLING
	}

	/**
	 * One person: their role, gender, age (whole years plus how far into the next, 0 to 1), why they came to live here
	 * (see NpcLife.getCameFor) and their bed: a double bed's index, or a single bed's.
	 *
	 * @param partnerOf the index in the member list of the one they're married to, or -1
	 */
	public record Member(Role role, boolean female, int age, float intoYear, String cameFor, boolean inDouble, int bed, int partnerOf) {
	}

	/** A husband or wife who died: their gender, how old they were, and how many years ago. */
	public record LateSpouse(boolean female, int ageAtDeath, int yearsAgo) {
	}
}
