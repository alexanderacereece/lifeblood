package npclaude.save;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.world.World;
import npclaude.people.PeopleRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Every bit of code that reads saves from older versions of the mod, in one place. Each method rewrites old data
 * into the current format before the normal loading code sees it, so nothing else needs to know about old saves.
 *
 * All of this is temporary: it can be deleted wholesale (with the calls to it) once the mod reaches a release
 * where old development worlds no longer matter.
 */
public final class SaveUpgrades {
	private SaveUpgrades() {
	}

	/** An NPC's own save data (the "NPClaude" compound). */
	public static void npc(CompoundTag npc) {
		// Version 1 stored bravery (0 to 1) directly on the NPC.
		if (npc.containsKey("Bravery") && !npc.containsKey("Personality")) {
			CompoundTag personality = new CompoundTag();
			personality.putFloat("Bravery", npc.getFloat("Bravery"));
			npc.putCompound("Personality", personality);
		}

		// Before health modes there was only an invulnerable flag.
		if (!npc.containsKey("HealthMode") && npc.getBoolean("Invulnerable")) {
			npc.putString("HealthMode", "INVULNERABLE");
		}

		// The first faction was called "default".
		if ("default".equalsIgnoreCase(npc.getStringOrDefault("Faction", ""))) {
			npc.putString("Faction", "settlers");
		}

		if (npc.containsKey("Personality")) {
			personality(npc.getCompound("Personality"));
		}
	}

	/** The register of people was the family register, saved under another name; its contents carry straight over. */
	@Nullable
	public static PeopleRegistry oldPeopleRegistry(World world) {
		PeopleRegistry old = (PeopleRegistry) world.getSavedData(PeopleRegistry.class, "npclaude_family");
		if (old == null) {
			return null;
		}

		CompoundTag tag = new CompoundTag();
		old.save(tag);
		PeopleRegistry registry = new PeopleRegistry(PeopleRegistry.DATA_ID);
		registry.load(tag);
		return registry;
	}

	/** A saved personality. Anything missing after this keeps the values the NPC was just rolled with. */
	public static void personality(CompoundTag tag) {
		// Before trait axes there was only bravery, which became courage.
		if (!tag.containsKey("Axes") && tag.containsKey("Bravery")) {
			CompoundTag axes = new CompoundTag();
			axes.putFloat("courage", tag.getFloat("Bravery") * 2.0F - 1.0F);
			tag.putCompound("Axes", axes);
		}

		// Orientation used to be stored on its own; it's a quirk now.
		String attraction = tag.getStringOrDefault("Attraction", "");
		String orientation = attraction.equalsIgnoreCase("same") ? "gay" : attraction.equalsIgnoreCase("both") ? "bisexual" : null;
		if (orientation != null) {
			String quirks = tag.getStringOrDefault("Quirks", "");
			tag.putString("Quirks", quirks.isEmpty() ? orientation : quirks + "," + orientation);
		}

		// Tastes were food preferences only.
		if (!tag.containsKey("Tastes") && tag.containsKey("Food")) {
			tag.putCompound("Tastes", tag.getCompound("Food"));
		}
	}
}
