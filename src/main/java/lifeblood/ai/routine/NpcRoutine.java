package lifeblood.ai.routine;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC does with its day when nobody has told it to do anything: its schedule names an activity for each
 * hour, and the routine keeps a behavior running for the activity of the moment. The brain falls back on it when
 * there are no orders (follow, stay, go somewhere) and no instinct (a fight, a brawl) has taken over.
 */
public final class NpcRoutine implements NpcPart {
	private final EntityNPC npc;
	private String scheduleId = Routines.DEFAULT_SCHEDULE;
	/**
	 * How many hours ahead of the clock it lives: 1 for an early bird (up and to bed an hour early), -1 for a night owl
	 * (an hour late), 0 for most.
	 */
	private int hourShift;
	/** The activity under way, and the behavior carrying it out; null until first asked. */
	@Nullable
	private String activity;
	@Nullable
	private NpcBehavior behavior;

	public NpcRoutine(EntityNPC npc) {
		this.npc = npc;
	}

	public void setScheduleId(String scheduleId) {
		this.scheduleId = scheduleId;
		this.activity = null;
	}

	/** Sets how many hours ahead of the clock it lives (an early bird 1, a night owl -1). */
	public void setHourShift(int hours) {
		this.hourShift = hours;
		this.activity = null;
	}

	/** The activity the schedule calls for right now, by its own hours. */
	public String currentActivity() {
		int hour = Math.floorMod(Calendar.hourOfDay(this.npc.world.getWorldTime()) + this.hourShift, 24);
		return Routines.schedule(this.scheduleId).activityAt(hour);
	}

	/** The behavior for the activity of the moment, switching to a new one when the schedule moves on. */
	@Nullable
	public NpcBehavior behavior() {
		String now = this.currentActivity();
		if (!now.equals(this.activity)) {
			this.activity = now;
			this.behavior = Routines.startActivity(now);
		}

		return this.behavior;
	}

	/** The behavior for the activity under way, without moving on to a new one. */
	@Nullable
	public NpcBehavior currentBehavior() {
		return this.behavior;
	}

	@Override
	public String saveKey() {
		return "Routine";
	}

	/** The schedule is saved; the activity is worked out again from the time of day. */
	@Override
	public void save(CompoundTag tag) {
		tag.putString("Schedule", this.scheduleId);
		tag.putInt("HourShift", this.hourShift);
	}

	@Override
	public void load(CompoundTag tag) {
		this.setScheduleId(tag.getStringOrDefault("Schedule", Routines.DEFAULT_SCHEDULE));
		this.hourShift = tag.getIntegerOrDefault("HourShift", 0);
	}
}
