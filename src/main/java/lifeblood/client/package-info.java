/**
 * Everything that only exists on the client: rendering, textures and screens.
 *
 * Start with {@link lifeblood.client.LifebloodClient}. Nothing here holds game logic: screens show state and call into
 * common code, so the mod stays multiplayer-ready. Common code never imports this package; it goes through {@link
 * lifeblood.ClientHooks}.
 */
package lifeblood.client;
