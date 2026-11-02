package npclaude.command;

import static npclaude.command.CommandSupport.*;

import com.mojang.brigadier.arguments.ArgumentTypeString;
import com.mojang.brigadier.builder.ArgumentBuilderLiteral;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Global;
import net.minecraft.core.net.command.CommandSource;
import npclaude.appearance.Appearance;
import npclaude.appearance.AppearanceLayer;
import npclaude.appearance.PartCatalog;
import npclaude.client.AppearanceTextures;
import npclaude.client.NpcSkins;
import npclaude.entity.EntityNPC;
import npclaude.entity.SkinModel;
import npclaude.skin.NpcSkinFiles;

/** /npc subcommands: how NPCs look: skin files, appearance parts and palettes. */
final class LookCommands {
	static void addTo(ArgumentBuilderLiteral<CommandSource> root) {
		root
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
			.then(literal("look")
				.then(npcArgument()
					.executes(LookCommands::describeLook)
					.then(literal("randomize").executes(c -> {
						EntityNPC npc = findNpc(c);
						npc.setAppearance(Appearance.random(PartCatalog.get(), npc.getGender(), npc.getRandom(), npc.life.getHomeWarmth()));
						if (npc.getAppearance().isEmpty()) {
							throw error("There are no base parts yet. Put some in " + new File(PartCatalog.getDirectory(), "base"));
						}

						reply(c, npc.nickname + " has a new look.");
						if (!npc.getSkin().isEmpty()) {
							reply(c, "It has a skin file set, which shows instead. /npc skin " + npc.nickname + " default to use the look.");
						}

						return 1;
					}))
					.then(argument("layer", ArgumentTypeString.word()).suggests(LAYER_NAMES)
						.then(argument("part", ArgumentTypeString.word()).suggests(PART_NAMES)
							.executes(c -> setLook(c, ""))
							.then(argument("palette", ArgumentTypeString.word()).suggests(PALETTE_NAMES)
								.executes(c -> setLook(c, ArgumentTypeString.getString(c, "palette"))))))))
			.then(literal("parts")
				.executes(LookCommands::listParts)
				.then(literal("reload").executes(c -> {
					PartCatalog catalog = PartCatalog.reload();
					if (!Global.isServer) {
						AppearanceTextures.reload();
					}

					return reply(c, "Loaded " + catalog.partCount() + " parts and " + catalog.paletteCount() + " palettes.");
				})))
			.then(literal("skins")
				.executes(LookCommands::listSkins)
				.then(literal("reload").executes(c -> {
					if (!Global.isServer) {
						NpcSkins.reload();
					}

					return reply(c, "NPC skins will be reloaded from " + NpcSkinFiles.getDirectory());
				})));
	}

	private static int describeLook(CommandContext<CommandSource> c) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		Appearance appearance = Appearance.decode(npc.getAppearance());
		if (appearance.isEmpty()) {
			return reply(c, npc.nickname + " has no look built from parts" + (npc.getSkin().isEmpty() ? "." : " (it uses the skin '" + npc.getSkin() + "')."));
		}

		reply(c, npc.nickname + "'s look" + (npc.getSkin().isEmpty() ? ":" : " (hidden by the skin '" + npc.getSkin() + "'):"));
		boolean outfit = appearance.get(AppearanceLayer.OUTFIT) != null;
		for (Map.Entry<AppearanceLayer, Appearance.Choice> entry : appearance.choices().entrySet()) {
			Appearance.Choice choice = entry.getValue();
			reply(c, "- " + entry.getKey().id() + ": " + choice.part() + (choice.palette().isEmpty() ? "" : " (" + choice.palette() + ")")
				+ (outfit && entry.getKey().coveredByOutfit() ? ", hidden under the outfit" : ""));
		}

		return 1;
	}

	/** /npc look <npc> <layer> <part|none> [palette]: hair and facial hair keep sharing a color. */
	private static int setLook(CommandContext<CommandSource> c, String palette) throws CommandSyntaxException {
		EntityNPC npc = findNpc(c);
		AppearanceLayer layer = AppearanceLayer.byName(ArgumentTypeString.getString(c, "layer"));
		if (layer == null) {
			throw error("Unknown layer. Layers: base, eyes, mouth, facial_hair, bottom, top, hair, accessory.");
		}

		String part = ArgumentTypeString.getString(c, "part").toLowerCase(Locale.ROOT);
		PartCatalog catalog = PartCatalog.get();
		Appearance appearance = Appearance.decode(npc.getAppearance());
		if (part.equals("none")) {
			npc.setAppearance(appearance.with(layer, null, ""));
			return reply(c, npc.nickname + " has nothing on the " + layer.id() + " layer now.");
		}

		if (catalog.part(layer, part) == null) {
			throw error("No " + layer.id() + " part called '" + part + "'.");
		}

		if (palette.isEmpty()) {
			Appearance.Choice current = appearance.get(layer);
			palette = current != null ? current.palette() : "";
		}

		if (!palette.isEmpty() && (layer.paletteGroup == null || catalog.palette(layer.paletteGroup, palette) == null)) {
			throw error(layer.paletteGroup == null ? "The " + layer.id() + " layer isn't recolored." : "No " + layer.paletteGroup + " palette called '" + palette + "'.");
		}

		npc.setAppearance(appearance.with(layer, part, palette));
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
