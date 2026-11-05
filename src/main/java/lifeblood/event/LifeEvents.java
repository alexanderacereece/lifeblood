package lifeblood.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The event hub: something happens (a death, a birth, a hit, a gift, a couple changing), the code where it happens
 * posts an event, and every system that cares has subscribed to it. So a new system reacts to what goes on in the world
 * by listening, without editing the code that makes it happen.
 *
 * Listeners subscribe in their system's setup class (see lifeblood.Systems), and run in the order they subscribed, on
 * the side that runs the game. An event is a record in this package, named for what happened ({@link DeathEvent}).
 */
public final class LifeEvents {
	private static final Map<Class<?>, List<Consumer<Object>>> LISTENERS = new HashMap<>();

	private LifeEvents() {
	}

	/** Calls the listener every time an event of this type is posted. */
	@SuppressWarnings("unchecked")
	public static <E> void listen(Class<E> type, Consumer<? super E> listener) {
		LISTENERS.computeIfAbsent(type, key -> new ArrayList<>()).add(event -> ((Consumer<Object>) (Consumer<?>) listener).accept(event));
	}

	/** Tells every listener of this event's type. */
	public static void post(Object event) {
		List<Consumer<Object>> listeners = LISTENERS.get(event.getClass());
		if (listeners != null) {
			for (Consumer<Object> listener : listeners) {
				listener.accept(event);
			}
		}
	}
}
