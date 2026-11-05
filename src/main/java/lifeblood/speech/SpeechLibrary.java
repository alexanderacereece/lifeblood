package lifeblood.speech;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import lifeblood.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Every line NPCs can say, loaded from speech files: the ones bundled with the mod, plus any .speech files in the
 * game folder's npc_speech directory (added on top, so new lines can be written without rebuilding).
 *
 * Format:
 * <pre>
 * # a comment
 * == greet ==
 * Hey, [listener].
 * Oh! [listener]! Good to see you!   {extrovert, opinion=friend}
 * Mm.                                {taciturn, w=2}
 * </pre>
 * Each "== name ==" starts a symbol; each line under it is one way to say it. [name] inside a line expands another
 * symbol or fills in a fact or word. Braces at the end hold conditions (see {@link Condition}) and an optional
 * weight (w=2 makes a line twice as likely).
 */
public final class SpeechLibrary {
	/** The speech files shipped with the mod, by topic. basics.speech describes the format. */
	public static final List<String> BUNDLED = List.of("basics.speech", "conversation.speech", "gifts.speech", "remarks.speech",
		"combat.speech", "brawls.speech", "family.speech", "words.speech", "ages.speech");
	public static final String BUNDLED_PATH = "/assets/lifeblood/speech/";
	public static final String FOLDER_NAME = "npc_speech";

	/** One way to say a symbol. */
	public record Line(String text, List<Condition> conditions, float weight, String source) {
		public boolean allows(SpeechContext context) {
			for (Condition condition : this.conditions) {
				if (!condition.test(context)) {
					return false;
				}
			}

			return true;
		}

		public int specificity() {
			int count = 0;
			for (Condition condition : this.conditions) {
				if (condition.isSpecific()) {
					count++;
				}
			}

			return count;
		}
	}

	private static Map<String, List<Line>> symbols = new HashMap<>();
	private static boolean loaded;

	private SpeechLibrary() {
	}

	public static List<Line> get(String symbol) {
		ensureLoaded();
		return symbols.getOrDefault(symbol, Collections.emptyList());
	}

	public static boolean has(String symbol) {
		ensureLoaded();
		return symbols.containsKey(symbol);
	}

	private static void ensureLoaded() {
		if (!loaded) {
			reload();
		}
	}

	/** Reads every speech file again. Returns how many lines were loaded. */
	public static int reload() {
		Map<String, List<Line>> result = new HashMap<>();
		for (String name : BUNDLED) {
			try (InputStream stream = SpeechLibrary.class.getResourceAsStream(BUNDLED_PATH + name)) {
				if (stream == null) {
					Mod.LOG.error("Missing bundled speech file {}", name);
					continue;
				}

				parse(new InputStreamReader(stream, StandardCharsets.UTF_8), name, result);
			} catch (IOException e) {
				Mod.LOG.error("Couldn't read bundled speech file {}", name, e);
			}
		}

		File folder = FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME).toFile();
		File[] files = folder.listFiles((dir, file) -> file.endsWith(".speech"));
		if (files != null) {
			Arrays.sort(files);
			for (File file : files) {
				try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
					parse(reader, file.getName(), result);
				} catch (IOException e) {
					Mod.LOG.error("Couldn't read speech file {}", file, e);
				}
			}
		}

		symbols = result;
		loaded = true;
		int count = 0;
		for (List<Line> lines : result.values()) {
			count += lines.size();
		}

		return count;
	}

	private static void parse(Reader reader, String fileName, Map<String, List<Line>> into) throws IOException {
		BufferedReader lines = new BufferedReader(reader);
		String symbol = null;
		int number = 0;
		String raw;
		while ((raw = lines.readLine()) != null) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.startsWith("==") && line.endsWith("==") && line.length() > 4) {
				symbol = line.substring(2, line.length() - 2).strip();
				into.computeIfAbsent(symbol, key -> new ArrayList<>());
				continue;
			}

			String where = fileName + ":" + number;
			if (symbol == null) {
				Mod.LOG.warn("{}: line outside any == symbol ==, ignored", where);
				continue;
			}

			Line parsed = parseLine(line, where);
			if (parsed != null) {
				into.get(symbol).add(parsed);
			}
		}
	}

	@Nullable
	private static Line parseLine(String line, String where) {
		String text = line;
		List<Condition> conditions = new ArrayList<>();
		float weight = 1.0F;
		if (line.endsWith("}")) {
			int open = line.lastIndexOf('{');
			if (open < 0) {
				Mod.LOG.warn("{}: unmatched }}", where);
				return null;
			}

			text = line.substring(0, open).strip();
			for (String token : line.substring(open + 1, line.length() - 1).split("[,\\s]+")) {
				if (token.isEmpty()) {
					continue;
				}

				if (token.startsWith("w=")) {
					try {
						weight = Float.parseFloat(token.substring(2));
					} catch (NumberFormatException e) {
						Mod.LOG.warn("{}: bad weight '{}'", where, token);
					}

					continue;
				}

				Condition condition = Condition.parse(token);
				if (condition == null) {
					Mod.LOG.warn("{}: unknown condition '{}', line ignored", where, token);
					return null;
				}

				conditions.add(condition);
			}
		}

		// A lone "-" is a deliberately empty line, e.g. an optional part of a sentence.
		return new Line(text.equals("-") ? "" : text, conditions, weight, where);
	}
}
