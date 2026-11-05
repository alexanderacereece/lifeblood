package lifeblood.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.entity.player.Player;
import lifeblood.family.AgeStage;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.social.Memory;
import lifeblood.social.MemoryType;
import lifeblood.social.Opinions;
import lifeblood.speech.Word;
import lifeblood.words.WordBook;
import lifeblood.words.WordCategory;
import org.jetbrains.annotations.Nullable;

/**
 * NPCs asking the player for words: now and then, chatting with an NPC ("How are you?") turns into "What would
 * you call a food you'd eat for breakfast?". It counts as one of the five chats a minute. The player types a word (and its plural, for nouns), then says whether it's just for this
 * NPC or something anyone could say. See docs/speech.md, "Word book".
 *
 * An NPC that likes you at least a little (20+) asks at most once a day, about a quarter of the times you chat
 * (more if curious or a chatterbox, less if taciturn), until it knows 15 words. With /lb debug limits off it asks half
 * the time, any day, for testing.
 */
public final class WordDialogue {
	private static final float ASK_CHANCE = 0.25F;
	private static final int MAX_WORDS_PER_NPC = 15;
	/** How many of its words an NPC lists when asked. */
	private static final int LISTED_WORDS = 6;

	private WordDialogue() {
	}

	/** A chat that turns into asking for a word, if the NPC feels like asking now. */
	@Nullable
	static NpcDialogue maybeAsk(EntityNPC npc, Player player) {
		// Babies can't ask for words yet; kids ask less often.
		if (npc.life.getStage() == AgeStage.BABY || npc.life.isChild() && npc.getRandom().nextFloat() < 0.5F || Opinions.of(npc, player) < 20.0F) {
			return null;
		}

		WordBook book = WordBook.get(npc.world);
		long today = npc.world.getWorldTime() / Memory.TICKS_PER_DAY;
		float chance = ASK_CHANCE * (npc.personality.has(Quirk.CURIOUS) || npc.personality.has(Quirk.CHATTERBOX) ? 2.0F : 1.0F)
			* (npc.personality.has(Quirk.TACITURN) ? 0.5F : 1.0F);
		boolean limited = SocialLimits.isEnabled();
		if (limited && book.lastAsked(npc.getUuid(), player.uuid) == today || npc.getRandom().nextFloat() >= (limited ? chance : 0.5F)
			|| book.knownBy(npc.getUuid()).size() >= MAX_WORDS_PER_NPC) {
			return null;
		}

		book.setLastAsked(npc.getUuid(), player.uuid, today);
		return ask(npc, player, pickCategory(npc, player, book, npc.getRandom()));
	}

	/** Anything but a second nickname from the same player. */
	private static WordCategory pickCategory(EntityNPC npc, Player player, WordBook book, Random random) {
		List<WordCategory> options = new ArrayList<>(List.of(WordCategory.values()));
		if (book.pick(npc.getUuid(), WordCategory.NICKNAME, player.uuid, random) != null) {
			options.remove(WordCategory.NICKNAME);
		}

		return options.get(random.nextInt(options.size()));
	}

