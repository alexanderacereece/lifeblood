package lifeblood.appearance;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The npc_skins folder in the game directory. A skin's name is its file name without ".png".
 * Contains no client code, so commands can use it safely.
 */
public final class NpcSkinFiles {
	public static final String FOLDER_NAME = "npc_skins";

	private NpcSkinFiles() {
	}

	public static File getDirectory() {
		File dir = FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME).toFile();
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}

		return dir;
	}

	public static File getFile(String skin) {
		return new File(getDirectory(), skin + ".png");
	}

	public static boolean exists(String skin) {
		return !skin.isEmpty() && getFile(skin).isFile();
	}

	/** Reads a skin file and checks whether it uses slim (3-pixel) arms; false if it can't be read. */
	public static boolean isSlim(String skin) {
		if (!exists(skin)) {
			return false;
		}

		try {
			BufferedImage image = ImageIO.read(getFile(skin));
			return image != null && isSlim(image);
		} catch (IOException e) {
			return false;
		}
	}

	/** Slim skins have 3-pixel-wide arms, leaving the 4th column of the right arm (x 54-55, y 20-31) transparent. */
	public static boolean isSlim(BufferedImage image) {
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			return false;
		}

		for (int x = 54; x <= 55; x++) {
			for (int y = 20; y <= 31; y++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return false;
				}
			}
		}

		return true;
	}

	public static List<String> list() {
		List<String> skins = new ArrayList<>();
		File[] files = getDirectory().listFiles();
		if (files != null) {
			for (File file : files) {
				String name = file.getName();
				if (file.isFile() && name.toLowerCase(Locale.ROOT).endsWith(".png")) {
					skins.add(name.substring(0, name.length() - 4));
				}
			}
		}

		Collections.sort(skins);
		return skins;
	}
}
