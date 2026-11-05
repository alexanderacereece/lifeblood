package lifeblood.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;
import lifeblood.family.Kinship;
import lifeblood.family.Romance;
import lifeblood.people.PeopleRegistry;
import lifeblood.social.Opinions;
import lifeblood.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC says when a conversation reaches this point, and the replies the player can pick.
 *
 * The main menu: chat, give the held item, questions (about them, what they think of someone), ask them to come
 * along (or to stop), and, for people who trust you, practical matters (inventory, follow distance, how to fight).
 * The options themselves are registered in {@link DialogueMenus}, so other systems can add their own.
 */
public record NpcDialogue(String line, List<DialogueOption> options) {
	/** People they'll offer an opinion on: everyone within this distance, plus every player in the world. */
	private static final double ASK_ABOUT_RANGE = 32.0;
	private static final int MAX_PEOPLE = 7;
	/** Marks a line as narration rather than speech: shown without quotes. */
	public static final char NARRATION = '';

	/** Text for the dialogue box that isn't the NPC talking, like "That's probably enough for today." */
	public static String narration(String text) {
		return NARRATION + text;
	}

	public static NpcDialogue main(EntityNPC npc, Player player) {
		NpcDialogue request = RomanceDialogue.request(npc, player);
		if (request != null) {
			return request;
		}

		return main(npc, player, say(npc, player, "dialogue_open", "..."));
	}

	/** The main menu, after this line from the NPC. */
	public static NpcDialogue main(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = DialogueMenus.options(DialogueMenus.Menu.MAIN, npc, player);
		options.add(new DialogueOption("Goodbye.", session -> {
			String farewell = npc.voice.line("farewell", player, null);
			if (farewell != null) {
				npc.voice.sayAloud(farewell);
			}

			session.close();
		}));
		return new NpcDialogue(line, options);
	}

	/** Questions about them, the people they know, their family and their words. */
	public static NpcDialogue questions(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = DialogueMenus.options(DialogueMenus.Menu.QUESTIONS, npc, player);
		options.add(new DialogueOption("That's all.", session -> session.show(main(npc, player, say(npc, player, "never_mind", "All right.")))));
		return new NpcDialogue(line, options);
	}

	/** Who they can be asked about: you first, then the people nearby and the other players. */
	static NpcDialogue askAbout(EntityNPC npc, Player player) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Me?", session -> session.show(questions(npc, player, say(npc, player, "opinion_of_you", "You're all right.")))));
		List<Entity> people = new ArrayList<>();
		for (Entity entity : npc.world.entities) {
			if (entity == npc || entity == player) {
				continue;
			}

			boolean person = entity instanceof EntityNPC other && other.isAlive() && other.distanceTo(npc) <= ASK_ABOUT_RANGE
				|| entity instanceof Player;
			if (person) {
				people.add(entity);
			}
		}

		people.sort((a, b) -> Float.compare(a.distanceTo(npc), b.distanceTo(npc)));
		for (Entity other : people.subList(0, Math.min(MAX_PEOPLE, people.size()))) {
			String name = Opinions.nameOf(other);
			options.add(new DialogueOption(name + "?", session -> session.show(questions(npc, player, opinionOf(npc, player, other)))));
		}

		options.add(new DialogueOption("Never mind.", session -> session.show(questions(npc, player, say(npc, player, "never_mind", "Oh. All right.")))));
		return new NpcDialogue(say(npc, player, "ask_about_who", "Who?"), options);
	}

	private static String opinionOf(EntityNPC npc, Player player, Entity other) {
		Opinions.Label label = Opinions.Label.of(Opinions.of(npc, other));
		String line = npc.voice.line("opinion_of", player, context -> context.word("other", Word.name(Opinions.nameOf(other)))
			.fact("about", label.id())
			.fact("other_type", other instanceof Player ? "player" : "npc"));
		line = line != null ? line : "I don't really know " + Opinions.nameOf(other) + ".";
		// Family comes first: "Tom's my brother. Ugh."
		UUID id = Opinions.idOf(other);
		Kinship kin = id != null ? Kinship.of(PeopleRegistry.get(npc.world), npc.getUuid(), id) : null;
		if (kin != null) {
			line = Opinions.nameOf(other) + "'s my " + kin.label(Romance.genderOf(other)) + ". " + line;
		}

		return line;
	}

	/** Practical matters (inventory, follow distance, how to fight): only for people the NPC trusts. */
	public static NpcDialogue practical(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = DialogueMenus.options(DialogueMenus.Menu.PRACTICAL, npc, player);
		options.add(new DialogueOption("That's all.", session -> session.show(main(npc, player, say(npc, player, "never_mind", "All right.")))));
		return new NpcDialogue(line, options);
	}

	/** A line from the speech files for this symbol, or the fallback if there's none. */
	public static String say(EntityNPC npc, Player player, String symbol, String fallback) {
		return orDefault(npc.voice.line(symbol, player, null), fallback);
	}

	private static String orDefault(@Nullable String line, String fallback) {
		return line != null ? line : fallback;
	}
}
