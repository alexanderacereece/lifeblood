package lifeblood.speech;

import lifeblood.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * Something two NPCs can chat about: the weather, food, home, the news. The one who starts the chat picks a topic,
 * opens with a line from "chat_open_&lt;id&gt;", and the other answers from "chat_reply_&lt;id&gt;", so the reply is about
 * the same thing. Built-in topics are in ChatTopics; other systems add theirs from their setup (gossip adds news).
 */
public interface ChatTopic {
	/** Its id, which names its symbols: "weather" speaks from chat_open_weather and chat_reply_weather. */
	String id();

	/** How likely the speaker is to bring it up with this listener, relative to other topics; 0 for never. */
	float weight(EntityNPC speaker, EntityNPC listener);

	/** A chat about it between these two, or null if the speaker has nothing to say about it right now. */
	@Nullable
	Exchange start(EntityNPC speaker, EntityNPC listener);

	/** One chat on a topic: what each side's lines get to know, and what happens once it's been said. */
	interface Exchange {
		/** Adds the facts and words the opening line can use (the speaker's view). */
		default void opening(SpeechContext context) {
		}

		/** Adds the facts and words the reply can use (the listener's view). */
		default void reply(SpeechContext context) {
		}

		/** The opening was said aloud: whatever it passed on has now been heard. */
		default void said() {
		}
	}

	/** A chat with nothing to add: the speech files' own facts are enough. */
	Exchange PLAIN = new Exchange() {
	};
}
