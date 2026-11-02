package npclaude.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import npclaude.combat.CombatStyle;
import npclaude.entity.EntityNPC;
import npclaude.entity.FollowDistance;
import npclaude.family.FamilyRegistry;
import npclaude.family.Kinship;
import npclaude.family.Romance;
import npclaude.item.NpcItems;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import npclaude.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC says when a conversation reaches this point, and the replies the player can pick.
 *
 * The main menu: chat, give the held item, questions (about them, what they think of someone), ask them to come
 * along (or to stop), and, for people who trust you, practical matters (inventory, follow distance, how to fight).
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

	static NpcDialogue main(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("How are you?", session -> {
			if (!SocialLimits.tryChat(npc, player)) {
				session.show(main(npc, player, narration(npc.nickname + " seems talked out for the moment.")));
				return;
			}

			npc.remember(MemoryType.HAD_A_NICE_CHAT, player, 1.0F);
			// Sometimes the chat turns into asking for a new word.
			NpcDialogue word = WordDialogue.maybeAsk(npc, player);
			session.show(word != null ? word : main(npc, player, say(npc, player, "smalltalk", "Fine.")));
		}));
		if (player.inventory.getCurrentItem() != null) {
			options.add(new DialogueOption("Here, take this.", session -> {
				if (SocialLimits.giftedToday(npc, player) && !NpcItems.isGuardianAmulet(player.inventory.getCurrentItem())) {
					session.show(main(npc, player, narration("You've already given " + npc.nickname + " something today. That's probably enough for now.")));
					return;
				}

				session.show(main(npc, player, NpcGifts.give(npc, player)));
			}));
		}

		DialogueOption romance = RomanceDialogue.mainOption(npc, player);
		if (romance != null) {
			options.add(romance);
		}

		options.add(new DialogueOption("Can I ask you something?", session -> session.show(questions(npc, player, say(npc, player,
			"questions_open", "Sure.")))));
		if (npc.following.getLeader() == player) {
			options.add(new DialogueOption("You can stop following me.", session -> {
				npc.following.stop();
				session.show(main(npc, player, say(npc, player, "follow_stop", "All right.")));
			}));
		} else {
			options.add(new DialogueOption("Come with me.", session -> {
				if (npc.following.willFollow(player)) {
					npc.following.start(player);
					session.show(main(npc, player, say(npc, player, "follow_accept", "Lead the way.")));
				} else {
					session.show(main(npc, player, say(npc, player, "follow_refuse", "I don't think so.")));
				}
			}));
		}

		if (npc.following.trusts(player)) {
			options.add(new DialogueOption("About the practical stuff...", session -> session.show(practical(npc, player, say(npc, player,
				"practical_open", "What do you need?")))));
		}

		options.add(new DialogueOption("Goodbye.", session -> {
			String farewell = npc.voice.line("farewell", player, null);
			if (farewell != null) {
				npc.voice.sayAloud(farewell);
			}

			session.close();
		}));
		return new NpcDialogue(line, options);
	}

	/** Questions about them and the people they know. Family goes here too, once there is family. */
	private static NpcDialogue questions(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Tell me about yourself.", session -> {
			session.show(questions(npc, player, say(npc, player, "about_me", "Not much to tell.")));
			session.openProfile();
		}));
		options.add(new DialogueOption("Where are you from?", session -> session.show(questions(npc, player, say(npc, player, "where_from", "Around here.")))));
		options.add(new DialogueOption("What do you think of...", session -> session.show(askAbout(npc, player))));
		options.add(new DialogueOption("What words do you know?", session -> session.show(questions(npc, player, WordDialogue.listWords(npc, player)))));
		options.add(new DialogueOption("Tell me about your family.", session -> session.show(questions(npc, player, RomanceDialogue.describeFamily(npc, player)))));
		options.add(new DialogueOption("That's all.", session -> session.show(main(npc, player, say(npc, player, "never_mind", "All right.")))));
		return new NpcDialogue(line, options);
	}

	/** Who they can be asked about: you first, then the people nearby and the other players. */
	private static NpcDialogue askAbout(EntityNPC npc, Player player) {
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
		Kinship kin = id != null ? Kinship.of(FamilyRegistry.get(npc.world), npc.getUuid(), id) : null;
		if (kin != null) {
			line = Opinions.nameOf(other) + "'s my " + kin.label(Romance.genderOf(other)) + ". " + line;
		}

		return line;
	}

	/** Inventory, follow distance and combat style: only for people the NPC trusts. */
	private static NpcDialogue practical(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Let me see what you're carrying.", DialogueSession::openInventory));
		if (npc.following.getLeader() == player) {
			options.add(new DialogueOption("Follow distance: " + npc.following.getDistance().displayName(), session -> {
				FollowDistance distance = npc.following.getDistance().next();
				npc.following.setDistance(distance);
				session.show(practical(npc, player, switch (distance) {
					case SHORT -> "I'll stay close.";
					case MEDIUM -> "I'll give you some room.";
					case LONG -> "I'll keep my distance.";
				}));
			}));
		}

		options.add(new DialogueOption("How you fight: " + npc.getCombatStyle().displayName(), session -> {
			CombatStyle style = npc.getCombatStyle().next();
			npc.setCombatStyle(style);
			session.show(practical(npc, player, switch (style) {
				case AUTO -> "I'll fight however my gear suits.";
				case MELEE -> "I'll fight up close.";
				case RANGED -> "I'll fight from a distance.";
			}));
		}));
		options.add(new DialogueOption("That's all.", session -> session.show(main(npc, player, say(npc, player, "never_mind", "All right.")))));
		return new NpcDialogue(line, options);
	}

	static String say(EntityNPC npc, Player player, String symbol, String fallback) {
		return orDefault(npc.voice.line(symbol, player, null), fallback);
	}

	private static String orDefault(@Nullable String line, String fallback) {
		return line != null ? line : fallback;
	}
}
