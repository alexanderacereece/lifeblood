package lifeblood.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import lifeblood.personality.NpcPersonality;
import lifeblood.personality.Quirk;
import lifeblood.personality.TraitAxis;
import org.junit.jupiter.api.Test;

/**
 * The personality compatibility table (docs/personality.md, "Personality compatibility"): each rule moves A's opinion
 * of B by its amount, measured against two blank personalities so the shared "kindred spirits" bonus cancels out.
 */
class CompatibilityTest {
	private static final float EPSILON = 0.001F;

	/** How much B's or A's traits move A's opinion of B, compared with two blank personalities. */
	private static float shift(NpcPersonality self, NpcPersonality other) {
		return Compatibility.of(self, new NpcMemories(), other, UUID.randomUUID())
			- Compatibility.of(new NpcPersonality(), new NpcMemories(), new NpcPersonality(), UUID.randomUUID());
	}

	private static NpcPersonality with(TraitAxis axis, float value) {
		NpcPersonality personality = new NpcPersonality();
		personality.set(axis, value);
		return personality;
	}

	private static NpcPersonality with(Quirk... quirks) {
		NpcPersonality personality = new NpcPersonality();
		for (Quirk quirk : quirks) {
			personality.addQuirk(quirk);
		}

		return personality;
	}

	@Test
	void mannersCountForEveryone() {
		assertEquals(5.0F, shift(new NpcPersonality(), with(TraitAxis.MANNERS, 0.9F)), EPSILON);
		assertEquals(-5.0F, shift(new NpcPersonality(), with(TraitAxis.MANNERS, -0.9F)), EPSILON);
		// Rude people mind other rude people less.
		assertEquals(-2.0F, shift(with(TraitAxis.MANNERS, -0.9F), with(TraitAxis.MANNERS, -0.9F)), EPSILON);
	}

	@Test
	void theHardworkingResentTheLazyByDegree() {
		assertEquals(-10.0F, shift(with(TraitAxis.WORK_ETHIC, 1.0F), with(TraitAxis.WORK_ETHIC, -1.0F)), 0.5F);
		float mild = shift(with(TraitAxis.WORK_ETHIC, 0.5F), with(TraitAxis.WORK_ETHIC, -0.5F));
		assertTrue(mild < 0.0F && mild > -10.0F, "a milder pair minds less: " + mild);
		// The lazy don't care either way.
		assertTrue(shift(with(TraitAxis.WORK_ETHIC, -1.0F), with(TraitAxis.WORK_ETHIC, 1.0F)) > -0.5F);
	}

	@Test
	void onlyExtremesOfSpeechClashBothWays() {
		NpcPersonality crude = with(TraitAxis.SPEECH, -0.9F);
		NpcPersonality proper = with(TraitAxis.SPEECH, 0.9F);
		assertTrue(shift(crude, proper) <= -5.0F);
		assertTrue(shift(proper, crude) <= -5.0F);
		assertEquals(0.0F, shift(with(TraitAxis.SPEECH, -0.4F), with(TraitAxis.SPEECH, 0.4F)), 1.0F);
	}

	@Test
	void quirkPairs() {
		assertEquals(-6.0F, shift(with(Quirk.HOTHEAD), with(Quirk.HOTHEAD)), EPSILON);
		assertEquals(4.0F, shift(with(Quirk.HOTHEAD), with(Quirk.ZEN)), EPSILON);
		assertEquals(-4.0F, shift(with(Quirk.GREEDY), with(Quirk.GENEROUS)), EPSILON);
		assertEquals(4.0F, shift(with(Quirk.NOSY), with(Quirk.CHATTERBOX)), EPSILON);
		assertEquals(-3.0F, shift(with(Quirk.TACITURN), with(Quirk.CHATTERBOX)), EPSILON);
		assertEquals(-4.0F, shift(with(Quirk.PROUD), with(Quirk.PROUD)), EPSILON + 2.0F);
		assertEquals(2.0F, shift(new NpcPersonality(), with(Quirk.HUMBLE)), EPSILON);
	}

	@Test
	void paranoidStartCoolerAndGullibleWarmerEvenWithPlayers() {
		assertEquals(-3.0F, Compatibility.of(with(Quirk.PARANOID), new NpcMemories(), null, null), EPSILON);
		assertEquals(3.0F, Compatibility.of(with(Quirk.GULLIBLE), new NpcMemories(), null, null), EPSILON);
	}

	@Test
	void theTotalNeverPassesTheLimit() {
		NpcPersonality grump = with(Quirk.HOTHEAD, Quirk.PROUD, Quirk.COMPETITIVE);
		grump.set(TraitAxis.WORK_ETHIC, 1.0F);
		grump.set(TraitAxis.SPEECH, 1.0F);
		grump.set(TraitAxis.OUTLOOK, 1.0F);
		grump.set(TraitAxis.MANNERS, -1.0F);
		NpcPersonality other = with(Quirk.HOTHEAD, Quirk.PROUD, Quirk.COMPETITIVE);
		other.set(TraitAxis.WORK_ETHIC, -1.0F);
		other.set(TraitAxis.SPEECH, -1.0F);
		other.set(TraitAxis.OUTLOOK, -1.0F);
		other.set(TraitAxis.MANNERS, 1.0F);
		other.set(TraitAxis.SOCIABILITY, 1.0F);
		grump.set(TraitAxis.SOCIABILITY, -1.0F);
		float total = Compatibility.of(grump, new NpcMemories(), other, UUID.randomUUID());
		assertEquals(-Compatibility.LIMIT, total, EPSILON);
	}
}
