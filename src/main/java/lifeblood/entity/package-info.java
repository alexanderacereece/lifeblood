/**
 * The NPC entity itself and the part system it's built from.
 *
 * Start with {@link lifeblood.entity.EntityNPC}, which only does what every mob must, and {@link
 * lifeblood.entity.NpcPart}. Each part lives in its own system's package (health, family, social, following...), not
 * here: a new per-NPC system is a new part in its own package.
 */
package lifeblood.entity;
