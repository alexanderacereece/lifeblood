package lifeblood.family;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.world.World;
import lifeblood.appearance.Appearance;
import lifeblood.appearance.PartCatalog;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.event.BirthEvent;
import lifeblood.event.LifeEvents;
import lifeblood.people.NamePool;
import lifeblood.people.PeopleRegistry;
import lifeblood.social.MemoryType;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * Married couples have children, so settlements carry on across generations. A child inherits a blend of its
 * parents' personalities and looks, takes their faction and home, grows up over 18 in-game years (yearsToGrowUp), and is kept safe
 * (invincible, never fights) until then.
 *
 * A married couple at 80+ opinion, together and nearby, has about one child every two in-game years, up to four,
 * and not until the youngest is a year and a half old. Times are in in-game years (see Calendar), each standing for
 * a real year.
 */
public final class Children {
	public static final int MAX_CHILDREN = 4;
	/** The youngest must be at least this old before another child, in in-game years. */
	public static final float MIN_YEARS_BETWEEN = 1.5F;
	/** Once another child is possible, how long one takes to come on average, in in-game years. */
	public static final float AVERAGE_YEARS_TO_CHILD = 2.0F;

	private Children() {
	}

	/** The chance, per romance check, that a couple who can have a child has one: about one every two years. */
	public static float birthChance(World world) {
		return Romance.CHECK_INTERVAL / (AVERAGE_YEARS_TO_CHILD * Calendar.ticksPerYear(world));
	}

	/** Whether this couple could have another child now (not counting chance). */
	public static boolean canHaveChild(World world, PeopleRegistry family, UUID first, UUID second, long now) {
		List<UUID> children = family.childrenOf(first);
		children.retainAll(family.childrenOf(second));
		if (children.size() >= MAX_CHILDREN) {
			return false;
		}

		for (UUID child : children) {
			PeopleRegistry.Person person = family.find(child);
			if (person != null && person.born >= 0 && now - person.born < MIN_YEARS_BETWEEN * Calendar.ticksPerYear(world)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * A child is born to an NPC and their partner (an NPC or a player), next to the NPC parent. Returns the child,
	 * or null if it couldn't be created.
	 */
	@Nullable
	public static EntityNPC born(EntityNPC parent, Mob otherParent) {
		EntityNPC child = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, parent.world);
		if (child == null) {
			return null;
		}

		Random random = parent.getRandom();
		NpcGender gender = random.nextBoolean() ? NpcGender.FEMALE : NpcGender.MALE;
		EntityNPC npcParent = otherParent instanceof EntityNPC other ? other : null;
		child.moveTo(parent.x, parent.y, parent.z, random.nextFloat() * 360.0F, 0.0F);
		child.setNickname(NamePool.pick(gender, random));
		child.setGender(gender);
		child.personality.inherit(parent.personality, npcParent != null ? npcParent.personality : null, random);
		child.looks.setAppearance(Appearance.inherit(PartCatalog.get(), gender, Appearance.decode(parent.looks.getAppearance()),
			npcParent != null ? Appearance.decode(npcParent.looks.getAppearance()) : Appearance.NONE, random));
		child.setFaction(parent.getFaction());
		child.home.set(parent.home.getX(), parent.home.getY(), parent.home.getZ());
		child.life.setGrowth(0.0F);
		child.spawnInit();
		parent.world.entityJoinedWorld(child);

		PeopleRegistry family = PeopleRegistry.get(parent.world);
		Romance.register(parent);
		PeopleRegistry.Person person = Romance.register(child);
		person.born = parent.world.getWorldTime();
		family.addParent(child.getUuid(), parent.getUuid());
		if (npcParent != null) {
			Romance.register(npcParent);
			family.addParent(child.getUuid(), npcParent.getUuid());
			npcParent.remember(MemoryType.HAD_A_CHILD, null, 1.0F);
		} else if (otherParent instanceof Player player) {
			Romance.register(player);
			family.addParent(child.getUuid(), player.uuid);
		}

		family.setDirty();
		parent.remember(MemoryType.HAD_A_CHILD, null, 1.0F);
		LifeEvents.post(new BirthEvent(child, parent, otherParent));
		parent.voice.bark("new_baby", otherParent, context -> context.word("baby", lifeblood.speech.Word.name(child.nickname))
			.fact("baby_gender", gender.name().toLowerCase(java.util.Locale.ROOT)), true);
		return child;
	}

	/** Grown up: it says so, and a young man may grow a beard. */
	public static void cameOfAge(EntityNPC npc) {
		npc.looks.setAppearance(Appearance.decode(npc.looks.getAppearance()).cameOfAge(PartCatalog.get(), npc.getGender(), npc.getRandom()));
		npc.voice.bark("grown_up", null, null, true);
	}
}
