package lifeblood.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.net.command.CommandSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the whole /lb tree without a world: every command is described and lists itself, and targets parse. */
class CommandTreeTest {
	private static CommandDispatcher<CommandSource> dispatcher;
	private static final List<String> MESSAGES = new ArrayList<>();
	/** An admin with no world, whose messages are collected. */
	private static CommandSource source;

	@BeforeAll
	static void build() {
		dispatcher = new CommandDispatcher<>();
		new LifebloodCommand().register(dispatcher);
		source = (CommandSource) Proxy.newProxyInstance(CommandSource.class.getClassLoader(), new Class<?>[] {CommandSource.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "hasAdmin", "messageMayBeMultiline" -> true;
				case "sendMessage" -> {
					MESSAGES.add(String.valueOf(args[args.length - 1]));
					yield null;
				}
				case "getPlayerUsernames", "getPlayerNicknames", "getEntitySuggestions", "getChatSuggestions" -> List.of();
				case "toString", "getName" -> "test";
				default -> null;
			});
	}

	@Test
	void everyCommandIsDescribedAndListsItself() {
		CommandNode<CommandSource> root = dispatcher.getRoot().getChild(CommandTree.ROOT);
		assertNotNull(root);
		assertNotNull(CommandTree.descriptionOf(""));
		check(root, "");
	}

	private static void check(CommandNode<CommandSource> node, String path) {
		for (CommandNode<CommandSource> child : node.getChildren()) {
			String childPath = path;
			if (child instanceof LiteralCommandNode) {
				childPath = path.isEmpty() ? child.getName() : path + " " + child.getName();
				assertNotNull(CommandTree.descriptionOf(childPath), "no description for /lb " + childPath);
				assertNotNull(child.getCommand(), "/lb " + childPath + " does nothing when typed on its own");
			}

			check(child, childPath);
		}
	}

	@Test
	void typingACategoryListsItsCommands() throws CommandSyntaxException {
		MESSAGES.clear();
		dispatcher.execute("lb npc", source);
		assertTrue(MESSAGES.stream().anyMatch(line -> line.startsWith("/lb npc heal <npcs>")), String.join("\n", MESSAGES));
		assertTrue(MESSAGES.stream().anyMatch(line -> line.startsWith("/lb npc summon")), String.join("\n", MESSAGES));

		MESSAGES.clear();
		dispatcher.execute("lb", source);
		assertTrue(MESSAGES.stream().anyMatch(line -> line.startsWith("/lb faction")), String.join("\n", MESSAGES));

		MESSAGES.clear();
		dispatcher.execute("lb npc heal", source);
		assertEquals("/lb npc heal <npcs>", MESSAGES.get(0));

		MESSAGES.clear();
		dispatcher.execute("lb npc gender", source);
		assertEquals("/lb npc gender <npcs> [<gender>]", MESSAGES.get(0));
	}

	@Test
	void targetsTakeSelectorsAndNames() {
		for (String command : new String[] {"lb npc heal @s", "lb npc heal @e", "lb npc heal Walter", "lb npc heal \"Walter White\"",
			"lb npc teleport Walter 10 64 -3", "lb npc teleport Walter @p", "lb social opinion Walter @s", "lb faction join @a settlers",
			"lb npc summon random", "lb npc summon random 5 settlers", "lb npc summon \"Walter White\" raiders"}) {
			ParseResults<CommandSource> parsed = dispatcher.parse(command, source);
			assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(), command + " didn't parse: " + parsed.getExceptions());
		}
	}
}
