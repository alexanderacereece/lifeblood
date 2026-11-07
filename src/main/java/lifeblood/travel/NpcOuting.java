package lifeblood.travel;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.player.Player;
import lifeblood.NpcConfig;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * An NPC's trips away from home: the one under way, if any, and deciding each morning whether to set out on one. The
 * wanderers go most and homebodies hardly ever; the curious and brave go more; children never go, and nobody leaves
 * while under orders. Once a traveler is far from every player, their trip is handed to TravelRegister to carry on.
 */
public final class NpcOuting implements NpcPart {
	/** The hours someone may set out in: early in the day. */
	private static final int SET_OUT_FROM = 6;
	private static final int SET_OUT_UNTIL = 9;
	/** How far from every player a traveler is handed over to go on as a record, in blocks. */
	public static final double HANDOVER_DISTANCE = 96.0;
	/** How often it checks for handing over, in ticks. */
	private static final int CHECK_INTERVAL = 40;
	/** A gap this long since it last ticked means its area was unloaded with it: it catches up, in ticks. */
	private static final long CATCH_UP_GAP = 600L;

	/**
	 * For testing on a server with nobody on it: travelers stay real as if a player were near, so walking, shelter and
	 * caves can be watched. Set by /lb debug keepreal; not saved.
	 */
	private static volatile boolean keepReal;

	private final EntityNPC npc;
	@Nullable
	private Outing outing;
	/** The last day it thought about setting out, so it does once a day. */
	private long consideredDay = Long.MIN_VALUE;
	/** When it last ticked (world time), to notice time that passed while its area was unloaded. */
	private long lastTick = -1L;

	public NpcOuting(EntityNPC npc) {
		this.npc = npc;
	}

	@Nullable
	public Outing current() {
		return this.outing;
	}

	public boolean isAway() {
		return this.outing != null;
	}

	public void start(Outing outing) {
		this.outing = outing;
	}

	public void finish() {
		this.outing = null;
	}

	/** Starts its clock from now, as when a traveler is made real again, so the time spent away isn't counted twice. */
	public void resetClock() {
		this.lastTick = this.npc.world.getWorldTime();
	}

	@Override
	public void tick() {
		long now = this.npc.world.getWorldTime();
		long gap = this.lastTick < 0L ? 0L : now - this.lastTick;
		this.lastTick = now;
		if (this.npc.world.isClientSide) {
			return;
		}

		// Packing and putting things away happen at home, for real; the road between is what goes on out of sight.
		if (this.outing != null && this.outing.stage != Outing.Stage.HOME && this.outing.stage != Outing.Stage.PACKING) {
			// Away from every player, or back after its area sat unloaded a while: the trip goes on as a record.
			boolean due = (this.npc.tickCount + this.npc.id) % CHECK_INTERVAL == 0;
			if (!keepReal && gap > CATCH_UP_GAP || due && !nearPlayer()) {
				TravelRegister.get(this.npc.world).takeOver(this.npc, gap > CATCH_UP_GAP ? now - gap : now);
			}

			return;
		}

		if (this.outing != null) {
			return;
		}

		int hour = Calendar.hourOfDay(now);
		long day = Calendar.day(now);
		if (hour >= SET_OUT_FROM && hour < SET_OUT_UNTIL && day != this.consideredDay) {
			this.consideredDay = day;
			this.considerSettingOut();
		}
	}

	public static boolean isKeepingReal() {
		return keepReal;
	}

	public static void setKeepReal(boolean real) {
		keepReal = real;
	}

	/** Whether any player is near enough to see this NPC, so its trip plays out for real. */
	private boolean nearPlayer() {
		if (keepReal) {
			return true;
		}

		for (Player player : this.npc.world.players) {
			if (player.distanceTo(this.npc) < HANDOVER_DISTANCE) {
				return true;
			}
		}

		return false;
	}

	/** Once a day: maybe set out, by how many trips a year someone like them takes. */
	private void considerSettingOut() {
		if (this.npc.life.isChild() || this.npc.brain.hasOrders() || this.npc.state.isBusy()) {
			return;
		}

		double perYear = NpcConfig.tripsPerYear() * inclination(this.npc);
		double chance = perYear / Calendar.yearDays(this.npc.world);
		if (this.npc.getRandom().nextDouble() >= chance) {
			return;
		}

		OutingKind kind = OutingKinds.pick(this.npc, this.npc.getRandom());
		Outing outing = kind != null ? kind.plan(this.npc, this.npc.getRandom()) : null;
		if (outing != null) {
			outing.started = this.npc.world.getWorldTime();
			outing.homeX = (int) Math.floor(this.npc.home.getX());
			outing.homeY = (int) Math.floor(this.npc.home.getY());
			outing.homeZ = (int) Math.floor(this.npc.home.getZ());
			this.outing = outing;
			this.npc.voice.bark("trip_setting_out", null, context -> context.fact("trip", kind.id()), false);
		}
	}

	/** How much more (or less) than most this person travels: wanderers four times, homebodies a tenth. */
	static double inclination(EntityNPC npc) {
		double inclination = 1.0;
		if (npc.personality.has(Quirk.WANDERER)) {
			inclination *= 4.0;
		} else if (npc.personality.has(Quirk.HOMEBODY)) {
			inclination *= 0.1;
		}

		if (npc.personality.has(Quirk.CURIOUS)) {
			inclination *= 1.5;
		}

		inclination *= 1.0 + 0.3 * npc.personality.get(TraitAxis.COURAGE);
		return inclination;
	}

	@Override
	public String saveKey() {
		return "Outing";
	}

	@Override
	public void save(CompoundTag tag) {
		if (this.outing != null) {
			tag.putCompound("Current", this.outing.save());
		}

		tag.putLong("ConsideredDay", this.consideredDay);
		tag.putLong("LastTick", this.lastTick);
	}

	@Override
	public void load(CompoundTag tag) {
		this.outing = tag.containsKey("Current") ? Outing.load(tag.getCompound("Current")) : null;
		this.consideredDay = tag.getLongOrDefault("ConsideredDay", Long.MIN_VALUE);
		this.lastTick = tag.getLongOrDefault("LastTick", -1L);
	}
}
