package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.util.helper.Direction;
import net.minecraft.core.util.helper.DyeColor;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.util.phys.HitResult;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.building.LocalMaterials;
import lifeblood.building.Prefab;
import lifeblood.building.PrefabBeds;
import lifeblood.building.PrefabCapture;
import lifeblood.building.PrefabCheck;
import lifeblood.building.PrefabFiles;
import lifeblood.building.PrefabPlacer;
import lifeblood.building.Swaps;
import lifeblood.building.Turns;
import lifeblood.faction.FactionColors;
import lifeblood.faction.FactionRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * /lb prefab: saving builds as prefabs and placing them, for making and testing the houses NPCs will live in.
 *
 * - corner 1, corner 2: marks the block you're looking at (or the one you stand on) as a corner of the box to save.
 * - save &lt;name&gt; [&lt;from&gt; &lt;to&gt;]: saves the marked box, or the one between the corners given. Stand in front of
 *   the build, looking at it; that side becomes its front. It says what the box holds, so a wrong box shows at once.
 * - place &lt;name&gt; [faction &lt;faction&gt; | colors &lt;primary&gt; &lt;secondary&gt;]: places it a couple of blocks
 *   ahead of you, facing you, with its bottom layer level with the ground you stand on. Logs and stone become what's
 *   local; gray and light gray take the faction's colors (yours if you've joined one), or the ones given.
 * - undo: puts back what your last placement covered.
 * - check &lt;name&gt;, list.
 */
final class PrefabCommands {
	/** How far ahead of the player a placed prefab's near edge is, in blocks. */
	private static final int PLACE_DISTANCE = 2;
	/** What each player's last placement covered, to undo it. Kept only while the game runs. */
	private static final Map<UUID, Placement> LAST_PLACED = new HashMap<>();

	/** How many kinds of block the summary of a saved prefab names. */
	private static final int CONTENTS_SHOWN = 8;
	/** How far away a looked-at block can be marked as a corner. */
	private static final double CORNER_REACH = 64.0;
	/** Each player's marked corners, first and second, to save the box between. Kept only while the game runs. */
	private static final Map<UUID, int[][]> CORNERS = new HashMap<>();

