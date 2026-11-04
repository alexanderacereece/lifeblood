package lifeblood.mixin;

import net.minecraft.core.world.pathfinder.Node;
import net.minecraft.core.world.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes a path's steps, so the navigator can check them for hazards before following it. */
@Mixin(value = Path.class, remap = false)
public interface PathAccessor {
	@Accessor("nodes")
	Node[] lifeblood$getNodes();
}
