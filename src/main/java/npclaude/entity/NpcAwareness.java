package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pos.TilePos;
import npclaude.personality.Surroundings;
import npclaude.social.MemoryType;
import org.jetbrains.annotations.Nullable;

/**
 * Noticing the world around it: remarking when the weather turns, the season changes or it walks somewhere new, and
 * its mood following the weather and the place it's in.
 */
public final class NpcAwareness implements NpcPart {
	/** How often the NPC notices a change of weather, biome or season (2 seconds). */
	private static final int NOTICE_INTERVAL = 40;
	/** How often the weather and the place it's in affect its mood (30 seconds). */
	private static final int MOOD_INTERVAL = 600;
	/** At least this long between remarks about a new biome, so walking along a border doesn't set it off (2 minutes). */
	private static final int BIOME_REMARK_COOLDOWN = 2400;

	private final EntityNPC npc;
	/** The surroundings last noticed, so changes can be remarked on. Not saved, so nothing fires on load. */
	@Nullable
	private String lastWeather;
	@Nullable
	private String lastBiome;
	@Nullable
	private String lastSeason;
	/** A remark about new surroundings waiting to be made (after a short, random pause), and when. */
	@Nullable
	private String pendingRemark;
	private int pendingRemarkTick;
	private int lastBiomeRemarkTick = -BIOME_REMARK_COOLDOWN;

	NpcAwareness(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		int offset = this.npc.tickCount + this.npc.id;
		if (offset % NOTICE_INTERVAL == 0) {
			this.notice();
		}

		if (offset % MOOD_INTERVAL == 0) {
			this.feel();
		}

		if (this.pendingRemark != null && this.npc.tickCount >= this.pendingRemarkTick) {
			String remark = this.pendingRemark;
			this.pendingRemark = null;
			if (!this.npc.isBusy()) {
				this.npc.voice.bark(remark, this.npc.following.getLeader(), null, false);
			}
		}
	}

	/**
	 * Notices when the weather turns, the season changes, or it walks into a new biome, and maybe says something
	 * about it a moment later. The stronger its feelings about the new surroundings, the likelier it speaks up.
	 */
	private void notice() {
		String weather = Surroundings.weatherAt(this.npc.world, this.npc.x, this.npc.y + this.npc.getHeadHeight(), this.npc.z);
		String biome = Surroundings.placeAt(this.npc.world, this.npc.x, this.npc.y, this.npc.z);
		String season = Surroundings.seasonOf(this.npc.world);
		if (this.lastWeather != null && !weather.equals(this.lastWeather)) {
			this.considerRemark("weather_change", Surroundings.Kind.WEATHER, weather, 300);
		} else if (this.lastSeason != null && season != null && !season.equals(this.lastSeason)) {
			this.considerRemark("season_change", Surroundings.Kind.SEASON, season, 600);
		} else if (this.lastBiome != null && biome != null && !biome.equals(this.lastBiome)
			&& this.npc.tickCount - this.lastBiomeRemarkTick >= BIOME_REMARK_COOLDOWN) {
			this.lastBiomeRemarkTick = this.npc.tickCount;
			this.considerRemark("biome_enter", Surroundings.Kind.BIOME, biome, 60);
		}

		this.lastWeather = weather;
		this.lastBiome = biome;
		this.lastSeason = season;
	}

	private void considerRemark(String event, Surroundings.Kind kind, String key, int maxDelay) {
		float like = this.npc.personality.tastes.like(kind, key);
		if (this.npc.getRandom().nextFloat() < 0.25F + 0.6F * Math.abs(like)) {
			this.pendingRemark = event;
			this.pendingRemarkTick = this.npc.tickCount + 20 + this.npc.getRandom().nextInt(maxDelay);
		}
	}

	/** Weather it likes cheers it up while it's out in it; living somewhere it loves (or hates) colors its mood. */
	private void feel() {
		double headY = this.npc.y + this.npc.getHeadHeight();
		if (this.npc.world.canBlockSeeSky(new TilePos(MathHelper.floor(this.npc.x), MathHelper.floor(headY), MathHelper.floor(this.npc.z)))) {
			float like = this.npc.personality.tastes.like(Surroundings.Kind.WEATHER, Surroundings.weatherAt(this.npc.world, this.npc.x, headY, this.npc.z));
			if (like >= 0.3F) {
				this.npc.remember(MemoryType.ENJOYED_THE_WEATHER, null, like);
			} else if (like <= -0.3F) {
				this.npc.remember(MemoryType.MISERABLE_WEATHER, null, -like);
			}
		}

		String biome = Surroundings.placeAt(this.npc.world, this.npc.x, this.npc.y, this.npc.z);
		if (biome != null) {
			float like = this.npc.personality.tastes.like(Surroundings.Kind.BIOME, biome);
			if (like >= 0.3F) {
				this.npc.remember(MemoryType.LIKES_IT_HERE, null, like);
			} else if (like <= -0.3F) {
				this.npc.remember(MemoryType.HATES_IT_HERE, null, -like);
			}
		}
	}

	/** Nothing to save: what it last noticed starts fresh, so loading never sets off a remark. */
	@Override
	public void save(CompoundTag tag) {
	}

	@Override
	public void load(CompoundTag tag) {
	}
}
