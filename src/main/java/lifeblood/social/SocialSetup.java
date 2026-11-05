package lifeblood.social;

import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;

/**
 * How people react to deaths around them and in their family (see SocialEvents).
 */
public final class SocialSetup {
	private SocialSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		LifeEvents.listen(DeathEvent.class, SocialEvents::onDeath);
	}
}
