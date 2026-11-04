package lifeblood.dialogue;

import java.util.function.Consumer;

/** Something the player can say, and what happens when they say it. */
public record DialogueOption(String text, Consumer<DialogueSession> action) {
}
