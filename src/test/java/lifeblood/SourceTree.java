package lifeblood;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The mod's source files as the architecture rules and the code map see them: each file's package, class name, doc
 * comment, imports and length, read straight from src/main/java (tests run from the project folder).
 */
final class SourceTree {
	static final Path MAIN = Path.of("src/main/java");
	static final Path TEST = Path.of("src/test/java");

	private static final Pattern PACKAGE = Pattern.compile("^package ([\\w.]+);", Pattern.MULTILINE);
	private static final Pattern IMPORT = Pattern.compile("^import (?:static )?([\\w.]+)(?:\\.\\*)?;", Pattern.MULTILINE);
	/** The first top-level type: its doc comment (if any), annotations, modifiers, kind and name. */
	private static final Pattern TYPE = Pattern.compile(
		"(/\\*\\*(?:[^*]|\\*(?!/))*\\*/\\s*)?((?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*)^(?:public |protected |private |abstract |final |sealed |non-sealed |static |strictfp )*(class|interface|enum|record|@interface) (\\w+)",
		Pattern.MULTILINE);
	private static final Pattern PACKAGE_DOC = Pattern.compile("(/\\*\\*(?:[^*]|\\*(?!/))*\\*/)\\s*package ");

	/** One source file. {@code doc} is the doc comment's text without the comment markers, or null if it has none. */
	record SourceFile(Path path, String packageName, String name, String doc, List<String> imports, int lines, String text) {
		/** The package relative to lifeblood, with slashes: "ai/behavior"; "" for the root package. */
		String folder() {
			return packageName.equals("lifeblood") ? "" : packageName.substring("lifeblood.".length()).replace('.', '/');
		}

		boolean isPackageInfo() {
			return name.equals("package-info");
		}
	}

	private SourceTree() {
	}

	static List<SourceFile> read(Path root) {
		List<SourceFile> files = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java")).sorted()::iterator) {
				files.add(parse(path));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return files;
	}

	/** Every package, by name, with its package-info if it has one (null otherwise). */
	static TreeMap<String, SourceFile> packages(List<SourceFile> files) {
		TreeMap<String, SourceFile> packages = new TreeMap<>();
		for (SourceFile file : files) {
			packages.putIfAbsent(file.packageName(), null);
			if (file.isPackageInfo()) {
				packages.put(file.packageName(), file);
			}
		}

		return packages;
	}

	private static SourceFile parse(Path path) throws IOException {
		String text = Files.readString(path, StandardCharsets.UTF_8);
		String fileName = path.getFileName().toString();
		String name = fileName.substring(0, fileName.length() - ".java".length());
		Matcher pkg = PACKAGE.matcher(text);
		String packageName = pkg.find() ? pkg.group(1) : "";
		List<String> imports = new ArrayList<>();
		Matcher imp = IMPORT.matcher(text);
		while (imp.find()) {
			imports.add(imp.group(1));
		}

		String doc = null;
		if (name.equals("package-info")) {
			Matcher m = PACKAGE_DOC.matcher(text);
			if (m.find()) {
				doc = clean(m.group(1));
			}
		} else {
			Matcher m = TYPE.matcher(text);
			while (m.find()) {
				if (m.group(4).equals(name)) {
					doc = m.group(1) != null ? clean(m.group(1)) : null;
					break;
				}
			}
		}

		int lines = (int) text.lines().count();
		return new SourceFile(path, packageName, name, doc, imports, lines, text);
	}

	/** A doc comment's text: markers and leading stars gone, lines joined with newlines. */
	private static String clean(String comment) {
		String body = comment.substring(3, comment.length() - 2);
		StringBuilder out = new StringBuilder();
		for (String line : body.split("\n")) {
			String trimmed = line.strip();
			if (trimmed.startsWith("*")) {
				trimmed = trimmed.substring(1).strip();
			}

			out.append(trimmed).append('\n');
		}

		return out.toString().strip();
	}

	/**
	 * The first sentence of a doc comment, as plain text: {@code {@link X}} becomes X, tags and HTML go. Sentences end
	 * at a full stop followed by a space or the end of a paragraph; "e.g." and "i.e." don't end one.
	 */
	static String firstSentence(String doc) {
		if (doc == null) {
			return "";
		}

		String text = doc.split("\n\\s*\n")[0];
		int list = text.indexOf("\n- ");
		if (list >= 0) {
			// "Families and words:" followed by a list: the lead-in is the summary.
			text = text.substring(0, list).strip().replaceAll(":$", ".");
		}

		text = text.replaceAll("\\{@(?:link|linkplain|code) (?:[a-z]\\w*\\.)*([^}]*)}", "$1").replace('#', '.');
		text = text.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		text = text.replaceAll("\\s+", " ").strip();
		String guarded = text.replace("e.g.", "e\u0001g\u0001").replace("i.e.", "i\u0001e\u0001");
		Matcher end = Pattern.compile("[.!?](?=\\s|$)").matcher(guarded);
		String sentence = end.find() ? guarded.substring(0, end.end()) : guarded;
		return sentence.replace('\u0001', '.');
	}
}
