package lifeblood.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import lifeblood.entity.NpcGender;
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
	void outfitsCoverTopAndBottomOnly() {
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			assertEquals(layer == AppearanceLayer.TOP || layer == AppearanceLayer.BOTTOM, layer.coveredByOutfit(), layer.id());
		}

		// Drawn after the clothes it replaces, before hair.
		assertTrue(AppearanceLayer.OUTFIT.ordinal() > AppearanceLayer.TOP.ordinal());
		assertTrue(AppearanceLayer.OUTFIT.ordinal() < AppearanceLayer.HAIR.ordinal());
		assertEquals("dye", AppearanceLayer.OUTFIT.paletteGroup);
	}

	@Test
	void removingALayer() {
		Appearance look = Appearance.NONE.with(AppearanceLayer.HAIR, "short", "black").with(AppearanceLayer.HAIR, null, "");
		assertNull(look.get(AppearanceLayer.HAIR));
	}

	private static PartCatalog.Part part(String name, String... tags) {
		return new PartCatalog.Part(name, Set.of(tags), null, null);
	}

	@Test
	void foldersTagPartsByGenderClimateAndJob() {
		PartCatalog.Part coat = part("coat", "female", "cold");
		assertEquals(NpcGender.FEMALE, coat.gender());
		assertEquals("cold", coat.climate());
		assertNull(coat.job());
		assertTrue(coat.fits(new Wearer(NpcGender.FEMALE, null, "cold")));
		assertFalse(coat.fits(new Wearer(NpcGender.MALE, null, "cold")));
		assertFalse(coat.fits(new Wearer(NpcGender.FEMALE, null, "hot")));
		assertFalse(coat.fits(Wearer.of(NpcGender.FEMALE)));

		PartCatalog.Part uniform = part("uniform", "guard");
		assertEquals("guard", uniform.job());
		assertTrue(uniform.fits(new Wearer(NpcGender.MALE, "guard", "mild")));
		assertFalse(uniform.fits(new Wearer(NpcGender.MALE, null, "mild")));
		assertTrue(part("shirt").fits(new Wearer(NpcGender.MALE, "guard", "hot")));
	}

	@Test
	void aJobsOwnClothesComeFirstThenTheClimates() {
		Random random = new Random(3);
		List<PartCatalog.Part> guard = List.of(part("shirt"), part("coat", "cold"), part("uniform", "guard"));
		List<PartCatalog.Part> cold = List.of(part("shirt"), part("coat", "cold"));
		int coats = 0;
		for (int i = 0; i < 1000; i++) {
			assertEquals("uniform", Appearance.pickPart(guard, random));
			coats += Appearance.pickPart(cold, random).equals("coat") ? 1 : 0;
		}

		assertTrue(coats > 600 && coats < 800, "coats " + coats);
	}

	@Test
	void childrenAreBornWithTheNaturalColor() {
		Appearance look = Appearance.NONE.with(AppearanceLayer.HAIR, "short", "black~12+pink");
		assertEquals("black", look.hairColor().natural());
		assertEquals("pink", look.hairColor().dye());
		assertNull(Appearance.NONE.hairColor());
	}
}
