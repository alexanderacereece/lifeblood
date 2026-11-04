package lifeblood.dialogue;

import org.jetbrains.annotations.Nullable;

/**
 * What to ask the player to type: the question, a hint about what kind of answer fits ("A place name, like
 * Greenhollow"), words shown before the box ("a", "some") so the player types just the word, a starting text, and an
 * optional extra button.
 */
public record TextRequest(String question, String hint, String prefix, String initial, @Nullable String extraLabel) {
}
