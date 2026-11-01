package npclaude.dialogue;

import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;

/** An open conversation. Dialogue options act on the conversation through this. */
public interface DialogueSession {
	EntityNPC getNpc();

	Player getPlayer();

	/** Shows a line spoken by the NPC. */
	void say(String line);

	/** Replaces the current options, e.g. to move deeper into a conversation. */
	void show(NpcDialogue dialogue);

	void openInventory();

	/** Shows the NPC's profile: personality, tastes and mood, laid out. */
	void openProfile();

	void close();
}
