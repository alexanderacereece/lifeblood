package npclaude.ai.routine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import npclaude.ai.NpcBehavior;
import npclaude.ai.behavior.WanderBehavior;
import org.jetbrains.annotations.Nullable;

/**
 * The activities NPCs can fill their days with, and the schedules that arrange them. Each activity makes the
 * behavior that carries it out. Occupations will bring their own schedules (a farmer's day, a guard's watch) and
 * activities (working the fields, patrolling); for now everyone wanders about their home all day.
 */
public final class Routines {
	public static final String WANDER = "wander";
	public static final String DEFAULT_SCHEDULE = "default";

	private static final Map<String, Supplier<NpcBehavior>> ACTIVITIES = new LinkedHashMap<>();
	private static final Map<String, Schedule> SCHEDULES = new LinkedHashMap<>();

	static {
		registerActivity(WANDER, WanderBehavior::new);
		registerSchedule(Schedule.allDay(DEFAULT_SCHEDULE, WANDER));
	}

	private Routines() {
	}

	public static void registerActivity(String id, Supplier<NpcBehavior> behavior) {
		if (ACTIVITIES.putIfAbsent(id, behavior) != null) {
			throw new IllegalArgumentException("Duplicate NPC activity: " + id);
		}
	}

	public static void registerSchedule(Schedule schedule) {
		if (SCHEDULES.putIfAbsent(schedule.id(), schedule) != null) {
			throw new IllegalArgumentException("Duplicate NPC schedule: " + schedule.id());
		}
	}

	/** The behavior that carries out this activity, freshly made; null for an unknown activity. */
	@Nullable
	public static NpcBehavior startActivity(String id) {
		Supplier<NpcBehavior> behavior = ACTIVITIES.get(id);
		return behavior != null ? behavior.get() : null;
	}

	/** The schedule with this id, or the default one if there's no such schedule. */
	public static Schedule schedule(String id) {
		Schedule schedule = SCHEDULES.get(id);
		return schedule != null ? schedule : SCHEDULES.get(DEFAULT_SCHEDULE);
	}

	public static Set<String> scheduleIds() {
		return SCHEDULES.keySet();
	}
}
