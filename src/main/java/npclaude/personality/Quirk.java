package npclaude.personality;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Discrete traits that give an NPC its own topics, habits and opinions of others. Most come in opposing pairs,
 * where most NPCs sit in the unmarked middle; an NPC can never have both sides of a pair.
 * See docs/personality.md for what each one does.
 */
public enum Quirk {
	CHATTERBOX,
	TACITURN,
	HOTHEAD,
	ZEN,
	GULLIBLE,
	PARANOID,
	GREEDY,
	GENEROUS,
	HONEST,
	LIAR,
	NOSY,
	DETACHED,
	PROUD,
	HUMBLE,
	EARLY_BIRD,
	NIGHT_OWL,
	ROMANTIC,
	LOYAL,
	GLUTTON,
	PIOUS,
	CURIOUS,
	JEALOUS,
	SENTIMENTAL,
	COMPETITIVE;

	/** The other side of this quirk's pair, or null for a quirk without one. */
	@Nullable
	private Quirk opposite;

	static {
		pair(CHATTERBOX, TACITURN);
		pair(HOTHEAD, ZEN);
		pair(GULLIBLE, PARANOID);
		pair(GREEDY, GENEROUS);
		pair(HONEST, LIAR);
		pair(NOSY, DETACHED);
		pair(PROUD, HUMBLE);
		pair(EARLY_BIRD, NIGHT_OWL);
	}

	private static void pair(Quirk a, Quirk b) {
		a.opposite = b;
		b.opposite = a;
	}

	@Nullable
	public Quirk getOpposite() {
		return this.opposite;
	}

	/**
	 * Whether these two quirks can't belong to the same NPC. For now that's only the two sides of a pair;
	 * other true impossibilities get added here as playtesting finds them.
	 */
	public boolean conflictsWith(Quirk other) {
		return this.opposite == other;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return this.id().replace('_', ' ');
	}

	/** A few words on what it means, for the profile screen. */
	public String blurb() {
		return switch (this) {
			case CHATTERBOX -> "never stops talking";
			case TACITURN -> "a person of few words";
			case HOTHEAD -> "quick to anger";
			case ZEN -> "hard to rattle";
			case GULLIBLE -> "believes anything";
			case PARANOID -> "trusts no one";
			case GREEDY -> "loves money and things";
			case GENEROUS -> "gives freely";
			case HONEST -> "tells it straight";
			case LIAR -> "bends the truth";
			case NOSY -> "into everyone's business";
			case DETACHED -> "keeps to themselves";
			case PROUD -> "takes slights to heart";
			case HUMBLE -> "modest to a fault";
			case EARLY_BIRD -> "up with the sun";
			case NIGHT_OWL -> "comes alive at night";
			case ROMANTIC -> "looking for love";
			case LOYAL -> "sticks by their people";
			case GLUTTON -> "lives to eat";
			case PIOUS -> "devoted to the Cube God";
			case CURIOUS -> "wants to know everything";
			case JEALOUS -> "minds who you spend time with";
			case SENTIMENTAL -> "holds on to memories";
			case COMPETITIVE -> "has to win";
		};
	}

	@Nullable
	public static Quirk byName(String name) {
		for (Quirk quirk : values()) {
			if (quirk.id().equalsIgnoreCase(name)) {
				return quirk;
			}
		}

		return null;
	}
}
