package lifeblood.gossip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.social.Opinions;
import lifeblood.speech.ChatTopic;
import lifeblood.speech.SpeechContext;
import lifeblood.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * Passing news on: the "news" chat topic (fresh news the listener hasn't heard), the "old_news" one ("remember
 * when...", for big news that's no longer fresh), and telling a player who asks. Nobody tells people news about
 * themselves, and nobody spreads shameful news about their own friends. Liars dress stories up now and then, and an
 * exaggerated story stays exaggerated as it travels.
 *
 * Lines get the facts news (its kind: "wedding"), exaggerated, firsthand (yes if they saw it or it's family),
 * subject_opinion and other_opinion (what the speaker thinks of those involved), and the words subject, other, baby
 * and source (who told them).
 */
public final class GossipTopics {
	/** An opinion this high keeps someone from spreading shameful news about a person. */
	private static final float FRIEND = 50.0F;

	private GossipTopics() {
	}

	/** Fresh news the listener hasn't heard: nosy people bring it up most. */
	public static final ChatTopic NEWS = new Topic("news", true);
	/** Big news that's no longer fresh, remembered together now and then: the sentimental bring it up most. */
	public static final ChatTopic OLD_NEWS = new Topic("old_news", false);

	private record Topic(String id, boolean fresh) implements ChatTopic {
		@Override
		public float weight(EntityNPC speaker, EntityNPC listener) {
			float weight = this.fresh ? 1.5F : 0.25F;
			if (this.fresh && speaker.personality.has(Quirk.NOSY)) {
				weight *= 3.0F;
			}

			if (!this.fresh && speaker.personality.has(Quirk.SENTIMENTAL)) {
				weight *= 3.0F;
			}

			if (speaker.personality.has(Quirk.TACITURN)) {
				weight *= 0.4F;
			}

			return weight;
		}

		@Override
		@Nullable
		public ChatTopic.Exchange start(EntityNPC speaker, EntityNPC listener) {
			News piece = this.fresh ? freshestFor(speaker, listener.getUuid()) : rememberedFor(speaker, listener.getUuid());
			if (piece == null) {
				return null;
			}

			GossipRegister register = GossipRegister.get(speaker.world);
			GossipRegister.Heard heard = register.heard(piece, speaker.getUuid());
			boolean exaggerated = heard != null && heard.exaggerated() || Gossip.embellishes(speaker);
			return new ChatTopic.Exchange() {
				@Override
				public void opening(SpeechContext context) {
					describe(context, speaker, piece, heard, exaggerated, true);
				}

				@Override
				public void reply(SpeechContext context) {
					describe(context, listener, piece, null, exaggerated, false);
				}

				@Override
				public void said() {
					if (register.hear(piece, listener.getUuid(), speaker.getUuid(), speaker.nickname, exaggerated)) {
						Gossip.heardFromSomeone(listener, piece, exaggerated);
					}
				}
			};
		}
	}

	/**
	 * What an NPC says when a player asks for news: the extras for its "news_tell" line, with the player now knowing
	 * it, or null if it has nothing fresh to tell them.
	 */
	@Nullable
	public static Consumer<SpeechContext> tellPlayer(EntityNPC npc, Player player) {
		if (player.uuid == null) {
			return null;
		}

		News piece = freshestFor(npc, player.uuid);
		if (piece == null) {
			return null;
		}

		GossipRegister register = GossipRegister.get(npc.world);
		GossipRegister.Heard heard = register.heard(piece, npc.getUuid());
		boolean exaggerated = heard != null && heard.exaggerated() || Gossip.embellishes(npc);
		register.hear(piece, player.uuid, npc.getUuid(), npc.nickname, exaggerated);
		return context -> describe(context, npc, piece, heard, exaggerated, true);
	}

	/** The biggest, newest fresh news the teller knows and the listener doesn't, that the teller would pass on. */
	@Nullable
	static News freshestFor(EntityNPC teller, UUID listener) {
		GossipRegister register = GossipRegister.get(teller.world);
		long now = teller.world.getWorldTime();
		long fresh = GossipRegister.freshTicks(teller.world);
		News best = null;
		for (News piece : register.all()) {
			if (now - piece.when() >= fresh || !wouldTell(teller, listener, piece, register)) {
				continue;
			}

			if (best == null || piece.kind().importance > best.kind().importance
				|| piece.kind().importance == best.kind().importance && piece.when() > best.when()) {
				best = piece;
			}
		}

		return best;
	}

	/** Big news from a while back that the teller remembers, picked at random. */
	@Nullable
	private static News rememberedFor(EntityNPC teller, UUID listener) {
		GossipRegister register = GossipRegister.get(teller.world);
		long now = teller.world.getWorldTime();
		long fresh = GossipRegister.freshTicks(teller.world);
		List<News> remembered = new ArrayList<>();
		for (News piece : register.all()) {
			if (piece.kind().remembered && now - piece.when() >= fresh && register.heard(piece, teller.getUuid()) != null
				&& !piece.involves(teller.getUuid()) && !piece.involves(listener)) {
				remembered.add(piece);
			}
		}

		return remembered.isEmpty() ? null : remembered.get(teller.getRandom().nextInt(remembered.size()));
	}

	/** Knows it, the listener doesn't, it's about neither of them, and it isn't shameful news about the teller's friend. */
	private static boolean wouldTell(EntityNPC teller, UUID listener, News piece, GossipRegister register) {
		return register.heard(piece, teller.getUuid()) != null && register.heard(piece, listener) == null && !piece.involves(teller.getUuid())
			&& !piece.involves(listener) && !(piece.kind().shameful && Gossip.regard(teller, piece.subject()) >= FRIEND);
	}

	/**
	 * The facts and words a line about this news can use, from this NPC's point of view. How they found out is only
	 * known for the one telling it.
	 */
	private static void describe(SpeechContext context, EntityNPC npc, News piece, @Nullable GossipRegister.Heard heard, boolean exaggerated,
		boolean teller) {
		context.fact("news", piece.kind().id());
		context.fact("exaggerated", exaggerated ? "yes" : "no");
		if (teller) {
			context.fact("firsthand", heard == null || heard.firsthand() ? "yes" : "no");
		}

		context.fact("subject_opinion", Opinions.Label.of(Gossip.regard(npc, piece.subject())).id());
		context.word("subject", Word.name(piece.subjectName()));
		if (piece.other() != null) {
			context.fact("other_opinion", Opinions.Label.of(Gossip.regard(npc, piece.other())).id());
			context.word("other", Word.name(piece.otherName()));
		}

		if (!piece.babyName().isEmpty()) {
			context.word("baby", Word.name(piece.babyName()));
		}

		if (teller && heard != null && !heard.firsthand()) {
			context.word("source", Word.name(heard.fromName()));
		}
	}
}
