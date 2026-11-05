package lifeblood.family;

import static org.junit.jupiter.api.Assertions.assertEquals;

import lifeblood.entity.NpcGender;
import org.junit.jupiter.api.Test;

class FamilyNamesTest {
	@Test
	void aChildTakesItsFathersName() {
		assertEquals("Thorne", FamilyNames.childName("Webb", NpcGender.FEMALE, "Thorne", NpcGender.MALE));
		assertEquals("Thorne", FamilyNames.childName("Thorne", NpcGender.MALE, "Webb", NpcGender.FEMALE));
	}

	@Test
	void withoutAnNpcFatherItTakesTheParentItWasBornTo() {
		// A player parent has no family name.
		assertEquals("Webb", FamilyNames.childName("Webb", NpcGender.FEMALE, null, null));
		assertEquals("Thorne", FamilyNames.childName("Thorne", NpcGender.MALE, null, null));
		// Two mothers, or two fathers.
		assertEquals("Webb", FamilyNames.childName("Webb", NpcGender.FEMALE, "Hale", NpcGender.FEMALE));
		assertEquals("Thorne", FamilyNames.childName("Thorne", NpcGender.MALE, "Hale", NpcGender.MALE));
	}
}
