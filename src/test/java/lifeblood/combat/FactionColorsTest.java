package lifeblood.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.util.helper.DyeColor;
import org.junit.jupiter.api.Test;

class FactionColorsTest {
	@Test
	void colorsAreFoundByName() {
		assertEquals(DyeColor.RED, FactionColors.byName("red"));
		assertEquals(DyeColor.LIGHT_BLUE, FactionColors.byName("lightblue"));
		assertEquals(DyeColor.LIGHT_BLUE, FactionColors.byName("light_blue"));
		assertEquals(DyeColor.SILVER, FactionColors.byName("light_gray"));
		assertEquals(DyeColor.SILVER, FactionColors.byName("silver"));
		assertNull(FactionColors.byName("plaid"));
		for (DyeColor color : DyeColor.values()) {
			assertEquals(color, FactionColors.byName(FactionColors.nameOf(color)));
		}
	}

	@Test
	void newFactionsGetTwoDifferentColorsThatArentPlaceholders() {
		for (String faction : new String[] {"a", "guild", "river_folk", "the_order", "x9"}) {
			FactionColors colors = FactionColors.forNewFaction(faction);
			assertNotEquals(colors.primary(), colors.secondary(), faction);
			for (DyeColor color : new DyeColor[] {colors.primary(), colors.secondary()}) {
				assertNotEquals(DyeColor.GRAY, color, faction);
				assertNotEquals(DyeColor.SILVER, color, faction);
			}

			assertEquals(colors, FactionColors.forNewFaction(faction));
		}
	}
}
