package npclaude.dialogue;

import java.util.function.Consumer;
import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/** An open conversation. Dialogue options act on the conversation through this. */
public interface DialogueSession {
	EntityNPC getNpc();

	Player getPlayer();

	/** Shows a line spoken by the NPC. */
	void say(String line);

	/** Replaces the current options, e.g. to move deeper into a conversation. */
	void show(NpcDialogue dialogue);

	void openInventory();

	/**
	 * Asks the player to type something. Done passes the text on; the extra button (if labeled) and Cancel call their
	 * own callbacks. The conversation shows again before any callback runs.
	 */
	void askForText(String question, String initial, @Nullable String extraLabel, Consumer<String> onDone, @Nullable Runnable onExtra,
		Runnable onCancel);

	/** Shows the NPC's profile: personality, tastes and mood, laid out. */
	void openProfile();

	void close();
}
