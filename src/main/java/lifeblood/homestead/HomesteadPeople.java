package lifeblood.homestead;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.world.World;
import lifeblood.appearance.Appearance;
import lifeblood.appearance.PartCatalog;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.family.FamilyNames;
import lifeblood.family.Romance;
import lifeblood.homestead.HouseholdPlan.Member;
import lifeblood.homestead.HouseholdPlan.Role;
import lifeblood.people.NamePool;
import lifeblood.people.PeopleRegistry;
import lifeblood.social.MemoryType;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * Brings a planned household to life: makes each person where they sleep, with their age, looks and family name, and
 * writes their history into the register of people as history, not news: marriages from years ago, children's
 * parents, a late husband or wife, siblings' parents back home. Friends who came out together start out close.
 */
public final class HomesteadPeople {
	private HomesteadPeople() {
	}

	/**
	 * Makes the household's people, each standing at their bed (bedSpots: {x, y, z} per member, in the plan's order),
	 * all calling the house's middle home. Returns them in the plan's order, without any that couldn't be made.
	 */
	public static List<EntityNPC> settle(World world, HouseholdPlan plan, List<double[]> bedSpots, double[] home, Random random) {
		List<EntityNPC> people = new ArrayList<>();
		EntityNPC[] byIndex = new EntityNPC[plan.members().size()];
		String headFamily = NamePool.pickFamilyName(random);
		for (int i = 0; i < plan.members().size(); i++) {
			Member member = plan.members().get(i);
			EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
			if (npc == null) {
				continue;
			}

			double[] spot = bedSpots.get(i);
			NpcGender gender = member.female() ? NpcGender.FEMALE : NpcGender.MALE;
			npc.moveTo(spot[0], spot[1], spot[2], random.nextFloat() * 360.0F, 0.0F);
			npc.setNickname(NamePool.pick(gender, random));
			npc.setGender(gender);
			// Family and siblings share the head's family name; friends have their own.
			boolean family = member.role() != Role.FRIEND;
			npc.life.setBirthFamilyName(family ? headFamily : NamePool.pickFamilyName(random));
			npc.life.setAge(member.age(), member.intoYear());
			npc.life.setCameFor(member.cameFor());
			npc.home.set(home[0], home[1], home[2]);
			byIndex[i] = npc;
		}

		marry(world, plan, byIndex, random);
		raiseChildren(plan, byIndex, random);
		for (EntityNPC npc : byIndex) {
			if (npc != null) {
				npc.looks.rollIfNeeded();
				npc.spawnInit();
				world.entityJoinedWorld(npc);
				Romance.register(npc);
				people.add(npc);
			}
		}

		recordFamily(world, plan, byIndex, random);
		befriend(byIndex, plan);
		return people;
	}

	/** Couples were married years ago; a bride usually took her husband's family name. Partners don't share a birth name. */
	private static void marry(World world, HouseholdPlan plan, EntityNPC[] byIndex, Random random) {
		for (int i = 0; i < byIndex.length; i++) {
			int partner = plan.members().get(i).partnerOf();
			if (partner > i && byIndex[i] != null && byIndex[partner] != null) {
				byIndex[partner].life.setBirthFamilyName(NamePool.pickFamilyName(random));
				FamilyNames.atWedding(byIndex[i], byIndex[partner], random);
			}
		}

		// A widow was born with another name and took her late husband's, which is the family's.
		EntityNPC head = byIndex[0];
		if (plan.lateSpouse() != null && head != null && head.getGender() == NpcGender.FEMALE && random.nextFloat() < FamilyNames.BRIDE_TAKES_NAME) {
			String family = head.life.getFamilyName();
			head.life.setBirthFamilyName(NamePool.pickFamilyName(random));
			head.life.changeFamilyName(family);
		}
	}

	/** Children take after the head and their husband or wife, in personality and looks, and carry the family name. */
	private static void raiseChildren(HouseholdPlan plan, EntityNPC[] byIndex, Random random) {
		EntityNPC head = byIndex[0];
		EntityNPC spouse = spouseOf(plan, byIndex);
		if (head == null) {
			return;
		}

		String childName = childFamilyName(plan, head, spouse);
		for (int i = 0; i < byIndex.length; i++) {
			EntityNPC child = byIndex[i];
			if (child == null || plan.members().get(i).role() != Role.CHILD) {
				continue;
			}

			child.life.setBirthFamilyName(childName);
			child.personality.inherit(head.personality, spouse != null ? spouse.personality : null, random);
			head.looks.rollIfNeeded();
			if (spouse != null) {
				spouse.looks.rollIfNeeded();
			}

			child.looks.setAppearance(Appearance.inherit(PartCatalog.get(), child.getGender(), Appearance.decode(head.looks.getAppearance()),
				spouse != null ? Appearance.decode(spouse.looks.getAppearance()) : Appearance.NONE, random));
		}
	}

	/** The children's family name: their father's, which a widowed mother usually carries too. */
	private static String childFamilyName(HouseholdPlan plan, EntityNPC head, @Nullable EntityNPC spouse) {
		if (spouse != null && spouse.getGender() == NpcGender.MALE) {
			return spouse.life.getFamilyName();
		}

		return head.life.getFamilyName();
	}

