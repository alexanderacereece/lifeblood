package npclaude.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;
import npclaude.personality.Quirk;
import npclaude.social.Memory;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import npclaude.speech.Word;
import npclaude.words.WordBook;
import npclaude.words.WordCategory;
import org.jetbrains.annotations.Nullable;

/**
 * NPCs asking the player for words: now and then a conversation opens with "What would you call something you'd
 * eat for breakfast?". The player types a word (and its plural, for nouns), then says whether it's just for this
 * NPC or something anyone could say. See docs/personality.md, section 9.
 *
 * An NPC that likes you at least a little (20+) asks at most once a day, about a quarter of the time you talk
 * (more if curious or a chatterbox, less if taciturn), until it knows 15 words. With /npc limits off it asks every
 * time, for testing.
 */
public final class WordDialogue {
	private static final float ASK_CHANCE = 0.25F;
	private static final int MAX_WORDS_PER_NPC = 15;

	private WordDialogue() {
	}

	/** The conversation, opening with a question for a word, if the NPC feels like asking now. */
	@Nullable
	static NpcDialogue maybeAsk(EntityNPC npc, Player player) {
		if (npc.isChild() && npc.getRandom().nextFloat() < 0.5F || Opinions.of(npc, player) < 20.0F) {
			return null;
		}

		WordBook book = WordBook.get(npc.world);
		long today = npc.world.getWorldTime() / Memory.TICKS_PER_DAY;
		float chance = ASK_CHANCE * (npc.personality.has(Quirk.CURIOUS) || npc.personality.has(Quirk.CHATTERBOX) ? 2.0F : 1.0F)
			* (npc.personality.has(Quirk.TACITURN) ? 0.5F : 1.0F);
		if (SocialLimits.isEnabled() && (book.lastAsked(npc.getUuid(), player.uuid) == today || npc.getRandom().nextFloat() >= chance)
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
		options.add(new DialogueOption("Let me think... (type a word)", session -> session.askForText(prompt, "", null,
			text -> afterSingular(npc, player, category, clean(text), session),
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

		session.askForText("And if there's more than one " + singular + "?", Word.plural(singular), category.canBeMass ? "Can't count it" : null,
			plural -> afterWord(npc, player, category, new Word(singular, clean(plural).isEmpty() ? Word.plural(singular) : clean(plural), Word.Kind.ONE), session),
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

		List<String> parts = new ArrayList<>();
		for (WordBook.TaughtWord word : words) {
			String from = word.knownBy.get(npc.getUuid());
			String source = word.teacher.equals(player.uuid) && from.equals(player.username) ? "you taught me" : "from " + from;
			parts.add("'" + word.word.singular() + "' (" + source + ")");
		}

		return "I know " + (parts.size() == 1 ? parts.get(0) : String.join(", ", parts.subList(0, parts.size() - 1)) + " and "
			+ parts.get(parts.size() - 1)) + ".";
	}
}
