package lifeblood.client;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.PlayerSkinParser;
import net.minecraft.client.render.TextureManager;
import lifeblood.Lifeblood;
import lifeblood.appearance.Appearance;
import lifeblood.appearance.AppearanceLayer;
import lifeblood.appearance.NpcSkinFiles;
import lifeblood.appearance.PartCatalog;
import lifeblood.appearance.SkinModel;
import lifeblood.appearance.SlimArms;
import org.jetbrains.annotations.Nullable;

/**
 * Builds an NPC's skin from its appearance: each layer's part, recolored with its palette, drawn over the ones
 * before it. Textures are cached by the exact appearance, so NPCs who look alike share one, and building one only
 * happens the first time it's seen.
 */
@Environment(EnvType.CLIENT)
public final class AppearanceTextures {
	/** The five grays a recolorable part is drawn with, darkest first; each becomes the palette color at its index. */
	private static final int[] GRAYS = {0x202020, 0x505050, 0x808080, 0xB0B0B0, 0xE0E0E0};

	private static final Map<String, NpcSkins.LoadedSkin> TEXTURES = new HashMap<>();
	/** Part images by source, read once; a null value means the file couldn't be read. */
	private static final Map<String, BufferedImage> IMAGES = new HashMap<>();

	private AppearanceTextures() {
	}

	@Nullable
	public static NpcSkins.LoadedSkin get(TextureManager textureManager, String encoded, SkinModel model) {
		if (encoded.isEmpty()) {
			return null;
		}

		String key = encoded + "|" + model.name();
		if (TEXTURES.containsKey(key)) {
			return TEXTURES.get(key);
		}

		NpcSkins.LoadedSkin skin = build(textureManager, Appearance.decode(encoded), model);
		TEXTURES.put(key, skin);
		return skin;
	}

	/** Forgets every built texture and part image, so edited parts and palettes show up. */
	public static void reload() {
		for (NpcSkins.LoadedSkin skin : TEXTURES.values()) {
			if (skin != null) {
				skin.texture().delete();
			}
		}

		TEXTURES.clear();
		IMAGES.clear();
	}

	@Nullable
	private static NpcSkins.LoadedSkin build(TextureManager textureManager, Appearance appearance, SkinModel model) {
		PartCatalog catalog = PartCatalog.get();
		Appearance.Choice baseChoice = appearance.get(AppearanceLayer.BASE);
		PartCatalog.Part base = baseChoice != null ? catalog.part(AppearanceLayer.BASE, baseChoice.part()) : null;
		if (base == null) {
			return null;
		}

		// The skin model decides the arm width (women are slim unless set otherwise), or else the base part; every
		// part follows it. A wide part on a slim NPC has its arms squeezed, unless there's a slim version of it.
		BufferedImage baseImage = read(base.source(model == SkinModel.SLIM));
		if (baseImage == null) {
			return null;
		}

		boolean slim = model == SkinModel.AUTO ? NpcSkinFiles.isSlim(baseImage) : model == SkinModel.SLIM;
		BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		boolean outfit = appearance.get(AppearanceLayer.OUTFIT) != null;
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			if (outfit && layer.coveredByOutfit()) {
				continue;
			}

			Appearance.Choice choice = appearance.get(layer);
			PartCatalog.Part part = choice != null ? catalog.part(layer, choice.part()) : null;
			PartCatalog.Source file = part != null ? part.source(slim) : null;
			BufferedImage image = slim && file != null && file != part.slim() ? readSqueezed(file) : read(file);
			if (image == null) {
				continue;
			}

			// The mouth takes the skin's color and a beard the hair's (falling back to their own if that layer is empty).
			Appearance.Choice source = layer.paletteFrom() != null && appearance.get(layer.paletteFrom()) != null ? appearance.get(layer.paletteFrom()) : choice;
			int[] palette = layer.paletteGroup != null && !source.palette().isEmpty() ? catalog.palette(layer.paletteGroup, source.palette()) : null;
			drawOver(result, image, palette);
		}

