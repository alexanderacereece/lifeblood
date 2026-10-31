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
import java.util.Map;
import net.minecraft.core.Global;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.arguments.ArgumentTypeVector3d;
import net.minecraft.core.net.command.helpers.DoubleCoordinates;
import net.minecraft.core.world.World;
import npclaude.ai.NpcBehavior;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.ai.behavior.GoToBehavior;
import npclaude.ai.behavior.StayBehavior;
import npclaude.ai.behavior.WanderBehavior;
import npclaude.client.NpcSkins;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.entity.EntityNPC;
import npclaude.entity.NpcGender;
import npclaude.entity.SkinModel;
import npclaude.perf.NpcProfiler;
import npclaude.skin.NpcSkinFiles;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * /npc summon <name> [skin|default] [faction|none]
 * /npc follow|stay|remove <name>
 * /npc goto <name> [<position>|<target>]       (walks there, then stays; no target means to you)
 * /npc teleport <name> [<position>|<target>]   (like /teleport; no target means to you)
 *   A target is another NPC's name, a player's name, or @s for yourself.
 * /npc tpto <name>                           (teleports you to the NPC)
 * /npc gender <name> [male|female]
 * /npc perf                 (AI and pathfinding cost over the last second)
 * /npc wander <name> [radius]
 * /npc skin <name> <skin> [auto|wide|slim]
 * /npc invulnerable <name> <true|false>
 * /npc personality <name> bravery <0-100>
 * /npc faction list
 * /npc faction create <faction> [ally|neutral|hostile]   (its default stance; neutral if left out)
 * /npc faction delete <faction>
 * /npc faction default <faction> [ally|neutral|hostile]
 * /npc faction relation <faction> <faction> [ally|neutral|hostile|default]   (default removes the explicit relation)
 * /npc faction set <name> <faction|none>
 * /npc faction join <faction|none>                       (you join a faction, or leave yours)
 * /npc relation <name> [ally|neutral|hostile|default]    (the NPC's personal feeling toward you; default removes it)
 * /npc list
 * /npc skins [reload]
 *
 * Names with spaces need quotes: /npc follow "Walter White"
 */
public class NpcCommand implements CommandManager.CommandRegistry {
	private static final String NONE_KEYWORD = "none";
	private static final String DEFAULT_KEYWORD = "default";
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
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> RELATION_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> RELATION_OR_DEFAULT_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		builder.suggest(DEFAULT_KEYWORD);
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> FACTION_OR_NONE_NAMES = (c, builder) -> {
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		builder.suggest(NONE_KEYWORD);
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> TARGET_NAMES = (c, builder) -> {
		builder.suggest("@s");
		for (Player player : c.getSource().getWorld().players) {
			builder.suggest(player.username);
		}

		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSource> GENDER_NAMES = (c, builder) -> {
		for (NpcGender gender : NpcGender.values()) {
			builder.suggest(gender.name().toLowerCase(Locale.ROOT));
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
					.executes(c -> summon(c, null, null))
					.then(argument("skin", ArgumentTypeString.word())
						.suggests(SKIN_NAMES)
						.executes(c -> summon(c, ArgumentTypeString.getString(c, "skin"), null))
						.then(argument("faction", ArgumentTypeString.word())
							.suggests(FACTION_OR_NONE_NAMES)
							.executes(c -> summon(c, ArgumentTypeString.getString(c, "skin"), requireFactionOrNone(c, "faction")))))))
			.then(literal("follow")
				.then(npcArgument().executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC npc = findNpc(c);
					npc.brain.set(new FollowPlayerBehavior(player.uuid));
					return reply(c, npc.nickname + " is now following you.");
				})))
			.then(literal("goto")
				.then(npcArgument()
					.executes(c -> {
						Player player = requirePlayer(c.getSource());
						return goTo(c, player.x, player.bb.minY(), player.z, "you");
					})
					.then(argument("position", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c);
						return goTo(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
						Entity target = findTarget(c);
						return goTo(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(literal("teleport")
				.then(npcArgument()
					.executes(c -> {
						Player player = requirePlayer(c.getSource());
						return teleport(c, player.x, player.bb.minY(), player.z, "you");
					})
					.then(argument("position", ArgumentTypeVector3d.vec3d()).executes(c -> {
						double[] pos = position(c);
						return teleport(c, pos[0], pos[1], pos[2], describe(pos));
					}))
					.then(argument("target", ArgumentTypeString.string()).suggests(TARGET_NAMES).executes(c -> {
						Entity target = findTarget(c);
						return teleport(c, target.x, target.bb.minY(), target.z, nameOf(target));
					}))))
			.then(literal("tpto")
				.then(npcArgument().executes(c -> {
					Player player = requirePlayer(c.getSource());
					EntityNPC npc = findNpc(c);
					c.getSource().teleportPlayerToPos(player, npc.x, npc.y, npc.z);
					return reply(c, "Teleported you to " + npc.nickname + ".");
				})))
			.then(literal("gender")
				.then(npcArgument()
					.executes(c -> {
						EntityNPC npc = findNpc(c);
						return reply(c, npc.nickname + " is " + npc.getGender().displayName().toLowerCase(Locale.ROOT) + ".");
					})
					.then(argument("gender", ArgumentTypeString.word()).suggests(GENDER_NAMES).executes(c -> {
						EntityNPC npc = findNpc(c);
						String name = ArgumentTypeString.getString(c, "gender");
						NpcGender gender = NpcGender.byName(name);
						if (gender == null) {
							throw error("Unknown gender '" + name + "'. Use male or female.");
						}

						npc.setGender(gender);
						return reply(c, npc.nickname + " is now " + gender.displayName().toLowerCase(Locale.ROOT) + ".");
					}))))
			.then(literal("perf").executes(c -> {
				int count = getNpcs(c.getSource().getWorld()).size();
				return reply(c, count + " NPC(s) loaded. " + NpcProfiler.report());
			}))
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
				.then(literal("list").executes(NpcCommand::listFactions))
				.then(literal("create")
					.then(argument("faction", ArgumentTypeString.word())
						.executes(c -> createFaction(c, FactionRelation.NEUTRAL))
						.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_NAMES)
							.executes(c -> createFaction(c, requireRelation(c, "stance"))))))
				.then(literal("delete")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES).executes(c -> {
						String faction = requireFaction(c, "faction");
						FactionRegistry.get(c.getSource().getWorld()).remove(faction);
						return reply(c, "Deleted the " + faction + ". Their players are unaffiliated; their NPCs still stand together.");
					})))
				.then(literal("default")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.executes(c -> {
							String faction = requireFaction(c, "faction");
							FactionRelation stance = FactionRegistry.get(c.getSource().getWorld()).getDefault(faction);
							return reply(c, "The " + faction + " are " + describe(stance) + " toward anyone without an explicit relation.");
						})
						.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_NAMES).executes(c -> {
							String faction = requireFaction(c, "faction");
							FactionRelation stance = requireRelation(c, "stance");
							FactionRegistry.get(c.getSource().getWorld()).setDefault(faction, stance);
							return reply(c, "The " + faction + " are now " + describe(stance) + " toward anyone without an explicit relation.");
						}))))
				.then(literal("relation")
					.then(argument("a", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.then(argument("b", ArgumentTypeString.word()).suggests(FACTION_NAMES)
							.executes(NpcCommand::showFactionRelation)
							.then(argument("relation", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
								String a = requireFaction(c, "a");
								String b = requireFaction(c, "b");
								if (a.equals(b)) {
									throw error("A faction is always allied with itself.");
								}

								FactionRelation relation = requireRelationOrDefault(c, "relation");
								FactionRegistry.get(c.getSource().getWorld()).setExplicit(a, b, relation);
								if (relation == null) {
									return reply(c, "The " + a + " and the " + b + " now treat each other by their default stances.");
								}

								return reply(c, "The " + a + " and the " + b + " are now " + describe(relation) + ".");
							})))))
				.then(literal("set")
					.then(npcArgument()
						.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES).executes(c -> {
							EntityNPC npc = findNpc(c);
							String faction = requireFactionOrNone(c, "faction");
							npc.setFaction(faction);
							return reply(c, faction != null ? npc.nickname + " has joined the " + faction + "." : npc.nickname + " is now unaffiliated.");
						}))))
				.then(literal("join")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES).executes(c -> {
						Player player = requirePlayer(c.getSource());
						String faction = requireFactionOrNone(c, "faction");
						FactionRegistry.get(c.getSource().getWorld()).setPlayerFaction(player.uuid, faction);
						return reply(c, faction != null ? "You have joined the " + faction + "." : "You are now unaffiliated.");
					}))))
			.then(literal("relation")
				.then(npcArgument()
					.executes(NpcCommand::showPersonalRelation)
					.then(argument("relation", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
						Player player = requirePlayer(c.getSource());
						EntityNPC npc = findNpc(c);
						FactionRelation relation = requireRelationOrDefault(c, "relation");
						npc.relations.set(player, relation);
						if (relation == null) {
							return reply(c, npc.nickname + " now sees you as its faction does: " + describePersonal(NpcFactions.regard(npc, player)) + ".");
						}

						return reply(c, npc.nickname + " is now " + describePersonal(relation) + ".");
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

	/** skin: null picks a skin matching the name if one exists; "default" means no skin. faction: null means the default. */
	private static int summon(CommandContext<CommandSource> c, @Nullable String skin, @Nullable String faction) throws CommandSyntaxException {
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
		} else if (skin.equalsIgnoreCase("default")) {
			skin = "";
		}

		World world = source.getWorld();
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		if (npc == null) {
			throw error("Could not create the NPC entity.");
		}

		npc.moveTo(pos.x(), pos.y() - player.heightOffset, pos.z(), player.yRot, 0.0F);
		npc.setNickname(name);
		npc.setSkin(skin);
		// A first guess, changeable with /npc gender: slim-armed skins are usually female characters.
		npc.setGender(NpcSkinFiles.isSlim(skin) ? NpcGender.FEMALE : NpcGender.MALE);
		if (faction != null) {
			npc.setFaction(faction);
		}

		npc.setHomeHere();
		npc.brain.set(new StayBehavior());
		npc.spawnInit();
		world.entityJoinedWorld(npc);

		String skinInfo = skin.isEmpty() ? "default skin" : "skin '" + skin + "'";
		if (!skin.isEmpty() && !NpcSkinFiles.exists(skin)) {
			skinInfo += ", which is not in " + NpcSkinFiles.FOLDER_NAME + " yet";
		}

		return reply(c, "Summoned " + name + ", " + factionLabel(npc.getFaction()) + " (" + skinInfo + ").");
	}

	private static double[] position(CommandContext<CommandSource> c) throws CommandSyntaxException {
		DoubleCoordinates position = c.getArgument("position", DoubleCoordinates.class);
		CommandSource source = c.getSource();
		return new double[]{position.getX(source), position.getY(source, true), position.getZ(source)};
	}

	private static String describe(double[] pos) {
		return String.format(Locale.ROOT, "%.0f %.0f %.0f", pos[0], pos[1], pos[2]);
	}

	/** Resolves a target like vanilla /teleport: @s is you, otherwise an NPC's name or a player's name. */
	private static Entity findTarget(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "target");
		if (name.equals("@s")) {
			return requirePlayer(c.getSource());
		}

		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			if (npc.nickname.equalsIgnoreCase(name)) {
				return findNpcNamed(c, name);
			}
		}

		Player player = c.getSource().getWorld().getPlayerEntityByName(name);
		if (player == null) {
			throw error("No NPC or player named '" + name + "'.");
		}

		return player;
	}

	private static String nameOf(Entity entity) {
		if (entity instanceof EntityNPC npc) {
			return npc.nickname;
		}

		return entity instanceof Player player ? player.username : "there";
	}

	private static int goTo(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		npc.brain.set(new GoToBehavior(x, y, z));
		return reply(c, npc.nickname + " is heading to " + where + ".");
	}

	private static int teleport(CommandContext<CommandSource> c, double x, double y, double z, String where) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		npc.navigator.stop();
		npc.teleportTo(x, y, z);
		npc.setHomeHere();
		return reply(c, "Teleported " + npc.nickname + " to " + where + ".");
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
			c.getSource().sendMessage(String.format(Locale.ROOT, "- %s (%s): %s, skin %s, health %d, %s style, faction %s, at %d %d %d%s",
				npc.nickname, npc.getGender().displayName().toLowerCase(Locale.ROOT), doing, skin, npc.getHealth(),
				npc.getCombatStyle().displayName().toLowerCase(Locale.ROOT), npc.getFaction() != null ? npc.getFaction() : "none",
				(int) Math.floor(npc.x), (int) Math.floor(npc.y), (int) Math.floor(npc.z),
				npc.isInvulnerable() ? ", invulnerable" : ""));
		}

		return npcs.size();
	}

	private static int showPersonality(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		return reply(c, npc.nickname + "'s personality: bravery " + Math.round(npc.personality.getBravery() * 100));
	}

	private static int listFactions(CommandContext<CommandSource> c) {
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		c.getSource().sendMessage("Factions in this world:");
		for (String faction : registry.all()) {
			List<String> details = new ArrayList<>();
			details.add(describe(registry.getDefault(faction)) + " by default");
			for (Map.Entry<String, FactionRelation> relation : registry.explicitRelationsOf(faction).entrySet()) {
				details.add(describe(relation.getValue()) + " with " + relation.getKey());
			}

			int players = registry.countPlayers(faction);
			if (players > 0) {
				details.add(players + " player(s)");
			}

			c.getSource().sendMessage("- " + faction + ": " + String.join(", ", details));
		}

		Player player = c.getSource().getSender();
		if (player != null) {
			c.getSource().sendMessage("You are " + factionLabel(registry.getPlayerFaction(player.uuid)) + ".");
		}

		return registry.all().size();
	}

	private static int createFaction(CommandContext<CommandSource> c, FactionRelation stance) throws CommandSyntaxException {
		String faction = ArgumentTypeString.getString(c, "faction").toLowerCase(Locale.ROOT);
		if (!FactionRegistry.isValidId(faction) || faction.equals(NONE_KEYWORD)) {
			throw error("Faction names may only use a-z, 0-9 and _, up to 32 characters, and can't be 'none'.");
		}

		if (!FactionRegistry.get(c.getSource().getWorld()).create(faction, stance)) {
			throw error("The faction '" + faction + "' already exists.");
		}

		return reply(c, "Created the " + faction + ", " + describe(stance) + " toward anyone without an explicit relation.");
	}

	private static int showFactionRelation(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String a = requireFaction(c, "a");
		String b = requireFaction(c, "b");
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		FactionRelation explicit = registry.getExplicit(a, b);
		if (a.equals(b)) {
			return reply(c, "A faction is always allied with itself.");
		}

		if (explicit != null) {
			return reply(c, "The " + a + " and the " + b + " are " + describe(explicit) + ".");
		}

		return reply(c, "No explicit relation: the " + a + " are " + describe(registry.regard(a, b)) + " toward the " + b
			+ ", who are " + describe(registry.regard(b, a)) + " toward them (their default stances).");
	}

	private static int showPersonalRelation(CommandContext<CommandSource> c) throws CommandSyntaxException {
		Player player = requirePlayer(c.getSource());
		EntityNPC npc = findNpc(c);
		FactionRelation personal = npc.relations.get(player);
		FactionRelation overall = NpcFactions.regard(npc, player);
		String member = npc.getFaction() != null ? "one of the " + npc.getFaction() : "someone unaffiliated";
		String reason = personal != null ? "personally" : "as " + member + ", toward someone "
			+ factionLabel(FactionRegistry.get(c.getSource().getWorld()).getPlayerFaction(player.uuid));
		return reply(c, npc.nickname + " is " + describePersonal(overall) + " (" + reason + ").");
	}

	private static String factionLabel(@Nullable String faction) {
		return faction != null ? "of the " + faction : "unaffiliated";
	}

	private static String describePersonal(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "your ally";
			case NEUTRAL -> "neutral toward you";
			case HOSTILE -> "hostile toward you";
		};
	}

	private static String describe(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "allied";
			case NEUTRAL -> "neutral";
			case HOSTILE -> "hostile";
		};
	}

	private static String requireFaction(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String faction = ArgumentTypeString.getString(c, argument).toLowerCase(Locale.ROOT);
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		if (!registry.exists(faction)) {
			throw error("Unknown faction '" + faction + "'. Factions: " + String.join(", ", registry.all()));
		}

		return faction;
	}

	/** A faction name, or null for "none" (unaffiliated). */
	@Nullable
	private static String requireFactionOrNone(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(NONE_KEYWORD) ? null : requireFaction(c, argument);
	}

	private static FactionRelation requireRelation(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, argument);
		FactionRelation relation = FactionRelation.byName(name);
		if (relation == null) {
			throw error("Unknown relation '" + name + "'. Use ally, neutral or hostile.");
		}

		return relation;
	}

	/** A relation, or null for "default" (remove the explicit setting). */
	@Nullable
	private static FactionRelation requireRelationOrDefault(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(DEFAULT_KEYWORD) ? null : requireRelation(c, argument);
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
		return findNpcNamed(c, ArgumentTypeString.getString(c, "npc"));
	}

	private static EntityNPC findNpcNamed(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
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
