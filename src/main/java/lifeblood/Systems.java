package lifeblood;

import lifeblood.ai.AiSetup;
import lifeblood.brawl.BrawlSetup;
import lifeblood.combat.CombatSetup;
import lifeblood.dialogue.DialogueSetup;
import lifeblood.family.FamilySetup;
import lifeblood.following.FollowingSetup;
import lifeblood.gossip.GossipSetup;
import lifeblood.health.HealthSetup;
import lifeblood.homestead.HomesteadSetup;
import lifeblood.ownership.OwnershipSetup;
import lifeblood.people.PeopleSetup;
import lifeblood.social.SocialSetup;
import lifeblood.travel.TravelSetup;

/**
 * Every system's setup, in one ordered list: the one place that shows everything wired into the mod. Each system
 * registers what it brings (behaviors, instincts, routines, world systems, dialogue options, event listeners) in its own
 * setup class, and nowhere else; ArchitectureTest checks that. A new system with something to register adds its setup
 * here.
 */
public final class Systems {
	private static boolean registered;

	private Systems() {
	}

	/** Registers everything, once (later calls do nothing, so tests can call it too). */
	public static synchronized void registerAll() {
		if (registered) {
			return;
		}

		registered = true;
		// World systems run in this order each tick: leftover placers are forgotten before anything else runs.
		OwnershipSetup.register();
		// Death listeners run in this order: people react and the news reaches the family while a widow's marriage
		// still stands, then the register records the death (ending it).
		SocialSetup.register();
		GossipSetup.register();
		PeopleSetup.register();
		AiSetup.register();
		FollowingSetup.register();
		CombatSetup.register();
		BrawlSetup.register();
		HealthSetup.register();
		FamilySetup.register();
		HomesteadSetup.register();
		TravelSetup.register();
		DialogueSetup.register();
	}
}
