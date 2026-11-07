/**
 * The event hub: what happens in the world (deaths, births, real attacks, gifts, couples changing, brawls ending, a
 * household catching up on time away), posted where it happens and heard by every system that subscribed.
 *
 * Start with {@link lifeblood.event.LifeEvents}. A new kind of event is a record here; systems subscribe in their
 * setup class. Use an event when other systems should react to something; call directly when one system asks another
 * for something.
 */
package lifeblood.event;
