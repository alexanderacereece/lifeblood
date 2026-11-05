/**
 * The brain: which behavior an NPC runs each tick, the registry of behaviors that can be saved, and the NPC's home.
 *
 * Start with {@link lifeblood.ai.NpcBrain}. Behaviors that belong to a system live in that system's package (combat,
 * brawl, health, family, following); ai/behavior holds the plain orders, and ai/routine the daily schedules.
 * Pathfinding is in nav.
 */
package lifeblood.ai;
