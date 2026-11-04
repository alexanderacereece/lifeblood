package lifeblood.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lifeblood.ai.behavior.FollowPlayerBehavior;
import lifeblood.ai.behavior.GoToBehavior;
import lifeblood.ai.behavior.StayBehavior;
import lifeblood.ai.behavior.WanderBehavior;
import org.jetbrains.annotations.Nullable;

/** Registry of behavior ids, used to recreate behaviors when an NPC is loaded. */
public final class NpcBehaviors {
	private static final Map<String, Supplier<NpcBehavior>> FACTORIES = new LinkedHashMap<>();

	static {
		register(FollowPlayerBehavior.ID, FollowPlayerBehavior::new);
		register(GoToBehavior.ID, GoToBehavior::new);
		register(StayBehavior.ID, StayBehavior::new);
		register(WanderBehavior.ID, WanderBehavior::new);
	}

	private NpcBehaviors() {
	}

	public static void register(String id, Supplier<NpcBehavior> factory) {
		if (FACTORIES.putIfAbsent(id, factory) != null) {
			throw new IllegalArgumentException("Duplicate NPC behavior id: " + id);
		}
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
