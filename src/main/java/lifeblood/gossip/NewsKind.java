package lifeblood.gossip;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Kinds of news people pass on. Each has an importance (bigger news is told first), whether it's still talked about
 * once it's no longer fresh ("remember when..."), and whether it reflects badly on the person it's about, which
 * colors how people take hearing it and keeps friends from spreading it.
 */
public enum NewsKind {
	/** Subject and other started dating. */
	DATING(2, false, false),
	/** Subject and other got engaged. */
	ENGAGEMENT(3, false, false),
	/** Subject and other got married. */
	WEDDING(4, true, false),
	/** Subject and other split up. */
	BREAKUP(3, false, false),
	/** Subject and other had a baby, named in the news. */
	BIRTH(4, true, false),
	/** Subject died, with nobody to blame. */
	DEATH(5, true, false),
	/** Subject killed other. */
	KILLING(6, true, true),
	/** Subject attacked other out of nowhere. */
	ATTACK(3, false, true),
	/** Subject beat other in a brawl. */
	BRAWL(2, false, false);

	/** Bigger news is passed on first. */
	public final int importance;
	/** Still talked about once it's old news. */
	public final boolean remembered;
	/** Reflects badly on the subject. */
	public final boolean shameful;

	NewsKind(int importance, boolean remembered, boolean shameful) {
		this.importance = importance;
		this.remembered = remembered;
		this.shameful = shameful;
	}

	/** Its name in speech facts: "wedding". */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static NewsKind byName(String name) {
		for (NewsKind kind : values()) {
			if (kind.id().equalsIgnoreCase(name)) {
				return kind;
			}
		}

		return null;
	}
}
