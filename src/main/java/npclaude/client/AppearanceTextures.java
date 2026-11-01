package npclaude.client;

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
import net.minecraft.client.render.texture.Texture;
import npclaude.NPClaude;
import npclaude.appearance.Appearance;
import npclaude.appearance.AppearanceLayer;
import npclaude.appearance.PartCatalog;
import npclaude.entity.SkinModel;
import npclaude.skin.NpcSkinFiles;
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

		// The base part decides the arm width (unless the NPC's skin model forces one); clothes follow it.
		BufferedImage baseImage = read(base.source(model == SkinModel.SLIM));
		if (baseImage == null) {
			return null;
		}

		boolean slim = model == SkinModel.AUTO ? NpcSkinFiles.isSlim(baseImage) : model == SkinModel.SLIM;
		BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			Appearance.Choice choice = appearance.get(layer);
			PartCatalog.Part part = choice != null ? catalog.part(layer, choice.part()) : null;
			BufferedImage image = part != null ? read(part.source(slim)) : null;
			if (image == null) {
				continue;
			}

			int[] palette = layer.paletteGroup != null && !choice.palette().isEmpty() ? catalog.palette(layer.paletteGroup, choice.palette()) : null;
			drawOver(result, image, palette);
		}

		PlayerSkinParser parser = slim ? PlayerSkinParser.instanceAlex : PlayerSkinParser.instanceSteve;
		return new NpcSkins.LoadedSkin(textureManager.loadBufferedTexture(parser.parseImage(result)), slim);
	}

	/** Alpha-blends a part over the image so far, swapping the five grays for the palette's colors first. */
	private static void drawOver(BufferedImage target, BufferedImage part, @Nullable int[] palette) {
		int height = Math.min(64, part.getHeight());
		for (int y = 0; y < height; y++) {
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
				NPClaude.LOGGER.warn("Appearance part {} must be a 64x64 PNG", source);
				image = null;
			}
		} catch (IOException e) {
			NPClaude.LOGGER.warn("Could not read appearance part {}", source, e);
		}

		IMAGES.put(key, image);
		return image;
	}
}
