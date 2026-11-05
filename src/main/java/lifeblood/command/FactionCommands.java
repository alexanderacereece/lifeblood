package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.command.CommandSource;
import lifeblood.faction.FactionColors;
import lifeblood.faction.FactionRegistry;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import lifeblood.entity.EntityNPC;
import lifeblood.social.Opinions;

/** /lb faction: factions, their stances, relations, colors and members. How one NPC treats a player is /lb social stance. */
final class FactionCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("faction", "Factions: their stances, relations, colors and members.")
			.then(branch("faction list", "Every faction, how it stands toward others, and its players.").executes(FactionCommands::listFactions))
			.then(branch("faction create", "Makes a faction, with its stance toward anyone without an explicit relation (neutral if left out).")
				.then(argument("faction", ArgumentTypeString.word())
					.executes(c -> createFaction(c, FactionRelation.NEUTRAL))
					.then(argument("stance", ArgumentTypeString.word()).suggests(RELATION_NAMES)
						.executes(c -> createFaction(c, requireRelation(c, "stance"))))))
			.then(branch("faction delete", "Removes a faction; its players become unaffiliated, its NPCs still stand together.")
				.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_NAMES).executes(c -> {
					String faction = requireFaction(c, "faction");
					FactionRegistry.get(c.getSource().getWorld()).remove(faction);
					return reply(c, "Deleted the " + faction + ". Their players are unaffiliated; their NPCs still stand together.");
				})))
			.then(branch("faction default", "Shows or sets a faction's stance toward anyone without an explicit relation.")
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
			.then(branch("faction relation", "Shows or sets how two factions treat each other; default removes the explicit relation.")
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
			.then(branch("faction colors", "Shows or sets a faction's primary and secondary colors, used on its buildings.")
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
			.then(branch("faction join", "Puts NPCs or players in a faction, or none to leave theirs.")
				.then(peopleArgument("targets")
					.then(argument("faction", ArgumentTypeString.word()).suggests(FACTION_OR_NONE_NAMES).executes(c -> {
						String faction = requireFactionOrNone(c, "faction");
						FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
						List<Entity> targets = getPeople(c, "targets");
						for (Entity target : targets) {
							if (target instanceof EntityNPC npc) {
								npc.setFaction(faction);
							} else if (target instanceof Player player) {
								registry.setPlayerFaction(player.uuid, faction);
							}

							if (targets.size() <= 3) {
								reply(c, faction != null ? nameOf(target) + " has joined the " + faction + "." : nameOf(target) + " is now unaffiliated.");
							}
						}

						if (targets.size() > 3) {
							reply(c, faction != null ? targets.size() + " have joined the " + faction + "." : targets.size() + " are now unaffiliated.");
						}

						return targets.size();
					})))));
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

	private FactionCommands() {
	}
}
