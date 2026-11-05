package lifeblood.command;

import static lifeblood.command.CommandSupport.*;
import static lifeblood.command.CommandTree.branch;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.net.command.CommandSource;
import lifeblood.appearance.Appearance;
import lifeblood.appearance.AppearanceLayer;
import lifeblood.appearance.NpcSkinFiles;
import lifeblood.appearance.PartCatalog;
import lifeblood.appearance.SkinModel;
import lifeblood.entity.EntityNPC;

/** /lb look: how NPCs look: appearance parts, palettes and skin files. Reloading them is under /lb reload. */
final class LookCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root.then(branch("look", "How NPCs look: appearance parts, palettes and skin files.")
			.then(branch("look show", "The parts and colors an NPC's look is built from.")
				.then(npcArgument("npc").executes(LookCommands::describeLook)))
			.then(branch("look randomize", "Gives NPCs a new random look from the parts available.")
				.then(npcsArgument("npcs").executes(c -> {
					List<EntityNPC> npcs = getNpcs(c, "npcs");
					for (EntityNPC npc : npcs) {
						npc.looks.randomize();
						if (npc.looks.getAppearance().isEmpty()) {
							throw error("There are no base parts yet. Put some in " + new File(PartCatalog.getDirectory(), "base"));
						}

						if (!npc.looks.getSkin().isEmpty()) {
							reply(c, npc.nickname + " has a skin file set, which shows instead; /lb look skin " + npc.nickname + " none uses the look.");
						}
					}

					return replyEach(c, npcs, name -> name + " has a new look.", "%d NPCs have a new look.");
				})))
			.then(branch("look set", "Puts a part (or none) on one layer of an NPC's look, with a palette, e.g. hair bob auburn.")
				.then(npcArgument("npc")
					.then(argument("layer", ArgumentTypeString.word()).suggests(LAYER_NAMES)
						.then(argument("part", ArgumentTypeString.word()).suggests(PART_NAMES)
							.executes(c -> setLook(c, ""))
							.then(argument("palette", ArgumentTypeString.word()).suggests(PALETTE_NAMES)
								.executes(c -> setLook(c, ArgumentTypeString.getString(c, "palette"))))))))
			.then(branch("look skin", "Gives NPCs a whole skin file from npc_skins (or none, to show their look), with arm width.")
				.then(npcsArgument("npcs")
					.then(argument("skin", ArgumentTypeString.word()).suggests(SKIN_OR_NONE_NAMES)
						.executes(c -> setSkin(c, SkinModel.AUTO))
						.then(argument("model", ArgumentTypeString.word()).suggests(SKIN_MODELS).executes(c -> {
							String modelName = ArgumentTypeString.getString(c, "model");
							SkinModel model = SkinModel.byName(modelName);
							if (model == null) {
								throw error("Unknown skin model '" + modelName + "'. Use auto, wide or slim.");
							}

							return setSkin(c, model);
						})))))
			.then(branch("look parts", "Lists the appearance parts and palettes.").executes(LookCommands::listParts))
			.then(branch("look skins", "Lists the skin files.").executes(LookCommands::listSkins)));
	}

	private static int describeLook(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
		Appearance appearance = Appearance.decode(npc.looks.getAppearance());
		if (appearance.isEmpty()) {
			return reply(c, npc.nickname + " has no look built from parts" + (npc.looks.getSkin().isEmpty() ? "." : " (it uses the skin '" + npc.looks.getSkin() + "')."));
		}

		reply(c, npc.nickname + "'s look" + (npc.looks.getSkin().isEmpty() ? ":" : " (hidden by the skin '" + npc.looks.getSkin() + "'):"));
		boolean outfit = appearance.get(AppearanceLayer.OUTFIT) != null;
		for (Map.Entry<AppearanceLayer, Appearance.Choice> entry : appearance.choices().entrySet()) {
			Appearance.Choice choice = entry.getValue();
			reply(c, "- " + entry.getKey().id() + ": " + choice.part() + (choice.palette().isEmpty() ? "" : " (" + choice.palette() + ")")
				+ (outfit && entry.getKey().coveredByOutfit() ? ", hidden under the outfit" : ""));
		}

		return 1;
	}

	/** /lb look set &lt;npc&gt; &lt;layer&gt; &lt;part|none&gt; [palette]: hair and facial hair keep sharing a color. */
	private static int setLook(CommandContext<CommandSource> c, String palette) throws CommandSyntaxException {
		EntityNPC npc = getNpc(c, "npc");
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		if (layer == null) {
			throw error("Unknown layer. Layers: base, eyes, mouth, facial_hair, bottom, top, outfit, hair, accessory.");
		}

		String part = ArgumentTypeString.getString(c, "part").toLowerCase(Locale.ROOT);
		PartCatalog catalog = PartCatalog.get();
		Appearance appearance = Appearance.decode(npc.looks.getAppearance());
		if (part.equals("none")) {
			npc.looks.setAppearance(appearance.with(layer, null, ""));
			return reply(c, npc.nickname + " has nothing on the " + layer.id() + " layer now.");
		}

		if (catalog.part(layer, part) == null) {
			throw error("No " + layer.id() + " part called '" + part + "'.");
		}

		if (palette.isEmpty()) {
			Appearance.Choice current = appearance.get(layer);
			palette = current != null ? current.palette() : "";
		}

		if (!palette.isEmpty() && catalog.colors(layer, palette) == null) {
			throw error(layer.paletteGroup == null ? "The " + layer.id() + " layer isn't recolored." : "No " + layer.paletteGroup + " palette called '" + palette + "'.");
		}

		npc.looks.setAppearance(appearance.with(layer, part, palette));
		return reply(c, npc.nickname + " now has " + part + (palette.isEmpty() ? "" : " (" + palette + ")") + " on the " + layer.id() + " layer.");
	}

	private static int listParts(CommandContext<CommandSource> c) {
		PartCatalog catalog = PartCatalog.get();
		reply(c, "Appearance parts (from the mod and " + PartCatalog.getDirectory() + "):");
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			List<String> parts = catalog.allParts(layer);
			reply(c, "- " + layer.id() + ": " + (parts.isEmpty() ? "none yet" : String.join(", ", parts)));
		}

		for (Map.Entry<String, Map<String, int[]>> group : catalog.palettes().entrySet()) {
			reply(c, "- " + group.getKey() + " palettes: " + String.join(", ", group.getValue().keySet()));
		}

		return 1;
	}

	private static int setSkin(CommandContext<CommandSource> c, SkinModel model) throws CommandSyntaxException {
		String given = ArgumentTypeString.getString(c, "skin");
		String skin = given.equalsIgnoreCase(NONE_KEYWORD) ? "" : given;
		List<EntityNPC> npcs = getNpcs(c, "npcs");
		for (EntityNPC npc : npcs) {
			npc.looks.setSkin(skin);
			npc.looks.setSkinModel(model);
		}

		if (!skin.isEmpty() && !NpcSkinFiles.exists(skin)) {
			reply(c, "Note: " + skin + ".png is not in " + NpcSkinFiles.getDirectory());
		}

		String what = skin.isEmpty() ? "shows its look from parts again" : "now uses the skin '" + skin + "' (" + model.name().toLowerCase(Locale.ROOT) + " arms)";
		return replyEach(c, npcs, name -> name + " " + what + ".", "%d NPCs: " + what + ".");
	}

	private static int listSkins(CommandContext<CommandSource> c) {
		List<String> skins = NpcSkinFiles.list();
		if (skins.isEmpty()) {
			return reply(c, "No skins found. Put 64x64 PNG skins in " + NpcSkinFiles.getDirectory());
		}

		return reply(c, "Skins: " + String.join(", ", skins));
	}

	private LookCommands() {
	}
}
