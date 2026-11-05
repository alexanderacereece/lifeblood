package lifeblood.appearance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HairColorTest {
	private static final int[] BROWN = {0x2E1B12, 0x442A1C, 0x593927, 0x6E4A32, 0x855E42};
	private static final int[] GRAY = {0x3C3938, 0x585553, 0x736F6C, 0x8E8A86, 0xA09C98};
	private static final int[] WHITE = {0x5D5B5A, 0x898784, 0xB3B0AD, 0xDDDAD6, 0xE2E0DC};

	@Test
	void encodesAndParsesBack() {
		for (String text : new String[] {"auburn", "auburn~7", "auburn+pink", "auburn~12+muted_blue"}) {
			assertEquals(text, HairColor.parse(text).encode());
		}

		HairColor hair = HairColor.parse("auburn~7+pink");
		assertEquals("auburn", hair.natural());
		assertEquals(7, hair.gray());
		assertEquals("pink", hair.dye());
		assertNull(HairColor.parse("auburn").dye());
		assertEquals(0, HairColor.parse("auburn~junk").gray());
	}

	@Test
	void graysFromTheOnsetThenWhitens() {
		assertEquals(0, HairColor.grayAt(30, 40));
		assertEquals(0, HairColor.grayAt(40, 40));
		assertEquals(5, HairColor.grayAt(50, 40));
		assertEquals(HairColor.STEPS, HairColor.grayAt(60, 40));
		assertEquals(2 * HairColor.STEPS, HairColor.grayAt(90, 40));
		assertEquals(2 * HairColor.STEPS, HairColor.grayAt(120, 40));
	}

	@Test
	void everyoneHasTheirOwnOnsetAlways() {
		UUID person = UUID.randomUUID();
		assertEquals(HairColor.onsetAge(person), HairColor.onsetAge(person));
		for (int i = 0; i < 200; i++) {
			int onset = HairColor.onsetAge(UUID.randomUUID());
			assertTrue(onset >= 25 && onset <= 60, "onset " + onset);
		}
	}

	@Test
	void highlightsGrayFirstAndItEndsGrayThenWhite() {
		assertArrayEquals(BROWN, HairColor.grayed(BROWN, GRAY, WHITE, 0));
		assertArrayEquals(GRAY, HairColor.grayed(BROWN, GRAY, WHITE, HairColor.STEPS));
		assertArrayEquals(WHITE, HairColor.grayed(BROWN, GRAY, WHITE, 2 * HairColor.STEPS));

		int[] halfway = HairColor.grayed(BROWN, GRAY, WHITE, HairColor.STEPS / 2);
		float highlightLeft = distance(halfway[4], GRAY[4]) / (float) distance(BROWN[4], GRAY[4]);
		float shadowLeft = distance(halfway[0], GRAY[0]) / (float) distance(BROWN[0], GRAY[0]);
		assertTrue(highlightLeft < shadowLeft, "the highlight should be further along to gray than the shadow");
	}

	private static int distance(int a, int b) {
		return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}
}
