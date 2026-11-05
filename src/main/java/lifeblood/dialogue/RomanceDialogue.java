package lifeblood.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;
import lifeblood.family.Kinship;
import lifeblood.family.Romance;
import lifeblood.people.PeopleRegistry;
import lifeblood.social.MemoryType;
import lifeblood.social.Opinions;
import lifeblood.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * The player's side of romance and family in conversation: asking someone out, proposing, the wedding, breaking up,
 * answering when the NPC is the one asking, and asking about their family. The NPC decides by the same rules it
 * uses with other NPCs (see {@link Romance}); the player is free to decide.
 */
public final class RomanceDialogue {
	private RomanceDialogue() {
	}

	/** The romance option for the main menu, if one fits right now. */
	@Nullable
	static DialogueOption mainOption(EntityNPC npc, Player player) {
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		PeopleRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		if (partnership != null && partnership.other(npc.getUuid()).equals(player.uuid)) {
			return new DialogueOption("About us...", session -> session.show(aboutUs(npc, player, NpcDialogue.say(npc, player, "about_us", "Yes?"))));
		}

		if (Opinions.of(npc, player) >= Opinions.FRIEND_THRESHOLD && Romance.couldFallFor(npc, player)) {
			return new DialogueOption("Would you go out with me?", session -> session.show(NpcDialogue.main(npc, player, askOut(npc, player))));
		}

		return null;
	}

