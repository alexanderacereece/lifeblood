package lifeblood.family;

import java.util.Random;
import net.minecraft.core.entity.Mob;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/**
 * How family names pass down, the traditional way: a child takes its father's family name, and a bride usually takes
 * her husband's at the wedding, remembering the one she was born with. Names stay through widowhood and divorce.
 * Players have no family name, so a child of a player and an NPC takes the NPC's, and an NPC marrying a player keeps
 * their own. Same-sex couples keep their own names; their children take the name of the parent who has them.
 */
public final class FamilyNames {
	/** How often a bride takes her husband's family name rather than keeping her own. */
	public static final float BRIDE_TAKES_NAME = 0.8F;

	private FamilyNames() {
	}

	/** The family name a newborn gets from its NPC parent and its other parent (an NPC or a player). */
	public static String forChild(EntityNPC parent, Mob otherParent) {
		EntityNPC other = otherParent instanceof EntityNPC npc ? npc : null;
		parent.life.ensureFamilyName();
		if (other != null) {
			other.life.ensureFamilyName();
		}

		return childName(parent.life.getFamilyName(), parent.getGender(), other != null ? other.life.getFamilyName() : null,
			other != null ? other.getGender() : null);
	}

	/**
	 * Which family name a child takes: the father's when its parents are a man and a woman who both have one (NPCs);
	 * otherwise the first parent's, the NPC the child is born to. The other parent's is null for a player. Pure, for tests.
	 */
	static String childName(String parentName, NpcGender parentGender, @Nullable String otherName, @Nullable NpcGender otherGender) {
		boolean otherIsFather = otherName != null && !otherName.isEmpty() && otherGender == NpcGender.MALE && parentGender == NpcGender.FEMALE;
		return otherIsFather ? otherName : parentName;
	}

	/** At the wedding: a bride marrying a man (both NPCs) usually takes his family name. */
	public static void atWedding(EntityNPC npc, Mob other, Random random) {
		if (!(other instanceof EntityNPC partner) || npc.getGender() == partner.getGender()) {
			return;
		}

		EntityNPC bride = npc.getGender() == NpcGender.FEMALE ? npc : partner;
		EntityNPC groom = bride == npc ? partner : npc;
		String name = groom.life.getFamilyName();
		if (!name.isEmpty() && random.nextFloat() < BRIDE_TAKES_NAME) {
			bride.life.changeFamilyName(name);
		}
	}
}
