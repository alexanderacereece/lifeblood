package npclaude.dialogue;

import java.util.List;
import npclaude.combat.CombatStyle;
import npclaude.entity.EntityNPC;

/** What an NPC says when a conversation reaches this point, and the replies the player can pick. */
public record NpcDialogue(String line, List<DialogueOption> options) {
	/** Placeholder conversation every NPC has until real dialogue exists. */
	public static NpcDialogue placeholder(EntityNPC npc) {
		return placeholder(npc, "...");
	}

	private static NpcDialogue placeholder(EntityNPC npc, String line) {
		return new NpcDialogue(line, List.of(
			new DialogueOption("Hi.", session -> session.say("Hey.")),
			new DialogueOption("Let me see your inventory.", DialogueSession::openInventory),
			// Debug: cycles the NPC's combat style.
			new DialogueOption("Combat style: " + npc.getCombatStyle().displayName(), session -> {
				CombatStyle style = npc.getCombatStyle().next();
				npc.setCombatStyle(style);
				session.show(placeholder(npc, describe(style)));
			})
		));
	}

	private static String describe(CombatStyle style) {
		return switch (style) {
			case AUTO -> "I'll fight however my gear suits.";
			case MELEE -> "I'll fight up close.";
			case RANGED -> "I'll fight from a distance.";
		};
	}
}
