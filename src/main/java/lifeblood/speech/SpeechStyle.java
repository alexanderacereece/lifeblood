package lifeblood.speech;

import com.mojang.nbt.tags.CompoundTag;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import lifeblood.entity.AgeStage;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.TraitAxis;

/**
 * The surface of an NPC's voice, independent of personality, so two NPCs with the same traits still sound different:
 * what they call people, a catchphrase, a filler they tack on, and habits like dropping g's.
 * The choices themselves come from the style_* symbols in the speech files.
 *
 * The habits are rolled once for life. The words (address, catchphrase, filler) belong to an age stage: they come from
 * the stage's versions of the style symbols in ages.speech ("style_catchphrase.child"), babies have none, and they're
 * picked again as the NPC grows into each new stage (see {@link #fitsAge} and {@link #growInto}).
 */
public class SpeechStyle {
	private static final Pattern ING = Pattern.compile("\\b([A-Za-z]{3,})ing\\b'?");
	private static final Set<String> KEEP_ING = Set.of("str", "spr", "th", "br", "k", "r", "s", "w", "sw", "st", "cl", "fl", "sl");
	private static final float CATCHPHRASE_CHANCE = 0.6F;
	private static final float FILLER_CHANCE = 0.35F;
	/** Chance a teenager's catchphrase sticks with them into adulthood, as some do. */
	private static final float KEEP_TEEN_CATCHPHRASE_CHANCE = 0.25F;

	private String address = "";
	private String catchphrase = "";
	private String filler = "";
	private boolean dropsGs;
	private boolean trailsOff;
	private boolean rolled;
	/** The age stage the words were picked for; null if not picked yet. */
	@Nullable
	private AgeStage stage;

	public boolean isRolled() {
		return this.rolled;
	}

	public String getAddress() {
		return this.address;
	}

	public String getCatchphrase() {
		return this.catchphrase;
	}

	/** Rolls a new style: habits for life, and words for the NPC's current age. */
	public void roll(EntityNPC npc, Random random) {
		this.rollHabits(npc, random);
		this.rollWords(npc, random);
		this.rolled = true;
	}

	/** Whether the words were picked for the age stage the NPC is at now. */
	public boolean fitsAge(EntityNPC npc) {
		return this.stage == npc.life.getStage();
	}

	/**
	 * Picks words for the stage the NPC has grown into, keeping its habits. A teenager's catchphrase sometimes stays
	 * with them as an adult.
	 */
	public void growInto(EntityNPC npc, Random random) {
		String old = this.catchphrase;
		boolean keepCatchphrase = this.stage == AgeStage.TEEN && npc.life.getStage() == AgeStage.ADULT && !old.isEmpty()
			&& random.nextFloat() < KEEP_TEEN_CATCHPHRASE_CHANCE;
		this.rollWords(npc, random);
		if (keepCatchphrase) {
			this.catchphrase = old;
		}
	}

	/** Picks address, catchphrase and filler from the style symbols, using the age versions for the young. */
	private void rollWords(EntityNPC npc, Random random) {
		this.stage = npc.life.getStage();
		this.address = "";
		this.catchphrase = "";
		this.filler = "";
		// Babies only babble; their words come when they start talking.
		if (this.stage == AgeStage.BABY) {
			return;
		}

		SpeechContext context = new SpeechContext(npc, null, 0.0F).fact("age", this.stage.id());
		long now = npc.world.getWorldTime();
		this.address = SpeechEngine.fixPronounI(orEmpty(SpeechEngine.speak("style_address", context, new HashMap<>(), now, random)).toLowerCase());
		this.catchphrase = random.nextFloat() < CATCHPHRASE_CHANCE ? orEmpty(SpeechEngine.speak("style_catchphrase", context, new HashMap<>(), now, random)) : "";
		this.filler = random.nextFloat() < FILLER_CHANCE ? SpeechEngine.fixPronounI(orEmpty(SpeechEngine.speak("style_filler", context, new HashMap<>(), now, random)).toLowerCase()) : "";
	}

	/** Rolls the lifelong habits. Crude NPCs drop their g's more often; shy and timid ones trail off. */
	private void rollHabits(EntityNPC npc, Random random) {
		float crude = Math.max(0.0F, -npc.personality.get(TraitAxis.SPEECH));
		float shy = Math.max(0.0F, -npc.personality.get(TraitAxis.SOCIABILITY)) + Math.max(0.0F, -npc.personality.get(TraitAxis.COURAGE));
		this.dropsGs = random.nextFloat() < 0.1F + 0.6F * crude;
		this.trailsOff = random.nextFloat() < 0.1F + 0.25F * shy;
	}

	private static String orEmpty(String text) {
		return text != null ? text : "";
	}

	/** Applies the speech habits to a finished line. */
	public String apply(String line, Random random) {
		String result = line;
		if (this.dropsGs) {
			Matcher matcher = ING.matcher(result);
			StringBuilder builder = new StringBuilder();
			while (matcher.find()) {
				String stem = matcher.group(1);
				// The match swallows a following apostrophe, so "everything's" becomes "everythin's", not "everythin''s".
				matcher.appendReplacement(builder, KEEP_ING.contains(stem.toLowerCase()) ? matcher.group() : stem + "in'");
			}

			matcher.appendTail(builder);
			result = builder.toString();
		}

		if (!this.filler.isEmpty() && result.endsWith(".") && !result.endsWith("..") && random.nextFloat() < 0.3F) {
			result = result.substring(0, result.length() - 1) + ", " + this.filler + ".";
		}

		if (this.trailsOff && result.endsWith(".") && !result.endsWith("..") && random.nextFloat() < 0.3F) {
			result = result + "..";
		}

		// Fillers saved before "I" was kept capitalized still come out right.
		return SpeechEngine.fixPronounI(result);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Rolled", this.rolled);
		tag.putString("Address", this.address);
		tag.putString("Catchphrase", this.catchphrase);
		tag.putString("Filler", this.filler);
		tag.putBoolean("DropsGs", this.dropsGs);
		tag.putBoolean("TrailsOff", this.trailsOff);
		tag.putString("Stage", this.stage != null ? this.stage.id() : "");
		return tag;
	}

	public void load(CompoundTag tag) {
		this.rolled = tag.getBoolean("Rolled");
		this.address = tag.getStringOrDefault("Address", "");
		this.catchphrase = tag.getStringOrDefault("Catchphrase", "");
		this.filler = tag.getStringOrDefault("Filler", "");
		this.dropsGs = tag.getBoolean("DropsGs");
		this.trailsOff = tag.getBoolean("TrailsOff");
		this.stage = AgeStage.byId(tag.getStringOrDefault("Stage", ""));
	}
}
