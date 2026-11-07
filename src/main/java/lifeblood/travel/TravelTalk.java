package lifeblood.travel;

import lifeblood.entity.EntityNPC;
import lifeblood.speech.ChatTopic;
import lifeblood.speech.SpeechContext;
import lifeblood.speech.SpeechFacts;
import lifeblood.speech.Word;

/**
 * What travelers say about their trips: the facts for "Where are you headed?" (what kind of trip, how far along, which
 * way and how far), and the "travels" chat topic for talking a trip over afterwards (chat_open_travels,
 * chat_reply_travels).
 */
public final class TravelTalk {
	/** How likely someone back from a trip brings it up in a chat, next to other topics. */
	private static final float TRAVELS_WEIGHT = 1.2F;

	/** Talking over a trip just taken; the back_from fact (see SpeechFacts) says which. */
	public static final ChatTopic TRAVELS = new ChatTopic() {
		@Override
		public String id() {
			return "travels";
		}

		@Override
		public float weight(EntityNPC speaker, EntityNPC listener) {
			return SpeechFacts.backFrom(speaker).equals("none") ? 0.0F : TRAVELS_WEIGHT;
		}

		@Override
		public ChatTopic.Exchange start(EntityNPC speaker, EntityNPC listener) {
			return ChatTopic.PLAIN;
		}
	};

	private TravelTalk() {
	}

	/**
	 * Facts about the trip under way, for the line saying where they're headed: trip (journey, adventure, visit),
	 * trip_stage (going, there, heading_home), passing_through (yes for strangers), [direction] (north, south-west...)
	 * and [distance] ("a day's walk", "a few hours").
	 */
	public static void addTripFacts(EntityNPC npc, SpeechContext context) {
		Outing outing = npc.outing.current();
		if (outing == null) {
			context.fact("trip", "none");
			return;
		}

		context.fact("trip", outing.kind);
		context.fact("trip_stage", switch (outing.stage) {
			case PACKING, OUTBOUND -> "going";
			case THERE -> "there";
			case HOMEWARD, HOME -> "heading_home";
		});
		context.fact("passing_through", outing.passingThrough ? "yes" : "no");
		double dx = outing.targetX() - npc.x;
		double dz = outing.targetZ() - npc.z;
		context.word("direction", Word.name(direction(dx, dz)));
		context.word("distance", Word.name(distance(Math.sqrt(dx * dx + dz * dz))));
	}

	/** A compass direction for a way across the map (north is -z). */
	static String direction(double dx, double dz) {
		String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		int index = (int) Math.round(((angle % 360.0) + 360.0) % 360.0 / 45.0) % 8;
		return names[index];
	}

	/** How far that is to walk, said as a person would. */
	static String distance(double blocks) {
		if (blocks < 100.0) {
			return "not far";
		} else if (blocks < 400.0) {
			return "a few hours' walk";
		} else if (blocks < 1500.0) {
			return "a day or two's walk";
		}

		return "a long way";
	}
}
