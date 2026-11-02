package npclaude.family;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import npclaude.entity.NpcGender;
import npclaude.people.PeopleRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * What one person is to another, worked out from parents and partnerships: "Tom is Ann's brother". Also how much
 * the tie warms an opinion, and whether it's blood (no romance between blood relatives).
 */
public enum Kinship {
	SPOUSE("husband", "wife", "spouse", 30, false),
	FIANCE("fiancé", "fiancée", "fiancé", 25, false),
	PARTNER("boyfriend", "girlfriend", "partner", 20, false),
	LATE_SPOUSE("late husband", "late wife", "late spouse", 0, false),
	PARENT("father", "mother", "parent", 20, true),
	CHILD("son", "daughter", "child", 20, true),
	SIBLING("brother", "sister", "sibling", 15, true),
	GRANDPARENT("grandfather", "grandmother", "grandparent", 12, true),
	GRANDCHILD("grandson", "granddaughter", "grandchild", 12, true),
	AUNT_UNCLE("uncle", "aunt", "aunt or uncle", 8, true),
	NIECE_NEPHEW("nephew", "niece", "niece or nephew", 8, true),
	COUSIN("cousin", "cousin", "cousin", 6, true),
	PARENT_IN_LAW("father-in-law", "mother-in-law", "parent-in-law", 5, false),
	CHILD_IN_LAW("son-in-law", "daughter-in-law", "child-in-law", 5, false),
	SIBLING_IN_LAW("brother-in-law", "sister-in-law", "sibling-in-law", 5, false);

	private final String male;
	private final String female;
	private final String neutral;
	/** How much this tie adds to an opinion, as a standing factor. */
	public final int opinion;
	public final boolean blood;

	Kinship(String male, String female, String neutral, int opinion, boolean blood) {
		this.male = male;
		this.female = female;
		this.neutral = neutral;
		this.opinion = opinion;
		this.blood = blood;
	}

	/** The word for someone in this role, by their gender: "sister". */
	public String label(@Nullable NpcGender gender) {
		return gender == NpcGender.MALE ? this.male : gender == NpcGender.FEMALE ? this.female : this.neutral;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** What other is to self, or null if they aren't related. */
	@Nullable
	public static Kinship of(PeopleRegistry family, UUID self, UUID other) {
		if (self.equals(other)) {
			return null;
		}

		PeopleRegistry.Partnership partnership = family.partnershipOf(self);
		if (partnership != null && partnership.other(self).equals(other)) {
			return switch (partnership.stage) {
				case MARRIED -> SPOUSE;
				case ENGAGED -> FIANCE;
				case DATING -> PARTNER;
			};
		}

		PeopleRegistry.Person person = family.find(self);
		if (person != null && other.equals(person.lateSpouse)) {
			return LATE_SPOUSE;
		}

		if (family.parentsOf(self).contains(other)) {
			return PARENT;
		}

		if (family.parentsOf(other).contains(self)) {
			return CHILD;
		}

		if (family.siblingsOf(self).contains(other)) {
			return SIBLING;
		}

		for (UUID parent : family.parentsOf(self)) {
			if (family.parentsOf(parent).contains(other)) {
				return GRANDPARENT;
			}

			if (family.siblingsOf(parent).contains(other)) {
				return AUNT_UNCLE;
			}

			for (UUID auntOrUncle : family.siblingsOf(parent)) {
				if (family.parentsOf(other).contains(auntOrUncle)) {
					return COUSIN;
				}
			}
		}

		for (UUID parent : family.parentsOf(other)) {
			if (family.parentsOf(parent).contains(self)) {
				return GRANDCHILD;
			}

			if (family.siblingsOf(parent).contains(self)) {
				return NIECE_NEPHEW;
			}
		}

		UUID spouse = family.spouseOf(self);
		if (spouse != null) {
			if (family.parentsOf(spouse).contains(other)) {
				return PARENT_IN_LAW;
			}

			if (family.siblingsOf(spouse).contains(other)) {
				return SIBLING_IN_LAW;
			}
		}

		UUID otherSpouse = family.spouseOf(other);
		if (otherSpouse != null) {
			if (family.parentsOf(otherSpouse).contains(self)) {
				return CHILD_IN_LAW;
			}

			if (family.siblingsOf(self).contains(otherSpouse)) {
				return SIBLING_IN_LAW;
			}
		}

		return null;
	}

	/** Everyone related to this person by blood or marriage, alive or not (for listing a family). */
	public static Set<UUID> relativesOf(PeopleRegistry family, UUID self) {
		Set<UUID> relatives = new HashSet<>();
		for (PeopleRegistry.Person person : family.everyone()) {
			if (of(family, self, person.id) != null) {
				relatives.add(person.id);
			}
		}

		return relatives;
	}
}
