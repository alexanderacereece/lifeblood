package npclaude.family;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.lang.I18n;
import npclaude.NPClaude;
import npclaude.appearance.Appearance;
import npclaude.appearance.PartCatalog;
import npclaude.entity.EntityNPC;
import npclaude.entity.NpcGender;
import npclaude.people.PeopleRegistry;
import npclaude.social.Memory;
import npclaude.social.MemoryType;
import org.jetbrains.annotations.Nullable;

/**
 * Married couples have children, so settlements carry on across generations. A child inherits a blend of its
 * parents' personalities and looks, takes their faction and home, grows up over 20 in-game days, and is kept safe
 * (invincible, never fights) until then.
 *
 * A married couple at 80+ opinion, together and nearby, has about one child every ten in-game days, up to four,
 * and not until the youngest is at least five days old.
 */
public final class Children {
	public static final int MAX_CHILDREN = 4;
	private static final float MIN_DAYS_BETWEEN = 5.0F;
	/** Per romance check (once a minute): about one child every ten in-game days. */
	public static final float BIRTH_CHANCE = 0.005F;

	private static Map<NpcGender, List<String>> names;

	private Children() {
	}

	/** Whether this couple could have another child now (not counting chance). */
	public static boolean canHaveChild(PeopleRegistry family, UUID first, UUID second, long now) {
		List<UUID> children = family.childrenOf(first);
		children.retainAll(family.childrenOf(second));
		if (children.size() >= MAX_CHILDREN) {
			return false;
		}

		for (UUID child : children) {
			PeopleRegistry.Person person = family.find(child);
			if (person != null && person.born >= 0 && now - person.born < MIN_DAYS_BETWEEN * Memory.TICKS_PER_DAY) {
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
		child.setNickname(pickName(gender, random));
		child.setGender(gender);
		child.personality.inherit(parent.personality, npcParent != null ? npcParent.personality : null, random);
		child.setAppearance(Appearance.inherit(PartCatalog.get(), gender, Appearance.decode(parent.getAppearance()),
			npcParent != null ? Appearance.decode(npcParent.getAppearance()) : Appearance.NONE, random));
		child.setFaction(parent.getFaction());
		child.setHome(parent.getHomeX(), parent.getHomeY(), parent.getHomeZ());
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
		parent.voice.bark("new_baby", otherParent, context -> context.word("baby", npclaude.speech.Word.name(child.nickname))
			.fact("baby_gender", gender.name().toLowerCase(java.util.Locale.ROOT)), true);
		return child;
	}

	/** Grown up: it says so, and a young man may grow a beard. */
	public static void cameOfAge(EntityNPC npc) {
		npc.setAppearance(Appearance.decode(npc.getAppearance()).cameOfAge(PartCatalog.get(), npc.getGender(), npc.getRandom()));
		npc.voice.bark("grown_up", null, null, true);
	}

	private static String pickName(NpcGender gender, Random random) {
		if (names == null) {
			names = loadNames();
		}

		List<String> options = names.getOrDefault(gender, List.of());
		return options.isEmpty() ? (gender == NpcGender.MALE ? "Tom" : "Ann") : options.get(random.nextInt(options.size()));
	}

	/** assets/npclaude/names.txt: [male] and [female] sections, one name per line. */
	private static Map<NpcGender, List<String>> loadNames() {
		Map<NpcGender, List<String>> result = new EnumMap<>(NpcGender.class);
		try (InputStream stream = I18n.getResourceAsStream("/assets/npclaude/names.txt")) {
			if (stream == null) {
				return result;
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			NpcGender section = null;
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				if (line.startsWith("[") && line.endsWith("]")) {
					section = NpcGender.byName(line.substring(1, line.length() - 1));
				} else if (section != null) {
					result.computeIfAbsent(section, g -> new ArrayList<>()).add(line);
				}
			}
		} catch (IOException e) {
			NPClaude.LOGGER.warn("Could not read the name list", e);
		}

		return result;
	}
}
