package lifeblood.gossip;

import lifeblood.event.AttackedEvent;
import lifeblood.event.BirthEvent;
import lifeblood.event.BrawlEndedEvent;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;
import lifeblood.event.PartnershipEvent;
import lifeblood.speech.ChatTopics;

/** News: what happens becomes news (see Gossip), and NPCs pass it on in chats (see GossipTopics). */
public final class GossipSetup {
	private GossipSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		LifeEvents.listen(DeathEvent.class, Gossip::onDeath);
		LifeEvents.listen(BirthEvent.class, Gossip::onBirth);
		LifeEvents.listen(PartnershipEvent.class, Gossip::onPartnership);
		LifeEvents.listen(AttackedEvent.class, Gossip::onAttacked);
		LifeEvents.listen(BrawlEndedEvent.class, Gossip::onBrawlEnded);
		ChatTopics.register(GossipTopics.NEWS);
		ChatTopics.register(GossipTopics.OLD_NEWS);
	}
}
