package npclaude.skin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
