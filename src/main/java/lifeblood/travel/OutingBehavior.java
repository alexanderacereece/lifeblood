package lifeblood.travel;

import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.personality.Quirk;
import lifeblood.social.MemoryType;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * Carrying out a trip, an instinct every NPC has that takes over from their day while they're away (orders, fights and
 * the like still come first): packing at home, walking there in legs, doing what they came for, walking home, and
 * putting things away. Nights on the road are spent in shelter: another homestead if one is close, else a hole dug
 * in the ground and closed over, as a player would.
 */
public final class OutingBehavior implements NpcBehavior {
	public static final String ID = "outing";
	/** Above orders' 0, below everything that must interrupt (fights, recovering, children keeping near a parent). */
	public static final int PRIORITY = 1;
	/** The hours of the night, when travelers take shelter. */
	private static final int NIGHT_FROM = 20;
	private static final int NIGHT_UNTIL = 6;
	/** How near another homestead must be for them to spend the night there instead of digging in, in blocks. */
	private static final int HOMESTEAD_SHELTER = 32;

	private final LongWalk walk = new LongWalk();
	@Nullable
	private Shelter shelter;
	@Nullable
	private NpcBehavior there;
	@Nullable
	private Supplies.Packing packing;
	private final Supplies.Unpacking unpacking = new Supplies.Unpacking();
	/** The trip last ticked, for describing it. */
	@Nullable
	private Outing lastOuting;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public int getPriority() {
		return PRIORITY;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return npc.outing.isAway() && !npc.brain.hasOrders();
	}

	@Override
	public void tick(EntityNPC npc) {
		Outing outing = npc.outing.current();
		if (outing == null) {
			return;
		}

		this.lastOuting = outing;
		npc.state.setDoing(outing.stage == Outing.Stage.HOMEWARD ? "heading_home" : outing.kind);
		switch (outing.stage) {
			case PACKING -> this.pack(npc, outing);
			case OUTBOUND, HOMEWARD -> this.travel(npc, outing);
			case THERE -> this.beThere(npc, outing);
			case HOME -> this.comeHome(npc, outing);
		}
	}

	private void pack(EntityNPC npc, Outing outing) {
		if (this.packing == null) {
			OutingKind kind = OutingKinds.byId(outing.kind);
			this.packing = new Supplies.Packing(kind != null ? kind::packs : stack -> false);
		}

		if (outing.passingThrough || this.packing.tick(npc)) {
			outing.stage = Outing.Stage.OUTBOUND;
			this.walk.reset();
		}
	}

	/** On the road: walking by day, sheltering by night. */
	private void travel(EntityNPC npc, Outing outing) {
		if (this.sheltering(npc)) {
			return;
		}

		LongWalk.Status status = this.walk.toward(npc, outing.targetX(), outing.targetZ());
		if (status == LongWalk.Status.WALKING) {
			return;
		}

		this.walk.reset();
		if (outing.stage == Outing.Stage.OUTBOUND) {
			// There, or lost and giving up on it: either way, on to the stay or home.
			if (status == LongWalk.Status.ARRIVED) {
				outing.stage = Outing.Stage.THERE;
				outing.arrived = npc.world.getWorldTime();
			} else {
				outing.stage = Outing.Stage.HOMEWARD;
				npc.voice.bark("trip_lost", null, null, false);
			}
		} else {
			outing.stage = Outing.Stage.HOME;
		}
	}

	/** Night on the road: into shelter and out of it in the morning. Returns true while that's what they're doing. */
	private boolean sheltering(EntityNPC npc) {
		if (isNight(npc)) {
			if (this.shelter == null) {
				this.shelter = new Shelter(npc);
				npc.navigator.stop();
				if (npc.personality.has(Quirk.HOMEBODY)) {
					npc.remember(MemoryType.HOMESICK, null, 1.0F);
				}
			}

			// A homestead close by is better than a hole in the ground: they stay by its door.
			HomesteadRegister.Homestead near = nearbyHomestead(npc);
			if (near != null) {
				if (!npc.navigator.isMoving() && npc.distanceToSqr(near.x() + near.width() / 2.0, npc.y, near.z() + near.depth() / 2.0) > 36.0) {
					npc.navigator.moveTo(near.x() + near.width() / 2, near.y() + 1, near.z() + near.depth() / 2);
				}
			} else {
				this.shelter.night();
			}

			npc.state.setDoing("sheltering");
			return true;
		}

		if (this.shelter != null) {
			if (!this.shelter.morning()) {
				return true;
			}

			this.shelter = null;
		}

		return false;
	}