	private record Placement(Prefab before, int x, int y, int z) {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("prefab", "Saving builds as prefabs and placing them, with wood, stone and colors that fit the place.")
			.then(branch("prefab list", "The saved prefabs.").executes(PrefabCommands::list))
			.then(branch("prefab corner", "Marks the block you're looking at, or the one you stand on, as a corner of the box to save.")
				.then(branch("prefab corner 1", "Marks the first corner.").executes(c -> corner(c, 0)))
				.then(branch("prefab corner 2", "Marks the opposite corner.").executes(c -> corner(c, 1))))
			.then(branch("prefab save", "Saves the marked box, or the one between two corners; stand in front of the build looking at it, and that side is its front.")
				.then(argument("name", ArgumentTypeString.word())
					.executes(PrefabCommands::saveMarked)
					.then(argument("from", ArgumentTypeVector3d.vec3d())
						.then(argument("to", ArgumentTypeVector3d.vec3d()).executes(PrefabCommands::save)))))
			.then(branch("prefab place", "Places a prefab ahead of you, facing you, in your faction's colors, a faction's, or the ones given.")
				.then(argument("name", ArgumentTypeString.word()).suggests(PREFAB_NAMES)
					.executes(c -> place(c, null))
					.then(branch("prefab place faction", "Paints it in a faction's colors.")
						.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES)
							.executes(c -> place(c, FactionRegistry.get(c.getSource().getWorld()).getColors(requireFaction(c, "faction"))))))
					.then(branch("prefab place colors", "Paints it in the colors given: gray becomes the primary, light gray the secondary.")
						.then(argument("primary", ArgumentTypeString.word()).suggests(COLOR_NAMES)
							.then(argument("secondary", ArgumentTypeString.word()).suggests(COLOR_NAMES)
								.executes(c -> place(c, new FactionColors(requireColor(c, "primary"), requireColor(c, "secondary")))))))))
			.then(branch("prefab undo", "Puts back what your last placement covered.").executes(PrefabCommands::undo))
			.then(branch("prefab check", "What a prefab holds, what will change with the place, and anything to fix.")
				.then(argument("name", ArgumentTypeString.word()).suggests(PREFAB_NAMES).executes(PrefabCommands::check))));
	}

	private static int list(CommandContext<CommandSource> c) {
		List<String> names = PrefabFiles.list();
		if (names.isEmpty()) {
			return reply(c, "No prefabs saved yet. Build something and use /lb prefab save.");
		}

		// Folder tags in brackets: "single_shack (homestead)".
		StringJoiner joiner = new StringJoiner(", ");
		for (String name : names) {
			Set<String> tags = PrefabFiles.tagsOf(name);
			joiner.add(tags.isEmpty() ? name : name + " (" + String.join(", ", tags) + ")");
		}

		return reply(c, "Prefabs: " + joiner + ".");
	}

	/** Marks a corner: the block the player looks at, or the one under their feet if they look at nothing near. */
	private static int corner(CommandContext<CommandSource> c, int which) throws CommandSyntaxException {
		Player player = requirePlayer(c.getSource());
		int[] block;
		if (player.rayCast(CORNER_REACH, 1.0F, false, false, false) instanceof HitResult.Tile hit) {
			block = new int[] {hit.tilePos.x(), hit.tilePos.y(), hit.tilePos.z()};
		} else {
			block = new int[] {MathHelper.floor(player.x), MathHelper.floor(player.y - player.heightOffset) - 1, MathHelper.floor(player.z)};
		}

		int[][] corners = CORNERS.computeIfAbsent(player.uuid, uuid -> new int[2][]);
		corners[which] = block;
		String name = PrefabCheck.shortName(PrefabCapture.nameOf(c.getSource().getWorld().getBlockType(new TilePos().set(block[0], block[1], block[2]))));
		String marked = "Corner " + (which + 1) + ": " + block[0] + " " + block[1] + " " + block[2] + " (" + name + ").";
		int[] other = corners[1 - which];
		if (other == null) {
			return reply(c, marked + " Now mark corner " + (2 - which) + ".");
		}

		return reply(c, marked + " The box is " + (Math.abs(block[0] - other[0]) + 1) + "x" + (Math.abs(block[1] - other[1]) + 1) + "x"
			+ (Math.abs(block[2] - other[2]) + 1) + "; save it with /lb prefab save <name>.");
	}

	private static int saveMarked(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Player player = requirePlayer(c.getSource());
		int[][] corners = CORNERS.get(player.uuid);
		if (corners == null || corners[0] == null || corners[1] == null) {
			throw error("Mark the box first: look at one corner of the build and use /lb prefab corner 1, then the opposite corner and /lb prefab corner 2.");
		}

		return save(c, corners[0], corners[1]);
	}

	private static int save(CommandContext<CommandSource> c) throws CommandSyntaxException {
		double[] from = position(c, "from");
		double[] to = position(c, "to");
		return save(c, new int[] {MathHelper.floor(from[0]), MathHelper.floor(from[1]), MathHelper.floor(from[2])},
			new int[] {MathHelper.floor(to[0]), MathHelper.floor(to[1]), MathHelper.floor(to[2])});
	}

	private static int save(CommandContext<CommandSource> c, int[] from, int[] to) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "name");
		if (!PrefabFiles.isValidName(name)) {
			throw error("Prefab names use lowercase letters, numbers and underscores.");
		}

		Player player = requirePlayer(c.getSource());
		// You stand in front of the build looking at it, so its front faces back toward you.
		Direction front = Turns.turn(Turns.facingOfYaw(player.yRot), 2);
		Prefab prefab;
		try {
			prefab = PrefabCapture.capture(c.getSource().getWorld(), name, from[0], from[1], from[2], to[0], to[1], to[2], front);
			PrefabFiles.save(prefab);
		} catch (IllegalArgumentException e) {
			throw error(e.getMessage());
		} catch (IOException e) {
			throw error("Couldn't save the prefab: " + e.getMessage());
		}

		reply(c, "Saved " + name + " (" + prefab.width + "x" + prefab.height + "x" + prefab.depth + " from " + from[0] + " " + from[1] + " " + from[2]
			+ " to " + to[0] + " " + to[1] + " " + to[2] + ", front facing " + front.name().toLowerCase() + ") to the " + PrefabFiles.FOLDER_NAME
			+ " folder.");
		return report(c, prefab);
	}

	private static int check(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Prefab prefab = load(c);
		reply(c, prefab.name + ": " + prefab.width + "x" + prefab.height + "x" + prefab.depth + ", " + prefab.tileEntities().size()
			+ " blocks with contents.");
		return report(c, prefab);
	}

	/** What a prefab holds, what will change with the place, and the check's warnings. */
	private static int report(CommandContext<CommandSource> c, Prefab prefab) {
		reply(c, "Holds: " + describeContents(PrefabCheck.contents(prefab)) + ".");
		PrefabCheck.Report report = PrefabCheck.check(prefab);
		if (report.swappable().isEmpty()) {
			reply(c, "Nothing in it changes with the place: no logs, swappable stone, gray or light gray.");
		} else {
			StringJoiner swaps = new StringJoiner(", ");
			report.swappable().forEach((kind, count) -> swaps.add(count + " " + kind));
			reply(c, "Changes with the place: " + swaps + ".");
		}

		// Beds say who a house is for: a homestead without any is never used.
		PrefabBeds.Layout beds = PrefabBeds.layout(prefab);
		boolean homestead = PrefabFiles.tagsOf(prefab.name).contains(PrefabFiles.HOMESTEAD_TAG);
		if (beds.sleeps() > 0) {
			reply(c, "Beds: " + beds.doubles().size() + " double, " + beds.singles().size() + " single; sleeps " + beds.sleeps() + ".");
		} else if (homestead) {
			reply(c, "Warning: it's in the homestead folder but has no beds, so the world will never use it as a homestead.");
		}

		for (String warning : report.warnings()) {
			reply(c, "Warning: " + warning);
		}

		return 1;
	}

	/** Places a prefab ahead of the player; colors null means the player's faction's, or none. */
	private static int place(CommandContext<CommandSource> c, @Nullable FactionColors colors) throws CommandSyntaxException {
		Prefab prefab = load(c);
		Player player = requirePlayer(c.getSource());
		World world = c.getSource().getWorld();
		if (colors == null) {
			String faction = FactionRegistry.get(world).getPlayerFaction(player.uuid);
			colors = faction != null ? FactionRegistry.get(world).getColors(faction) : null;
		}

		Direction looking = Turns.facingOfYaw(player.yRot);
		int turns = Turns.between(prefab.front, Turns.turn(looking, 2));
		int[] size = PrefabPlacer.turnedSize(prefab, turns);
		int px = MathHelper.floor(player.x);
		int pz = MathHelper.floor(player.z);
		int groundY = MathHelper.floor(player.y - player.heightOffset) - 1;
		int nearX = px + Turns.stepX(looking) * PLACE_DISTANCE;
		int nearZ = pz + Turns.stepZ(looking) * PLACE_DISTANCE;
		// The box starts at the near edge and runs away from the player, centered across their view.
		int originX = Turns.stepX(looking) > 0 ? nearX : Turns.stepX(looking) < 0 ? nearX - size[0] + 1 : px - size[0] / 2;
		int originZ = Turns.stepZ(looking) > 0 ? nearZ : Turns.stepZ(looking) < 0 ? nearZ - size[1] + 1 : pz - size[1] / 2;

		int centerX = originX + size[0] / 2;
		int centerZ = originZ + size[1] / 2;
		Swaps swaps = LocalMaterials.survey(world, centerX, groundY, centerZ);
		if (colors != null) {
			swaps = swaps.withColors(colors.primary(), colors.secondary());
		}

		// Remember what was there, turned the same way, so undo can put it back.
		Prefab before = PrefabCapture.capture(world, "undo", originX, groundY, originZ, originX + size[0] - 1, groundY + prefab.height - 1,
			originZ + size[1] - 1, Direction.SOUTH);
		LAST_PLACED.put(player.uuid, new Placement(before, originX, groundY, originZ));
		PrefabPlacer.Result result = PrefabPlacer.place(world, prefab, originX, groundY, originZ, turns, swaps, player.uuid);
		reply(c, "Placed " + prefab.name + " (" + result.placed() + " blocks): " + describe(swaps) + ".");
		if (!result.missing().isEmpty()) {
			reply(c, "These blocks don't exist any more and were left out: " + String.join(", ", result.missing()) + ".");
		}

		return 1;
	}

	/** The most common blocks, then how many other kinds there are. */
	private static String describeContents(Map<String, Integer> contents) {
		if (contents.isEmpty()) {
			return "nothing but air";
		}

		StringJoiner joiner = new StringJoiner(", ");
		int shown = 0;
		for (Map.Entry<String, Integer> entry : contents.entrySet()) {
			if (shown++ == CONTENTS_SHOWN) {
				joiner.add("and " + (contents.size() - CONTENTS_SHOWN) + " more kinds");
				break;
			}

			joiner.add(entry.getValue() + " " + entry.getKey());
		}

		return joiner.toString();
	}

	private static String describe(Swaps swaps) {
		String wood = swaps.wood() != null ? swaps.wood().id() + " logs" : "logs as built (no trees nearby)";
		String stone = swaps.stone() != null ? swaps.stone().id() : "stone as built (none found underground)";
		String colors = swaps.primary() != null ? colorName(swaps.primary()) + " and " + colorName(swaps.secondary()) : "grays left gray";
		return wood + ", " + stone + ", " + colors;
	}

	private static String colorName(@Nullable DyeColor color) {
		return color != null ? FactionColors.nameOf(color) : "unchanged";
	}

	private static int undo(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Player player = requirePlayer(c.getSource());
		Placement last = LAST_PLACED.remove(player.uuid);
		if (last == null) {
			throw error("You haven't placed a prefab to undo.");
		}

		PrefabPlacer.place(c.getSource().getWorld(), last.before(), last.x(), last.y(), last.z(), 0, Swaps.NONE, null);
		return reply(c, "Put back what was there before.");
	}

	private static Prefab load(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "name");
		try {
			Prefab prefab = PrefabFiles.load(name);
			if (prefab == null) {
				throw error("There's no prefab called " + name + ".");
			}

			return prefab;
		} catch (IOException e) {
			throw error("Couldn't read the prefab " + name + ": " + e.getMessage());
		}
	}
}
