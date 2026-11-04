package lifeblood.speech;

import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import lifeblood.personality.TraitTier;
import org.jetbrains.annotations.Nullable;

/**
 * One requirement on a line in a speech file, written in the braces at its end:
 * a pole ("polite": leaning that way or more, "polite+": strongly), a quirk ("glutton"), or a fact ("time=night").
 * A leading "!" negates any of them.
 */
public sealed interface Condition {
	boolean test(SpeechContext context);

	/** Whether this narrows who says the line (so it counts toward how specific the line is). */
	boolean isSpecific();

	record Pole(TraitAxis axis, boolean positive, boolean strongOnly, boolean negated) implements Condition {
		@Override
		public boolean test(SpeechContext context) {
			float value = context.axis(this.axis);
			TraitTier tier = TraitTier.of(value);
			boolean matches = value > 0.0F == this.positive && (this.strongOnly ? tier == TraitTier.STRONG : tier != TraitTier.NEUTRAL);
			return matches != this.negated;
		}

		@Override
		public boolean isSpecific() {
			return !this.negated;
		}
	}

	record HasQuirk(Quirk quirk, boolean negated) implements Condition {
		@Override
		public boolean test(SpeechContext context) {
			return context.speaker().personality.has(this.quirk) != this.negated;
		}

		@Override
		public boolean isSpecific() {
			return !this.negated;
		}
	}

	record Fact(String key, String value, boolean negated) implements Condition {
		@Override
		public boolean test(SpeechContext context) {
			return this.value.equals(context.fact(this.key)) != this.negated;
		}

		@Override
		public boolean isSpecific() {
			return !this.negated;
		}
	}

	/** Parses one condition, or returns null if it isn't one (the caller reports it). */
	@Nullable
	static Condition parse(String token) {
		boolean negated = token.startsWith("!");
		String body = negated ? token.substring(1) : token;
		int equals = body.indexOf('=');
		if (equals > 0) {
			return new Fact(body.substring(0, equals), body.substring(equals + 1), negated);
		}

		boolean strong = body.endsWith("+");
		String name = strong ? body.substring(0, body.length() - 1) : body;
		for (TraitAxis axis : TraitAxis.values()) {
			if (axis.negative.equals(name) || axis.positive.equals(name)) {
				return new Pole(axis, axis.positive.equals(name), strong, negated);
			}
		}

		Quirk quirk = Quirk.byName(name);
		return quirk != null && !strong ? new HasQuirk(quirk, negated) : null;
	}
}
