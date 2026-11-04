package lifeblood.building;

import com.mojang.nbt.NbtIo;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * The npc_prefabs folder in the game directory, next to npc_parts and npc_skins: one compressed NBT file per prefab,
 * named after it ("cabin.prefab"). Prefabs bundled with the mod live in the jar under assets/lifeblood/prefabs and are
 * used when the folder has none of that name, so a saved file can override a bundled one.
 */
public final class PrefabFiles {
	public static final String FOLDER_NAME = "npc_prefabs";
	private static final String EXTENSION = ".prefab";
	private static final String BUNDLED = "/assets/lifeblood/prefabs/";
	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_]{1,40}");

	private PrefabFiles() {
	}

	public static boolean isValidName(String name) {
		return VALID_NAME.matcher(name).matches();
	}

	public static File getDirectory() {
		File dir = FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME).toFile();
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}

		return dir;
	}

	public static void save(Prefab prefab) throws IOException {
		try (OutputStream out = new FileOutputStream(new File(getDirectory(), prefab.name + EXTENSION))) {
			NbtIo.writeCompressed(prefab.save(), out);
		}
	}

	/** The prefab with this name from the folder, else bundled with the mod; null if there's none. */
	@Nullable
	public static Prefab load(String name) throws IOException {
		if (!isValidName(name)) {
			return null;
		}

		File file = new File(getDirectory(), name + EXTENSION);
		if (file.isFile()) {
			try (InputStream in = new FileInputStream(file)) {
				return read(in);
			}
		}

		try (InputStream in = PrefabFiles.class.getResourceAsStream(BUNDLED + name + EXTENSION)) {
			return in != null ? read(in) : null;
		}
	}

	private static Prefab read(InputStream in) throws IOException {
		try {
			return Prefab.load(NbtIo.readCompressed(in));
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/** The names of the prefabs in the folder. */
	public static List<String> list() {
		List<String> names = new ArrayList<>();
		File[] files = getDirectory().listFiles();
		if (files != null) {
			for (File file : files) {
				String name = file.getName().toLowerCase(Locale.ROOT);
				if (file.isFile() && name.endsWith(EXTENSION)) {
					names.add(name.substring(0, name.length() - EXTENSION.length()));
				}
			}
		}

		names.sort(null);
		return names;
	}
}
