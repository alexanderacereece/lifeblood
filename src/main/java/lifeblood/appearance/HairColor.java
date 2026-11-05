package lifeblood.appearance;

import java.util.Random;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Someone's hair color: the natural color they were born with, how far it has grayed with age, and a dye, if they
 * dye it. Saved in the hair layer's palette name: "auburn" for natural, "auburn~7" grayed 7 steps, "auburn+pink" dyed
 * pink. Children inherit the natural color, never the gray or the dye.
 *
 * Graying: everyone starts going gray at their own age (most between 30 and 50), turns fully gray over the next 20
 * years, then white over the 25 after that. Highlights go first, the way hair silvers at the temples, so a graying
 * head reads as salt and pepper rather than faded. Dyed hair keeps its dye; a beard is never dyed.
 */
public record HairColor(String natural, int gray, @Nullable String dye) {
	/** Steps from natural to fully gray, and as many again from gray to white. */
	public static final int STEPS = 10;
	/** The palettes a graying head blends toward, in the hair group. */
	private static final String GRAY_PALETTE = "gray";
	private static final String WHITE_PALETTE = "white";
	/** Used if those palettes are missing. */
	private static final int[] GRAY_FALLBACK = {0x3C3938, 0x585553, 0x736F6C, 0x8E8A86, 0xA09C98};
	private static final int[] WHITE_FALLBACK = {0x5D5B5A, 0x898784, 0xB3B0AD, 0xDDDAD6, 0xE2E0DC};
	/** When graying starts: a bell curve around this age, in years, kept within the limits below. */
	private static final float TYPICAL_ONSET = 40.0F;
	private static final float ONSET_SPREAD = 7.0F;
	private static final int EARLIEST_ONSET = 25;
	private static final int LATEST_ONSET = 60;
	/** Years from the first gray hairs to fully gray, then from gray to white. */
	private static final float YEARS_TO_GRAY = 20.0F;
	private static final float YEARS_TO_WHITE = 25.0F;
	/** How much faster the lightest shade grays than the darkest: 0.5 is half again as fast, so highlights go first. */
	private static final float HIGHLIGHTS_FIRST = 0.5F;

	public HairColor {
		gray = Math.max(0, Math.min(2 * STEPS, gray));
		dye = dye == null || dye.isEmpty() ? null : dye;
	}

	public static HairColor natural(String palette) {
		return new HairColor(palette, 0, null);
	}

	/** Reads a hair palette name: "auburn", "auburn~7", "auburn+pink" or "auburn~7+pink". */
	public static HairColor parse(String text) {
		String dye = null;
		int plus = text.indexOf('+');
		if (plus >= 0) {
			dye = text.substring(plus + 1);
			text = text.substring(0, plus);
		}

		int gray = 0;
		int tilde = text.indexOf('~');
		if (tilde >= 0) {
			try {
				gray = Integer.parseInt(text.substring(tilde + 1));
			} catch (NumberFormatException e) {
				gray = 0;
			}

			text = text.substring(0, tilde);
		}

		return new HairColor(text, gray, dye);
	}

	public String encode() {
		return this.natural + (this.gray > 0 ? "~" + this.gray : "") + (this.dye != null ? "+" + this.dye : "");
	}

	public HairColor withGray(int gray) {
		return new HairColor(this.natural, gray, this.dye);
	}

	public HairColor withDye(@Nullable String dye) {
		return new HairColor(this.natural, this.gray, dye);
	}

	/** The five colors it's drawn with: the dye's, or the natural color grayed as far as it has. Null if unknown. */
	@Nullable
	public int[] colors(PartCatalog catalog) {
		int[] dyed = this.dye != null ? catalog.palette("dye", this.dye) : null;
		if (dyed != null) {
			return dyed;
		}

		int[] natural = catalog.palette("hair", this.natural);
		if (natural == null || this.gray == 0) {
			return natural;
		}

		int[] gray = orElse(catalog.palette("hair", GRAY_PALETTE), GRAY_FALLBACK);
		int[] white = orElse(catalog.palette("hair", WHITE_PALETTE), WHITE_FALLBACK);
		return grayed(natural, gray, white, this.gray);
	}

	/** A natural palette grayed this many steps (up to STEPS gray, up to twice that white), highlights first. */
	static int[] grayed(int[] natural, int[] gray, int[] white, int steps) {
		float toGray = Math.min(steps, STEPS) / (float) STEPS;
		float toWhite = Math.max(0, steps - STEPS) / (float) STEPS;
		int[] result = new int[natural.length];
		for (int shade = 0; shade < natural.length; shade++) {
			// Lighter shades (later ones) gray faster, so the highlights are fully gray while the shadows still aren't.
			float headStart = HIGHLIGHTS_FIRST * shade / (natural.length - 1);
			float amount = Math.min(1.0F, toGray * (1.0F + headStart));
			int color = blend(natural[shade], gray[shade], amount);
			result[shade] = blend(color, white[shade], toWhite);
		}

		return result;
	}

	/** How many steps gray someone is at this age, given the age they started graying. */
	public static int grayAt(int age, int onset) {
		float years = age - onset;
		if (years <= 0.0F) {
			return 0;
		}

		float toGray = Math.min(1.0F, years / YEARS_TO_GRAY);
		float toWhite = Math.max(0.0F, Math.min(1.0F, (years - YEARS_TO_GRAY) / YEARS_TO_WHITE));
		return Math.round((toGray + toWhite) * STEPS);
	}

	/** The age someone starts going gray, the same every time for the same person. */
	public static int onsetAge(UUID person) {
		Random random = new Random(person.getMostSignificantBits() ^ person.getLeastSignificantBits());
		float age = TYPICAL_ONSET + (float) random.nextGaussian() * ONSET_SPREAD;
		return Math.max(EARLIEST_ONSET, Math.min(LATEST_ONSET, Math.round(age)));
	}

	private static int[] orElse(@Nullable int[] palette, int[] fallback) {
		return palette != null ? palette : fallback;
	}

	private static int blend(int from, int to, float amount) {
		int r = Math.round(((from >> 16) & 0xFF) * (1.0F - amount) + ((to >> 16) & 0xFF) * amount);
		int g = Math.round(((from >> 8) & 0xFF) * (1.0F - amount) + ((to >> 8) & 0xFF) * amount);
		int b = Math.round((from & 0xFF) * (1.0F - amount) + (to & 0xFF) * amount);
		return r << 16 | g << 8 | b;
	}
}
