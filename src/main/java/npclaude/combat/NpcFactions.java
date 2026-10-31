package npclaude.combat;

import java.util.Locale;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.world.World;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * How an NPC regards another person, combining every layer:
 * its personal relation with that person (players only, for now) overrides everything; otherwise its faction
 * decides (see {@link FactionRegistry#regard}), with players counting as members of whatever faction they joined.
 */
public final class NpcFactions {
	private NpcFactions() {
	}

	/** Cleans up a saved faction name: empty means unaffiliated; "default" (the very first saves) became the settlers. */
	@Nullable
	public static String normalize(String faction) {
		String name = faction.toLowerCase(Locale.ROOT);
		if (name.isEmpty()) {
			return null;
		}

		return name.equals("default") ? FactionRegistry.SETTLERS : name;
	}

	/** The faction someone belongs to: an NPC's own, or the one a player joined. Null means unaffiliated. */
	@Nullable
	public static String affiliation(World world, Entity entity) {
		if (entity instanceof EntityNPC npc) {
			return npc.getFaction();
		}

		if (entity instanceof Player player && player.uuid != null) {
			return FactionRegistry.get(world).getPlayerFaction(player.uuid);
		}

		return null;
	}

	/** How this NPC regards another NPC or a player. */
	public static FactionRelation regard(EntityNPC npc, Entity other) {
		if (other instanceof Player player) {
			FactionRelation personal = npc.relations.get(player);
			if (personal != null) {
				return personal;
			}
		}

		return FactionRegistry.get(npc.world).regard(npc.getFaction(), affiliation(npc.world, other));
	}
}
