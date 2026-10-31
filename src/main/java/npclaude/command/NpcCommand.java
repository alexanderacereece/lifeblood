package npclaude.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.ArgumentTypeBool;
import com.mojang.brigadier.arguments.ArgumentTypeInteger;
import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.builder.ArgumentBuilderRequired;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Global;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBehavior;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.ai.behavior.StayBehavior;
import npclaude.ai.behavior.WanderBehavior;
import npclaude.client.NpcSkins;
import npclaude.combat.NpcFactions;
import npclaude.entity.EntityNPC;
import npclaude.entity.SkinModel;
import npclaude.skin.NpcSkinFiles;
import org.joml.Vector3dc;

/**
 * /npc summon <name> [skin]
 * /npc follow|stay|remove <name>
 * /npc wander <name> [radius]
 * /npc skin <name> <skin> [auto|wide|slim]
 * /npc invulnerable <name> <true|false>
 * /npc personality <name> bravery <0-100>
 * /npc faction <name> <faction>
 * /npc list
 * /npc skins [reload]
 *
 * Names with spaces need quotes: /npc follow "Walter White"
 */
public class NpcCommand implements CommandManager.CommandRegistry {
	private static final SuggestionProvider<CommandSource> NPC_NAMES = (c, builder) -> {
		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> SKIN_NAMES = (c, builder) -> {
		for (String skin : NpcSkinFiles.list()) {
			builder.suggest(skin);
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> FACTION_NAMES = (c, builder) -> {
		for (String faction : NpcFactions.ALL) {
			builder.suggest(faction);
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> SKIN_MODELS = (c, builder) -> {
		for (SkinModel model : SkinModel.values()) {
			builder.suggest(model.name().toLowerCase(Locale.ROOT));
		}

		return builder.buildFuture();
	};

	@Override
	public void register(CommandDispatcher<CommandSource> dispatcher) {
		dispatcher.register(literal("npc")
			.requires(CommandSource::hasAdmin)
			.then(literal("summon")
				.then(argument("name", ArgumentTypeString.string())
					.executes(c -> summon(c, null))
					.then(argument("skin", ArgumentTypeString.word())
						.suggests(SKIN_NAMES)
						.executes(c -> summon(c, ArgumentTypeString.getString(c, "skin"))))))
			.then(literal("follow")
				.then(npcArgument().executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC npc = findNpc(c);
					npc.brain.set(new FollowPlayerBehavior(player.uuid));
					return reply(c, npc.nickname + " is now following you.");
				})))
			.then(literal("stay")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.setHomeHere();
					npc.brain.set(new StayBehavior());
					return reply(c, npc.nickname + " will stay here.");
				})))
			.then(literal("wander")
				.then(npcArgument()
					.executes(c -> wander(c, WanderBehavior.DEFAULT_RADIUS))
					.then(argument("radius", ArgumentTypeInteger.integer(1, 64))
						.executes(c -> wander(c, c.getArgument("radius", Integer.class))))))
			.then(literal("skin")
				.then(npcArgument()
					.then(argument("skin", ArgumentTypeString.word())
						.suggests(SKIN_NAMES)
						.executes(c -> setSkin(c, SkinModel.AUTO))
						.then(argument("model", ArgumentTypeString.word())
							.suggests(SKIN_MODELS)
							.executes(c -> {
								String modelName = ArgumentTypeString.getString(c, "model");
								SkinModel model = SkinModel.byName(modelName);
								if (model == null) {
									throw error("Unknown skin model '" + modelName + "'. Use auto, wide or slim.");
								}

								return setSkin(c, model);
							})))))
			.then(literal("invulnerable")
				.then(npcArgument()
					.then(argument("value", ArgumentTypeBool.bool()).executes(c -> {
						EntityNPC npc = findNpc(c);
						boolean value = ArgumentTypeBool.getBool(c, "value");
						npc.setInvulnerable(value);
						return reply(c, npc.nickname + (value ? " is now invulnerable." : " can now be hurt."));
					}))))
			.then(literal("personality")
				.then(npcArgument()
					.executes(NpcCommand::showPersonality)
					.then(literal("bravery")
						.then(argument("value", ArgumentTypeInteger.integer(0, 100)).executes(c -> {
							EntityNPC npc = findNpc(c);
							int value = c.getArgument("value", Integer.class);
							npc.personality.setBravery(value / 100.0F);
							return reply(c, npc.nickname + "'s bravery is now " + value + ".");
						})))))
			.then(literal("faction")
				.then(npcArgument()
					.then(argument("faction", ArgumentTypeString.word())
						.suggests(FACTION_NAMES)
						.executes(c -> {
							EntityNPC npc = findNpc(c);
							String faction = ArgumentTypeString.getString(c, "faction").toLowerCase(Locale.ROOT);
							if (!NpcFactions.isKnown(faction)) {
								throw error("Unknown faction '" + faction + "'. Factions: " + String.join(", ", NpcFactions.ALL));
							}

							npc.setFaction(faction);
							return reply(c, npc.nickname + " has joined the " + faction + ".");
						}))))
			.then(literal("remove")
				.then(npcArgument().executes(c -> {
					EntityNPC npc = findNpc(c);
					npc.remove();
					return reply(c, "Removed " + npc.nickname + ".");
				})))
			.then(literal("list").executes(NpcCommand::list))
			.then(literal("skins")
				.executes(NpcCommand::listSkins)
				.then(literal("reload").executes(c -> {
					if (!Global.isServer) {
						NpcSkins.reload();
					}

					return reply(c, "NPC skins will be reloaded from " + NpcSkinFiles.getDirectory());
				}))));
	}