		PlayerSkinParser parser = slim ? PlayerSkinParser.instanceAlex : PlayerSkinParser.instanceSteve;
		return new NpcSkins.LoadedSkin(textureManager.loadBufferedTexture(parser.parseImage(result)), slim);
	}

	/** Alpha-blends a part over the image so far, swapping the five grays for the palette's colors first. */
	private static void drawOver(BufferedImage target, BufferedImage part, @Nullable int[] palette) {
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int argb = part.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0) {
					continue;
				}

				int rgb = argb & 0xFFFFFF;
				if (palette != null) {
					for (int i = 0; i < GRAYS.length; i++) {
						if (rgb == GRAYS[i]) {
							rgb = palette[i];
							break;
						}
					}
				}

				if (alpha == 255) {
					target.setRGB(x, y, 0xFF000000 | rgb);
				} else {
					target.setRGB(x, y, blend(target.getRGB(x, y), rgb, alpha));
				}
			}
		}
	}

	private static int blend(int below, int rgb, int alpha) {
		int belowAlpha = below >>> 24;
		float a = alpha / 255.0F;
		int outAlpha = Math.round(alpha + belowAlpha * (1.0F - a));
		int r = Math.round(((rgb >> 16) & 0xFF) * a + ((below >> 16) & 0xFF) * (1.0F - a));
		int g = Math.round(((rgb >> 8) & 0xFF) * a + ((below >> 8) & 0xFF) * (1.0F - a));
		int b = Math.round((rgb & 0xFF) * a + (below & 0xFF) * (1.0F - a));
		return outAlpha << 24 | r << 16 | g << 8 | b;
	}

	/**
	 * Turns an old-style 64x32 part into the 64x64 layout, the way the game upgrades old skins: the left arm and leg
	 * are mirrored copies of the right ones. A hat area that's completely filled in (as on a solid base) is cleared,
	 * as old skins' hats were, so it doesn't become a box around the head.
	 */
	private static BufferedImage upgradeLegacy(BufferedImage old) {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = image.getGraphics();
		graphics.drawImage(old, 0, 0, null);
		int[][] copies = {
			{24, 48, 20, 52, 4, 16, 8, 20}, {28, 48, 24, 52, 8, 16, 12, 20}, {20, 52, 16, 64, 8, 20, 12, 32}, {24, 52, 20, 64, 4, 20, 8, 32},
			{28, 52, 24, 64, 0, 20, 4, 32}, {32, 52, 28, 64, 12, 20, 16, 32}, {40, 48, 36, 52, 44, 16, 48, 20}, {44, 48, 40, 52, 48, 16, 52, 20},
			{36, 52, 32, 64, 48, 20, 52, 32}, {40, 52, 36, 64, 44, 20, 48, 32}, {44, 52, 40, 64, 40, 20, 44, 32}, {48, 52, 44, 64, 52, 20, 56, 32}
		};
		for (int[] c : copies) {
			graphics.drawImage(old, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], null);
		}

		graphics.dispose();
		boolean hatFilled = true;
		for (int y = 0; y < 16 && hatFilled; y++) {
			for (int x = 32; x < 64; x++) {
				if ((image.getRGB(x, y) >>> 24) < 255) {
					hatFilled = false;
					break;
				}
			}
		}

		if (hatFilled) {
			for (int y = 0; y < 16; y++) {
				for (int x = 32; x < 64; x++) {
					image.setRGB(x, y, 0);
				}
			}
		}

		return image;
	}

	/** A wide part with its arms squeezed to slim (left alone if it has no wide arms to squeeze), read once. */
	@Nullable
	private static BufferedImage readSqueezed(PartCatalog.Source source) {
		String key = source + "|slim";
		if (IMAGES.containsKey(key)) {
			return IMAGES.get(key);
		}

		BufferedImage wide = read(source);
		BufferedImage image = wide != null && !NpcSkinFiles.isSlim(wide) ? SlimArms.squeeze(wide) : wide;
		IMAGES.put(key, image);
		return image;
	}

	@Nullable
	private static BufferedImage read(@Nullable PartCatalog.Source source) {
		if (source == null) {
			return null;
		}

		String key = source.toString();
		if (IMAGES.containsKey(key)) {
			return IMAGES.get(key);
		}

		BufferedImage image = null;
		try (InputStream stream = source.open()) {
			image = ImageIO.read(stream);
			if (image != null && (image.getWidth() != 64 || image.getHeight() != 64 && image.getHeight() != 32)) {
				Lifeblood.LOGGER.warn("Appearance part {} must be a 64x64 (or old-style 64x32) PNG", source);
				image = null;
			} else if (image != null && image.getHeight() == 32) {
				image = upgradeLegacy(image);
			}
		} catch (IOException e) {
			Lifeblood.LOGGER.warn("Could not read appearance part {}", source, e);
		}

		IMAGES.put(key, image);
		return image;
	}
}
