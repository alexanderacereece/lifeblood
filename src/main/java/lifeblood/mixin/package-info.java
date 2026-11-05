/**
 * Changes to BTA's own classes, each named after its target and listed in lifeblood.mixins.json.
 *
 * Each says why it exists. Keep them thin: hook in, then call the mod's own code, so the logic lives in its system's
 * package. Only classes listed under "client" in lifeblood.mixins.json may touch client code.
 */
package lifeblood.mixin;
