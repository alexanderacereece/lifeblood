package npclaude.dialogue;

import java.util.List;

/** What an NPC says when a conversation reaches this point, and the replies the player can pick. */
public record NpcDialogue(String line, List<DialogueOption> options) {
	/** Placeholder conversation every NPC has until real dialogue exists. */
	public static NpcDialogue placeholder() {
		return new NpcDialogue("...", List.of(
			new DialogueOption("Hi.", session -> session.say("Hey.")),
			new DialogueOption("Let me see your inventory.", DialogueSession::openInventory)
		));
	}
}
