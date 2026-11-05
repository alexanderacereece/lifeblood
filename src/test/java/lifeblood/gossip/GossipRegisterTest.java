package lifeblood.gossip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.nbt.tags.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GossipRegisterTest {
	private static final long FRESH = 1000L;
	private final UUID tom = UUID.randomUUID();
	private final UUID ada = UUID.randomUUID();
	private final UUID bram = UUID.randomUUID();

	@Test
	void theSameThingWhileFreshIsTheSameNews() {
		GossipRegister register = new GossipRegister(GossipRegister.DATA_ID);
		News first = register.report(NewsKind.ATTACK, this.tom, "Tom", this.ada, "Ada", "", 0L, FRESH);
		News again = register.report(NewsKind.ATTACK, this.tom, "Tom", this.ada, "Ada", "", 500L, FRESH);
		assertEquals(first.id(), again.id());
		assertEquals(500L, again.when());
		assertEquals(1, register.all().size());

		News later = register.report(NewsKind.ATTACK, this.tom, "Tom", this.ada, "Ada", "", 2000L, FRESH);
		assertNotEquals(first.id(), later.id());
	}

	@Test
	void everyoneHearsItOnceAndRemembersFromWhom() {
		GossipRegister register = new GossipRegister(GossipRegister.DATA_ID);
		News piece = register.report(NewsKind.WEDDING, this.tom, "Tom", this.ada, "Ada", "", 0L, FRESH);
		assertTrue(register.hear(piece, this.bram, this.tom, "Tom", true));
		assertFalse(register.hear(piece, this.bram, this.ada, "Ada", false));
		GossipRegister.Heard heard = register.heard(piece, this.bram);
		assertNotNull(heard);
		assertEquals("Tom", heard.fromName());
		assertTrue(heard.exaggerated());
		assertFalse(heard.firsthand());
		assertNull(register.heard(piece, this.tom));
	}

	@Test
	void keepsOnlyTheNewest() {
		GossipRegister register = new GossipRegister(GossipRegister.DATA_ID);
		for (int i = 0; i < GossipRegister.MAX_NEWS + 10; i++) {
			register.report(NewsKind.BRAWL, UUID.randomUUID(), "Someone", null, "", "", i, FRESH);
		}

		assertEquals(GossipRegister.MAX_NEWS, register.all().size());
		assertEquals(10L, register.all().get(0).when());
	}

	@Test
	void savesAndLoadsBack() {
		GossipRegister register = new GossipRegister(GossipRegister.DATA_ID);
		News piece = register.report(NewsKind.BIRTH, this.tom, "Tom", this.ada, "Ada", "Pip", 42L, FRESH);
		register.hear(piece, this.bram, null, "", false);
		CompoundTag tag = new CompoundTag();
		register.save(tag);

		GossipRegister loaded = new GossipRegister(GossipRegister.DATA_ID);
		loaded.load(tag);
		assertEquals(register.all(), loaded.all());
		assertTrue(loaded.heard(loaded.all().get(0), this.bram).firsthand());
		News next = loaded.report(NewsKind.DEATH, this.ada, "Ada", null, "", "", 5000L, FRESH);
		assertNotEquals(piece.id(), next.id());
	}
}
