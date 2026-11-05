/**
 * The mod itself: its entry point ({@link lifeblood.Lifeblood}), its settings ({@link lifeblood.NpcConfig}), and
 * {@link lifeblood.ClientHooks}, the only way common code asks the client for anything.
 *
 * Everything else lives in a package per system. docs/architecture.md says how they fit together, and docs/code-map.md
 * lists every class.
 */
package lifeblood;
