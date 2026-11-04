package lifeblood.dialogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;

/**
 * The conversation menus and the options in them. Any system can add options to a menu (an occupation's "What do
 * you sell?", a faction's "I'd like to join"), so the menus never need editing to grow. Options are listed by
 * their order number; each menu's way out ("Goodbye.", "That's all.") always comes last.
 */
public final class DialogueMenus {
	public enum Menu {
		/** The first menu: chat, gifts, following, and the doors to the others. */
		MAIN,
		/** "Can I ask you something?": about them, the people they know, their family, their words. */
		QUESTIONS,
		/** "About the practical stuff...": only for people the NPC trusts. */
		PRACTICAL
	}

	/** Adds whatever options apply, for this NPC and this player right now (often none). */
	@FunctionalInterface
	public interface Contributor {
		void addOptions(EntityNPC npc, Player player, List<DialogueOption> options);
	}

	private record Entry(int order, Contributor contributor) {
	}

	private static final Map<Menu, List<Entry>> MENUS = new EnumMap<>(Menu.class);

	private DialogueMenus() {
	}

	/** Adds options to a menu. Lower orders come first; the built-in options use steps of 100. */
	public static void register(Menu menu, int order, Contributor contributor) {
		List<Entry> entries = MENUS.computeIfAbsent(menu, m -> new ArrayList<>());
		entries.add(new Entry(order, contributor));
		entries.sort(Comparator.comparingInt(Entry::order));
	}

	/** Every option in the menu for this NPC and player, in order. */
	public static List<DialogueOption> options(Menu menu, EntityNPC npc, Player player) {
		List<DialogueOption> options = new ArrayList<>();
		for (Entry entry : MENUS.getOrDefault(menu, List.of())) {
			entry.contributor().addOptions(npc, player, options);
		}

		return options;
	}
}