	static NpcDialogue ask(EntityNPC npc, Player player, WordCategory category) {
		String prompt = category.prompts.get(npc.getRandom().nextInt(category.prompts.size()));
		String line = npc.voice.lineOr("ask_word", player, prompt, context -> context.fact("word_prompt", prompt));
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Let me think... (type a word)", session -> session.askForText(
			new TextRequest(prompt, category.hint, category.prefix, "", null),
			text -> afterSingular(npc, player, category, category.noun ? withoutArticle(clean(text)) : clean(text), session),
			null,
			() -> session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "never_mind", "Oh. All right."))))));
		options.add(new DialogueOption("I don't know, sorry.", session -> session.show(NpcDialogue.main(npc, player,
			NpcDialogue.say(npc, player, "never_mind", "Oh. All right.")))));
		return new NpcDialogue(line, options);
	}

	/** Nouns get asked for their plural too (or whether they can be counted at all). */
	private static void afterSingular(EntityNPC npc, Player player, WordCategory category, String singular, DialogueSession session) {
		if (singular.isEmpty()) {
			session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "never_mind", "Oh. All right.")));
			return;
		}

		if (!category.noun) {
			// Lines add their own punctuation: "Yikes!" is stored as "Yikes".
			String plain = singular.replaceAll("[!.?,]+$", "").strip();
			afterWord(npc, player, category, Word.name(plain.isEmpty() ? singular : plain), session);
			return;
		}

		String hint = category.canBeMass ? "Or \"Uncountable\" if you'd say \"some " + singular + "\", like bread." : "";
		session.askForText(new TextRequest("And if there's more than one " + singular + "?", hint, "two", Word.plural(singular),
				category.canBeMass ? "Uncountable" : null),
			plural -> afterWord(npc, player, category, new Word(singular, withoutArticle(clean(plural)).isEmpty() ? Word.plural(singular)
				: withoutArticle(clean(plural)), Word.Kind.ONE), session),
			() -> afterWord(npc, player, category, Word.mass(singular), session),
			() -> session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "never_mind", "Oh. All right."))));
	}

	/** Just for them, or anyone's? (A nickname is always just theirs.) */
	private static void afterWord(EntityNPC npc, Player player, WordCategory category, Word word, DialogueSession session) {
		if (category.alwaysPersonal()) {
			learn(npc, player, category, word, true, session);
			return;
		}

		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Just for you.", s -> learn(npc, player, category, word, true, s)));
		options.add(new DialogueOption("Anyone could say it.", s -> learn(npc, player, category, word, false, s)));
		session.show(new NpcDialogue(npc.voice.lineOr("word_whose", player, "Is that just for me, or something anyone could say?",
			context -> context.word("word", word)), options));
	}

	private static void learn(EntityNPC npc, Player player, WordCategory category, Word word, boolean personal, DialogueSession session) {
		WordBook.get(npc.world).teach(npc.getUuid(), category, word, personal, player.uuid, player.username);
		npc.remember(MemoryType.TAUGHT_ME_A_WORD, player, 1.0F);
		String line = npc.voice.lineOr("word_learned", player, word.singular() + ". I like it!", context -> context.word("word", word)
			.fact("word_category", category.id()).fact("personal", personal ? "yes" : "no"));
		session.show(NpcDialogue.main(npc, player, line));
	}

	/** "a squidbug" is just "squidbug": lines add their own "a", "the" or "some". */
	private static String withoutArticle(String text) {
		return text.replaceFirst("(?i)^(a|an|the|some|one|two)\\s+", "").strip();
	}

	/** Words go into speech, so anything the speech files treat as syntax is taken out. */
	private static String clean(String text) {
		return text.replaceAll("[\\[\\]{}@;=]", "").strip();
	}

	/** "I know 'blorp' (you taught me) and 'zing' (from Tub)." */
	static String listWords(EntityNPC npc, Player player) {
		List<WordBook.TaughtWord> words = WordBook.get(npc.world).knownBy(npc.getUuid());
		if (words.isEmpty()) {
			return NpcDialogue.say(npc, player, "no_words", "I don't know any special words. Teach me one sometime?");
		}

		// The newest few; the word book has the rest.
		words.sort((a, b) -> Integer.compare(b.id, a.id));
		int more = Math.max(0, words.size() - LISTED_WORDS);
		List<String> parts = new ArrayList<>();
		for (WordBook.TaughtWord word : words.subList(0, Math.min(LISTED_WORDS, words.size()))) {
			String from = word.knownBy.get(npc.getUuid());
			String source = word.teacher.equals(player.uuid) && from.equals(player.username) ? "you taught me" : "from " + from;
			parts.add("'" + word.word.singular() + "' (" + source + ")");
		}

		String list = parts.size() == 1 ? parts.get(0) : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
		return more > 0 ? "Lately I've learned " + list + ". And " + more + " more, if you check your word book." : "I know " + list + ".";
	}
}
