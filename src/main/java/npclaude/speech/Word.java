package npclaude.speech;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;

/**
 * A noun that knows its forms, so sentences agree with it: "a pork chop", "some blackberries", "some bread".
 *
 * Modifiers in speech files: [food.a] "a/an/some ...", [food.the] "the ...", [food.s] the plural,
 * [food.it] "it"/"them", [food.is] "is"/"are", [food.this] "this"/"these", and .cap to capitalize.
 */
public record Word(String singular, String plural, Kind kind) {
	public enum Kind {
		/** One countable thing: "a pork chop". */
		ONE,
		/** Already plural: "blackberries". */
		PLURAL,
		/** Can't be counted: "bread". */
		MASS
	}

	/** Endings of foods that are usually uncountable. */
	private static final List<String> MASS_ENDINGS = List.of("fish", "venison", "bread", "stew", "soup", "meat", "beef", "milk", "jerky",
		"salmon", "cod", "rice", "cheese");

	/** Endings of things that are usually uncountable: "some dirt", "some gunpowder". */
	private static final List<String> MASS_THING_ENDINGS = List.of("dirt", "sand", "gravel", "stone", "rack", "mud", "clay", "wool",
		"string", "leather", "paper", "powder", "dust", "sugar", "wheat", "cloth", "coal", "flint", "obsidian", "glass", "snow", "ice",
		"sulfur", "ash", "moss", "grass", "rock", "slate", "basalt", "granite", "marble", "permafrost", "shale", "redstone", "quartz",
		"olivine", "algae");

	public static Word one(String singular) {
		return new Word(singular, pluralize(singular), Kind.ONE);
	}

	public static Word mass(String name) {
		return new Word(name, name, Kind.MASS);
	}

	/** A plain name with no article, e.g. a faction: "the raiders" is written by the line itself. */
	public static Word name(String name) {
		return new Word(name, name, Kind.MASS);
	}

	/** A food's word, guessing from its name whether it's countable. */
	public static Word ofFood(Item item) {
		String name = new ItemStack(item).getDisplayName().toLowerCase(Locale.ROOT);
		for (String ending : MASS_ENDINGS) {
			if (name.endsWith(ending)) {
				return mass(name);
			}
		}

		return one(name);
	}

	/** An item talked about in general ("I love compasses", "dirt is the worst"). */
	public static Word ofThing(Item item) {
		String name = new ItemStack(item).getDisplayName().toLowerCase(Locale.ROOT);
		for (String ending : MASS_THING_ENDINGS) {
			if (name.endsWith(ending)) {
				return mass(name);
			}
		}

		return isAlreadyPlural(name) ? new Word(name, name, Kind.PLURAL) : one(name);
	}

	/** "pine leaves", "shears", "ice skates": already plural, so not "leaveses". "Compass" and "glass" aren't. */
	private static boolean isAlreadyPlural(String name) {
		return name.endsWith("s") && !name.endsWith("ss") && !name.endsWith("us") && !name.endsWith("is");
	}

	/** A particular stack: "a compass", "some compasses", "some dirt". */
	public static Word ofItem(ItemStack stack) {
		String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
		if (ofThing(stack.getItem()).kind() == Kind.MASS) {
			return mass(name);
		}

		if (isAlreadyPlural(name)) {
			return new Word(name, name, Kind.PLURAL);
		}

		if (stack.stackSize > 1) {
			return new Word(name, pluralize(name), Kind.PLURAL);
		}

		return one(name);
	}

	/** The usual plural of a word: "berry" to "berries", "box" to "boxes". */
	public static String plural(String word) {
		return pluralize(word);
	}

	private static String pluralize(String word) {
		if (word.endsWith("y") && word.length() > 1 && "aeiou".indexOf(word.charAt(word.length() - 2)) < 0) {
			return word.substring(0, word.length() - 1) + "ies";
		}

		if (word.endsWith("s") || word.endsWith("sh") || word.endsWith("ch") || word.endsWith("x")) {
			return word + "es";
		}

		return word + "s";
	}

	/** Renders the word with modifiers (everything after the name in [name.mod.mod]). */
	public String render(List<String> modifiers) {
		String text = this.kind == Kind.PLURAL ? this.plural : this.singular;
		for (String modifier : modifiers) {
			text = switch (modifier) {
				case "a" -> this.kind == Kind.ONE ? article(this.singular) + " " + this.singular : "some " + text;
				case "the" -> "the " + text;
				case "s" -> this.plural;
				case "it" -> this.kind == Kind.PLURAL ? "them" : "it";
				case "is" -> this.kind == Kind.PLURAL ? "are" : "is";
				case "this" -> this.kind == Kind.PLURAL ? "these" : "this";
				default -> text;
			};
		}

		return text;
	}

	private static String article(String word) {
		return !word.isEmpty() && "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
	}
}
