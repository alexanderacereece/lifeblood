package lifeblood.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.nbt.tags.CompoundTag;
import java.util.UUID;
import lifeblood.entity.NpcGender;
import lifeblood.people.PeopleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A three-generation family: grandpa and grandma have Mom and Uncle; Mom and Dad (married) have Kid and Sis;
 * Uncle has Cousin.
 */
class KinshipTest {
	private PeopleRegistry family;
	private UUID grandpa, grandma, mom, dad, uncle, kid, sis, cousin, stranger;

	@BeforeEach
	void buildFamily() {
		this.family = new PeopleRegistry(PeopleRegistry.DATA_ID);
		this.grandpa = add("Grandpa", NpcGender.MALE);
		this.grandma = add("Grandma", NpcGender.FEMALE);
		this.mom = add("Mom", NpcGender.FEMALE);
		this.dad = add("Dad", NpcGender.MALE);
		this.uncle = add("Uncle", NpcGender.MALE);
		this.kid = add("Kid", NpcGender.MALE);
		this.sis = add("Sis", NpcGender.FEMALE);
		this.cousin = add("Cousin", NpcGender.FEMALE);
		this.stranger = add("Stranger", NpcGender.MALE);
		parents(this.mom, this.grandpa, this.grandma);
		parents(this.uncle, this.grandpa, this.grandma);
		parents(this.kid, this.mom, this.dad);
		parents(this.sis, this.mom, this.dad);
		parents(this.cousin, this.uncle);
		this.family.startDating(this.mom, this.dad, 0L);
		this.family.setStage(this.mom, PeopleRegistry.Stage.MARRIED, 0L);
	}

	private UUID add(String name, NpcGender gender) {
		UUID id = UUID.randomUUID();
		this.family.person(id, name, gender, false);
		return id;
	}

	private void parents(UUID child, UUID... parents) {
		for (UUID parent : parents) {
			this.family.addParent(child, parent);
		}
	}

	@Test
	void closeFamily() {
		assertEquals(Kinship.SPOUSE, Kinship.of(this.family, this.mom, this.dad));
		assertEquals(Kinship.PARENT, Kinship.of(this.family, this.kid, this.mom));
		assertEquals(Kinship.CHILD, Kinship.of(this.family, this.mom, this.kid));
		assertEquals(Kinship.SIBLING, Kinship.of(this.family, this.kid, this.sis));
	}

	@Test
	void extendedFamily() {
		assertEquals(Kinship.GRANDPARENT, Kinship.of(this.family, this.kid, this.grandma));
		assertEquals(Kinship.GRANDCHILD, Kinship.of(this.family, this.grandpa, this.sis));
		assertEquals(Kinship.AUNT_UNCLE, Kinship.of(this.family, this.kid, this.uncle));
		assertEquals(Kinship.NIECE_NEPHEW, Kinship.of(this.family, this.uncle, this.kid));
		assertEquals(Kinship.COUSIN, Kinship.of(this.family, this.kid, this.cousin));
	}

	@Test
	void inLaws() {
		assertEquals(Kinship.PARENT_IN_LAW, Kinship.of(this.family, this.dad, this.grandma));
		assertEquals(Kinship.CHILD_IN_LAW, Kinship.of(this.family, this.grandma, this.dad));
		assertEquals(Kinship.SIBLING_IN_LAW, Kinship.of(this.family, this.dad, this.uncle));
		assertEquals(Kinship.SIBLING_IN_LAW, Kinship.of(this.family, this.uncle, this.dad));
	}

	@Test
	void strangersAndSelfAreNotKin() {
		assertNull(Kinship.of(this.family, this.kid, this.stranger));
		assertNull(Kinship.of(this.family, this.kid, this.kid));
	}

	@Test
	void widowsRememberTheirSpouse() {
		this.family.died(this.dad);
		assertEquals(Kinship.LATE_SPOUSE, Kinship.of(this.family, this.mom, this.dad));
		assertTrue(Kinship.relativesOf(this.family, this.kid).contains(this.cousin));
	}

	@Test
	void familySurvivesSaving() {
		CompoundTag tag = new CompoundTag();
		this.family.save(tag);
		PeopleRegistry loaded = new PeopleRegistry(PeopleRegistry.DATA_ID);
		loaded.load(tag);
		assertEquals(Kinship.COUSIN, Kinship.of(loaded, this.kid, this.cousin));
		assertEquals(Kinship.SPOUSE, Kinship.of(loaded, this.dad, this.mom));
		assertEquals("Grandma", loaded.nameOf(this.grandma));
	}
}
