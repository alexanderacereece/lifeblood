package lifeblood;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lifeblood.SourceTree.SourceFile;
import org.junit.jupiter.api.Test;

/**
 * The project's layout rules, checked on every build so they hold however the code grows and whoever changes it. Each
 * failure says what's wrong and how to fix it. The rules are explained in CLAUDE.md and docs/architecture.md; change
 * them there and here together, never just to get a build through.
 */
class ArchitectureTest {
	/** Longest a source file may be, in lines, with no exceptions. Past this, split it: a part, a helper class, a sub-package. */
	static final int MAX_LINES = 600;

	private static final List<SourceFile> MAIN = SourceTree.read(SourceTree.MAIN);
	private static final List<SourceFile> TEST = SourceTree.read(SourceTree.TEST);

	@Test
	void everyPackageSaysWhatItIs() {
		List<String> problems = new ArrayList<>();
		SourceTree.packages(MAIN).forEach((name, info) -> {
			if (info == null || info.doc() == null || info.doc().isBlank()) {
				problems.add(name + " has no package-info.java with a doc comment saying what the package is for, where to start, and what doesn't belong in it");
			}
		});
		assertNone(problems);
	}

	@Test
	void everyClassSaysWhatItIs() {
		List<String> problems = new ArrayList<>();
		for (SourceFile file : MAIN) {
			if (!file.isPackageInfo() && (file.doc() == null || file.doc().isBlank())) {
				problems.add(file.path() + " has no doc comment on its class: say what it is and how it fits");
			}
		}

		assertNone(problems);
	}

	@Test
	void everyTopLevelPackageIsInTheArchitectureDoc() throws IOException {
		String doc = Files.readString(Path.of("docs/architecture.md"), StandardCharsets.UTF_8);
		List<String> problems = new ArrayList<>();
		for (String name : SourceTree.packages(MAIN).keySet()) {
			String[] parts = name.split("\\.");
			if (parts.length >= 2 && !doc.contains("| `" + parts[1] + "`") && !doc.contains(", `" + parts[1] + "`")) {
				problems.add("lifeblood." + parts[1] + " isn't in the package table of docs/architecture.md");
			}
		}

		assertNone(problems.stream().distinct().toList());
	}

	/**
	 * A dedicated server has no client classes, so common code that touches one crashes it. Common code asks the client
	 * for things through {@link ClientHooks}. Only client/ and the client mixins listed in lifeblood.mixins.json may
	 * import lifeblood.client.
	 */
	@Test
	void commonCodeNeverTouchesClientCode() throws IOException {
		Set<String> clientMixins = clientMixins();
		List<String> problems = new ArrayList<>();
		for (SourceFile file : MAIN) {
			boolean client = file.packageName().startsWith("lifeblood.client")
				|| file.packageName().equals("lifeblood.mixin") && clientMixins.contains(file.name());
			if (client) {
				continue;
			}

			for (String imported : file.imports()) {
				if (imported.startsWith("lifeblood.client") || imported.startsWith("net.minecraft.client")) {
					problems.add(file.path() + " imports " + imported + ": common code reaches the client only through ClientHooks");
				}
			}
		}

		assertNone(problems);
	}

	/** Commands are tools layered on top of the game; nothing in the game depends on them. */
	@Test
	void nothingDependsOnCommands() {
		List<String> problems = new ArrayList<>();
		for (SourceFile file : MAIN) {
			if (file.packageName().equals("lifeblood.command") || file.name().equals("Lifeblood")) {
				continue;
			}

			for (String imported : file.imports()) {
				if (imported.startsWith("lifeblood.command")) {
					problems.add(file.path() + " imports " + imported + ": game code must not depend on /lb commands; move the shared logic into its system's package");
				}
			}
		}

		assertNone(problems);
	}

	/** One name, one class: a name says where to look, and nobody writes a second X without finding the first. */
	@Test
	void classNamesAreUnique() {
		Map<String, List<Path>> byName = new HashMap<>();
		for (SourceFile file : concat(MAIN, TEST)) {
			if (!file.isPackageInfo()) {
				byName.computeIfAbsent(file.name(), k -> new ArrayList<>()).add(file.path());
			}
		}

		List<String> problems = new ArrayList<>();
		byName.forEach((name, paths) -> {
			if (paths.size() > 1) {
				problems.add(name + " exists more than once: " + paths);
			}
		});
		assertNone(problems);
	}

	@Test
	void filesStaySmall() {
		List<String> problems = new ArrayList<>();
		for (SourceFile file : MAIN) {
			if (file.lines() > MAX_LINES) {
				problems.add(file.path() + " is " + file.lines() + " lines (limit " + MAX_LINES + "): split it into parts, helpers or a sub-package; never raise the limit");
			}
		}

		assertNone(problems);
	}

	/** Every class in mixin/ is a mixin listed in lifeblood.mixins.json, named after its target, and says why it exists. */
	@Test
	void mixinsAreListedAndNamed() throws IOException {
		String config = Files.readString(Path.of("src/main/resources/lifeblood.mixins.json"), StandardCharsets.UTF_8);
		List<String> problems = new ArrayList<>();
		for (SourceFile file : MAIN) {
			if (!file.packageName().equals("lifeblood.mixin") || file.isPackageInfo()) {
				continue;
			}

			if (!config.contains("\"" + file.name() + "\"")) {
				problems.add(file.name() + " isn't listed in lifeblood.mixins.json");
			}

			if (!file.name().endsWith("Mixin") && !file.name().endsWith("Accessor")) {
				problems.add(file.name() + " should be named after its target, ending in Mixin or Accessor");
			}
		}

		assertNone(problems);
	}

	/** Tests live in the same package as the class they test, so moving a class moves its test with it. */
	@Test
	void testsSitWithWhatTheyTest() {
		Map<String, String> mainPackages = new TreeMap<>();
		for (SourceFile file : MAIN) {
			mainPackages.put(file.name(), file.packageName());
		}

		List<String> problems = new ArrayList<>();
		for (SourceFile test : TEST) {
			if (!test.name().endsWith("Test")) {
				continue;
			}

			String subject = test.name().substring(0, test.name().length() - "Test".length());
			String home = mainPackages.get(subject);
			if (home != null && !home.equals(test.packageName())) {
				problems.add(test.name() + " is in " + test.packageName() + " but " + subject + " is in " + home);
			}
		}

		assertNone(problems);
	}

	private static Set<String> clientMixins() throws IOException {
		String config = Files.readString(Path.of("src/main/resources/lifeblood.mixins.json"), StandardCharsets.UTF_8);
		Matcher section = Pattern.compile("\"client\"\\s*:\\s*\\[([^]]*)]").matcher(config);
		Set<String> names = new HashSet<>();
		if (section.find()) {
			Matcher name = Pattern.compile("\"(\\w+)\"").matcher(section.group(1));
			while (name.find()) {
				names.add(name.group(1));
			}
		}

		return names;
	}

	private static List<SourceFile> concat(List<SourceFile> a, List<SourceFile> b) {
		List<SourceFile> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	private static void assertNone(List<String> problems) {
		assertTrue(problems.isEmpty(), () -> "\n- " + String.join("\n- ", problems) + "\n");
	}
}
