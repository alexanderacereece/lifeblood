package lifeblood.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.TextFormatting;

/**
 * How the /lb command tree describes itself. Every category and command is made with {@link #branch}, which records a
 * one-line description and makes the node list what's under it when typed on its own: /lb shows the categories,
 * /lb npc the NPC commands, /lb npc heal how to use it. A command that does something with no further arguments
 * (/lb npc list) replaces the listing with its own action.
 */
final class CommandTree {
	/** The root everything hangs from. */
	static final String ROOT = "lb";

	/** Descriptions by path from the root, without it: "npc", "npc heal". */
	private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
	/** The action of a node typed on its own: listing what's under it. One instance, so usage text can tell it apart. */
	private static final Command<CommandSource> LIST = CommandTree::list;

	private CommandTree() {
	}

	/** The root, /lb, with what it's for; typed on its own it lists the categories. */
	static ArgumentBuilderLiteral<CommandSource> root(String description) {
		DESCRIPTIONS.put("", description);
		return CommandSupport.literal(ROOT).executes(LIST);
	}

	/**
	 * A category or command at this path ("npc", "npc heal"), named after its last word, with its description. Typed on
	 * its own it lists what's under it, until given an action of its own with executes().
	 */
	static ArgumentBuilderLiteral<CommandSource> branch(String path, String description) {
		DESCRIPTIONS.put(path, description);
		String name = path.substring(path.lastIndexOf(' ') + 1);
		return CommandSupport.literal(name).executes(LIST);
	}

	/** The description recorded for a path ("npc heal"; "" for the root), or null. */
	static String descriptionOf(String path) {
		return DESCRIPTIONS.get(path);
	}

	/** Lists the commands under the node just typed, with how to use each and what it does. */
	static int list(CommandContext<CommandSource> c) {
		List<ParsedCommandNode<CommandSource>> nodes = c.getNodes();
		CommandNode<CommandSource> node = nodes.get(nodes.size() - 1).getNode();
		// The path is the words typed after the root, without arguments: "prefab place faction".
		StringBuilder pathBuilder = new StringBuilder();
		for (int i = 1; i < nodes.size(); i++) {
			CommandNode<CommandSource> part = nodes.get(i).getNode();
			if (part instanceof LiteralCommandNode) {
				pathBuilder.append(pathBuilder.length() > 0 ? " " : "").append(part.getName());
			}
		}

		String path = pathBuilder.toString();
		String typed = "/" + c.getInput().trim().replaceFirst("^/", "");
		CommandSource source = c.getSource();
		boolean hasSubcommands = node.getChildren().stream().anyMatch(child -> child instanceof LiteralCommandNode);
		if (!hasSubcommands) {
			// A single command: show how it's used, from the words before it.
			String before = typed.substring(0, typed.length() - node.getName().length()).trim();
			source.sendMessage(before + " " + usage(node));
			describe(source, path);
			return 1;
		}

		String heading = DESCRIPTIONS.get(path);
		if (heading != null) {
			source.sendMessage(typed + ": " + heading);
		}

		int count = 0;
		for (CommandNode<CommandSource> child : node.getChildren()) {
			if (!child.canUse(source)) {
				continue;
			}

			String childPath = path.isEmpty() ? child.getName() : path + " " + child.getName();
			String description = child instanceof LiteralCommandNode ? DESCRIPTIONS.get(childPath) : null;
			source.sendMessage(typed + " " + usage(child) + (description != null ? TextFormatting.LIGHT_GRAY + "  " + description : ""));
			count++;
		}

		return count;
	}

	/**
	 * How a node is used, like vanilla's usage text but ignoring the listing every node does on its own: "heal &lt;npcs&gt;",
	 * "teleport &lt;npcs&gt; (&lt;location&gt;|&lt;destination&gt;)", "gender &lt;npcs&gt; [&lt;gender&gt;]". Words in brackets can
	 * be left out; a choice of several is in parentheses.
	 */
	static String usage(CommandNode<CommandSource> node) {
		String self = node instanceof LiteralCommandNode ? node.getName() : "<" + node.getName() + ">";
		List<CommandNode<CommandSource>> children = new ArrayList<>(node.getChildren());
		if (children.isEmpty()) {
			return self;
		}

		String rest;
		if (children.size() == 1) {
			rest = usage(children.get(0));
		} else {
			StringBuilder choices = new StringBuilder("(");
			for (int i = 0; i < children.size(); i++) {
				CommandNode<CommandSource> child = children.get(i);
				choices.append(i > 0 ? "|" : "").append(child instanceof LiteralCommandNode ? child.getName() : "<" + child.getName() + ">");
			}

			rest = choices.append(")").toString();
		}

		boolean doesSomethingAlready = node.getCommand() != null && node.getCommand() != LIST;
		return self + " " + (doesSomethingAlready ? "[" + rest + "]" : rest);
	}

	private static void describe(CommandSource source, String path) {
		String description = DESCRIPTIONS.get(path);
		if (description != null) {
			source.sendMessage(TextFormatting.LIGHT_GRAY + description);
		}
	}
}
