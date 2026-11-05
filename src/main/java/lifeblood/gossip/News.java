package lifeblood.gossip;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One piece of news: what kind, who it's about (and who else), when it happened, and the names to tell it with,
 * kept so it can still be told once they're gone. A birth also names the baby.
 */
public record News(int id, NewsKind kind, UUID subject, String subjectName, @Nullable UUID other, String otherName, String babyName,
	long when) {
	/** Whether this person is one of the people it's about. */
	public boolean involves(UUID person) {
		return person.equals(this.subject) || person.equals(this.other);
	}
}
