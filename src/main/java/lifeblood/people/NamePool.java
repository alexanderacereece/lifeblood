package lifeblood.people;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.lang.I18n;
import lifeblood.Lifeblood;
import lifeblood.entity.NpcGender;

/**
 * The names NPCs are given, from assets/lifeblood/names.txt: a [male] and a [female] section, one name per line, # for
 * comments. Children born in the world are named from it, and so is anyone summoned without a name.
 */
public final class NamePool {
	private static final String FILE = "/assets/lifeblood/names.txt";
	/** Used only if the list is missing or a section is empty. */
	private static final String FALLBACK_MALE = "Tom";
	private static final String FALLBACK_FEMALE = "Ann";

	private static Map<NpcGender, List<String>> names;

	private NamePool() {
	}

	/** A random name for someone of this gender. */
	public static String pick(NpcGender gender, Random random) {
		if (names == null) {
			names = load();
		}

		List<String> options = names.getOrDefault(gender, List.of());
		if (options.isEmpty()) {
			return gender == NpcGender.MALE ? FALLBACK_MALE : FALLBACK_FEMALE;
		}

		return options.get(random.nextInt(options.size()));
	}

	private static Map<NpcGender, List<String>> load() {
		try (InputStream stream = I18n.getResourceAsStream(FILE)) {
			if (stream != null) {
				return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
			}
		} catch (IOException e) {
			Lifeblood.LOGGER.warn("Could not read the name list", e);
		}

		return new EnumMap<>(NpcGender.class);
	}

	/** Reads a name list in the names.txt format. */
	static Map<NpcGender, List<String>> parse(Reader source) throws IOException {
		Map<NpcGender, List<String>> result = new EnumMap<>(NpcGender.class);
		BufferedReader reader = new BufferedReader(source);
		NpcGender section = null;
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith("[") && line.endsWith("]")) {
				section = NpcGender.byName(line.substring(1, line.length() - 1));
			} else if (section != null) {
				result.computeIfAbsent(section, g -> new ArrayList<>()).add(line);
			}
		}

		return result;
	}

	/** Reads a name list from text, for tests. */
	static Map<NpcGender, List<String>> parse(String text) throws IOException {
		return parse(new StringReader(text));
	}
}
