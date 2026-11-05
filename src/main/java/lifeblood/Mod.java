package lifeblood;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;

/**
 * The mod's id and its log, for any code to use. Kept apart from the entry point ({@link Lifeblood}), which wires up
 * everything, so the basic packages can log without depending on the top of the mod.
 */
public final class Mod {
	/** "lifeblood": the mod's id, registered with HalpLibe the first time it's used (at startup). */
	public static final String ID = HalpLibe.registerMod("lifeblood", true);
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	private Mod() {
	}
}