	/** When the NPC has something to ask: the conversation opens with it. */
	@Nullable
	static NpcDialogue request(EntityNPC npc, Player player) {
		Romance.Request request = npc.social.getRomanceRequest(player);
		if (request == null) {
			return null;
		}

		String symbol = request == Romance.Request.ASK_OUT ? "ask_player_out" : "propose_to_player";
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Yes!", session -> {
			npc.social.setRomanceRequest(null, null);
			PeopleRegistry family = PeopleRegistry.get(npc.world);
			Romance.register(player);
			if (request == Romance.Request.ASK_OUT) {
				if (family.partnershipOf(player.uuid) != null || family.partnershipOf(npc.getUuid()) != null) {
					session.show(NpcDialogue.main(npc, player, NpcDialogue.narration("One of you is already with someone else.")));
					return;
				}

				Romance.startDating(npc, player, family);
				session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "player_said_yes", "Really? Oh, that's wonderful!")));
			} else {
				Romance.getEngaged(npc, player, family);
				session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "player_accepted_proposal", "We're getting married!")));
			}
		}));
		options.add(new DialogueOption("I'm sorry. No.", session -> {
			npc.social.setRomanceRequest(null, null);
			npc.remember(MemoryType.TURNED_ME_DOWN, player, request == Romance.Request.ASK_OUT ? 1.0F : 1.5F);
			session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "player_said_no", "Oh. I see.")));
		}));
		options.add(new DialogueOption("Can I think about it?", session -> session.show(NpcDialogue.main(npc, player,
			NpcDialogue.say(npc, player, "player_wants_time", "Take your time.")))));
		return new NpcDialogue(NpcDialogue.say(npc, player, symbol, "Can I ask you something?"), options);
	}

	/** The player asks the NPC out. */
	private static String askOut(EntityNPC npc, Player player) {
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		Romance.register(player);
		UUID theirPartner = family.partnerOf(npc.getUuid());
		UUID yourPartner = family.partnerOf(player.uuid);
		if (theirPartner != null) {
			return npc.voice.lineOr("ask_out_taken", player, "I'm seeing someone, actually.", context -> context.word("partner", Word.name(family.nameOf(theirPartner))));
		}

		if (yourPartner != null) {
			return npc.voice.lineOr("ask_out_you_are_taken", player, "Aren't you with someone already?", context -> context.word("partner", Word.name(family.nameOf(yourPartner))));
		}

		if (Opinions.of(npc, player) < Romance.DATING_OPINION) {
			npc.social.setRomanceRequest(null, null);
			return NpcDialogue.say(npc, player, "ask_out_not_yet", "I like you, but... not like that. Not yet, anyway.");
		}

		npc.social.setRomanceRequest(null, null);
		Romance.startDating(npc, player, family);
		return NpcDialogue.say(npc, player, "ask_out_yes", "Yes! I'd love to.");
	}

	private static NpcDialogue aboutUs(EntityNPC npc, Player player, String line) {
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		PeopleRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		List<DialogueOption> options = new ArrayList<>();
		if (partnership == null) {
			return NpcDialogue.main(npc, player, line);
		}

		long now = npc.world.getWorldTime();
		float opinion = Opinions.of(npc, player);
		switch (partnership.stage) {
			case DATING -> {
				options.add(new DialogueOption("Will you marry me?", session -> {
					if (!Romance.datedLongEnough(partnership, npc.world, now) || opinion < Romance.ENGAGED_OPINION) {
						session.show(aboutUs(npc, player, NpcDialogue.say(npc, player, "proposal_too_soon", "It's... a little soon, isn't it?")));
						return;
					}

					npc.social.setRomanceRequest(null, null);
					Romance.getEngaged(npc, player, family);
					session.show(aboutUs(npc, player, NpcDialogue.say(npc, player, "proposal_yes", "Yes! Of course, yes!")));
				}));
				options.add(new DialogueOption("I think we should break up.", session -> endIt(npc, player, session, "break_up_heard")));
			}
			case ENGAGED -> {
				options.add(new DialogueOption("Let's get married.", session -> {
					if (!Romance.engagedLongEnough(partnership, npc.world, now) || opinion < Romance.ENGAGED_OPINION) {
						session.show(aboutUs(npc, player, NpcDialogue.say(npc, player, "wedding_too_soon", "Soon. Let's not rush it.")));
						return;
					}

					Romance.wed(npc, player, family);
					session.show(aboutUs(npc, player, NpcDialogue.say(npc, player, "wedding_day", "I can't believe it. We're married!")));
				}));
				options.add(new DialogueOption("I want to call off the engagement.", session -> endIt(npc, player, session, "break_up_heard")));
			}
			case MARRIED -> options.add(new DialogueOption("I want a divorce.", session -> endIt(npc, player, session, "divorce_heard")));
		}

		options.add(new DialogueOption("Never mind.", session -> session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, "never_mind", "All right.")))));
		return new NpcDialogue(line, options);
	}

	private static void endIt(EntityNPC npc, Player player, DialogueSession session, String symbol) {
		PeopleRegistry.get(npc.world).breakUp(npc.getUuid());
		npc.remember(MemoryType.BROKE_UP_WITH_ME, player, 1.0F);
		npc.social.setRomanceRequest(null, null);
		session.show(NpcDialogue.main(npc, player, NpcDialogue.say(npc, player, symbol, "...I see.")));
	}

	/** "My wife is Ruth. My parents are Ed and Sue. I have two kids, Tom and Ann." */
	static String describeFamily(EntityNPC npc, Player player) {
		PeopleRegistry family = PeopleRegistry.get(npc.world);
		Romance.register(npc);
		UUID self = npc.getUuid();
		List<String> sentences = new ArrayList<>();
		PeopleRegistry.Partnership partnership = family.partnershipOf(self);
		if (partnership != null) {
			UUID partner = partnership.other(self);
			String who = partner.equals(player.uuid) ? "you" : family.nameOf(partner);
			Kinship kin = Kinship.of(family, self, partner);
			sentences.add(partner.equals(player.uuid) ? "I've got you, haven't I?" : "My " + kin.label(genderOf(family, partner)) + " is " + who + ".");
		}

		PeopleRegistry.Person person = family.find(self);
		if (person != null && person.lateSpouse != null) {
			sentences.add("My " + Kinship.LATE_SPOUSE.label(genderOf(family, person.lateSpouse)) + ", " + family.nameOf(person.lateSpouse) + ", is gone now.");
		}

		List<String> parents = names(family, family.parentsOf(self), player);
		if (!parents.isEmpty()) {
			sentences.add(parents.size() == 1 ? "My parent is " + parents.get(0) + "." : "My parents are " + join(parents) + ".");
		}

		List<String> children = names(family, family.childrenOf(self), player);
		if (!children.isEmpty()) {
			sentences.add(children.size() == 1 ? "I have a child, " + children.get(0) + "." : "I have " + children.size() + " children: " + join(children) + ".");
		}

		List<String> siblings = names(family, new ArrayList<>(family.siblingsOf(self)), player);
		if (!siblings.isEmpty()) {
			sentences.add(siblings.size() == 1 ? "I've got a sibling, " + siblings.get(0) + "." : "I've got " + siblings.size() + " siblings: " + join(siblings) + ".");
		}

		int others = Kinship.relativesOf(family, self).size() - parents.size() - children.size() - siblings.size() - (partnership != null ? 1 : 0)
			- (person != null && person.lateSpouse != null ? 1 : 0);
		if (others > 0) {
			sentences.add("And " + others + " more relatives, here and there.");
		}

		if (sentences.isEmpty()) {
			return NpcDialogue.say(npc, player, "no_family", "No family to speak of. Just me.");
		}

		return String.join(" ", sentences);
	}

	@Nullable
	private static lifeblood.entity.NpcGender genderOf(PeopleRegistry family, UUID id) {
		PeopleRegistry.Person person = family.find(id);
		return person != null ? person.gender : null;
	}

	private static List<String> names(PeopleRegistry family, List<UUID> ids, Player player) {
		List<String> names = new ArrayList<>();
		for (UUID id : ids) {
			PeopleRegistry.Person person = family.find(id);
			String name = id.equals(player.uuid) ? "you" : family.nameOf(id);
			names.add(person != null && !person.alive ? name + " (gone now)" : name);
		}

		return names;
	}

	private static String join(List<String> names) {
		if (names.size() <= 1) {
			return String.join("", names);
		}

		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}
}
