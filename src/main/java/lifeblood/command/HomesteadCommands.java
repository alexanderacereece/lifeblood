package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.building.PrefabCheck;
import lifeblood.building.PrefabFiles;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.homestead.Homesteads;
import org.jetbrains.annotations.Nullable;

/**
 * /lb homestead: testing generated homesteads. The world makes them on its own as it generates; these put one up on
 * the spot, whatever the rules about where they go, and list the ones the world has made.
 */
final class HomesteadCommands {
	/** The homestead prefabs (those in a homestead folder), for suggestions. */
	private static final SuggestionProvider<CommandSource> HOMESTEAD_PREFABS = (c, builder) -> {
		for (String name : PrefabFiles.tagged(PrefabFiles.HOMESTEAD_TAG)) {
			builder.suggest(name);
		}

		return builder.buildFuture();
	};

	private HomesteadCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("homestead", "Generated homesteads: houses the world places with people already living in them.")
			.then(branch("homestead generate", "Puts up a homestead on flat ground near you now, with its household, ignoring how rare they are.")
				.executes(c -> generate(c, null, position(c, null)))
				.then(argument("prefab", ArgumentTypeString.word()).suggests(HOMESTEAD_PREFABS)
					.executes(c -> generate(c, ArgumentTypeString.getString(c, "prefab"), position(c, null)))
					.then(argument("location", ArgumentTypeVector3d.vec3d())
						.executes(c -> generate(c, ArgumentTypeString.getString(c, "prefab"), position(c, "location"))))))
			.then(branch("homestead list", "Every homestead the world has generated, where, and what its chests hold.").executes(HomesteadCommands::list)));
	}

	/** Where to put it: the location given, or where the command is run from. */
	private static double[] position(CommandContext<CommandSource> c, @Nullable String argument) throws CommandSyntaxException {
		if (argument != null) {
			return CommandSupport.position(c, argument);
		}

		var coordinates = c.getSource().getCoordinates(false);
		if (coordinates == null) {
			throw error("Give a location, or run this in the world.");
		}

		return new double[] {coordinates.x(), coordinates.y(), coordinates.z()};
	}

	private static int generate(CommandContext<CommandSource> c, @Nullable String prefab, double[] at) throws CommandSyntaxException {
		// The area decorated with a chunk starts half a chunk in, so this centers it on the spot.
		int chunkX = MathHelper.floor((at[0] - 16.0) / 16.0);
		int chunkZ = MathHelper.floor((at[2] - 16.0) / 16.0);
		try {
			return reply(c, Homesteads.generateNow(c.getSource().getWorld(), chunkX, chunkZ, prefab));
		} catch (IOException e) {
			throw error("Couldn't read the prefab: " + e.getMessage());
		}
	}

	/** What a homestead's chests hold, most first ("12 log_oak, 5 food_bread..."), or that they aren't loaded. */
	private static String describeChests(World world, HomesteadRegister.Homestead homestead) {
		Map<String, Integer> counts = new TreeMap<>();
		boolean loaded = false;
		for (int[] chest : homestead.chests()) {
			TilePos pos = new TilePos(chest[0], chest[1], chest[2]);
			if (world.isBlockLoaded(pos) && world.getTileEntity(pos) instanceof Container container) {
				loaded = true;
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					ItemStack stack = container.getItem(slot);
					if (stack != null) {
						counts.merge(PrefabCheck.shortName(stack.getItem().getKey()), stack.stackSize, Integer::sum);
					}
				}
			}
		}

		if (!loaded) {
			return "chests not loaded";
		}

		StringJoiner joiner = new StringJoiner(", ");
		counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.forEach(entry -> joiner.add(entry.getValue() + " " + entry.getKey()));
		return counts.isEmpty() ? "chests empty" : joiner.toString();
	}

	private static int list(CommandContext<CommandSource> c) {
		List<HomesteadRegister.Homestead> homesteads = HomesteadRegister.get(c.getSource().getWorld()).all();
		if (homesteads.isEmpty()) {
			return reply(c, "The world hasn't generated any homesteads yet.");
		}

		reply(c, homesteads.size() + (homesteads.size() == 1 ? " homestead:" : " homesteads:"));
		World world = c.getSource().getWorld();
		for (HomesteadRegister.Homestead homestead : homesteads) {
			reply(c, "- " + homestead.prefab() + " at " + homestead.x() + " " + homestead.y() + " " + homestead.z() + ": " + describeChests(world, homestead));
		}

		return homesteads.size();
	}
}
