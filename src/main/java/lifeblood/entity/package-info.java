/**
 * The NPC entity itself, the part system it's built from, and what any system may ask about an NPC.
 *
 * Start with {@link lifeblood.entity.EntityNPC}, which only does what every mob must, and {@link
 * lifeblood.entity.NpcPart}. Each part lives in its own system's package (health, family, social, following...), not
 * here: a new per-NPC system is a new part in its own package. {@link lifeblood.entity.NpcState} answers questions
 * lower layers may not ask the parts directly; {@link lifeblood.entity.Persons} says what counts as a person.
 */
package lifeblood.entity;
