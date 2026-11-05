package lifeblood.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import org.jetbrains.annotations.Nullable;

/**
 * The topics NPCs chat about: the built-in ones (the weather, food, home, and general chat, which includes family),
 * plus any another system registers from its setup. Picks one for a chat by the topics' weights.
 */
public final class ChatTopics {
	/** The topic every chat can fall back on: the plain chat_open and chat_reply lines. */
	public static final String GENERAL = "general";

	private static final List<ChatTopic> TOPICS = new ArrayList<>(List.of(
		simple("weather", (speaker, listener) -> 1.0F),
		simple("food", (speaker, listener) -> speaker.personality.has(Quirk.GLUTTON) ? 3.0F : 0.8F),
		// Talk of home comes up mostly away from it.
		simple("home", (speaker, listener) -> speaker.life.getHomeBiome().isEmpty() ? 0.0F
			: SpeechFacts.isAtHome(speaker) ? 0.2F : 1.2F),
		simple(GENERAL, (speaker, listener) -> 1.5F)));

	private ChatTopics() {
	}

	/** Adds a topic. Call from a setup class. */
	public static void register(ChatTopic topic) {
		TOPICS.add(topic);
	}

	/** A topic for the speaker to open a chat with, and the chat about it; general chat if nothing else fits. */
	public static Picked pick(EntityNPC speaker, EntityNPC listener, Random random) {
		List<ChatTopic> candidates = new ArrayList<>();
		List<Float> weights = new ArrayList<>();
		float total = 0.0F;
		for (ChatTopic topic : TOPICS) {
			float weight = topic.weight(speaker, listener);
			if (weight > 0.0F) {
				candidates.add(topic);
				weights.add(weight);
				total += weight;
			}
		}

		while (!candidates.isEmpty()) {
			float roll = random.nextFloat() * total;
			int chosen = candidates.size() - 1;
			for (int i = 0; i < candidates.size(); i++) {
				roll -= weights.get(i);
				if (roll < 0.0F) {
					chosen = i;
					break;
				}
			}

			ChatTopic topic = candidates.get(chosen);
			ChatTopic.Exchange exchange = topic.start(speaker, listener);
			if (exchange != null) {
				return new Picked(topic.id(), exchange);
			}

			total -= weights.remove(chosen);
			candidates.remove(chosen);
		}

		return new Picked(GENERAL, ChatTopic.PLAIN);
	}

	/** A topic picked for a chat, and the chat about it. */
	public record Picked(String id, ChatTopic.Exchange exchange) {
	}

	/** A built-in topic whose lines need nothing but the usual facts. */
	private static ChatTopic simple(String id, Weight weight) {
		return new ChatTopic() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public float weight(EntityNPC speaker, EntityNPC listener) {
				return weight.of(speaker, listener);
			}

			@Override
			@Nullable
			public ChatTopic.Exchange start(EntityNPC speaker, EntityNPC listener) {
				return ChatTopic.PLAIN;
			}
		};
	}

	private interface Weight {
		float of(EntityNPC speaker, EntityNPC listener);
	}
}
