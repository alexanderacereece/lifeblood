package lifeblood.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.PlayerSkinParser;
import net.minecraft.client.render.TextureManager;
import net.minecraft.client.render.texture.Texture;
import lifeblood.Lifeblood;
import lifeblood.appearance.SkinModel;
import lifeblood.appearance.NpcSkinFiles;
import org.jetbrains.annotations.Nullable;

/**
 * Loads NPC skins from the npc_skins folder and turns them into textures.
 * BTA's PlayerSkinParser does the heavy lifting: it upgrades old 64x32 skins to 64x64
 * and applies the steve/alex mask so the base layer is opaque and the outer layer keeps its transparency.
 */
@Environment(EnvType.CLIENT)
public final class NpcSkins {
	public static final String DEFAULT_TEXTURE = "/assets/minecraft/textures/entity/char/0.png";

	/** Raw images by skin name; a null value means the file is missing or invalid. */
	private static final Map<String, RawSkin> RAW = new HashMap<>();
	/** Uploaded textures by "name:wide" / "name:slim". */
	private static final Map<String, Texture> TEXTURES = new HashMap<>();

	private NpcSkins() {
	}

	public record LoadedSkin(Texture texture, boolean slim) {
	}

	private record RawSkin(BufferedImage image, boolean slim) {
	}

	@Nullable
	public static LoadedSkin get(TextureManager textureManager, String name, SkinModel model) {
		if (name.isEmpty()) {
			return null;
		}

		if (!RAW.containsKey(name)) {
			RAW.put(name, readSkin(name));
		}

		RawSkin raw = RAW.get(name);
		if (raw == null) {
			return null;
		}

		boolean slim = model == SkinModel.AUTO ? raw.slim() : model == SkinModel.SLIM;
		Texture texture = TEXTURES.computeIfAbsent(name + (slim ? ":slim" : ":wide"), key -> {
			PlayerSkinParser parser = slim ? PlayerSkinParser.instanceAlex : PlayerSkinParser.instanceSteve;
			return textureManager.loadBufferedTexture(parser.parseImage(raw.image()));
		});
		return new LoadedSkin(texture, slim);
	}

	/** Forgets every loaded skin so edited files are read again. */
	public static void reload() {
		for (Texture texture : TEXTURES.values()) {
			texture.delete();
		}

		TEXTURES.clear();
		RAW.clear();
	}

	@Nullable
	private static RawSkin readSkin(String name) {
		File file = NpcSkinFiles.getFile(name);
		if (!file.isFile()) {
			Lifeblood.LOGGER.warn("NPC skin '{}' not found at {}", name, file);
			return null;
		}

		try {
			BufferedImage image = ImageIO.read(file);
			if (image == null || image.getWidth() != 64 || image.getHeight() != 64 && image.getHeight() != 32) {
				Lifeblood.LOGGER.warn("NPC skin '{}' must be a 64x64 or 64x32 PNG", name);
				return null;
			}

			return new RawSkin(image, NpcSkinFiles.isSlim(image));
		} catch (IOException e) {
			Lifeblood.LOGGER.warn("Could not read NPC skin '{}'", name, e);
			return null;
		}
	}
}
