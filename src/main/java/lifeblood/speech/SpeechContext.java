package lifeblood.speech;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.entity.Entity;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a line can depend on at the moment an NPC speaks: who's speaking and to whom, the facts of the moment
 * ("time" = "night"), and the words lines can mention ([favorite_food]). Built by {@link SpeechFacts}.
 */
public class SpeechContext {
	/** At the worst or best mood, outlook shifts this far toward gloomy or cheerful. */
	private static final float MOOD_OUTLOOK_SHIFT = 0.6F;
	/** A bad mood makes people this much shorter with others and less keen to work. */
	private static final float MOOD_MANNERS_SHIFT = 0.3F;
	/** Chattiness: the chance of saying each optional piece of a line, before personality, mood and listener. */
	private static final float BASE_CHATTINESS = 0.45F;
	private static final float MIN_CHATTINESS = 0.05F;
	private static final float MAX_CHATTINESS = 0.95F;

	private final EntityNPC speaker;
	@Nullable
	private final Entity listener;
	private final float mood;
	private final Map<String, String> facts = new HashMap<>();
	private final Map<String, Word> words = new HashMap<>();
	/**
	 * What the chosen lines gave away about the speaker's tastes: topics like "favorite_food", and "felt:weather_like"
	 * when a line expressed how it feels about the weather (or biome, or season) right now.
	 */
	private final Set<String> mentioned = new HashSet<>();

	public SpeechContext(EntityNPC speaker, @Nullable Entity listener, float mood) {
		this.speaker = speaker;
		this.listener = listener;
		this.mood = mood;
	}

	public EntityNPC speaker() {
		return this.speaker;
	}

	@Nullable
	public Entity listener() {
		return this.listener;
	}

	public SpeechContext fact(String key, String value) {
		this.facts.put(key, value);
		return this;
	}

	public SpeechContext word(String key, Word word) {
		this.words.put(key, word);
		return this;
	}

	@Nullable
	public String fact(String key) {
		return this.facts.get(key);
	}

	@Nullable
	public Word word(String key) {
		return this.words.get(key);
	}

	public void mention(String topic) {
		this.mentioned.add(topic);
	}

	public Set<String> mentioned() {
		return this.mentioned;
	}

	/**
	 * How much it says right now, 0 to 1: the chance of each optional piece of a line ([detail?]). Sociable people
	 * and chatterboxes say more, the taciturn little; a bad mood or a listener it dislikes makes it shorter, and
	 * friends get a bit more.
	 */
	public float chattiness() {
		return chattiness(this.speaker.personality.get(TraitAxis.SOCIABILITY), this.speaker.personality.has(Quirk.CHATTERBOX),
			this.speaker.personality.has(Quirk.TACITURN), this.mood, this.fact("opinion"));
	}

	static float chattiness(float sociability, boolean chatterbox, boolean taciturn, float mood, @Nullable String opinion) {
		float chance = BASE_CHATTINESS + 0.25F * sociability + (chatterbox ? 0.3F : 0.0F) - (taciturn ? 0.35F : 0.0F);
		if (mood < -30.0F) {
			chance -= 0.15F;
		}

		if ("enemy".equals(opinion) || "disliked".equals(opinion)) {
			chance -= 0.2F;
		} else if ("friend".equals(opinion) || "close_friend".equals(opinion)) {
			chance += 0.1F;
		}

		return Math.max(MIN_CHATTINESS, Math.min(MAX_CHATTINESS, chance));
	}

	/**
	 * A trait as it comes across right now: the NPC's own value, shifted by mood. A cheerful NPC in mourning
	 * sounds gloomy for a while; bad days make people short with others.
	 */
	public float axis(TraitAxis axis) {
		float value = this.speaker.personality.get(axis);
		float mood = this.mood / 100.0F;
		value += switch (axis) {
			case OUTLOOK -> mood * MOOD_OUTLOOK_SHIFT;
			case MANNERS, WORK_ETHIC -> Math.min(0.0F, mood) * MOOD_MANNERS_SHIFT;
			default -> 0.0F;
		};
		return Math.max(-1.0F, Math.min(1.0F, value));
	}
}
