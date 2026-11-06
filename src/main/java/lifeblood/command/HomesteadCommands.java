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
import java.util.StringJoiner;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.util.helper.MathHelper;
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
			.then(branch("homestead list", "Every homestead the world has generated, and where.").executes(HomesteadCommands::list)));
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

	private static int list(CommandContext<CommandSource> c) {
		List<HomesteadRegister.Homestead> homesteads = HomesteadRegister.get(c.getSource().getWorld()).all();
		if (homesteads.isEmpty()) {
			return reply(c, "The world hasn't generated any homesteads yet.");
		}

		StringJoiner joiner = new StringJoiner("; ");
		for (HomesteadRegister.Homestead homestead : homesteads) {
			joiner.add(homestead.prefab() + " at " + homestead.x() + " " + homestead.y() + " " + homestead.z());
		}

		return reply(c, homesteads.size() + (homesteads.size() == 1 ? " homestead: " : " homesteads: ") + joiner + ".");
	}
}
