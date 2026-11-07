package lifeblood.travel;

import lifeblood.ai.NpcBehaviors;
import lifeblood.event.LifeEvents;
import lifeblood.event.TimeAwayEvent;
import lifeblood.speech.ChatTopics;
import lifeblood.travel.kinds.AdventureKind;
import lifeblood.travel.kinds.JourneyKind;
import lifeblood.travel.kinds.VisitKind;
import lifeblood.world.WorldSystems;

/**
 * Trips away from home: the kinds (journeys, visits, adventures), carrying a trip out as an instinct every NPC has,
 * trips going on out of sight and strangers passing through (world systems), trips taken while a household was
 * unwatched (a TimeAwayEvent listener), and talking a trip over (a chat topic).
 */
public final class TravelSetup {
	private TravelSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		OutingKinds.register(new JourneyKind());
		OutingKinds.register(new VisitKind());
		OutingKinds.register(new AdventureKind());
		NpcBehaviors.registerInstinct(OutingBehavior::new);
		WorldSystems.register(TravelSimulation.SYSTEM);
		WorldSystems.register(Strangers.SYSTEM);
		ChatTopics.register(TravelTalk.TRAVELS);
		LifeEvents.listen(TimeAwayEvent.class, TripsWhileAway::onTimeAway);
	}
}
