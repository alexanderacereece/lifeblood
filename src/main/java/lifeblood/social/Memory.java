package lifeblood.social;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One thing an NPC remembers: what happened, who did it (if anyone), when, and how strongly it was felt.
 * Times are world ticks, so memories keep fading while the NPC's chunk is unloaded.
 */
public record Memory(MemoryType type, @Nullable UUID subject, String subjectName, long created, long expires, float scale) {
	public static final long TICKS_PER_DAY = 24000L;

	/**
	 * How much of this memory is still felt, 0 to 1. Full strength for the first three quarters of its life,
	 * then fading out linearly.
	 */
	public float strength(long now) {
		if (now >= this.expires) {
			return 0.0F;
		}

		long life = Math.max(1L, this.expires - this.created);
		long left = this.expires - now;
		return Math.min(1.0F, left / (life * 0.25F));
	}

	public boolean isAbout(UUID person) {
		return person.equals(this.subject);
	}
}
