package lifeblood.homestead;

import lifeblood.ai.behavior.WanderBehavior;
import lifeblood.ai.routine.Routines;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.chores.ChoresBehavior;
import lifeblood.homestead.life.HomesteadDay;
import lifeblood.homestead.life.HouseholdCatchUp;
import lifeblood.homestead.life.MealBehavior;
import lifeblood.homestead.life.SleepBehavior;
import lifeblood.speech.ChatTopic;
import lifeblood.speech.ChatTopics;
import lifeblood.world.WorldSystems;

/**
 * Homesteaders' days (the homestead schedule and its activities: sleeping, chores, meals, evenings) and their talk of
 * how they came to live out here, as a chat topic (chat_open_settling, chat_reply_settling).
 */
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
		Routines.registerActivity(HomesteadDay.SLEEP, SleepBehavior::new);
		Routines.registerActivity(HomesteadDay.CHORES, ChoresBehavior::new);
		Routines.registerActivity(HomesteadDay.MEAL, MealBehavior::new);
		Routines.registerActivity(HomesteadDay.EVENING, () -> new WanderBehavior(HomesteadDay.EVENING_RADIUS));
		Routines.registerSchedule(HomesteadDay.schedule());
		WorldSystems.register(HouseholdCatchUp.SYSTEM);
	}
}
