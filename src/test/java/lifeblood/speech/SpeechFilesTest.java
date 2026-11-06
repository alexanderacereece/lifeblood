package lifeblood.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.junit.jupiter.api.Test;

/**
 * Checks the bundled speech files: every condition is a real pole, quirk or fact, and every [name] in a line is a
 * symbol, a fact or a word the game fills in. A new fact or word set in code needs adding to the lists here.
 */
class SpeechFilesTest {
	private static final Set<String> FACTS = Set.of("name", "family_name", "birth_family_name", "married_name", "listener", "listener_type", "opinion", "leader", "time", "weather", "mood",
		"health", "recovering", "has_faction", "has_enemy", "address", "catchphrase", "victim", "kind", "taste", "wet", "outside", "season",
		"biome", "weather_like", "season_like", "biome_like", "rude_gift", "surprising", "surprising_thing", "relation", "fighting_you",
		"in_combat", "about", "other_type", "is_favorite", "brawling", "forfeit", "friend_fighting", "relationship", "listener_kin", "age",
		"baby_gender", "word_prompt", "word_category", "personal", "birthday", "climate", "home_climate", "home_biome", "at_home", "unprovoked",
		"to_partner", "listener_age", "news", "exaggerated", "firsthand", "subject_opinion", "other_opinion", "came_for", "settler");
	private static final Set<String> WORDS = Set.of("favorite_food", "hated_food", "faction", "enemy_faction", "enemy", "food", "item",
		"favorite_thing", "hated_thing", "weather_word", "biome_word", "season_word", "favorite_weather", "hated_weather", "favorite_biome",
		"hated_biome", "favorite_season", "hated_season", "other", "animal", "brawler", "other_brawler", "partner", "baby", "word",
		"taught_food", "taught_animal", "taught_thing", "taught_place", "taught_exclamation", "taught_greeting", "taught_farewell",
		"taught_describe", "taught_activity", "taught_angry", "taught_happy", "taught_sad", "taught_scared", "home_biome", "kin_call", "subject", "source");
	private static final Pattern REFERENCE = Pattern.compile("\\[([a-z_0-9]+)(?:\\.[a-z]+)*\\??]");

	private record Line(String where, String text) {
	}

	@Test
	void everyConditionAndReferenceIsReal() throws IOException {
		Map<String, List<Line>> symbols = new HashMap<>();
		List<String> problems = new ArrayList<>();
		for (String file : SpeechLibrary.BUNDLED) {
			try (InputStream stream = SpeechFilesTest.class.getResourceAsStream(SpeechLibrary.BUNDLED_PATH + file)) {
				assertNotNull(stream, "missing " + file);
				read(file, new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), symbols, problems);
			}
		}

		for (List<Line> lines : symbols.values()) {
			for (Line line : lines) {
				Matcher matcher = REFERENCE.matcher(line.text());
				while (matcher.find()) {
					String name = matcher.group(1);
					if (!symbols.containsKey(name) && !FACTS.contains(name) && !WORDS.contains(name)) {
						problems.add(line.where() + ": [" + name + "] is nothing");
					}
				}
			}
		}

		// Age versions ("greet.child") must be for a real stage and stand in for a real symbol.
		for (String symbol : symbols.keySet()) {
			int dot = symbol.indexOf('.');
			if (dot > 0) {
				String stage = symbol.substring(dot + 1);
				if (!Set.of("baby", "child", "young_teen", "teen").contains(stage)) {
					problems.add(symbol + ": unknown age stage " + stage);
				}

				if (!symbols.containsKey(symbol.substring(0, dot))) {
					problems.add(symbol + ": no plain " + symbol.substring(0, dot) + " to stand in for");
				}
			}
		}

		assertTrue(symbols.containsKey("babble"), "babies need babble");
		assertTrue(symbols.size() > 50, "only " + symbols.size() + " symbols");
		assertEquals(List.of(), problems);
	}

	private static void read(String file, BufferedReader reader, Map<String, List<Line>> symbols, List<String> problems) throws IOException {
		String symbol = null;
		int number = 0;
		String raw;
		while ((raw = reader.readLine()) != null) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			String where = file + ":" + number;
			if (line.startsWith("==") && line.endsWith("==") && line.length() > 4) {
				symbol = line.substring(2, line.length() - 2).strip();
				symbols.computeIfAbsent(symbol, key -> new ArrayList<>());
				continue;
			}

			if (symbol == null) {
				problems.add(where + ": line outside any symbol");
				continue;
			}

			String text = line;
			if (line.endsWith("}")) {
				int open = line.lastIndexOf('{');
				text = line.substring(0, open).strip();
				for (String token : line.substring(open + 1, line.length() - 1).split("[,\\s]+")) {
					if (!token.isEmpty() && !token.startsWith("w=") && !isCondition(token)) {
						problems.add(where + ": unknown condition " + token);
					}
				}
			}

			symbols.get(symbol).add(new Line(where, text));
		}
	}

	private static boolean isCondition(String token) {
		String body = token.startsWith("!") ? token.substring(1) : token;
		int equals = body.indexOf('=');
		if (equals > 0) {
			return FACTS.contains(body.substring(0, equals));
		}

		String name = body.endsWith("+") ? body.substring(0, body.length() - 1) : body;
		for (TraitAxis axis : TraitAxis.values()) {
			if (axis.negative.equals(name) || axis.positive.equals(name)) {
				return true;
			}
		}

		return !body.endsWith("+") && Quirk.byName(name) != null;
	}
}
