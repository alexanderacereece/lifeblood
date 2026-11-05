package lifeblood.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * The behaviors NPCs can have: orders by id (so a saved NPC gets its orders back when it loads), and instincts, which
 * every NPC is born with. Systems register theirs in their setup class (see lifeblood.Systems).
 */
public final class NpcBehaviors {
	private static final Map<String, Supplier<NpcBehavior>> FACTORIES = new LinkedHashMap<>();
	private static final List<Supplier<NpcBehavior>> INSTINCTS = new ArrayList<>();

	private NpcBehaviors() {
	}

	public static void register(String id, Supplier<NpcBehavior> factory) {
		if (FACTORIES.putIfAbsent(id, factory) != null) {
			throw new IllegalArgumentException("Duplicate NPC behavior id: " + id);
		}
	}

	/** An instinct every NPC is given when it's made (fighting, recovering...); the brain orders them by priority. */
	public static void registerInstinct(Supplier<NpcBehavior> instinct) {
		INSTINCTS.add(instinct);
	}

	/** A fresh set of every instinct, for a new NPC's brain. */
	public static List<NpcBehavior> createInstincts() {
		List<NpcBehavior> instincts = new ArrayList<>();
		for (Supplier<NpcBehavior> instinct : INSTINCTS) {
			instincts.add(instinct.get());
		}

		return instincts;
	}

	@Nullable
	public static NpcBehavior create(String id) {
		Supplier<NpcBehavior> factory = FACTORIES.get(id);
		return factory != null ? factory.get() : null;
	}

	public static Set<String> ids() {
		return FACTORIES.keySet();
	}
}