	@Nullable
	private static EntityNPC spouseOf(HouseholdPlan plan, EntityNPC[] byIndex) {
		int partner = plan.members().get(0).partnerOf();
		return partner >= 0 ? byIndex[partner] : null;
	}

	/** The register learns the household's history: marriages, parents, a late husband or wife, parents back home. */
	private static void recordFamily(World world, HouseholdPlan plan, EntityNPC[] byIndex, Random random) {
		PeopleRegistry register = PeopleRegistry.get(world);
		long now = world.getWorldTime();
		long year = Calendar.ticksPerYear(world);
		int oldestChild = 0;
		for (Member member : plan.members()) {
			if (member.role() == Role.CHILD) {
				oldestChild = Math.max(oldestChild, member.age());
			}
		}

		for (int i = 0; i < byIndex.length; i++) {
			int partner = plan.members().get(i).partnerOf();
			if (partner > i && byIndex[i] != null && byIndex[partner] != null) {
				// Married before their eldest was born, and at least a year ago.
				long married = now - (oldestChild + 1 + random.nextInt(4)) * year;
				register.recordPartnership(byIndex[i].getUuid(), byIndex[partner].getUuid(), PeopleRegistry.Stage.MARRIED, married);
			}
		}

		List<UUID> parents = new ArrayList<>();
		EntityNPC head = byIndex[0];
		EntityNPC spouse = spouseOf(plan, byIndex);
		if (plan.kind() == HouseholdPlan.Kind.FAMILY || plan.kind() == HouseholdPlan.Kind.WIDOWED) {
			if (head != null) {
				parents.add(head.getUuid());
			}

			if (spouse != null) {
				parents.add(spouse.getUuid());
			}
		}

		if (plan.lateSpouse() != null && head != null) {
			parents.add(lateSpouse(register, plan.lateSpouse(), head, now, year, random));
		}

		if (plan.parentsBackHome() && head != null) {
			parents.add(parentBackHome(register, NpcGender.MALE, head.life.getFamilyName(), plan.members().get(0).age(), now, year, random));
			parents.add(parentBackHome(register, NpcGender.FEMALE, NamePool.pickFamilyName(random), plan.members().get(0).age(), now, year, random));
		}

		for (int i = 0; i < byIndex.length; i++) {
			Role role = plan.members().get(i).role();
			boolean child = role == Role.CHILD;
			boolean sibling = plan.parentsBackHome() && (role == Role.HEAD || role == Role.SIBLING);
			if (byIndex[i] != null && (child || sibling)) {
				for (UUID parent : parents) {
					if (!parent.equals(byIndex[i].getUuid())) {
						register.addParent(byIndex[i].getUuid(), parent);
					}
				}
			}
		}

		register.setDirty();
	}

	/** A husband or wife who died before the player came: in the register as dead, remembered as the head's late spouse. */
	private static UUID lateSpouse(PeopleRegistry register, HouseholdPlan.LateSpouse late, EntityNPC head, long now, long year, Random random) {
		NpcGender gender = late.female() ? NpcGender.FEMALE : NpcGender.MALE;
		PeopleRegistry.Person person = register.person(UUID.randomUUID(), NamePool.pick(gender, random), gender, false);
		// A late husband carries the family name his widow took; a late wife had taken her husband's.
		person.familyName = head.life.getFamilyName();
		person.birthTime = now - (late.ageAtDeath() + late.yearsAgo()) * year - (long) (random.nextFloat() * year);
		person.alive = false;
		PeopleRegistry.Person widowed = register.find(head.getUuid());
		if (widowed != null) {
			widowed.lateSpouse = person.id;
		}

		return person.id;
	}

	/** A parent still living back where the siblings came from, never seen in this world. */
	private static UUID parentBackHome(PeopleRegistry register, NpcGender gender, String familyName, int eldestAge, long now, long year, Random random) {
		PeopleRegistry.Person person = register.person(UUID.randomUUID(), NamePool.pick(gender, random), gender, false);
		person.familyName = familyName;
		int age = eldestAge + HouseholdPlanner.MIN_PARENT_AGE + 2 + random.nextInt(14);
		person.birthTime = now - age * year - (long) (random.nextFloat() * year);
		return person.id;
	}

	/** Friends who came out here together start out close, and so do the friends sharing a family's house. */
	private static void befriend(EntityNPC[] byIndex, HouseholdPlan plan) {
		for (int i = 0; i < byIndex.length; i++) {
			for (int j = 0; j < byIndex.length; j++) {
				boolean friends = plan.members().get(i).role() == Role.FRIEND || plan.members().get(j).role() == Role.FRIEND
					|| plan.kind() == HouseholdPlan.Kind.FRIENDS;
				if (i != j && friends && byIndex[i] != null && byIndex[j] != null && plan.members().get(i).partnerOf() != j) {
					byIndex[i].remember(MemoryType.SETTLED_HERE_TOGETHER, byIndex[j], 1.0F);
				}
			}
		}
	}
}
