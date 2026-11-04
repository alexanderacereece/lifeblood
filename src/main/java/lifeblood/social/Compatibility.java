package lifeblood.social;

import java.util.UUID;
import lifeblood.personality.NpcPersonality;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import lifeblood.personality.TraitTier;
import org.jetbrains.annotations.Nullable;

/**
 * How one NPC's traits make it regard someone else's, before anything has happened between them.
 * One-directional: a hardworking NPC resents a lazy one, while the lazy one doesn't care.
 * Capped so personality only sets a starting temperature; history decides the rest. See docs/personality.md.
 */
public final class Compatibility {
	public static final float LIMIT = 15.0F;

	private Compatibility() {
	}

	/**
	 * @param other the other person's personality, or null for a player (who has none on record)
	 * @param otherId the other person, to check whether they've been caught lying
	 */
	public static float of(NpcPersonality self, NpcMemories memories, @Nullable NpcPersonality other, @Nullable UUID otherId) {
		float total = 0.0F;
		if (self.has(Quirk.PARANOID)) {
			total -= 3.0F;
		}

		if (self.has(Quirk.GULLIBLE)) {
			total += 3.0F;
		}

		if (other == null) {
			return total;
		}

		boolean knownLiar = other.has(Quirk.LIAR) && otherId != null && memories.remembers(MemoryType.LIED_TO_ME, otherId);

		// Manners
		if (isStrong(other, TraitAxis.MANNERS, true)) {
			total += 5.0F;
		}

		if (isStrong(other, TraitAxis.MANNERS, false)) {
			total -= leans(self, TraitAxis.MANNERS, false) ? 2.0F : 5.0F;
		}

		if (leans(self, TraitAxis.MANNERS, false) && (leans(other, TraitAxis.MANNERS, true) || other.has(Quirk.GENEROUS))) {
			total -= 4.0F;
		}

		// Work ethic: the hardworking resent the lazy, scaled by both.
		float work = self.get(TraitAxis.WORK_ETHIC);
		float otherWork = other.get(TraitAxis.WORK_ETHIC);
		if (work > 0.0F && otherWork < 0.0F) {
			total -= 10.0F * work * -otherWork;
		}

		// Only the extremes of speech and outlook grate, both ways.
		if (isStrong(self, TraitAxis.SPEECH, true) && isStrong(other, TraitAxis.SPEECH, false)
			|| isStrong(self, TraitAxis.SPEECH, false) && isStrong(other, TraitAxis.SPEECH, true)) {
			total -= 5.0F;
		}

		if (isStrong(self, TraitAxis.OUTLOOK, true) && isStrong(other, TraitAxis.OUTLOOK, false)
			|| isStrong(self, TraitAxis.OUTLOOK, false) && isStrong(other, TraitAxis.OUTLOOK, true)) {
			total -= 3.0F;
		}

		// Kindred spirits
		total += similarity(self, other, TraitAxis.SOCIABILITY) * 4.0F;
		total += similarity(self, other, TraitAxis.HUMOR) * 4.0F;

		// Quirks
		if (self.has(Quirk.PIOUS) && (isStrong(other, TraitAxis.SPEECH, false) || knownLiar)) {
			total -= 6.0F;
		}

		if (self.has(Quirk.GREEDY) && other.has(Quirk.GENEROUS)) {
			total -= 4.0F;
		}

		if (self.has(Quirk.GENEROUS)) {
			total += 3.0F;
		}

		if (self.has(Quirk.NOSY) && (other.has(Quirk.CHATTERBOX) || other.has(Quirk.NOSY))) {
			total += 4.0F;
		}

		if ((self.has(Quirk.TACITURN) || self.has(Quirk.DETACHED) || self.has(Quirk.PARANOID)) && other.has(Quirk.NOSY)) {
			total -= 4.0F;
		}

		if (self.has(Quirk.TACITURN) && other.has(Quirk.CHATTERBOX)) {
			total -= 3.0F;
		}

		if (self.has(Quirk.HOTHEAD) && other.has(Quirk.HOTHEAD)) {
			total -= 6.0F;
		}

		if (self.has(Quirk.HOTHEAD) && other.has(Quirk.ZEN)) {
			total += 4.0F;
		}

		if (knownLiar && self.has(Quirk.PARANOID)) {
			total -= 6.0F;
		}

		if (knownLiar && self.has(Quirk.HONEST)) {
			total -= 8.0F;
		}

		if (other.has(Quirk.HUMBLE)) {
			total += 2.0F;
		}

		if (self.has(Quirk.PROUD) && other.has(Quirk.PROUD)) {
			total -= 4.0F;
		}

		if (self.has(Quirk.COMPETITIVE) && other.has(Quirk.COMPETITIVE)) {
			total -= 3.0F;
		}

		if (self.has(Quirk.CURIOUS) && other.has(Quirk.CHATTERBOX)) {
			total += 3.0F;
		}

		return Math.max(-LIMIT, Math.min(LIMIT, total));
	}

	/** 1 when two values match, falling to 0 once they're a full point apart. */
	private static float similarity(NpcPersonality a, NpcPersonality b, TraitAxis axis) {
		return Math.max(0.0F, 1.0F - Math.abs(a.get(axis) - b.get(axis)));
	}

	private static boolean leans(NpcPersonality personality, TraitAxis axis, boolean positive) {
		float value = personality.get(axis);
		return TraitTier.of(value) != TraitTier.NEUTRAL && value > 0.0F == positive;
	}

	private static boolean isStrong(NpcPersonality personality, TraitAxis axis, boolean positive) {
		float value = personality.get(axis);
		return TraitTier.of(value) == TraitTier.STRONG && value > 0.0F == positive;
	}
}
