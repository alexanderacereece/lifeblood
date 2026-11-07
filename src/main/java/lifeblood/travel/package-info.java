/**
 * Trips away from home (see docs/travelers.md): journeys, adventures in caves, visits to other homesteads, and strangers
 * passing through. Start with NpcOuting (an NPC's current trip, and deciding to go), OutingBehavior (carrying a trip out:
 * packing, walking far, sheltering at night, coming home) and OutingKinds (the registered kinds, in travel/kinds). While a
 * traveler is away from every player, TravelRegister keeps their trip going as a record (TravelSimulation), and turns
 * them back into a person where a player is. Strangers makes the people who pass through, and TripsWhileAway works out
 * the trips a household took while its house was unloaded.
 *
 * Not here: the player-rule actions trips are made of (work), or homesteaders' everyday chores (homestead).
 */
package lifeblood.travel;
