package npclaude.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppearanceTest {
	@Test
	void encodesInDrawingOrderAndDecodesBack() {
		Appearance look = Appearance.NONE
			.with(AppearanceLayer.HAIR, "bob", "auburn")
			.with(AppearanceLayer.BASE, "human", "tan")
			.with(AppearanceLayer.ACCESSORY, "chain", "");
		String encoded = look.encode();
		assertEquals("base=human@tan;hair=bob@auburn;accessory=chain", encoded);
		Appearance decoded = Appearance.decode(encoded);
		assertEquals(new Appearance.Choice("bob", "auburn"), decoded.get(AppearanceLayer.HAIR));
		assertEquals(new Appearance.Choice("chain", ""), decoded.get(AppearanceLayer.ACCESSORY));
		assertEquals(encoded, decoded.encode());
	}

	@Test
	void decodingSkipsJunk() {
		Appearance decoded = Appearance.decode("base=human@fair;nonsense;wings=big@red;=x");
		assertEquals(1, decoded.choices().size());
		assertTrue(Appearance.decode("").isEmpty());
	}

	@Test
	void hairColorReachesTheBeard() {
		Appearance look = Appearance.NONE
			.with(AppearanceLayer.HAIR, "short", "black")
			.with(AppearanceLayer.FACIAL_HAIR, "goatee", "black")
			.withHairColor("ginger");
		assertEquals("ginger", look.get(AppearanceLayer.HAIR).palette());
		assertEquals("ginger", look.get(AppearanceLayer.FACIAL_HAIR).palette());
	}

	@Test
	void removingALayer() {
		Appearance look = Appearance.NONE.with(AppearanceLayer.HAIR, "short", "black").with(AppearanceLayer.HAIR, null, "");
		assertNull(look.get(AppearanceLayer.HAIR));
	}
}