	private void beThere(EntityNPC npc, Outing outing) {
		OutingKind kind = OutingKinds.byId(outing.kind);
		if (kind == null) {
			outing.stage = Outing.Stage.HOMEWARD;
			return;
		}

		if (kind.sheltersAtNight() && this.sheltering(npc)) {
			return;
		}

		if (this.there == null) {
			this.there = kind.atDestination(outing);
			this.there.start(npc);
		}

		boolean stayOver = npc.world.getWorldTime() - outing.arrived > outing.stay;
		if (stayOver || !this.there.shouldContinue(npc)) {
			this.there.stop(npc);
			this.there = null;
			outing.stage = Outing.Stage.HOMEWARD;
			this.walk.reset();
			return;
		}

		this.there.tick(npc);
	}

	/** Home again: the haul put away, the trip remembered, and a word about how it went. */
	private void comeHome(EntityNPC npc, Outing outing) {
		if (!outing.passingThrough && !this.unpacking.tick(npc)) {
			return;
		}

		npc.remember(switch (outing.kind) {
			case "adventure" -> MemoryType.HAD_AN_ADVENTURE;
			case "visit" -> MemoryType.VISITED_NEIGHBORS;
			default -> MemoryType.WENT_ON_A_JOURNEY;
		}, null, 1.0F);
		if (outing.foundTreasure) {
			npc.remember(MemoryType.FOUND_TREASURE, null, 1.0F);
		}

		if (outing.gotHurt) {
			npc.remember(MemoryType.CAME_BACK_HURT, null, 1.0F);
		}

		npc.voice.bark("trip_home", null, context -> context.fact("trip", outing.kind)
			.fact("found_treasure", outing.foundTreasure ? "yes" : "no").fact("got_hurt", outing.gotHurt ? "yes" : "no"), true);
		npc.outing.finish();
		npc.state.setDoing("");
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
		if (this.there != null) {
			this.there.stop(npc);
			this.there = null;
		}

		npc.state.setDoing("");
	}

	static boolean isNight(EntityNPC npc) {
		int hour = Calendar.hourOfDay(npc.world.getWorldTime());
		return hour >= NIGHT_FROM || hour < NIGHT_UNTIL;
	}

	@Nullable
	private static HomesteadRegister.Homestead nearbyHomestead(EntityNPC npc) {
		for (HomesteadRegister.Homestead homestead : HomesteadRegister.get(npc.world).all()) {
			double dx = homestead.x() + homestead.width() / 2.0 - npc.x;
			double dz = homestead.z() + homestead.depth() / 2.0 - npc.z;
			if (dx * dx + dz * dz < HOMESTEAD_SHELTER * HOMESTEAD_SHELTER) {
				return homestead;
			}
		}

		return null;
	}

	@Override
	public String describe() {
		if (this.shelter != null) {
			String sheltering = this.shelter.describe();
			return sheltering != null ? sheltering : "sheltering";
		}

		if (this.there != null) {
			return this.there.describe();
		}

		Outing outing = this.lastOuting;
		if (outing == null) {
			return "on a trip";
		}

		String trip = outing.kind.equals("visit") ? "a visit" : outing.kind.equals("adventure") ? "an adventure" : "a journey";
		return switch (outing.stage) {
			case PACKING -> "packing for " + trip;
			case OUTBOUND -> outing.passingThrough ? "passing through, on the way home" : "on the road, off on " + trip;
			case THERE -> "there, on " + trip;
			case HOMEWARD -> "on the way home from " + trip;
			case HOME -> "home from " + trip + ", putting things away";
		};
	}
}
