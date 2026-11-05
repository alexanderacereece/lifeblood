package lifeblood.people;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.lang.I18n;
import lifeblood.Mod;
import lifeblood.entity.NpcGender;

/**
 * The names NPCs are given, from assets/lifeblood/names.txt: [male] and [female] sections of given names and a
 * [family] section of family names, one name per line, # for comments. Children born in the world get a given name
 * from it (and their family's name); anyone else gets both from it.
 */
public final class NamePool {
	private static final String FILE = "/assets/lifeblood/names.txt";
	private static final String FAMILY = "family";
	/** Used only if the list is missing or a section is empty. */
	private static final String FALLBACK_MALE = "Tom";
	private static final String FALLBACK_FEMALE = "Ann";
	private static final String FALLBACK_FAMILY = "Smith";

	private static Map<String, List<String>> names;

	private NamePool() {
	}

	/** A random given name for someone of this gender. */
	public static String pick(NpcGender gender, Random random) {
		return pick(gender.name().toLowerCase(Locale.ROOT), random, gender == NpcGender.MALE ? FALLBACK_MALE : FALLBACK_FEMALE);
	}

	/** A random family name, for someone whose family isn't from this world. */
	public static String pickFamilyName(Random random) {
		return pick(FAMILY, random, FALLBACK_FAMILY);
	}

	private static String pick(String section, Random random, String fallback) {
		if (names == null) {
			names = load();
		}

		List<String> options = names.getOrDefault(section, List.of());
		return options.isEmpty() ? fallback : options.get(random.nextInt(options.size()));
	}

	private static Map<String, List<String>> load() {
		try (InputStream stream = I18n.getResourceAsStream(FILE)) {
			if (stream != null) {
				return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			Mod.LOG.warn("Could not read the name list", e);
		}

		return new HashMap<>();
	}

	/** Reads a name list in the names.txt format: each section's names, by section name ("male", "family"). */
	static Map<String, List<String>> parse(Reader source) throws IOException {
		Map<String, List<String>> result = new HashMap<>();
		BufferedReader reader = new BufferedReader(source);
		String section = null;
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith("[") && line.endsWith("]")) {
				section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
			} else if (section != null) {
				result.computeIfAbsent(section, s -> new ArrayList<>()).add(line);
			}
		}

		return result;
	}

	/** Reads a name list from text, for tests. */
	static Map<String, List<String>> parse(String text) throws IOException {
		return parse(new StringReader(text));
	}

	/** The bundled list, for tests. */
	static Map<String, List<String>> bundled() {
		return load();
	}
}
