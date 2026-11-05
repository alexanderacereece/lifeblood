package lifeblood.dialogue;

import lifeblood.combat.CombatStyle;
import lifeblood.following.FollowDistance;
import lifeblood.ownership.Ownership;
import lifeblood.social.MemoryType;

/**
 * Puts the built-in options into the conversation menus, in steps of 100 so others can slot in between: chatting,
 * gifts, romance, questions, following, and the practical menu (inventory, follow distance, fighting style). Options for
 * systems below dialogue are registered here, since only dialogue knows how a conversation goes on.
 */
public final class DialogueSetup {
	private DialogueSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 100, (npc, player, options) -> options.add(new DialogueOption("How are you?", session -> {
			if (!SocialLimits.tryChat(npc, player)) {
				session.show(NpcDialogue.main(npc, player, NpcDialogue.narration(npc.nickname + " seems talked out for the moment.")));
				return;
			}

			npc.remember(MemoryType.HAD_A_NICE_CHAT, player, 1.0F);
			// Sometimes the chat turns into asking for a new word.
			NpcDialogue word = WordDialogue.maybeAsk(npc, player);
			session.show(word != null ? word : NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "smalltalk", "Fine.")));
		})));
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 200, (npc, player, options) -> {
			if (player.inventory.getCurrentItem() != null) {
				options.add(new DialogueOption("Here, take this.", session -> session.show(NpcDialogue.main(npc, player, NpcGifts.give(npc, player)))));
			}
		});
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 300, (npc, player, options) -> {
			DialogueOption romance = RomanceDialogue.mainOption(npc, player);
			if (romance != null) {
				options.add(romance);
			}
		});
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 400, (npc, player, options) -> options.add(new DialogueOption("Can I ask you something?",
			session -> session.show(NpcDialogue.questions(npc, player, NpcDialogue.say(npc, player, "questions_open", "Sure."))))));
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 500, (npc, player, options) -> {
			if (npc.following.getLeader() == player) {
				options.add(new DialogueOption("You can stop following me.", session -> {
					npc.following.stop();
					session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "follow_stop", "All right.")));
				}));
			} else {
				options.add(new DialogueOption("Come with me.", session -> {
					if (npc.following.willFollow(player)) {
						npc.following.start(player);
						session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "follow_accept", "Lead the way.")));
					} else {
						session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "follow_refuse", "I don't think so.")));
					}
				}));
			}
		});
		DialogueMenus.register(DialogueMenus.Menu.MAIN, 600, (npc, player, options) -> {
			if (npc.following.trusts(player)) {
				options.add(new DialogueOption("About the practical stuff...", session -> session.show(NpcDialogue.practical(npc, player, NpcDialogue.say(npc, player,
					"practical_open", "What do you need?")))));
			}
		});

		DialogueMenus.register(DialogueMenus.Menu.QUESTIONS, 100, (npc, player, options) -> options.add(new DialogueOption("Tell me about yourself.", session -> {
			session.show(NpcDialogue.questions(npc, player, NpcDialogue.say(npc, player, "about_me", "Not much to tell.")));
			session.openProfile();
		})));
		DialogueMenus.register(DialogueMenus.Menu.QUESTIONS, 200, (npc, player, options) -> options.add(new DialogueOption("Where are you from?",
			session -> session.show(NpcDialogue.questions(npc, player, NpcDialogue.say(npc, player, "where_from", "Around here."))))));
		DialogueMenus.register(DialogueMenus.Menu.QUESTIONS, 300, (npc, player, options) -> options.add(new DialogueOption("What do you think of...",
			session -> session.show(NpcDialogue.askAbout(npc, player)))));
		DialogueMenus.register(DialogueMenus.Menu.QUESTIONS, 400, (npc, player, options) -> options.add(new DialogueOption("What words do you know?",
			session -> session.show(NpcDialogue.questions(npc, player, WordDialogue.listWords(npc, player))))));
		DialogueMenus.register(DialogueMenus.Menu.QUESTIONS, 500, (npc, player, options) -> options.add(new DialogueOption("Tell me about your family.",
			session -> session.show(NpcDialogue.questions(npc, player, RomanceDialogue.describeFamily(npc, player))))));

		// Only family who share what it owns (spouse, parents, children) can go through its things.
		DialogueMenus.register(DialogueMenus.Menu.PRACTICAL, 100, (npc, player, options) -> {
			if (Ownership.shares(npc, player)) {
				options.add(new DialogueOption("Let me see what you're carrying.", DialogueSession::openInventory));
			}
		});
		DialogueMenus.register(DialogueMenus.Menu.PRACTICAL, 200, (npc, player, options) -> {
			if (npc.following.getLeader() == player) {
				options.add(new DialogueOption("Follow distance: " + npc.following.getDistance().displayName(), session -> {
					FollowDistance distance = npc.following.getDistance().next();
					npc.following.setDistance(distance);
					session.show(NpcDialogue.practical(npc, player, switch (distance) {
						case SHORT -> "I'll stay close.";
						case MEDIUM -> "I'll give you some room.";
						case LONG -> "I'll keep my distance.";
					}));
				}));
			}
		});
		DialogueMenus.register(DialogueMenus.Menu.PRACTICAL, 300, (npc, player, options) -> options.add(new DialogueOption("How you fight: "
			+ npc.getCombatStyle().displayName(), session -> {
			CombatStyle style = npc.getCombatStyle().next();
			npc.setCombatStyle(style);
			session.show(NpcDialogue.practical(npc, player, switch (style) {
				case AUTO -> "I'll fight however my gear suits.";
				case MELEE -> "I'll fight up close.";
				case RANGED -> "I'll fight from a distance.";
			}));
		})));
	}
}
