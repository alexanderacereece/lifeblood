package lifeblood.homestead;

import lifeblood.entity.EntityNPC;
import lifeblood.speech.ChatTopic;
import lifeblood.speech.ChatTopics;

/** Homesteaders talking about how they came to live out here, as a chat topic (chat_open_settling, chat_reply_settling). */
public final class HomesteadSetup {
	/** How likely someone with a story of coming here brings it up, next to other topics. */
	private static final float SETTLING_WEIGHT = 0.6F;

	/** The settling topic: anyone who came to live where they are, or was born there, can talk about it. */
	static final ChatTopic SETTLING = new ChatTopic() {
		@Override
		public String id() {
			return "settling";
		}

		@Override
		public float weight(EntityNPC speaker, EntityNPC listener) {
			return speaker.life.getCameFor().isEmpty() ? 0.0F : SETTLING_WEIGHT;
		}

		@Override
		public ChatTopic.Exchange start(EntityNPC speaker, EntityNPC listener) {
			return ChatTopic.PLAIN;
		}
	};

	private HomesteadSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		ChatTopics.register(SETTLING);
	}
}
