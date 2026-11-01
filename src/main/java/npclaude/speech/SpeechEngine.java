package npclaude.speech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import npclaude.NPClaude;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a symbol into a sentence: picks one of its lines that fits the moment, then fills in every [name] inside it,
 * recursively. More specific lines (more conditions) are more likely; lines the NPC used recently are much less likely.
 */
public final class SpeechEngine {
	private static final Pattern TOKEN = Pattern.compile("\\[([a-z_0-9]+)((?:\\.[a-z]+)*)]");
	private static final int MAX_DEPTH = 8;
	private static final int MAX_TRIES = 6;
	/** Each satisfied condition makes a line this many times more likely. */
	private static final float SPECIFICITY_BONUS = 2.0F;
	private static final float RECENT_PENALTY = 0.1F;
	/** A line counts as recent for this long (world ticks): about five minutes. */
	private static final long RECENT_TICKS = 6000L;
	/**
	 * Topics (a favorite or hated something) are words like [favorite_thing]. Once one comes up, lines about it are
	 * much less likely for half a day, so an NPC doesn't keep going on about the same thing.
	 */
	private static final Pattern TOPIC = Pattern.compile("\\[((?:favorite|hated)_[a-z_]+)");
	private static final float TOPIC_PENALTY = 0.05F;
	private static final long TOPIC_TICKS = 12000L;

	private SpeechEngine() {
	}

	/** Thrown inside expansion when a line needs a fact or symbol that isn't there, so another line is tried. */
	private static final class Missing extends Exception {
		Missing(String name) {
			super(name, null, false, false);
		}
	}

	/**
	 * Expands a symbol for this context, or returns null if nothing fits.
	 *
	 * @param used when each line was last used by this speaker, by line source; updated with the lines picked
	 */
	@Nullable
	public static String speak(String symbol, SpeechContext context, Map<String, Long> used, long now, Random random) {
		try {
			return tidy(expandSymbol(symbol, context, used, now, random, 0));
		} catch (Missing missing) {
			return null;
		}
	}

	private static String expandSymbol(String symbol, SpeechContext context, Map<String, Long> used, long now, Random random, int depth)
		throws Missing {
		if (depth > MAX_DEPTH) {
			NPClaude.LOGGER.warn("Speech symbol '{}' nests too deep", symbol);
			throw new Missing(symbol);
		}

		List<SpeechLibrary.Line> candidates = new ArrayList<>();
		for (SpeechLibrary.Line line : SpeechLibrary.get(symbol)) {
			if (line.allows(context)) {
				candidates.add(line);
			}
		}

		for (int attempt = 0; attempt < MAX_TRIES && !candidates.isEmpty(); attempt++) {
			SpeechLibrary.Line line = pick(candidates, used, now, random);
			try {
				String text = expandText(line.text(), context, used, now, random, depth);
				used.put(line.source(), now);
				Matcher topics = TOPIC.matcher(line.text());
				while (topics.find()) {
					used.put("@" + topics.group(1), now);
					context.mention(topics.group(1));
				}

				for (Condition condition : line.conditions()) {
					if (condition instanceof Condition.Fact fact && !fact.negated() && fact.key().endsWith("_like")) {
						context.mention("felt:" + fact.key());
					}
				}

				return text;
			} catch (Missing missing) {
				candidates.remove(line);
			}
		}

		throw new Missing(symbol);
	}

	private static SpeechLibrary.Line pick(List<SpeechLibrary.Line> candidates, Map<String, Long> used, long now, Random random) {
		float[] weights = new float[candidates.size()];
		float total = 0.0F;
		for (int i = 0; i < weights.length; i++) {
			SpeechLibrary.Line line = candidates.get(i);
			float weight = line.weight() * (float) Math.pow(SPECIFICITY_BONUS, line.specificity());
			Long last = used.get(line.source());
			if (last != null && now - last < RECENT_TICKS) {
				weight *= RECENT_PENALTY;
			}

			Matcher topics = TOPIC.matcher(line.text());
			while (topics.find()) {
				Long mentioned = used.get("@" + topics.group(1));
				if (mentioned != null && now - mentioned < TOPIC_TICKS) {
					weight *= TOPIC_PENALTY;
				}
			}

			weights[i] = weight;
			total += weight;
		}

		float roll = random.nextFloat() * total;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0.0F) {
				return candidates.get(i);
			}
		}

		return candidates.get(candidates.size() - 1);
	}

	private static String expandText(String text, SpeechContext context, Map<String, Long> used, long now, Random random, int depth)
		throws Missing {
		Matcher matcher = TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			result.append(text, last, matcher.start());
			String name = matcher.group(1);
			List<String> modifiers = matcher.group(2).isEmpty() ? List.of() : Arrays.asList(matcher.group(2).substring(1).split("\\."));
			result.append(resolve(name, modifiers, context, used, now, random, depth));
			last = matcher.end();
		}

		result.append(text, last, text.length());
		return result.toString();
	}

	/** A word fact, then a plain fact, then another symbol. */
	private static String resolve(String name, List<String> modifiers, SpeechContext context, Map<String, Long> used, long now, Random random,
		int depth) throws Missing {
		String value;
		Word word = context.word(name);
		if (word != null) {
			value = word.render(modifiers);
		} else if (context.fact(name) != null) {
			value = context.fact(name);
		} else if (SpeechLibrary.has(name)) {
			value = expandSymbol(name, context, used, now, random, depth + 1);
		} else {
			throw new Missing(name);
		}

		return modifiers.contains("cap") ? capitalize(value) : value;
	}

	/** Collapses spacing left by empty parts, fixes spaces before punctuation, and capitalizes the start. */
	static String tidy(String text) {
		String result = text.replaceAll("\\s+", " ").replaceAll(" ([,.!?;:])", "$1").replaceAll("^[ ,]+", "").strip();
		return capitalize(result);
	}

	static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
