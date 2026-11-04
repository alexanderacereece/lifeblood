package npclaude.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgeStageTest {
	private static AgeStage atAge(float years) {
		return AgeStage.of(years / 18.0F);
	}

	@Test
	void stagesFollowTheAgeBands() {
		assertEquals(AgeStage.BABY, atAge(0.0F));
		assertEquals(AgeStage.BABY, atAge(2.9F));
		assertEquals(AgeStage.CHILD, atAge(3.0F));
		assertEquals(AgeStage.CHILD, atAge(9.9F));
		assertEquals(AgeStage.YOUNG_TEEN, atAge(10.0F));
		assertEquals(AgeStage.YOUNG_TEEN, atAge(15.9F));
		assertEquals(AgeStage.TEEN, atAge(16.0F));
		assertEquals(AgeStage.TEEN, atAge(17.9F));
		assertEquals(AgeStage.ADULT, AgeStage.of(1.0F));
		assertEquals("young_teen", AgeStage.YOUNG_TEEN.id());
	}
}
