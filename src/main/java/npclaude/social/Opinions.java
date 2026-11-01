package npclaude.social;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import npclaude.combat.FactionRegistry;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.entity.EntityNPC;
import npclaude.family.FamilyRegistry;
import npclaude.family.Kinship;
import npclaude.personality.Quirk;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC thinks of someone, from -100 to +100: its memories of them plus standing factors
 * (faction ties, family, and personality compatibility). One-directional. See docs/personality.md, section 4.
 */
public final class Opinions {
	/** At or above this, the NPC treats the person as an ally whatever the factions say. */
	public static final float ALLY_THRESHOLD = 60.0F;
	/** The start of the "friend" label: trusted with practical matters. */
	public static final float FRIEND_THRESHOLD = 50.0F;
	public static final float HOSTILE_THRESHOLD = -60.0F;
	public static final float HOTHEAD_HOSTILE_THRESHOLD = -45.0F;
	public static final float ZEN_HOSTILE_THRESHOLD = -80.0F;

	private static final float SAME_FACTION = 10.0F;
	private static final float ALLIED_FACTION = 5.0F;
	private static final float HOSTILE_FACTION = -15.0F;

	private Opinions() {
	}

	/** An opinion and what it's made of, each part labeled. */
	public record Opinion(float value, Map<String, Float> parts) {
		public Label label() {
			return Label.of(this.value);
		}
	}

	public enum Label {
		ENEMY,
		DISLIKED,
		NEUTRAL,
		FRIENDLY,
		FRIEND,
		CLOSE_FRIEND;

		public static Label of(float opinion) {
			if (opinion <= -60.0F) {
				return ENEMY;
			} else if (opinion <= -20.0F) {
				return DISLIKED;
			} else if (opinion < 20.0F) {
				return NEUTRAL;
			} else if (opinion < FRIEND_THRESHOLD) {
				return FRIENDLY;
			} else if (opinion < 80.0F) {
				return FRIEND;
			}

			return CLOSE_FRIEND;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public String displayName() {
			return this.id().replace('_', ' ');
		}
	}

	public static float of(EntityNPC npc, Entity other) {
		return explain(npc, other).value();
	}

	public static Opinion explain(EntityNPC npc, Entity other) {
		Map<String, Float> parts = new LinkedHashMap<>();
		UUID id = idOf(other);
		long now = npc.world.getWorldTime();
		if (id != null) {
			for (Map.Entry<MemoryType, Float> part : npc.memories.opinionParts(id, now).entrySet()) {
				parts.put(part.getKey().description, part.getValue());
			}
		}

		String mine = npc.getFaction();
		String theirs = NpcFactions.affiliation(npc.world, other);
		if (mine != null) {
			if (mine.equals(theirs)) {
				parts.put("same faction", SAME_FACTION);
			} else {
				FactionRelation relation = FactionRegistry.get(npc.world).regard(mine, theirs);
				if (relation == FactionRelation.ALLY) {
					parts.put("allied faction", ALLIED_FACTION);
				} else if (relation == FactionRelation.HOSTILE) {
					parts.put("hostile faction", HOSTILE_FACTION);
				}
			}
		}

		if (id != null) {
			FamilyRegistry family = FamilyRegistry.get(npc.world);
			Kinship kin = Kinship.of(family, npc.getUuid(), id);
			if (kin != null && kin.opinion != 0) {
				FamilyRegistry.Person person = family.find(id);
				parts.put("family (" + kin.label(person != null ? person.gender : null) + ")", (float) kin.opinion);
			}
		}

		float compatibility = Compatibility.of(npc.personality, npc.memories, other instanceof EntityNPC otherNpc ? otherNpc.personality : null, id);
		if (compatibility != 0.0F) {
			parts.put("personality", compatibility);
		}

		float total = 0.0F;
		for (float part : parts.values()) {
			total += part;
		}

		return new Opinion(Math.max(-100.0F, Math.min(100.0F, total)), parts);
	}

	/** How low this NPC's opinion must fall before it turns on someone: sooner for hotheads, later for the zen. */
	public static float hostileThreshold(EntityNPC npc) {
		if (npc.personality.has(Quirk.HOTHEAD)) {
			return HOTHEAD_HOSTILE_THRESHOLD;
		}

		return npc.personality.has(Quirk.ZEN) ? ZEN_HOSTILE_THRESHOLD : HOSTILE_THRESHOLD;
	}

	/** A person's lasting identity: a player's account id or an NPC's own id. Null for anything else. */
	@Nullable
	public static UUID idOf(Entity entity) {
		if (entity instanceof EntityNPC npc) {
			return npc.getUuid();
		}

		return entity instanceof Player player ? player.uuid : null;
	}

	public static String nameOf(Entity entity) {
		if (entity instanceof Player player) {
			return player.username;
		}

		return entity instanceof EntityNPC npc ? npc.nickname : entity.getClass().getSimpleName();
	}
}
