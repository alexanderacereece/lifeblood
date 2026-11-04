package lifeblood.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;

/**
 * /lb, the one entry to every Lifeblood command. Nothing in the mod needs a command to play: everything is done through
 * items, screens and the NPCs themselves. Commands are for admins, building content and testing.
 *
 * Each category is a class with addTo(root); every node is made with {@link CommandTree#branch}, so typing any part of
 * the tree on its own lists what's under it with a description. Commands follow vanilla's shape: the verb, then who it's
 * for (any vanilla selector, @s, or an NPC's name; see {@link TargetArgument}), then values; a command given who but no
 * value shows the current value, like /gamerule.
 *
 * - npc: summon, list, remove, heal, teleport, goto, follow, stay, wander, free, gender, mortality, grow, say
 * - personality: show, randomize, set, quirk add|remove
 * - tastes: show, reveal, item, weather, biome, season
 * - social: opinion, mood, remember, forget, stance
 * - family: show, parent, unlink, couple, baby
 * - look: show, randomize, set, skin, parts, skins
 * - faction: list, create, delete, default, relation, colors, join
 * - prefab: list, save, place, undo, check
 * - words: list, delete
 * - owner, reload, debug
 */
public class LifebloodCommand implements CommandManager.CommandRegistry {
	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		ArgumentBuilderLiteral<CommandSource> root = CommandTree.root("Lifeblood's tools for admins, content and testing. Nothing in the mod needs them to play.")
			.requires(CommandSource::hasAdmin);
		NpcCommands.addTo(root);
		PersonalityCommands.addTo(root);
		FamilyCommands.addTo(root);
		LookCommands.addTo(root);
		FactionCommands.addTo(root);
		PrefabCommands.addTo(root);
		ToolCommands.addTo(root);
		dispatcher.register(root);
	}
}
