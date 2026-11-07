package lifeblood.travel;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import lifeblood.entity.EntityNPC;

/**
 * Travelers who are away from every player, saved with the world (BTA SavedData): each one's whole NPC as it was when
 * it left sight, its trip, where it's got to, and anything it's found since. TravelSimulation moves them along and
 * makes them real again where a player is; a trip never stops just because nobody's watching.
 */
public class TravelRegister extends SavedData {
	public static final String DATA_ID = "lifeblood_travelers";
	/** How many simulation passes a traveler just taken over waits before they can be made real again. */
	static final int SETTLING_PASSES = 3;

	/** One traveler on the move out of sight. */
	public static final class Traveler {
		public final UUID id;
		/** The NPC, saved whole (as a chunk saves an entity). */
		public final CompoundTag npc;
		public final Outing outing;
		/** Where they've got to on the map. */
		public double x;
		public double z;
		/** How far their trip has been carried on (world time). */
		public long simulatedTo;
		/** What they've found since leaving sight, to add to what they carry; and the health they've lost. */
		public final List<ItemStack> found = new ArrayList<>();
		public float healthLost;
		/**
		 * How many passes of the simulation to wait before they can be made real again. Not saved. A traveler just taken
		 * out of the world isn't put back in the same moment: the old one is still being removed, and would take the new
		 * one with it.
		 */
		public int settling = SETTLING_PASSES;

		Traveler(UUID id, CompoundTag npc, Outing outing, double x, double z, long simulatedTo) {
			this.id = id;
			this.npc = npc;
			this.outing = outing;
			this.x = x;
			this.z = z;
			this.simulatedTo = simulatedTo;
		}
	}

	private final List<Traveler> travelers = new ArrayList<>();

	/** Called by BTA through reflection when loading saved data. */
	public TravelRegister(String id) {
		super(id);
	}

	public static TravelRegister get(World world) {
		TravelRegister register = (TravelRegister) world.getSavedData(TravelRegister.class, DATA_ID);
		if (register == null) {
			register = new TravelRegister(DATA_ID);
			world.setSavedData(DATA_ID, register);
			register.setDirty();
		}

		return register;
	}

	public List<Traveler> all() {
		return Collections.unmodifiableList(this.travelers);
	}

	/**
	 * Takes over a traveler leaving every player's sight: the NPC is saved whole and taken out of the world, and its trip
	 * goes on from the given time (when it was last really doing it).
	 */
	public void takeOver(EntityNPC npc, long since) {
		Outing outing = npc.outing.current();
		if (outing == null || npc.removed) {
			return;
		}

		CompoundTag saved = new CompoundTag();
		npc.save(saved);
		this.travelers.add(new Traveler(npc.getUuid(), saved, outing, npc.x, npc.z, since));
		npc.remove();
		this.setDirty();
	}

	/** Adds a traveler who was never in sight: a stranger setting out from beyond the explored world. */
	public void add(Traveler traveler) {
		this.travelers.add(traveler);
		this.setDirty();
	}

	/** Makes a traveler record for an NPC that hasn't joined the world (a stranger), to start out of sight. */
	public static Traveler recordFor(EntityNPC npc, Outing outing, double x, double z, long now) {
		CompoundTag saved = new CompoundTag();
		npc.save(saved);
		return new Traveler(npc.getUuid(), saved, outing, x, z, now);
	}

	void remove(Traveler traveler) {
		this.travelers.remove(traveler);
		this.setDirty();
	}

	@Override
	public void load(CompoundTag tag) {
		this.travelers.clear();
		for (Tag<?> entry : tag.getList("Travelers")) {
			if (!(entry instanceof CompoundTag traveler)) {
				continue;
			}

			try {
				Traveler loaded = new Traveler(UUID.fromString(traveler.getString("Id")), traveler.getCompound("Npc"), Outing.load(traveler.getCompound("Outing")),
					traveler.getDouble("X"), traveler.getDouble("Z"), traveler.getLong("SimulatedTo"));
				loaded.healthLost = traveler.getFloat("HealthLost");
				for (Tag<?> item : traveler.getList("Found")) {
					if (item instanceof CompoundTag stack) {
						ItemStack found = ItemStack.readItemStackFromNbt(stack);
						if (found != null) {
							loaded.found.add(found);
						}
					}
				}

				this.travelers.add(loaded);
			} catch (IllegalArgumentException e) {
				// A damaged entry is skipped rather than losing the rest.
			}
		}
	}

	@Override
	public void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Traveler traveler : this.travelers) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Id", traveler.id.toString());
			entry.putCompound("Npc", traveler.npc);
			entry.putCompound("Outing", traveler.outing.save());
			entry.putDouble("X", traveler.x);
			entry.putDouble("Z", traveler.z);
			entry.putLong("SimulatedTo", traveler.simulatedTo);
			entry.putFloat("HealthLost", traveler.healthLost);
			ListTag found = new ListTag();
			for (ItemStack stack : traveler.found) {
				CompoundTag item = new CompoundTag();
				stack.writeToNBT(item);
				found.addTag(item);
			}

			entry.putList("Found", found);
			list.addTag(entry);
		}

		tag.putList("Travelers", list);
	}
}
