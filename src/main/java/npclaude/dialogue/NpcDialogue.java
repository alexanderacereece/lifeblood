package npclaude.dialogue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import npclaude.combat.CombatStyle;
import npclaude.entity.EntityNPC;
import npclaude.entity.FollowDistance;
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

	public static NpcDialogue main(EntityNPC npc, Player player) {
		return main(npc, player, say(npc, player, "dialogue_open", "..."));
	}

	private static NpcDialogue main(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("How are you?", session -> {
			npc.remember(MemoryType.HAD_A_NICE_CHAT, player, 1.0F);
			session.show(main(npc, player, say(npc, player, "smalltalk", "Fine.")));
		}));
		if (player.inventory.getCurrentItem() != null) {
			options.add(new DialogueOption("Here, take this.", session -> session.show(main(npc, player, NpcGifts.give(npc, player)))));
		}

		options.add(new DialogueOption("Can I ask you something?", session -> session.show(questions(npc, player, say(npc, player,
			"questions_open", "Sure.")))));
		if (npc.getLeader() == player) {
			options.add(new DialogueOption("You can stop following me.", session -> {
				npc.stopFollowing();
				session.show(main(npc, player, say(npc, player, "follow_stop", "All right.")));
			}));
		} else {
			options.add(new DialogueOption("Come with me.", session -> {
				if (npc.willFollow(player)) {
					npc.startFollowing(player);
					session.show(main(npc, player, say(npc, player, "follow_accept", "Lead the way.")));
				} else {
					session.show(main(npc, player, say(npc, player, "follow_refuse", "I don't think so.")));
				}
			}));
		}

		if (npc.trusts(player)) {
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
		options.add(new DialogueOption("What do you think of...", session -> session.show(askAbout(npc, player))));
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
		return line != null ? line : "I don't really know " + Opinions.nameOf(other) + ".";
	}

	/** Inventory, follow distance and combat style: only for people the NPC trusts. */
	private static NpcDialogue practical(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Let me see what you're carrying.", DialogueSession::openInventory));
		if (npc.getLeader() == player) {
			options.add(new DialogueOption("Follow distance: " + npc.getFollowDistance().displayName(), session -> {
				FollowDistance distance = npc.getFollowDistance().next();
				npc.setFollowDistance(distance);
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

	private static String say(EntityNPC npc, Player player, String symbol, String fallback) {
		return orDefault(npc.voice.line(symbol, player, null), fallback);
	}

	private static String orDefault(@Nullable String line, String fallback) {
		return line != null ? line : fallback;
	}
}
