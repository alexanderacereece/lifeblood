package npclaude.words;

import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of word the player is teaching, which decides how it's asked for and how it's used. Each comes with
 * a hint (what kind of answer fits, with an example) and words shown before the text box ("a", "Welcome to") so
 * it's clear what's being asked and the player types just the word. Nouns (food, animal, thing) are asked for in
 * the singular, then the plural (food and things can also be uncountable, like "bread").
 * Speech files use a known word of each category as [taught_food], [taught_angry] and so on.
 */
public enum WordCategory {
	FOOD(true, true, "A food, like \"flapjack\". Just the word for one of them.", "a(n)",
		List.of("What would you call a food you'd eat for breakfast?", "If you invented a new snack, what would you call it?")),
	ANIMAL(true, false, "A creature, like \"snibbit\". Just the word for one of them.", "a(n)",
		List.of("What would you call a little creature you'd find in a garden?", "If you saw a beast nobody had ever named, what would you call it?")),
	THING(true, true, "An object, like \"doohickey\". Just the word for one of them.", "a(n)",
		List.of("What would you call a handy little gadget?", "What would you call a tool nobody's invented yet?")),
	PLACE(false, false, "The name of a place, like \"Greenhollow\".", "Welcome to",
		List.of("If you found a hidden valley, what would you name it?", "What would you name a faraway land?")),
	EXCLAMATION(false, false, "Something to shout, like \"Holy cow\".", "",
		List.of("What do you shout when something surprises you?", "What do you say when you can't believe your eyes?")),
	ANGRY(false, false, "Something to grumble when you're mad, like \"Blast it\".", "",
		List.of("What do you say when you're really mad?", "Teach me something to grumble when things go wrong!")),
	HAPPY(false, false, "Something to cheer, like \"Woohoo\".", "",
		List.of("What do you say when you're really happy?", "What do you cheer when something goes right?")),
	SAD(false, false, "Something to sigh when you're down, like \"Oh, bother\".", "",
		List.of("What do you say when you're sad?", "What do you sigh when everything goes wrong?")),
	SCARED(false, false, "Something to yelp when you're frightened, like \"Yikes\".", "",
		List.of("What do you say when you're scared?", "What do you yelp when something jumps out at you?")),
	GREETING(false, false, "A way to say hello, like \"Howdy\".", "",
		List.of("How do people say hello where you're from?", "Teach me a new way to say hi!")),
	FAREWELL(false, false, "A way to say goodbye, like \"Toodles\".", "",
		List.of("How do people say goodbye where you're from?", "Teach me a new way to say bye!")),
	NICKNAME(false, false, "A nickname for you, just for me to use, like \"Sparky\".", "",
		List.of("What should I call you? A nickname, I mean.", "Do you have a nickname? What should I call you?")),
	DESCRIBE(false, false, "A describing word for something great, like \"spiffy\".", "That's",
		List.of("What's a word for something really, really good?", "What would you call something wonderful?")),
	ACTIVITY(false, false, "Something to do, said like \"rock skipping\".", "I love",
		List.of("What's something fun to do on a lazy afternoon?", "What's a hobby you'd recommend?"));

	/** Asked for in singular and plural. */
	public final boolean noun;
	/** Can be uncountable ("some bread") instead of having a plural. */
	public final boolean canBeMass;
	public final String hint;
	/** Shown before the text box. */
	public final String prefix;
	public final List<String> prompts;

	WordCategory(boolean noun, boolean canBeMass, String hint, String prefix, List<String> prompts) {
		this.noun = noun;
		this.canBeMass = canBeMass;
		this.hint = hint;
		this.prefix = prefix;
		this.prompts = prompts;
	}

	/** A nickname for you is always theirs alone. */
	public boolean alwaysPersonal() {
		return this == NICKNAME;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return switch (this) {
			case EXCLAMATION -> "surprised";
			case DESCRIBE -> "describing word";
			default -> this.id();
		};
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
