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
