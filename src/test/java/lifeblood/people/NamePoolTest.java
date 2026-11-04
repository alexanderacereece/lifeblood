package lifeblood.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lifeblood.entity.NpcGender;
import org.junit.jupiter.api.Test;

class NamePoolTest {
	@Test
	void readsSectionsAndSkipsComments() throws IOException {
		Map<NpcGender, List<String>> names = NamePool.parse("# a comment\n[male]\nAldric\n\nArlo\n[female]\n  Bea  \n# another\n");
		assertEquals(List.of("Aldric", "Arlo"), names.get(NpcGender.MALE));
		assertEquals(List.of("Bea"), names.get(NpcGender.FEMALE));
	}

	@Test
	void theBundledPoolHasNamesForBoth() {
		Random random = new Random(1);
		for (NpcGender gender : NpcGender.values()) {
			String name = NamePool.pick(gender, random);
			assertFalse(name.isBlank());
			assertTrue(name.length() <= 32, name);
		}
	}
}
