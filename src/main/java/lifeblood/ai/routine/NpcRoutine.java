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
	/** The activity under way, and the behavior carrying it out; null until first asked. */
	@Nullable
	private String activity;
	@Nullable
	private NpcBehavior behavior;

	public NpcRoutine(EntityNPC npc) {
		this.npc = npc;
	}

	public String getScheduleId() {
		return this.scheduleId;
	}

	public void setScheduleId(String scheduleId) {
		this.scheduleId = scheduleId;
		this.activity = null;
	}

	/** The activity the schedule calls for right now. */
	public String currentActivity() {
		return Routines.schedule(this.scheduleId).activityAt(Calendar.hourOfDay(this.npc.world.getWorldTime()));
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
	}

	@Override
	public void load(CompoundTag tag) {
		this.setScheduleId(tag.getStringOrDefault("Schedule", Routines.DEFAULT_SCHEDULE));
	}
}
