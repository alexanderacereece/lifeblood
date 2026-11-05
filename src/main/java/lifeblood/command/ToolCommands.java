package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import lifeblood.ClientHooks;
import lifeblood.appearance.NpcSkinFiles;
import lifeblood.appearance.PartCatalog;
import lifeblood.dialogue.SocialLimits;
import lifeblood.ownership.BlockOwners;
import lifeblood.people.PeopleRegistry;
import lifeblood.perf.NpcProfiler;
import lifeblood.speech.SpeechLibrary;

/**
 * Tools for building and testing the mod rather than playing it:
 * - /lb owner: who placed a block;
 * - /lb reload: reading speech, parts and skins from their folders again;
 * - /lb debug: performance and the social limits.
 */
final class ToolCommands {
	private ToolCommands() {
	}

	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("owner", "Who placed the block at a position.")
			.then(argument("location", ArgumentTypeVector3d.vec3d()).executes(c -> {
				double[] pos = position(c, "location");
				World world = c.getSource().getWorld();
				int x = MathHelper.floor(pos[0]);
				int y = MathHelper.floor(pos[1]);
				int z = MathHelper.floor(pos[2]);
				UUID owner = BlockOwners.ownerAt(world, x, y, z);
				String where = x + " " + y + " " + z;
				return reply(c, owner == null ? "Nobody owns the block at " + where + "." : ownerName(world, owner) + " owns the block at " + where + ".");
			})));

		root.then(branch("reload", "Reads files from their folders again, without restarting.")
			.then(branch("reload all", "Speech, appearance parts and skins.").executes(c -> {
				reloadSpeech(c);
				reloadParts(c);
				return reloadSkins(c);
			}))
			.then(branch("reload speech", "The speech files, bundled and in npc_speech.").executes(ToolCommands::reloadSpeech))
			.then(branch("reload parts", "The appearance parts and palettes, bundled and in npc_parts.").executes(ToolCommands::reloadParts))
			.then(branch("reload skins", "The skin files in npc_skins.").executes(ToolCommands::reloadSkins)));

		root.then(branch("debug", "Measuring and loosening things while testing.")
			.then(branch("debug perf", "How much time NPCs' thinking and pathfinding took over the last second.").executes(c -> {
				int count = getNpcs(c.getSource().getWorld()).size();
				return reply(c, count + " NPC(s) loaded. " + NpcProfiler.report());
			}))
			.then(branch("debug limits", "Shows or switches the one-gift-a-day and five-chats-a-minute limits.")
				.executes(c -> reply(c, "Gift and chat limits are " + (SocialLimits.isEnabled() ? "on" : "off") + "."))
				.then(branch("debug limits on", "One gift per NPC a day, five chats a minute.").executes(c -> {
					SocialLimits.setEnabled(true);
					return reply(c, "Gift and chat limits are on: one gift per NPC a day, five chats a minute.");
				}))
				.then(branch("debug limits off", "No limits, until turned back on or the game restarts.").executes(c -> {
					SocialLimits.setEnabled(false);
					return reply(c, "Gift and chat limits are off (until you turn them back on or restart).");
				}))));
	}

	private static int reloadSpeech(CommandContext<CommandSource> c) {
		return reply(c, "Loaded " + SpeechLibrary.reload() + " speech lines.");
	}

	private static int reloadParts(CommandContext<CommandSource> c) {
		PartCatalog catalog = PartCatalog.reload();
		ClientHooks.reloadAppearanceTextures();

		return reply(c, "Loaded " + catalog.partCount() + " parts and " + catalog.paletteCount() + " palettes.");
	}

	private static int reloadSkins(CommandContext<CommandSource> c) {
		ClientHooks.reloadSkins();

		return reply(c, "NPC skins will be reloaded from " + NpcSkinFiles.getDirectory());
	}

	private static String ownerName(World world, UUID owner) {
		for (Player player : world.players) {
			if (owner.equals(player.uuid)) {
				return player.username;
			}
		}

		PeopleRegistry.Person person = PeopleRegistry.get(world).find(owner);
		return person != null && person.name != null ? person.name : "Someone not around (" + owner + ")";
	}
}