	private static int summon(CommandContext<CommandSource> c, String skin) throws CommandSyntaxException {
		CommandSource source = c.getSource();
		Player player = requirePlayer(source);
		Vector3dc pos = source.getCoordinates(false);
		if (pos == null) {
			throw error("You must be in a world to summon an NPC.");
		}

		String name = ArgumentTypeString.getString(c, "name").trim();
		if (name.isEmpty() || name.length() > 32) {
			throw error("NPC names must be 1 to 32 characters long.");
		}

		// With no skin given, use a skin file with the same name as the NPC if there is one.
		if (skin == null) {
			String fromName = name.toLowerCase(Locale.ROOT).replace(' ', '_');
			skin = NpcSkinFiles.exists(fromName) ? fromName : "";
		}

		World world = source.getWorld();
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		if (npc == null) {
			throw error("Could not create the NPC entity.");
		}

		npc.moveTo(pos.x(), pos.y() - player.heightOffset, pos.z(), player.yRot, 0.0F);
		npc.setNickname(name);
		npc.setSkin(skin);
		npc.setHomeHere();
		npc.brain.set(new StayBehavior());
		npc.spawnInit();
		world.entityJoinedWorld(npc);

		String skinInfo = skin.isEmpty() ? "default skin" : "skin '" + skin + "'";
		if (!skin.isEmpty() && !NpcSkinFiles.exists(skin)) {
			skinInfo += ", which is not in " + NpcSkinFiles.FOLDER_NAME + " yet";
		}

		return reply(c, "Summoned " + name + " (" + skinInfo + ").");
	}

	private static int wander(CommandContext<CommandSource> c, int radius) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		npc.setHomeHere();
		npc.brain.set(new WanderBehavior(radius));
		return reply(c, npc.nickname + " will wander within " + radius + " blocks of here.");
	}

	private static int setSkin(CommandContext<CommandSource> c, SkinModel model) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		String skin = ArgumentTypeString.getString(c, "skin");
		npc.setSkin(skin);
		npc.setSkinModel(model);
		String message = npc.nickname + " now uses skin '" + skin + "' (" + model.name().toLowerCase(Locale.ROOT) + " arms).";
		if (!NpcSkinFiles.exists(skin)) {
			message += " Note: " + skin + ".png is not in " + NpcSkinFiles.getDirectory();
		}

		return reply(c, message);
	}

	private static int list(CommandContext<CommandSource> c) {
		List<EntityNPC> npcs = getNpcs(c.getSource().getWorld());
		if (npcs.isEmpty()) {
			return reply(c, "There are no NPCs loaded.");
		}

		c.getSource().sendMessage(npcs.size() + " NPC(s) loaded:");
		for (EntityNPC npc : npcs) {
			NpcBehavior active = npc.brain.getActive();
			String doing = active != null ? active.describe() : "idle";
			String skin = npc.getSkin().isEmpty() ? "default" : npc.getSkin();
			c.getSource().sendMessage(String.format(Locale.ROOT, "- %s: %s, skin %s, health %d, %s style, faction %s, at %d %d %d%s",
				npc.nickname, doing, skin, npc.getHealth(), npc.getCombatStyle().displayName().toLowerCase(Locale.ROOT), npc.getFaction(),
				(int) Math.floor(npc.x), (int) Math.floor(npc.y), (int) Math.floor(npc.z),
				npc.isInvulnerable() ? ", invulnerable" : ""));
		}

		return npcs.size();
	}

	private static int showPersonality(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		return reply(c, npc.nickname + "'s personality: bravery " + Math.round(npc.personality.getBravery() * 100));
	}

	private static int listSkins(CommandContext<CommandSource> c) {
		List<String> skins = NpcSkinFiles.list();
		if (skins.isEmpty()) {
			return reply(c, "No skins found. Put 64x64 PNG skins in " + NpcSkinFiles.getDirectory());
		}

		return reply(c, "Skins: " + String.join(", ", skins));
	}

	// Helpers

	private static List<EntityNPC> getNpcs(World world) {
		List<EntityNPC> npcs = new ArrayList<>();
		if (world != null) {
			for (Entity entity : world.getLoadedEntityList()) {
				if (entity instanceof EntityNPC npc && !npc.removed) {
					npcs.add(npc);
				}
			}
		}

		return npcs;
	}

	/** Finds the NPC named in the "npc" argument; with duplicates, the nearest one wins. */
	private static EntityNPC findNpc(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "npc");
		Player player = c.getSource().getSender();
		EntityNPC best = null;
		double bestDistance = Double.MAX_VALUE;
		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			if (!npc.nickname.equalsIgnoreCase(name)) {
				continue;
			}

			double distance = player != null ? npc.distanceToSqr(player) : 0.0;
			if (distance < bestDistance) {
				best = npc;
				bestDistance = distance;
			}
		}

		if (best == null) {
			throw error("No loaded NPC named '" + name + "'. Try /npc list.");
		}

		return best;
	}

	private static Player requirePlayer(CommandSource source) throws CommandSyntaxException {
		Player player = source.getSender();
		if (player == null) {
			throw error("This command must be run by a player.");
		}

		return player;
	}

	private static int reply(CommandContext<CommandSource> c, String message) {
		c.getSource().sendMessage(message);
		return 1;
	}

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(() -> message).create();
	}

	private static ArgumentBuilderLiteral<CommandSource> literal(String name) {
		return ArgumentBuilderLiteral.literal(name);
	}

	private static <T> ArgumentBuilderRequired<CommandSource, T> argument(String name, ArgumentType<T> type) {
		return ArgumentBuilderRequired.argument(name, type);
	}

	private static ArgumentBuilderRequired<CommandSource, String> npcArgument() {
		return NpcCommand.<String>argument("npc", ArgumentTypeString.string()).suggests(NPC_NAMES);
	}
}
