package lifeblood.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.nbt.tags.CompoundTag;
import java.util.UUID;
import lifeblood.entity.NpcGender;
import org.junit.jupiter.api.Test;

class PeopleRegistryTest {
	@Test
	void recordsSurviveSaving() {
		PeopleRegistry people = new PeopleRegistry(PeopleRegistry.DATA_ID);
		UUID id = UUID.randomUUID();
		PeopleRegistry.Person person = people.person(id, "Tub", NpcGender.MALE, false);
		person.birthTime = -123456L;
		person.homeBiome = "tundra";
		person.faction = "settlers";
		person.lastX = 10.5;
		person.lastY = 64.0;
		person.lastZ = -3.25;
		person.lastDimension = 1;
		person.lastSeen = 9000L;
		UUID unaffiliated = UUID.randomUUID();
		people.person(unaffiliated, "Timbo", NpcGender.MALE, false);
		people.died(unaffiliated);

		CompoundTag tag = new CompoundTag();
		people.save(tag);
		PeopleRegistry loaded = new PeopleRegistry(PeopleRegistry.DATA_ID);
		loaded.load(tag);
		PeopleRegistry.Person copy = loaded.find(id);
		assertEquals("Tub", copy.name);
		assertEquals(NpcGender.MALE, copy.gender);
		assertEquals(-123456L, copy.birthTime);
		assertEquals("tundra", copy.homeBiome);
		assertEquals("settlers", copy.faction);
		assertEquals(-3.25, copy.lastZ);
		assertEquals(1, copy.lastDimension);
		assertEquals(9000L, copy.lastSeen);
		assertNull(loaded.find(unaffiliated).faction);
		assertFalse(loaded.find(unaffiliated).alive);
	}
}
