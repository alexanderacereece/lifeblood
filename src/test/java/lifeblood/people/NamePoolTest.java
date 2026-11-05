package lifeblood.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamePoolTest {
	private static final Path BUNDLED = Path.of("src/main/resources/assets/lifeblood/names.txt");

	@Test
	void readsSectionsAndSkipsComments() throws IOException {
		Map<String, List<String>> names = NamePool.parse("# a comment\n[male]\nAldric\n\nArlo\n[Female]\n  Bea  \n# another\n[family]\nThorne\n");
		assertEquals(List.of("Aldric", "Arlo"), names.get("male"));
		assertEquals(List.of("Bea"), names.get("female"));
		assertEquals(List.of("Thorne"), names.get("family"));
	}

	/** Plenty of each, every one a plain capitalized word of letters, short enough for a name tag, and no repeats. */
	@Test
	void theBundledListIsTidy() throws IOException {
		Map<String, List<String>> names = NamePool.parse(Files.readString(BUNDLED, StandardCharsets.UTF_8));
		for (String section : List.of("male", "female", "family")) {
			List<String> list = names.getOrDefault(section, List.of());
			assertTrue(list.size() >= 100, section + " has only " + list.size() + " names");
			Set<String> seen = new HashSet<>();
			for (String name : list) {
				assertTrue(name.matches("[A-Z][a-z]{1,10}"), section + ": " + name);
				assertTrue(seen.add(name), section + " lists " + name + " twice");
			}
		}
	}
}
