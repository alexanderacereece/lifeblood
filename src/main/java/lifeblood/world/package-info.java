/**
 * The world's own clock and calendar, and systems that run per world whether or not an NPC is near.
 *
 * Start with {@link lifeblood.world.Calendar} (days, years, seasons: every duration in the mod is measured with it)
 * and {@link lifeblood.world.WorldSystems}. Settlements, elections and wars plug in as world systems. GameSpeed runs a
 * dedicated server faster, for testing.
 */
package lifeblood.world;
