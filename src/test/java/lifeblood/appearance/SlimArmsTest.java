package lifeblood.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import lifeblood.skin.NpcSkinFiles;
import org.junit.jupiter.api.Test;

class SlimArmsTest {
	/** A skin whose right arm is painted with a different color per pixel column, so moved columns can be traced. */
	private static BufferedImage wideArm() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int x = 40; x < 56; x++) {
			for (int y = 16; y < 32; y++) {
				boolean face = y >= 20 || x >= 44 && x < 52;
				if (face) {
					image.setRGB(x, y, 0xFF000000 | x);
				}
			}
		}

		// The body, which must not move.
		image.setRGB(20, 25, 0xFFABCDEF);
		return image;
	}

	@Test
	void armsBecomeThreeWideInTheSlimLayout() {
		BufferedImage wide = wideArm();
		assertFalse(NpcSkinFiles.isSlim(wide));
		BufferedImage slim = SlimArms.squeeze(wide);
		assertTrue(NpcSkinFiles.isSlim(slim), "the game should now see slim arms");

		// Outer side: unchanged.
		for (int x = 40; x < 44; x++) {
			assertEquals(0xFF000000 | x, slim.getRGB(x, 25));
		}

		// Front: columns 44, 45 and 47 (the inner column 46 is dropped).
		assertEquals(0xFF000000 | 44, slim.getRGB(44, 25));
		assertEquals(0xFF000000 | 45, slim.getRGB(45, 25));
		assertEquals(0xFF000000 | 47, slim.getRGB(46, 25));
		// Inner side: the whole 4 columns, moved one left.
		for (int i = 0; i < 4; i++) {
			assertEquals(0xFF000000 | (48 + i), slim.getRGB(47 + i, 25));
		}

		// Back: mirrored, so it drops column 53 and keeps 52, 54 and 55.
		assertEquals(0xFF000000 | 52, slim.getRGB(51, 25));
		assertEquals(0xFF000000 | 54, slim.getRGB(52, 25));
		assertEquals(0xFF000000 | 55, slim.getRGB(53, 25));
		// Top and bottom.
		assertEquals(0xFF000000 | 47, slim.getRGB(46, 17));
		assertEquals(0xFF000000 | 48, slim.getRGB(47, 17));
		assertEquals(0xFF000000 | 51, slim.getRGB(49, 17));
		assertEquals(0, slim.getRGB(50, 17) >>> 24);

		assertEquals(0xFFABCDEF, slim.getRGB(20, 25));
	}
}
