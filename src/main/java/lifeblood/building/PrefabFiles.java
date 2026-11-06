package lifeblood.building;

import com.mojang.nbt.NbtIo;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.lang.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * The npc_prefabs folder in the game directory, next to npc_parts and npc_skins: one compressed NBT file per prefab,
 * named after it ("cabin.prefab"). Prefabs bundled with the mod live in the jar under assets/lifeblood/prefabs. A file
 * in the game folder is used over a bundled one of the same name, so a saved file can override a bundled one.
 *
 * Folders are tags, as with appearance parts: a prefab in prefabs/homestead/ is tagged "homestead", which is what makes
 * the world use it for generated homesteads. Names are unique whatever folder a prefab is in.
 */
public final class PrefabFiles {
	public static final String FOLDER_NAME = "npc_prefabs";
	/** The folder tag for houses the world may generate as homesteads, with people living in them. */
	public static final String HOMESTEAD_TAG = "homestead";
	private static final String EXTENSION = ".prefab";
	private static final String BUNDLED = "/assets/lifeblood/prefabs";
	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_]{1,40}");

	/** Where one prefab's file is: a bundled resource or a file in the game folder, and its folder tags. */
	private record Entry(@Nullable String resource, @Nullable File file, Set<String> tags) {
	}

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

	/** Saves a prefab into the game folder: over its old file if it has one (keeping its folder), else at the top. */
	public static void save(Prefab prefab) throws IOException {
		Entry existing = scan().get(prefab.name);
		File file = existing != null && existing.file() != null ? existing.file() : new File(getDirectory(), prefab.name + EXTENSION);
		try (OutputStream out = new FileOutputStream(file)) {
			NbtIo.writeCompressed(prefab.save(), out);
		}
	}

	/** The prefab with this name from the game folder, else bundled with the mod; null if there's none. */
	@Nullable
	public static Prefab load(String name) throws IOException {
		Entry entry = isValidName(name) ? scan().get(name) : null;
		if (entry == null) {
			return null;
		}

		try (InputStream in = entry.file() != null ? new FileInputStream(entry.file()) : I18n.getResourceAsStream(entry.resource())) {
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

	/** The names of every prefab, bundled and in the game folder, sorted. */
	public static List<String> list() {
		return new ArrayList<>(scan().keySet());
	}

	/** The names of the prefabs with this folder tag ("homestead"), sorted. */
	public static List<String> tagged(String tag) {
		List<String> names = new ArrayList<>();
		scan().forEach((name, entry) -> {
			if (entry.tags().contains(tag)) {
				names.add(name);
			}
		});
		return names;
	}

	/** A prefab's folder tags, empty if it has none or doesn't exist. */
	public static Set<String> tagsOf(String name) {
		Entry entry = scan().get(name);
		return entry != null ? entry.tags() : Set.of();
	}

	/** Every prefab by name: the bundled ones, then the game folder's over them. Read fresh, so new files show at once. */
	private static Map<String, Entry> scan() {
		Map<String, Entry> found = new TreeMap<>();
		scanBundled(found, BUNDLED, Set.of());
		scanFolder(found, getDirectory(), Set.of());
		return found;
	}

	private static void scanBundled(Map<String, Entry> found, String folder, Set<String> tags) {
		for (String path : I18n.getFilesInDirectory(folder)) {
			// Folders inside a jar may be listed with a slash on the end.
			String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
			String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
			if (name.contains(".")) {
				add(found, name, new Entry(trimmed, null, tags));
			} else {
				scanBundled(found, folder + "/" + name, withTag(tags, name));
			}
		}
	}

	private static void scanFolder(Map<String, Entry> found, File folder, Set<String> tags) {
		File[] files = folder.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				scanFolder(found, file, withTag(tags, file.getName()));
			} else {
				add(found, file.getName(), new Entry(null, file, tags));
			}
		}
	}

	private static Set<String> withTag(Set<String> tags, String tag) {
		Set<String> more = new TreeSet<>(tags);
		more.add(tag.toLowerCase(Locale.ROOT));
		return Collections.unmodifiableSet(more);
	}

	private static void add(Map<String, Entry> found, String fileName, Entry entry) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(EXTENSION)) {
			String name = lower.substring(0, lower.length() - EXTENSION.length());
			if (isValidName(name)) {
				found.put(name, entry);
			}
		}
	}
}
