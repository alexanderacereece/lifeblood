package lifeblood.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TravelTalkTest {
	@Test
	void directionsFollowTheMapWithNorthAsMinusZ() {
		assertEquals("north", TravelTalk.direction(0, -10));
		assertEquals("south", TravelTalk.direction(0, 10));
		assertEquals("east", TravelTalk.direction(10, 0));
		assertEquals("west", TravelTalk.direction(-10, 0));
		assertEquals("north-east", TravelTalk.direction(10, -10));
		assertEquals("south-west", TravelTalk.direction(-10, 10));
	}

	@Test
	void distancesAreSaidAsAPersonWould() {
		assertEquals("not far", TravelTalk.distance(50));
		assertEquals("a few hours' walk", TravelTalk.distance(250));
		assertEquals("a day or two's walk", TravelTalk.distance(1000));
		assertEquals("a long way", TravelTalk.distance(5000));
	}
}
