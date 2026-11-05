package lifeblood.appearance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.lang.I18n;
import lifeblood.Mod;
import lifeblood.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/**
 * Every appearance part and palette available: the ones bundled with the mod (assets/lifeblood/parts/) plus the
 * ones in the game folder's npc_parts/, which can be added to without rebuilding. Game-folder files win over
 * bundled ones with the same name.
 *
 * Layout (the same in both places):
 * - <layer>/<name>.png: a 64x64 skin-format part, usable by anyone. Layers: base, eyes, mouth, facial_hair,
 *   bottom, top, outfit, hair, accessory.
 * - Folders inside a layer are tags on the parts in them, in any order and nesting: male or female (only rolled
 *   for that gender), cold, mild or hot (only rolled in that climate, and preferred there), and any other name is a
 *   job (only rolled for that job: outfit/guard/, top/female/farmer/). See {@link Wearer}. Commands can still put
 *   any part on anyone.
 * - <name>_slim.png next to <name>.png: the slim-armed version, used when the NPC's base part has slim arms.
 * - palettes.txt: "[group]" headers (skin, hair, eyes, dye) followed by "name: #202020 #505050 #808080 #B0B0B0
 *   #E0E0E0" lines, darkest to lightest, optionally ending in a weight ("x40": forty times as likely to be rolled
 *   as a palette of weight 1, the default; "x0" is never rolled). Lines starting with # are comments.
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

	/** A part: its name, its tags (the folders it's in: gender, climate, job), and its wide and slim images. */
	public record Part(String name, Set<String> tags, @Nullable Source wide, @Nullable Source slim) {
		/** The image to use for an arm width, falling back to whichever exists. */
		@Nullable
		public Source source(boolean slim) {
			if (slim && this.slim != null) {
				return this.slim;
			}

			return this.wide != null ? this.wide : this.slim;
		}

		/** The gender it's rolled for; null for anyone. */
		@Nullable
		public NpcGender gender() {
			for (String tag : this.tags) {
				if (NpcGender.byName(tag) != null) {
					return NpcGender.byName(tag);
				}
			}

			return null;
		}

		/** The climate it's rolled in ("cold", "mild", "hot"); null for any. */
		@Nullable
		public String climate() {
			for (String tag : this.tags) {
				if (CLIMATES.contains(tag)) {
					return tag;
				}
			}

			return null;
		}

		/** The job it belongs to ("guard"); null for everyday wear. */
		@Nullable
		public String job() {
			for (String tag : this.tags) {
				if (NpcGender.byName(tag) == null && !CLIMATES.contains(tag)) {
					return tag;
				}
			}

			return null;
		}

		/** Whether it's rolled for this wearer: their gender, their climate, and their job or none. */
		public boolean fits(Wearer wearer) {
			NpcGender gender = this.gender();
			String climate = this.climate();
			String job = this.job();
			return (gender == null || gender == wearer.gender()) && (climate == null || climate.equals(wearer.climate()))
				&& (job == null || job.equals(wearer.job()));
		}
	}

	/** The folder names that are climates rather than jobs. */
	private static final Set<String> CLIMATES = Set.of("cold", "mild", "hot");

	private static PartCatalog instance;

	private final Map<AppearanceLayer, Map<String, Part>> parts = new EnumMap<>(AppearanceLayer.class);
	private final Map<String, Map<String, int[]>> palettes = new LinkedHashMap<>();
	/** How likely each palette is to be rolled, by group; 1 unless palettes.txt says otherwise. */
	private final Map<String, Map<String, Float>> weights = new HashMap<>();

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

	/** The parts rolled for this wearer (see {@link Part#fits}). */
	public List<Part> parts(AppearanceLayer layer, Wearer wearer) {
		List<Part> fitting = new ArrayList<>();
		for (Part part : this.parts.getOrDefault(layer, Map.of()).values()) {
			if (part.fits(wearer)) {
				fitting.add(part);
			}
		}

		return fitting;
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

	/** How likely a palette is to be rolled, relative to the others in its group (1 unless set). */
	public float weight(String group, String name) {
		return this.weights.getOrDefault(group, Map.of()).getOrDefault(name, 1.0F);
	}

	/**
	 * The five colors a layer is drawn with for a palette name: a hair color with its graying and dye worked out (see
	 * {@link HairColor}; beards are never dyed), any other a palette from the layer's group. Null if unknown.
	 */
	@Nullable
	public int[] colors(AppearanceLayer layer, String palette) {
		if (layer.paletteGroup == null || palette.isEmpty()) {
			return null;
		}

		if ("hair".equals(layer.paletteGroup)) {
			HairColor hair = HairColor.parse(palette);
			return (layer == AppearanceLayer.FACIAL_HAIR ? hair.withDye(null) : hair).colors(this);
		}

		return this.palette(layer.paletteGroup, palette);
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
			scanBundled(found, BUNDLED + layer.id(), Set.of());
			scanFolder(found, new File(dir, layer.id()), Set.of());
			catalog.parts.put(layer, found);
		}

		try (InputStream stream = I18n.getResourceAsStream(BUNDLED + "palettes.txt")) {
			if (stream != null) {
				catalog.readPalettes(stream, "bundled palettes.txt");
			}
		} catch (IOException e) {
			Mod.LOG.warn("Could not read the bundled palettes", e);
		}

		File palettes = new File(dir, "palettes.txt");
		if (palettes.isFile()) {
			try (InputStream stream = new FileInputStream(palettes)) {
				catalog.readPalettes(stream, palettes.toString());
			} catch (IOException e) {
				Mod.LOG.warn("Could not read {}", palettes, e);
			}
		}

		Mod.LOG.info("Loaded {} appearance parts and {} palettes", catalog.partCount(), catalog.paletteCount());
		return catalog;
	}

	/** Bundled parts in a folder and the folders inside it, each folder name a tag. */
	private static void scanBundled(Map<String, Part> found, String folder, Set<String> tags) {
		for (String path : I18n.getFilesInDirectory(folder)) {
			// Folders inside a jar may be listed with a slash on the end.
			String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
			String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
			if (name.contains(".")) {
				addPart(found, name, tags, new Source(path, null));
			} else {
				scanBundled(found, folder + "/" + name, withTag(tags, name));
			}
		}
	}

	/** Game-folder parts in a folder and the folders inside it, each folder name a tag. */
	private static void scanFolder(Map<String, Part> found, File folder, Set<String> tags) {
		File[] files = folder.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				scanFolder(found, file, withTag(tags, file.getName()));
			} else {
				addPart(found, file.getName(), tags, new Source(null, file));
			}
		}
	}

	private static Set<String> withTag(Set<String> tags, String tag) {
		Set<String> more = new TreeSet<>(tags);
		more.add(tag.toLowerCase(Locale.ROOT));
		return more;
	}

	/** Files later in the scan (the game folder) replace earlier ones of the same name. */
	private static void addPart(Map<String, Part> found, String fileName, Set<String> tags, Source source) {
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
		Set<String> partTags = existing != null && !existing.tags().isEmpty() ? existing.tags() : tags;
		found.put(name, new Part(name, partTags, slim ? wide : source, slim ? source : slimSource));
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
				Mod.LOG.warn("{} line {}: expected \"[group]\" or \"name: five colors\"", where, number);
				continue;
			}

			String name = line.substring(0, colon).strip().toLowerCase(Locale.ROOT).replace(' ', '_');
			String[] colors = line.substring(colon + 1).strip().split("\\s+");
			float weight = 1.0F;
			if (colors.length == 6 && colors[5].startsWith("x")) {
				try {
					weight = Math.max(0.0F, Float.parseFloat(colors[5].substring(1)));
				} catch (NumberFormatException e) {
					Mod.LOG.warn("{} line {}: '{}' has a weight that isn't a number like x40", where, number, name);
				}

				colors = Arrays.copyOf(colors, 5);
			}

			if (colors.length != 5) {
				Mod.LOG.warn("{} line {}: palette '{}' needs exactly five colors", where, number, name);
				continue;
			}

			int[] rgb = new int[5];
			try {
				for (int i = 0; i < 5; i++) {
					rgb[i] = Integer.parseInt(colors[i].replace("#", ""), 16) & 0xFFFFFF;
				}
			} catch (NumberFormatException e) {
				Mod.LOG.warn("{} line {}: '{}' has a color that isn't hex like #A0522D", where, number, name);
				continue;
			}

			this.palettes.computeIfAbsent(group, g -> new LinkedHashMap<>()).put(name, rgb);
			this.weights.computeIfAbsent(group, g -> new HashMap<>()).put(name, weight);
		}
	}

	public Map<String, Map<String, int[]>> palettes() {
		return Collections.unmodifiableMap(this.palettes);
	}
}
