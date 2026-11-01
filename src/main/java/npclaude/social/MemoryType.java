package npclaude.social;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Kinds of things an NPC remembers. Each has an effect on its opinion of the person involved, an effect on its mood,
 * how long it lasts (in-game days), and how many times it stacks (each repeat counts half as much as the last).
 * See docs/personality.md, section 4.
 */
public enum MemoryType {
	// About a person
	SAVED_MY_LIFE("saved my life", 30, 10, 60, 3),
	FOUGHT_BESIDE_ME("fought beside me", 8, 0, 10, 5),
	GAVE_ME_A_GIFT("gave me a gift", 5, 3, 5, 5),
	/** Scaled by how much the NPC likes the food. */
	GAVE_ME_FOOD_I_LIKE("gave me food I like", 10, 5, 8, 3),
	GAVE_ME_FOOD_I_HATE("gave me food I hate", -3, -2, 3, 3),
	HAD_A_NICE_CHAT("had a nice chat", 3, 2, 2, 5),
	TAUGHT_ME_A_WORD("taught me a word", 2, 0, 3, 5),
	INSULTED_ME("insulted me", -8, -3, 4, 5, true),
	HIT_ME("hit me", -12, -5, 3, 5),
	BEAT_ME_IN_A_BRAWL("beat me in a brawl", -5, -5, 10, 3, true),
	FLED_AND_LEFT_ME("fled and left me", -15, 0, 15, 3),
	LIED_TO_ME("lied to me", -20, 0, 20, 3),
	STOLE_FROM_ME("stole from me", -25, -5, 30, 3),
	ATTACKED_MY_HOME("attacked my home", -30, -10, 40, 3),
	KILLED_A_FRIEND("killed a friend", -40, -15, 80, 3),
	KILLED_FAMILY("killed family", -70, -30, 200, 3),
	KILLED_MY_SPOUSE_OR_CHILD("killed my spouse or child", -100, -40, 400, 3),

	// Mood only
	/** Scaled by how much the NPC likes the food. */
	ATE_FOOD_I_LIKE("ate food I like", 0, 6, 0.25F, 3),
	ATE_FOOD_I_HATE("ate food I hate", 0, -4, 0.25F, 3),
	SLEPT_IN_A_BED("slept in a bed", 0, 4, 1, 1),
	SLEPT_OUTSIDE("slept outside", 0, -4, 1, 1),
	GOT_RAINED_ON("got rained on", 0, -3, 0.25F, 3),
	SAW_A_DEATH("saw a death", 0, -6, 2, 3),
	A_FRIEND_DIED("a friend died", 0, -20, 10, 3),
	FAMILY_DIED("family died", 0, -30, 20, 3),
	GOT_MARRIED("got married", 0, 30, 10, 1),
	WON_A_FIGHT("won a fight", 0, 4, 1, 3),
	LOST_A_FIGHT("lost a fight", 0, -5, 1, 3),
	NEARLY_DIED("nearly died", 0, -15, 2, 2);

	public final String description;
	public final int opinion;
	public final int mood;
	public final float days;
	public final int stackLimit;
	/** Hurts pride: proud NPCs feel it twice as much. */
	public final boolean pride;

	MemoryType(String description, int opinion, int mood, float days, int stackLimit) {
		this(description, opinion, mood, days, stackLimit, false);
	}

	MemoryType(String description, int opinion, int mood, float days, int stackLimit, boolean pride) {
		this.description = description;
		this.opinion = opinion;
		this.mood = mood;
		this.days = days;
		this.stackLimit = stackLimit;
		this.pride = pride;
	}

	/** Whether this memory is about a particular person (otherwise it only affects mood). */
	public boolean isAboutSomeone() {
		return this.opinion != 0;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static MemoryType byName(String name) {
		for (MemoryType type : values()) {
			if (type.id().equalsIgnoreCase(name)) {
				return type;
			}
		}

		return null;
	}
}
