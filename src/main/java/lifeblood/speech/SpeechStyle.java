package lifeblood.speech;

import com.mojang.nbt.tags.CompoundTag;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lifeblood.entity.EntityNPC;
import lifeblood.personality.TraitAxis;

/**
 * The surface of an NPC's voice, rolled once and independent of personality, so two NPCs with the same traits still
 * sound different: what they call people, a catchphrase, a filler they tack on, and habits like dropping g's.
 * The choices themselves come from the style_* symbols in the speech files.
 */
public class SpeechStyle {
	private static final Pattern ING = Pattern.compile("\\b([A-Za-z]{3,})ing\\b'?");
	private static final Set<String> KEEP_ING = Set.of("str", "spr", "th", "br", "k", "r", "s", "w", "sw", "st", "cl", "fl", "sl");
	private static final float CATCHPHRASE_CHANCE = 0.6F;
	private static final float FILLER_CHANCE = 0.35F;

	private String address = "";
	private String catchphrase = "";
	private String filler = "";
	private boolean dropsGs;
	private boolean trailsOff;
	private boolean rolled;

	public boolean isRolled() {
		return this.rolled;
	}

	public String getAddress() {
		return this.address;
	}

	public String getCatchphrase() {
		return this.catchphrase;
	}

	/** Rolls a new style. Crude NPCs drop their g's more often; shy and timid ones trail off. */
	public void roll(EntityNPC npc, Random random) {
		SpeechContext context = new SpeechContext(npc, null, 0.0F);
		long now = npc.world.getWorldTime();
		this.address = SpeechEngine.fixPronounI(orEmpty(SpeechEngine.speak("style_address", context, new HashMap<>(), now, random)).toLowerCase());
		this.catchphrase = random.nextFloat() < CATCHPHRASE_CHANCE ? orEmpty(SpeechEngine.speak("style_catchphrase", context, new HashMap<>(), now, random)) : "";
		this.filler = random.nextFloat() < FILLER_CHANCE ? SpeechEngine.fixPronounI(orEmpty(SpeechEngine.speak("style_filler", context, new HashMap<>(), now, random)).toLowerCase()) : "";
		float crude = Math.max(0.0F, -npc.personality.get(TraitAxis.SPEECH));
		float shy = Math.max(0.0F, -npc.personality.get(TraitAxis.SOCIABILITY)) + Math.max(0.0F, -npc.personality.get(TraitAxis.COURAGE));
		this.dropsGs = random.nextFloat() < 0.1F + 0.6F * crude;
		this.trailsOff = random.nextFloat() < 0.1F + 0.25F * shy;
		this.rolled = true;
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
		return tag;
	}

	public void load(CompoundTag tag) {
		this.rolled = tag.getBoolean("Rolled");
		this.address = tag.getStringOrDefault("Address", "");
		this.catchphrase = tag.getStringOrDefault("Catchphrase", "");
		this.filler = tag.getStringOrDefault("Filler", "");
		this.dropsGs = tag.getBoolean("DropsGs");
		this.trailsOff = tag.getBoolean("TrailsOff");
	}
}
