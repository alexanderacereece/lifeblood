package lifeblood.event;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.World;
import lifeblood.entity.EntityNPC;

/**
 * A household that went unwatched for a while has just been loaded again and is catching up on the time (see
 * homestead/life/HouseholdCatchUp). Systems add what their people would have been doing meanwhile: trips taken, what
 * was found, who didn't come back.
 *
 * @param world  the world
 * @param people the household's people, loaded again
 * @param ticks  how long they went unwatched, in ticks (already capped)
 * @param store  puts something they brought home into the household's chests (dropping it at home if they're full)
 */
public record TimeAwayEvent(World world, List<EntityNPC> people, long ticks, Consumer<ItemStack> store) {
}
