package lifeblood.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.nbt.tags.CompoundTag;
import java.util.Random;
import lifeblood.family.AgeStage;
import org.junit.jupiter.api.Test;

/** Speech styles: habits that change how lines come out, and words that grow up with the NPC. */
class SpeechStyleTest {
	private static SpeechStyle style(boolean dropsGs, boolean trailsOff, String filler) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Rolled", true);
		tag.putBoolean("DropsGs", dropsGs);
		tag.putBoolean("TrailsOff", trailsOff);
		tag.putString("Filler", filler);
		tag.putString("Stage", AgeStage.ADULT.id());
		SpeechStyle style = new SpeechStyle();
		style.load(tag);
		return style;
	}

	@Test
	void droppedGsKeepShortWordsAndApostrophes() {
		SpeechStyle style = style(true, false, "");
		Random random = new Random(1);
		assertEquals("I'm goin' fishin' now", style.apply("I'm going fishing now", random));
		// "thing", "king" and "string" aren't -ing words, so they stay.
		assertEquals("A string and a thing", style.apply("A string and a thing", random));
		assertEquals("Everythin's fine", style.apply("Everything's fine", random));
	}

	@Test
	void noHabitsLeavesLinesAlone() {
		SpeechStyle style = style(false, false, "");
		assertEquals("Nothing's changing.", style.apply("Nothing's changing.", new Random(1)));
	}

	@Test
	void habitsOnlyTouchSentencesThatEndPlainly() {
		SpeechStyle style = style(false, true, "eh");
		Random random = new Random(1);
		boolean sawFiller = false;
		boolean sawTrail = false;
		for (int i = 0; i < 200; i++) {
			String line = style.apply("Nice day.", random);
			sawFiller |= line.contains(", eh.");
			sawTrail |= line.endsWith("...");
			// A question is never changed.
			assertEquals("Nice day?", style.apply("Nice day?", random));
		}

		assertTrue(sawFiller, "the filler gets tacked on sometimes");
		assertTrue(sawTrail, "trailing off happens sometimes");
	}

	@Test
	void onlyATeenagersCatchphraseCanLastIntoAdulthood() {
		assertTrue(SpeechStyle.keepsCatchphrase(AgeStage.TEEN, AgeStage.ADULT, "Whatever.", 0.0F));
		assertFalse(SpeechStyle.keepsCatchphrase(AgeStage.TEEN, AgeStage.ADULT, "Whatever.", 0.99F));
		assertFalse(SpeechStyle.keepsCatchphrase(AgeStage.TEEN, AgeStage.ADULT, "", 0.0F));
		assertFalse(SpeechStyle.keepsCatchphrase(AgeStage.CHILD, AgeStage.YOUNG_TEEN, "Pinky promise!", 0.0F));
		assertFalse(SpeechStyle.keepsCatchphrase(null, AgeStage.ADULT, "Hm.", 0.0F));
	}

	@Test
	void theStageItsWordsWerePickedForIsSaved() {
		CompoundTag tag = style(false, false, "").save();
		assertEquals(AgeStage.ADULT.id(), tag.getString("Stage"));
	}
}
