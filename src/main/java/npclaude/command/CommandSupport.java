package npclaude.command;

import com.mojang.brigadier.arguments.ArgumentType;
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
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.CommandSource;
import net.minecraft.core.net.command.helpers.DoubleCoordinates;
import net.minecraft.core.world.World;
import npclaude.appearance.AppearanceLayer;
import npclaude.appearance.PartCatalog;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.entity.EntityNPC;
import npclaude.entity.HealthMode;
import npclaude.entity.NpcGender;
import npclaude.entity.SkinModel;
import npclaude.family.Romance;
import npclaude.personality.Quirk;
import npclaude.personality.TraitAxis;
import npclaude.skin.NpcSkinFiles;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import org.jetbrains.annotations.Nullable;

/** What the /npc subcommands share: argument suggestions, finding NPCs and targets, and replying. */
final class CommandSupport {
	static final String NONE_KEYWORD = "none";
	static final String DEFAULT_KEYWORD = "default";

	static final SuggestionProvider<CommandSource> NPC_NAMES = (c, builder) -> {
		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> SKIN_NAMES = (c, builder) -> {
		for (String skin : NpcSkinFiles.list()) {
			builder.suggest(skin);
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> FACTION_NAMES = (c, builder) -> {
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> RELATION_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> RELATION_OR_DEFAULT_NAMES = (c, builder) -> {
		for (FactionRelation relation : FactionRelation.values()) {
			builder.suggest(relation.id());
		}

		builder.suggest(DEFAULT_KEYWORD);
		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> FACTION_OR_NONE_NAMES = (c, builder) -> {
		for (String faction : FactionRegistry.get(c.getSource().getWorld()).all()) {
			builder.suggest(faction);
		}

		builder.suggest(NONE_KEYWORD);
		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> TARGET_NAMES = (c, builder) -> {
		builder.suggest("@s");
		for (Player player : c.getSource().getWorld().players) {
			builder.suggest(player.username);
		}

		for (EntityNPC npc : getNpcs(c.getSource().getWorld())) {
			builder.suggest(ArgumentTypeString.escapeIfRequired(npc.nickname));
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> GENDER_NAMES = (c, builder) -> {
		for (NpcGender gender : NpcGender.values()) {
			builder.suggest(gender.name().toLowerCase(Locale.ROOT));
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> AXIS_NAMES = (c, builder) -> {
		for (TraitAxis axis : TraitAxis.values()) {
			builder.suggest(axis.id());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> QUIRK_NAMES = (c, builder) -> {
		for (Quirk quirk : Quirk.values()) {
			builder.suggest(quirk.id());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> LAYER_NAMES = (c, builder) -> {
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			builder.suggest(layer.id());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> PART_NAMES = (c, builder) -> {
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		builder.suggest("none");
		if (layer != null) {
			for (String part : PartCatalog.get().allParts(layer)) {
				builder.suggest(part);
			}
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> PALETTE_NAMES = (c, builder) -> {
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		if (layer != null && layer.paletteGroup != null) {
			for (String palette : PartCatalog.get().paletteNames(layer.paletteGroup)) {
				builder.suggest(palette);
			}
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> MEMORY_NAMES = (c, builder) -> {
		for (MemoryType type : MemoryType.values()) {
			builder.suggest(type.id());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> HEALTH_MODES = (c, builder) -> {
		for (HealthMode mode : HealthMode.values()) {
			builder.suggest(mode.displayName());
		}

		return builder.buildFuture();
	};

	static final SuggestionProvider<CommandSource> SKIN_MODELS = (c, builder) -> {
		for (SkinModel model : SkinModel.values()) {
			builder.suggest(model.name().toLowerCase(Locale.ROOT));
		}

		return builder.buildFuture();
	};

	static void registerPerson(Entity entity) {
		if (entity instanceof EntityNPC other) {
			Romance.register(other);
		} else if (entity instanceof Player player) {
			Romance.register(player);
		}
	}

	static UUID requireId(Entity entity) throws CommandSyntaxException {
		UUID id = Opinions.idOf(entity);
		if (id == null) {
			throw error("That's not a person.");
		}

		return id;
	}

	static double[] position(CommandContext<CommandSource> c) throws CommandSyntaxException {
		DoubleCoordinates position = c.getArgument("position", DoubleCoordinates.class);
		CommandSource source = c.getSource();
		return new double[]{position.getX(source), position.getY(source, true), position.getZ(source)};
	}

	static String describe(double[] pos) {
		return String.format(Locale.ROOT, "%.0f %.0f %.0f", pos[0], pos[1], pos[2]);
	}

	/** Resolves a target like vanilla /teleport: @s is you, otherwise an NPC's name or a player's name. */
	static Entity findTarget(CommandContext<CommandSource> c) throws CommandSyntaxException {
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

	static String nameOf(Entity entity) {
		if (entity instanceof EntityNPC npc) {
			return npc.nickname;
		}

		return entity instanceof Player player ? player.username : "there";
	}

	static String describeLike(float like) {
		if (like >= 0.6F) {
			return "loves";
		} else if (like >= 0.2F) {
			return "likes";
		} else if (like > -0.2F) {
			return "doesn't mind";
		} else if (like > -0.6F) {
			return "dislikes";
		}

		return "hates";
	}

	static String itemName(Item item) {
		return new ItemStack(item).getDisplayName().toLowerCase(Locale.ROOT);
	}

	static Quirk requireQuirk(CommandContext<CommandSource> c) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, "quirk");
		Quirk quirk = Quirk.byName(name);
		if (quirk == null) {
			throw error("Unknown quirk '" + name + "'.");
		}

		return quirk;
	}

	static Item requireHeldItem(CommandContext<CommandSource> c) throws CommandSyntaxException {
		ItemStack held = requirePlayer(c.getSource()).inventory.getCurrentItem();
		if (held == null) {
			throw error("Hold the item you want to ask about.");
		}

		return held.getItem();
	}

	static String factionLabel(@Nullable String faction) {
		return faction != null ? "of the " + faction : "unaffiliated";
	}

	static String describe(FactionRelation relation) {
		return switch (relation) {
			case ALLY -> "allied";
			case NEUTRAL -> "neutral";
			case HOSTILE -> "hostile";
		};
	}

	static String requireFaction(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String faction = ArgumentTypeString.getString(c, argument).toLowerCase(Locale.ROOT);
		FactionRegistry registry = FactionRegistry.get(c.getSource().getWorld());
		if (!registry.exists(faction)) {
			throw error("Unknown faction '" + faction + "'. Factions: " + String.join(", ", registry.all()));
		}

		return faction;
	}

	/** A faction name, or null for "none" (unaffiliated). */
	@Nullable
	static String requireFactionOrNone(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(NONE_KEYWORD) ? null : requireFaction(c, argument);
	}

	static FactionRelation requireRelation(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		String name = ArgumentTypeString.getString(c, argument);
		FactionRelation relation = FactionRelation.byName(name);
		if (relation == null) {
			throw error("Unknown relation '" + name + "'. Use ally, neutral or hostile.");
		}

		return relation;
	}

	/** A relation, or null for "default" (remove the explicit setting). */
	@Nullable
	static FactionRelation requireRelationOrDefault(CommandContext<CommandSource> c, String argument) throws CommandSyntaxException {
		return ArgumentTypeString.getString(c, argument).equalsIgnoreCase(DEFAULT_KEYWORD) ? null : requireRelation(c, argument);
	}

	static List<EntityNPC> getNpcs(World world) {
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
	static EntityNPC findNpc(CommandContext<CommandSource> c) throws CommandSyntaxException {
		return findNpcNamed(c, ArgumentTypeString.getString(c, "npc"));
	}

	static EntityNPC findNpcNamed(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
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

	static Player requirePlayer(CommandSource source) throws CommandSyntaxException {
		Player player = source.getSender();
		if (player == null) {
			throw error("This command must be run by a player.");
		}

		return player;
	}

	static int reply(CommandContext<CommandSource> c, String message) {
		c.getSource().sendMessage(message);
		return 1;
	}

	static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(() -> message).create();
	}

	static ArgumentBuilderLiteral<CommandSource> literal(String name) {
		return ArgumentBuilderLiteral.literal(name);
	}

	static <T> ArgumentBuilderRequired<CommandSource, T> argument(String name, ArgumentType<T> type) {
		return ArgumentBuilderRequired.argument(name, type);
	}

	static ArgumentBuilderRequired<CommandSource, String> npcArgument() {
		return CommandSupport.<String>argument("npc", ArgumentTypeString.string()).suggests(NPC_NAMES);
	}

	private CommandSupport() {
	}
}
