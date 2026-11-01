package npclaude.interaction;

import java.util.function.Consumer;
import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;

/**
 * Bridges a right-click on an NPC (game logic) to the screens that show the interaction (client).
 * The client installs a handler at startup; without one (e.g. a dedicated server) nothing opens.
 */
public final class NpcInteractions {
	@FunctionalInterface
	public interface Handler {
		void interact(EntityNPC npc, Player player);
	}

	private static Handler handler = (npc, player) -> {
	};
	private static Consumer<Player> wordBookHandler = player -> {
	};

	private NpcInteractions() {
	}

	public static void setHandler(Handler handler) {
		NpcInteractions.handler = handler;
	}

	public static void interact(EntityNPC npc, Player player) {
		handler.interact(npc, player);
	}

	/** The client opens the word book screen (from the word book item). */
	public static void setWordBookHandler(Consumer<Player> handler) {
		wordBookHandler = handler;
	}

	public static void openWordBook(Player player) {
		wordBookHandler.accept(player);
	}
}
