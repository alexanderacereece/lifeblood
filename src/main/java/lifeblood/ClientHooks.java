package lifeblood;

import java.util.function.Consumer;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;

/**
 * Everything common code asks of the client, in one place: opening screens and refreshing textures. Common code must
 * never import anything from lifeblood.client (a dedicated server doesn't have those classes, and loading one crashes
 * it), so it calls these instead. The client fills them in at startup (LifebloodClient); on a dedicated server they
 * stay as they are and do nothing.
 *
 * A new thing the client must do for common code gets a hook here, in the same shape: a handler, a setter for the
 * client, and a method for common code to call.
 */
public final class ClientHooks {
	/** Opens the conversation with an NPC for the player who right-clicked it. */
	@FunctionalInterface
	public interface Handler {
		void interact(EntityNPC npc, Player player);
	}

	private static Handler handler = (npc, player) -> {
	};
	private static Consumer<Player> wordBookHandler = player -> {
	};
	private static Runnable appearanceReload = () -> {
	};
	private static Runnable skinReload = () -> {
	};

	private ClientHooks() {
	}

	public static void setHandler(Handler handler) {
		ClientHooks.handler = handler;
	}

	/** A player right-clicked an NPC: the client opens the conversation. */
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

	/** The client throws away the textures it built from appearance parts, so they're rebuilt from reloaded parts. */
	public static void setAppearanceReload(Runnable reload) {
		appearanceReload = reload;
	}

	public static void reloadAppearanceTextures() {
		appearanceReload.run();
	}

	/** The client reads the skin files in npc_skins again. */
	public static void setSkinReload(Runnable reload) {
		skinReload = reload;
	}

	public static void reloadSkins() {
		skinReload.run();
	}
}
