package npclaude.dialogue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import npclaude.combat.CombatStyle;
import npclaude.entity.EntityNPC;
import npclaude.entity.FollowDistance;
import npclaude.social.MemoryType;
import org.jetbrains.annotations.Nullable;

/** What an NPC says when a conversation reaches this point, and the replies the player can pick. */
public record NpcDialogue(String line, List<DialogueOption> options) {
	/** The conversation every NPC has for now: small talk, gifts, its inventory, and a few settings. */
	public static NpcDialogue main(EntityNPC npc, Player player) {
		return main(npc, player, orDefault(npc.voice.line("dialogue_open", player, null), "..."));
	}

	private static NpcDialogue main(EntityNPC npc, Player player, String line) {
		List<DialogueOption> options = new ArrayList<>();
		options.add(new DialogueOption("Hi.", session -> {
			npc.remember(MemoryType.HAD_A_NICE_CHAT, player, 1.0F);
			session.show(main(npc, player, orDefault(npc.voice.line("smalltalk", player, null), "Hey.")));
		}));
		ItemStack held = player.inventory.getCurrentItem();
		if (held != null) {
			options.add(new DialogueOption("Here, take this.", session ->
				session.show(main(npc, player, NpcGifts.give(npc, player)))));
		}

		options.add(new DialogueOption("Let me see your inventory.", DialogueSession::openInventory));
		options.add(new DialogueOption("Follow distance: " + npc.getFollowDistance().displayName(), session -> {
			FollowDistance distance = npc.getFollowDistance().next();
			npc.setFollowDistance(distance);
			session.show(main(npc, player, switch (distance) {
				case SHORT -> "I'll stay close.";
				case MEDIUM -> "I'll give you some room.";
				case LONG -> "I'll keep my distance.";
			}));
		}));
		// Debug: cycles the NPC's combat style.
		options.add(new DialogueOption("Combat style: " + npc.getCombatStyle().displayName(), session -> {
			CombatStyle style = npc.getCombatStyle().next();
			npc.setCombatStyle(style);
			session.show(main(npc, player, describe(style)));
		}));
		return new NpcDialogue(line, options);
	}

	private static String orDefault(@Nullable String line, String fallback) {
		return line != null ? line : fallback;
	}

	private static String describe(CombatStyle style) {
		return switch (style) {
			case AUTO -> "I'll fight however my gear suits.";
			case MELEE -> "I'll fight up close.";
			case RANGED -> "I'll fight from a distance.";
		};
	}
}
