package npclaude.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WordTest {
	@Test
	void plurals() {
		assertEquals("berries", Word.plural("berry"));
		assertEquals("days", Word.plural("day"));
		assertEquals("boxes", Word.plural("box"));
		assertEquals("torches", Word.plural("torch"));
		assertEquals("apples", Word.plural("apple"));
	}

	@Test
	void countableWordsTakeArticles() {
		Word apple = Word.one("apple");
		assertEquals("an apple", apple.render(List.of("a")));
		assertEquals("apples", apple.render(List.of("s")));
		assertEquals("it", apple.render(List.of("it")));
		assertEquals("is", apple.render(List.of("is")));
		assertEquals("a cake", Word.one("cake").render(List.of("a")));
	}

	@Test
	void massNounsDont() {
		Word rain = Word.mass("rain");
		assertEquals("some rain", rain.render(List.of("a")));
		assertEquals("the rain", rain.render(List.of("the")));
	}

	@Test
	void namesStayAsTheyAre() {
		assertEquals("Timbo", Word.name("Timbo").render(List.of()));
	}
}
