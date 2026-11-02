package npclaude.appearance;

import java.awt.image.BufferedImage;

/**
 * Turns a 64x64 skin's 4-pixel-wide arms into slim 3-pixel ones, so one wide part (a base body, a shirt) fits slim
 * NPCs too. Minecraft draws slim arms from their own smaller layout rather than squeezing anything, so this
 * rearranges the arm faces into that layout: the faces as wide as the arm (front, back, top, bottom) lose one
 * inner column, keeping both edges (outlines, cuffs, seams), and the sides, which are the arm's depth, stay whole.
 */
public final class SlimArms {
	/** Where each arm box starts in a 64x64 skin: right arm, left arm, right sleeve, left sleeve. */
	private static final int[][] ARMS = {{40, 16}, {32, 48}, {40, 32}, {48, 48}};
	private static final int DEPTH = 4;
	private static final int HEIGHT = 12;
	private static final int WIDE = 4;
	private static final int SLIM = 3;
	/** The column dropped from the front, top and bottom; the back is seen mirrored, so it loses the matching one. */
	private static final int DROPPED = 2;

	private SlimArms() {
	}

	/** A copy of the skin with slim arms. */
	public static BufferedImage squeeze(BufferedImage wide) {
		BufferedImage slim = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		slim.getGraphics().drawImage(wide, 0, 0, null);
		for (int[] arm : ARMS) {
			int u = arm[0];
			int v = arm[1];
			clear(slim, u + DEPTH, v, WIDE * 2, DEPTH);
			clear(slim, u, v + DEPTH, (DEPTH + WIDE) * 2, HEIGHT);
			// Top and bottom.
			copyDropping(wide, u + DEPTH, v, slim, u + DEPTH, v, DEPTH, DROPPED);
			copyDropping(wide, u + DEPTH + WIDE, v, slim, u + DEPTH + SLIM, v, DEPTH, DROPPED);
			// Outer side, front, inner side, back.
			copy(wide, u, v + DEPTH, slim, u, v + DEPTH, DEPTH, HEIGHT);
			copyDropping(wide, u + DEPTH, v + DEPTH, slim, u + DEPTH, v + DEPTH, HEIGHT, DROPPED);
			copy(wide, u + DEPTH + WIDE, v + DEPTH, slim, u + DEPTH + SLIM, v + DEPTH, DEPTH, HEIGHT);
			copyDropping(wide, u + DEPTH * 2 + WIDE, v + DEPTH, slim, u + DEPTH * 2 + SLIM, v + DEPTH, HEIGHT, WIDE - 1 - DROPPED);
		}

		return slim;
	}

	/** Copies a face 4 wide into 3, leaving out one column. */
	private static void copyDropping(BufferedImage from, int fromX, int fromY, BufferedImage to, int toX, int toY, int height, int dropped) {
		int column = 0;
		for (int x = 0; x < WIDE; x++) {
			if (x == dropped) {
				continue;
			}

			copy(from, fromX + x, fromY, to, toX + column, toY, 1, height);
			column++;
		}
	}

	private static void copy(BufferedImage from, int fromX, int fromY, BufferedImage to, int toX, int toY, int width, int height) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				to.setRGB(toX + x, toY + y, from.getRGB(fromX + x, fromY + y));
			}
		}
	}

	private static void clear(BufferedImage image, int x0, int y0, int width, int height) {
		for (int y = y0; y < y0 + height; y++) {
			for (int x = x0; x < x0 + width; x++) {
				image.setRGB(x, y, 0);
			}
		}
	}
}
