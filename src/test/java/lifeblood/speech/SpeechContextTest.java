package lifeblood.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeechContextTest {
	@Test
	void chattyPeopleSayMoreAndQuietOnesLess() {
		float plain = SpeechContext.chattiness(0.0F, false, false, 0.0F, "neutral");
		assertEquals(0.45F, plain, 0.001F);
		assertTrue(SpeechContext.chattiness(1.0F, true, false, 0.0F, "neutral") > 0.9F);
		assertTrue(SpeechContext.chattiness(-1.0F, false, true, 0.0F, "neutral") < 0.1F);
	}

	@Test
	void moodAndListenerChangeHowMuch() {
		float plain = SpeechContext.chattiness(0.0F, false, false, 0.0F, "neutral");
		assertTrue(SpeechContext.chattiness(0.0F, false, false, -50.0F, "neutral") < plain);
		assertTrue(SpeechContext.chattiness(0.0F, false, false, 0.0F, "disliked") < plain);
		assertTrue(SpeechContext.chattiness(0.0F, false, false, 0.0F, "close_friend") > plain);
	}

	@Test
	void alwaysSomeChanceEitherWay() {
		assertEquals(0.05F, SpeechContext.chattiness(-1.0F, false, true, -100.0F, "enemy"), 0.001F);
		assertEquals(0.95F, SpeechContext.chattiness(1.0F, true, false, 0.0F, "friend"), 0.001F);
	}
}
