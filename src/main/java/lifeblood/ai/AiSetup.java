package lifeblood.ai;

import lifeblood.ai.behavior.GoToBehavior;
import lifeblood.ai.behavior.StayBehavior;
import lifeblood.ai.behavior.WanderBehavior;
import lifeblood.ai.routine.Routines;
import lifeblood.ai.routine.Schedule;

/**
 * The plain orders (go somewhere, stay, wander) and the default daily routine of wandering about home all day.
 */
public final class AiSetup {
	private AiSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.register(GoToBehavior.ID, GoToBehavior::new);
		NpcBehaviors.register(StayBehavior.ID, StayBehavior::new);
		NpcBehaviors.register(WanderBehavior.ID, WanderBehavior::new);
		Routines.registerActivity(Routines.WANDER, WanderBehavior::new);
		Routines.registerSchedule(Schedule.allDay(Routines.DEFAULT_SCHEDULE, Routines.WANDER));
	}
}
