package npclaude.words;

import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of word the player is teaching, which decides how it's asked for and how it's used. Nouns (food,
 * animal, thing) are asked for in the singular, then the plural (food can also be uncountable, like "bread").
 * Speech files use a known word of each category as [taught_food], [taught_greeting] and so on.
 */
public enum WordCategory {
	FOOD(true, true, List.of("What would you call something you'd eat for breakfast?", "What's a good name for a snack?",
		"If you invented a new food, what would you call it?")),
	ANIMAL(true, false, List.of("What would you call a little creature you might find in a garden?", "What's a good name for a strange animal?",
		"If you saw a beast nobody had ever named, what would you call it?")),
	THING(true, true, List.of("What would you call a handy little gadget?", "What's a good word for some odd thing you'd keep in your pocket?",
		"What would you call a tool nobody's invented yet?")),
	PLACE(false, false, List.of("If you named a place, what would you call it?", "What's a good name for a faraway land?",
		"What would you call a hidden valley?")),
	EXCLAMATION(false, false, List.of("What's something you'd shout when you stub your toe?", "What do you say when you're surprised?",
		"Teach me something to yell when things go wrong!")),
	GREETING(false, false, List.of("How do people say hello where you're from?", "Teach me a new way to say hi!")),
	FAREWELL(false, false, List.of("How do people say goodbye where you're from?", "Teach me a new way to say bye!")),
	NICKNAME(false, false, List.of("What should I call you? A nickname, I mean.", "Do you have a nickname? What should I call you?")),
	DESCRIBE(false, false, List.of("What's a word for something really good?", "What would you call something wonderful?",
		"What's a word for something awful?")),
	ACTIVITY(false, false, List.of("What's something fun to do on a lazy afternoon? Like 'rock skipping'.",
		"What's a hobby you'd recommend? Say it like 'cloud watching'."));

	/** Asked for in singular and plural. */
	public final boolean noun;
	/** Can be uncountable ("some bread") instead of having a plural. */
	public final boolean canBeMass;
	public final List<String> prompts;

	WordCategory(boolean noun, boolean canBeMass, List<String> prompts) {
		this.noun = noun;
		this.canBeMass = canBeMass;
		this.prompts = prompts;
	}

	/** A nickname for you is always theirs alone. */
	public boolean alwaysPersonal() {
		return this == NICKNAME;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static WordCategory byName(String name) {
		for (WordCategory category : values()) {
			if (category.id().equalsIgnoreCase(name)) {
				return category;
			}
		}

		return null;
	}
}
