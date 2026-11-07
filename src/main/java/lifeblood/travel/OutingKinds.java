package lifeblood.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lifeblood.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/** The kinds of trip people take, registered from setups, and picking one for someone by how much they'd want each. */
public final class OutingKinds {
	private static final List<OutingKind> KINDS = new ArrayList<>();

	private OutingKinds() {
	}

	/** Adds a kind. Call from a setup class. */
	public static void register(OutingKind kind) {
		if (byId(kind.id()) != null) {
			throw new IllegalArgumentException("Duplicate outing kind: " + kind.id());
		}

		KINDS.add(kind);
	}

	@Nullable
	public static OutingKind byId(String id) {
		for (OutingKind kind : KINDS) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}

		return null;
	}

	/** A kind of trip for this person, weighed by what they'd want; null if they'd want none. */
	@Nullable
	public static OutingKind pick(EntityNPC npc, Random random) {
		float total = 0.0F;
		for (OutingKind kind : KINDS) {
			total += Math.max(0.0F, kind.weight(npc));
		}

		if (total <= 0.0F) {
			return null;
		}

		float roll = random.nextFloat() * total;
		for (OutingKind kind : KINDS) {
			roll -= Math.max(0.0F, kind.weight(npc));
			if (roll <= 0.0F) {
				return kind;
			}
		}

		return KINDS.get(KINDS.size() - 1);
	}
}
