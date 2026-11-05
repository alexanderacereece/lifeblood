/**
 * The /lb command tree: tools for admins, content and testing, never the way to play.
 *
 * Start with {@link lifeblood.command.LifebloodCommand}. One class per area with an addTo(root) method, every node
 * made with {@link lifeblood.command.CommandTree#branch}, and targets taken as {@link
 * lifeblood.command.TargetArgument}s. Shared helpers are in {@link lifeblood.command.CommandSupport}. Game logic never
 * lives here, and nothing outside this package depends on it.
 */
package lifeblood.command;
