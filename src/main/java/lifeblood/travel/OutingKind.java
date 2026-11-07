package lifeblood.travel;

import java.util.List;
import java.util.Random;
import net.minecraft.core.item.ItemStack;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * One kind of trip: who'd go on it, where, what they pack, what they do once there (as a behavior while they're
 * loaded, or as an outcome while nobody's near), and how it's remembered. Registered with OutingKinds from a setup.
 */
public interface OutingKind {
	/** Its id, saved with the trip and used as the "doing" fact while on it ("journey", "adventure", "visit"). */
	String id();

	/** How much this person wants to go on it now, relative to other kinds; 0 for not at all. */
	float weight(EntityNPC npc);

	/** Plans a trip for this person from home, or null if there's nowhere to go. */
	@Nullable
	Outing plan(EntityNPC npc, Random random);

	/** What to take from home: true for things worth packing for this trip (food is always packed). */
	boolean packs(ItemStack stack);

	/** Whether they take shelter for the night while there (an adventure underground carries on in the dark). */
	default boolean sheltersAtNight() {
		return true;
	}

	/** What they do once there, while loaded: done when it says so, or when the stay is up. */
	NpcBehavior atDestination(Outing outing);

	/**
	 * What happens there while nobody's near, all at once: what they found (added to what they carry), whether they got
	 * hurt, and whether they died. Most trips are safe and find little.
	 */
	Outcome simulate(Outing outing, EntityNPC npc, Random random);

	/** What a simulated stay came to. */
	record Outcome(List<ItemStack> found, float healthLost, boolean died) {
		public static final Outcome NOTHING = new Outcome(List.of(), 0.0F, false);
	}
}
