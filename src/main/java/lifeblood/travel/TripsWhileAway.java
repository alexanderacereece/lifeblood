package lifeblood.travel;

import java.util.Random;
import lifeblood.NpcConfig;
import lifeblood.entity.EntityNPC;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;
import lifeblood.event.TimeAwayEvent;
import lifeblood.social.MemoryType;
import lifeblood.world.Calendar;

/**
 * The trips a household's people took while nobody was near (a TimeAwayEvent listener): each grown-up who isn't away
 * already may have set out a few times in the days unwatched, as often as they would have in sight. Each trip is worked
 * out by its kind as a trip out of sight is: what they found goes into the household's chests, wounds are carried, and
 * now and then someone didn't come back, which is a death like any other. A trip recent enough is still remembered.
 */
public final class TripsWhileAway {
	/** How recent a trip must be, in in-game days, to still be on their mind (the memory lasts about a week). */
	private static final double REMEMBERED_DAYS = 7.0;

	private TripsWhileAway() {
	}

	static void onTimeAway(TimeAwayEvent event) {
		Random random = event.world().rand;
		double days = event.ticks() / (double) Calendar.TICKS_PER_DAY;
		for (EntityNPC npc : event.people()) {
			if (npc.life.isChild() || npc.outing.isAway() || !npc.isAlive()) {
				continue;
			}

			double expected = days * NpcConfig.tripsPerYear() * NpcOuting.inclination(npc) / Calendar.yearDays(npc.world);
			int trips = (int) Math.floor(expected) + (random.nextDouble() < expected - Math.floor(expected) ? 1 : 0);
			for (int trip = 0; trip < trips; trip++) {
				if (!oneTrip(event, npc, random, trip == trips - 1 && days * random.nextDouble() < REMEMBERED_DAYS)) {
					break;
				}
			}
		}
	}

	/** One trip taken meanwhile; returns false if they didn't come back from it. */
	private static boolean oneTrip(TimeAwayEvent event, EntityNPC npc, Random random, boolean remembered) {
		OutingKind kind = OutingKinds.pick(npc, random);
		Outing outing = kind != null ? kind.plan(npc, random) : null;
		if (outing == null) {
			return true;
		}

		OutingKind.Outcome outcome = kind.simulate(outing, npc, random);
		if (outcome.died()) {
			// Out there and gone: the household is one fewer, and the family hears.
			LifeEvents.post(new DeathEvent(npc, null));
			npc.remove();
			return false;
		}

		outcome.found().forEach(event.store());
		if (outcome.healthLost() > 0.0F) {
			npc.setHealthRaw(Math.max(1, Math.round(npc.getHealth() - outcome.healthLost())));
		}

		if (remembered) {
			npc.remember(switch (kind.id()) {
				case "adventure" -> MemoryType.HAD_AN_ADVENTURE;
				case "visit" -> MemoryType.VISITED_NEIGHBORS;
				default -> MemoryType.WENT_ON_A_JOURNEY;
			}, null, 1.0F);
			if (outing.foundTreasure) {
				npc.remember(MemoryType.FOUND_TREASURE, null, 1.0F);
			}
		}

		return true;
	}
}
