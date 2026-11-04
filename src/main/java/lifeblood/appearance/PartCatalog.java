package lifeblood.appearance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.lang.I18n;
import lifeblood.Lifeblood;
import lifeblood.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/**
 * Every appearance part and palette available: the ones bundled with the mod (assets/lifeblood/parts/) plus the
 * ones in the game folder's npc_parts/, which can be added to without rebuilding. Game-folder files win over
 * bundled ones with the same name.
 *
 * Layout (the same in both places):
 * - <layer>/<name>.png: a 64x64 skin-format part, usable by anyone. Layers: base, eyes, mouth, facial_hair,
 *   bottom, top, hair, accessory.
 * - <layer>/male/<name>.png and <layer>/female/<name>.png: parts only rolled for that gender (still assignable by
 *   command to anyone).
 * - <name>_slim.png next to <name>.png: the slim-armed version, used when the NPC's base part has slim arms.
 * - palettes.txt: "[group]" headers (skin, hair, eyes, dye) followed by "name: #202020 #505050 #808080 #B0B0B0
 *   #E0E0E0" lines, darkest to lightest. Lines starting with # are comments.
 *
 * Recoloring: in a part, the five grays #202020, #505050, #808080, #B0B0B0 and #E0E0E0 become the palette's five
 * colors; every other color is drawn as is. Contains no client code.
 */
public final class PartCatalog {
	public static final String FOLDER_NAME = "npc_parts";
	private static final String BUNDLED = "/assets/lifeblood/parts/";

	/** Where a part's image comes from: a bundled resource or a file in the game folder. */
	public record Source(@Nullable String resource, @Nullable File file) {
		public InputStream open() throws IOException {
			if (this.file != null) {
				return new FileInputStream(this.file);
			}

			InputStream stream = I18n.getResourceAsStream(this.resource);
			if (stream == null) {
				throw new IOException("Missing resource " + this.resource);
			}

			return stream;
		}

		@Override
		public String toString() {
			return this.file != null ? this.file.toString() : this.resource;
		}
	}

	/** A part: its name, the gender it's rolled for (null for anyone), and its wide and slim images. */
	public record Part(String name, @Nullable NpcGender gender, @Nullable Source wide, @Nullable Source slim) {
		/** The image to use for an arm width, falling back to whichever exists. */
		@Nullable
		public Source source(boolean slim) {
			if (slim && this.slim != null) {
				return this.slim;
			}

			return this.wide != null ? this.wide : this.slim;
		}
	}

	private static PartCatalog instance;

	private final Map<AppearanceLayer, Map<String, Part>> parts = new EnumMap<>(AppearanceLayer.class);
	private final Map<String, Map<String, int[]>> palettes = new LinkedHashMap<>();

	private PartCatalog() {
	}

	public static synchronized PartCatalog get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	/** Reads every part and palette again; returns the new catalog. */
	public static synchronized PartCatalog reload() {
		instance = load();
		return instance;
	}

	public static File getDirectory() {
		File dir = FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME).toFile();
		if (!dir.isDirectory()) {
			dir.mkdirs();
			for (AppearanceLayer layer : AppearanceLayer.values()) {
				new File(dir, layer.id()).mkdirs();
			}
		}

