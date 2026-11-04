package lifeblood.command;

import static lifeblood.command.CommandSupport.*;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import lifeblood.combat.FactionColors;
import lifeblood.combat.FactionRegistry;
import lifeblood.combat.FactionRelation;
import lifeblood.combat.NpcFactions;
import lifeblood.entity.EntityNPC;
import lifeblood.social.Opinions;

/** /npc subcommands: factions, their relations, and how NPCs treat you. */
final class FactionCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
			.then(literal("faction")
				.then(literal("list").executes(FactionCommands::listFactions))
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
				.then(literal("colors")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.executes(c -> {
							String faction = requireFaction(c, "faction");
							FactionColors colors = FactionRegistry.get(c.getSource().getWorld()).getColors(faction);
							return reply(c, "The " + faction + "'s colors are " + FactionColors.nameOf(colors.primary()) + " and "
								+ FactionColors.nameOf(colors.secondary()) + ".");
						})
						.then(argument("primary", ArgumentTypeString.word()).suggests(COLOR_NAMES)
							.then(argument("secondary", ArgumentTypeString.word()).suggests(COLOR_NAMES).executes(c -> {
								String faction = requireFaction(c, "faction");
								FactionColors colors = new FactionColors(requireColor(c, "primary"), requireColor(c, "secondary"));
								FactionRegistry.get(c.getSource().getWorld()).setColors(faction, colors);
								return reply(c, "The " + faction + "'s colors are now " + FactionColors.nameOf(colors.primary()) + " and "
									+ FactionColors.nameOf(colors.secondary()) + ".");
							})))))
				.then(literal("relation")
					.then(argument("a", ArgumentTypeString.word()).suggests(FACTION_NAMES)
						.then(argument("b", ArgumentTypeString.word()).suggests(FACTION_NAMES)
							.executes(FactionCommands::showFactionRelation)
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
					.executes(FactionCommands::showPersonalRelation)
					.then(argument("relation", ArgumentTypeString.word()).suggests(RELATION_OR_DEFAULT_NAMES).executes(c -> {
						Player player = requirePlayer(c.getSource());
						EntityNPC npc = findNpc(c);
						FactionRelation relation = requireRelationOrDefault(c, "relation");
						npc.relations.set(player, relation);
						if (relation == null) {
							return reply(c, npc.nickname + " now sees you as its faction does: " + describePersonal(NpcFactions.regard(npc, player)) + ".");
						}

						return reply(c, npc.nickname + " is now " + describePersonal(relation) + ".");
					}))));
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
		float opinion = Opinions.of(npc, player);
		String member = npc.getFaction() != null ? "one of the " + npc.getFaction() : "someone unaffiliated";
		String reason;
		if (personal != null) {
			reason = "set by command";
		} else if (opinion >= Opinions.ALLY_THRESHOLD || opinion <= Opinions.hostileThreshold(npc)) {
			reason = "because its opinion of you is " + Math.round(opinion);
		} else {
			reason = "as " + member + ", toward someone " + factionLabel(FactionRegistry.get(c.getSource().getWorld()).getPlayerFaction(player.uuid));
		}

		reply(c, npc.nickname + " is " + describePersonal(overall) + " (" + reason + ").");
		if (overall == FactionRelation.HOSTILE && !player.getGamemode().hasHostileMobs()) {
			reply(c, "You're in a game mode monsters ignore, so " + npc.nickname + " won't attack you either.");
		}

		return 1;
	}

	private static String describePersonal(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "your ally";
			case NEUTRAL -> "neutral toward you";
			case HOSTILE -> "hostile toward you";
		};
	}

	private FactionCommands() {
	}
}