		return dir;
	}

	/** Part names rolled for this gender: shared parts plus that gender's own. */
	public List<String> parts(AppearanceLayer layer, NpcGender gender) {
		List<String> names = new ArrayList<>();
		for (Part part : this.parts.getOrDefault(layer, Map.of()).values()) {
			if (part.gender() == null || part.gender() == gender) {
				names.add(part.name());
			}
		}

		return names;
	}

	public List<String> allParts(AppearanceLayer layer) {
		return new ArrayList<>(this.parts.getOrDefault(layer, Map.of()).keySet());
	}

	@Nullable
	public Part part(AppearanceLayer layer, String name) {
		return this.parts.getOrDefault(layer, Map.of()).get(name);
	}

	public List<String> paletteNames(String group) {
		return new ArrayList<>(this.palettes.getOrDefault(group, Map.of()).keySet());
	}

	/** A palette's five colors (RGB), darkest first; null if there's no such palette. */
	@Nullable
	public int[] palette(String group, String name) {
		return this.palettes.getOrDefault(group, Map.of()).get(name);
	}

	public int partCount() {
		int count = 0;
		for (Map<String, Part> layer : this.parts.values()) {
			count += layer.size();
		}

		return count;
	}

	public int paletteCount() {
		int count = 0;
		for (Map<String, int[]> group : this.palettes.values()) {
			count += group.size();
		}

		return count;
	}

	private static PartCatalog load() {
		PartCatalog catalog = new PartCatalog();
		File dir = getDirectory();
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			Map<String, Part> found = new TreeMap<>();
			for (NpcGender gender : new NpcGender[] {null, NpcGender.MALE, NpcGender.FEMALE}) {
				String sub = layer.id() + "/" + (gender != null ? gender.name().toLowerCase(Locale.ROOT) + "/" : "");
				for (String path : I18n.getFilesInDirectory(BUNDLED + sub)) {
					addPart(found, fileName(path), gender, new Source(path, null));
				}

				File[] files = new File(dir, sub).listFiles();
				if (files != null) {
					for (File file : files) {
						if (file.isFile()) {
							addPart(found, file.getName(), gender, new Source(null, file));
						}
					}
				}
			}

			catalog.parts.put(layer, found);
		}

		try (InputStream stream = I18n.getResourceAsStream(BUNDLED + "palettes.txt")) {
			if (stream != null) {
				catalog.readPalettes(stream, "bundled palettes.txt");
			}
		} catch (IOException e) {
			Lifeblood.LOGGER.warn("Could not read the bundled palettes", e);
		}

		File palettes = new File(dir, "palettes.txt");
		if (palettes.isFile()) {
			try (InputStream stream = new FileInputStream(palettes)) {
				catalog.readPalettes(stream, palettes.toString());
			} catch (IOException e) {
				Lifeblood.LOGGER.warn("Could not read {}", palettes, e);
			}
		}

		Lifeblood.LOGGER.info("Loaded {} appearance parts and {} palettes", catalog.partCount(), catalog.paletteCount());
		return catalog;
	}

	private static String fileName(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/** Files later in the scan (the game folder) replace earlier ones of the same name. */
	private static void addPart(Map<String, Part> found, String fileName, @Nullable NpcGender gender, Source source) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".png")) {
			return;
		}

		String name = lower.substring(0, lower.length() - 4);
		boolean slim = name.endsWith("_slim");
		if (slim) {
			name = name.substring(0, name.length() - "_slim".length());
		}

		Part existing = found.get(name);
		Source wide = existing != null ? existing.wide() : null;
		Source slimSource = existing != null ? existing.slim() : null;
		NpcGender partGender = existing != null && existing.gender() != null ? existing.gender() : gender;
		found.put(name, new Part(name, partGender, slim ? wide : source, slim ? source : slimSource));
	}

	private void readPalettes(InputStream stream, String where) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		String group = null;
		String line;
		int number = 0;
		while ((line = reader.readLine()) != null) {
			number++;
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith("[") && line.endsWith("]")) {
				group = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
				continue;
			}

			int colon = line.indexOf(':');
			if (group == null || colon <= 0) {
				Lifeblood.LOGGER.warn("{} line {}: expected \"[group]\" or \"name: five colors\"", where, number);
				continue;
			}

			String name = line.substring(0, colon).strip().toLowerCase(Locale.ROOT).replace(' ', '_');
			String[] colors = line.substring(colon + 1).strip().split("\\s+");
			if (colors.length != 5) {
				Lifeblood.LOGGER.warn("{} line {}: palette '{}' needs exactly five colors", where, number, name);
				continue;
			}

			int[] rgb = new int[5];
			try {
				for (int i = 0; i < 5; i++) {
					rgb[i] = Integer.parseInt(colors[i].replace("#", ""), 16) & 0xFFFFFF;
				}
			} catch (NumberFormatException e) {
				Lifeblood.LOGGER.warn("{} line {}: '{}' has a color that isn't hex like #A0522D", where, number, name);
				continue;
			}

			this.palettes.computeIfAbsent(group, g -> new LinkedHashMap<>()).put(name, rgb);
		}
	}

	public Map<String, Map<String, int[]>> palettes() {
		return Collections.unmodifiableMap(this.palettes);
	}
}
