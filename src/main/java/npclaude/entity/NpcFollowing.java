package npclaude.entity;

import com.mojang.nbt.tags.CompoundTag;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import npclaude.ai.behavior.FollowPlayerBehavior;
import npclaude.ai.behavior.WanderBehavior;
import npclaude.combat.FactionRelation;
import npclaude.combat.NpcFactions;
import npclaude.personality.ItemDefaults;
import npclaude.personality.Quirk;
import npclaude.personality.TasteCategory;
import npclaude.personality.Tastes;
import npclaude.personality.TraitAxis;
import npclaude.social.Opinions;
import npclaude.speech.SpeechContext;
import npclaude.speech.Word;
import org.jetbrains.annotations.Nullable;

/**
 * Following a player: whether it will, how closely, leaving when it falls out with them, and keeping an eye on what
 * they get up to.
 */
public final class NpcFollowing implements NpcPart {
	/** How often a follower checks whether it still wants to follow (5 seconds). */
	private static final int CHECK_INTERVAL = 100;
	/** How often a follower looks at what its leader is up to (2 seconds). */
	private static final int WATCH_INTERVAL = 40;
	/** A follower whose opinion of its leader sinks this low leaves. */
	private static final float QUIT_OPINION = -15.0F;
	/** This far above sea level, the air feels thin. */
	private static final int HIGH_UP_ABOVE_SEA = 70;
	/** A leader below this share of health is badly hurt. */
	private static final float LEADER_HURT_HEALTH = 0.3F;

	private final EntityNPC npc;
	private FollowDistance distance = FollowDistance.MEDIUM;
	/** When each kind of remark about the leader can next be made, by event. */
	private final Map<String, Integer> remarkReady = new HashMap<>();
	/** How many valuables (and of this NPC's favorite thing) its leader carried at the last look; null before the first. */
	@Nullable
	private Map<Item, Integer> leaderValuables;

	NpcFollowing(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		int offset = this.npc.tickCount + this.npc.id;
		if (offset % CHECK_INTERVAL == 0) {
			this.reconsider();
		}

		if (offset % WATCH_INTERVAL == 0) {
			this.watchLeader();
		}
	}

	public FollowDistance getDistance() {
		return this.distance;
	}

	public void setDistance(FollowDistance distance) {
		this.distance = distance;
	}

	/** The player this NPC is following, if it's following someone who is here (not while recovering at home). */
	@Nullable
	public Player getLeader() {
		if (this.npc.health.isRecovering()) {
			return null;
		}

		FollowPlayerBehavior follow = this.npc.brain.findBehavior(FollowPlayerBehavior.class);
		return follow != null ? follow.getPlayer(this.npc) : null;
	}

	/**
	 * Whether it would come along if this player asked: always for allies (its own faction, or a close friend),
	 * never for enemies or while recovering, otherwise if it likes them enough. Outgoing and loyal NPCs need less
	 * convincing, lazy ones more.
	 */
	public boolean willFollow(Player player) {
		if (this.npc.health.isRecovering()) {
			return false;
		}

		FactionRelation relation = NpcFactions.regard(this.npc, player);
		if (relation != FactionRelation.NEUTRAL) {
			return relation == FactionRelation.ALLY;
		}

		float threshold = 25.0F - 10.0F * this.npc.personality.get(TraitAxis.SOCIABILITY) - 10.0F * this.npc.personality.get(TraitAxis.WORK_ETHIC)
			- (this.npc.personality.has(Quirk.LOYAL) ? 5.0F : 0.0F);
		return Opinions.of(this.npc, player) >= Math.max(10.0F, Math.min(45.0F, threshold));
	}

	/** Whether it lets this player see its things and tell it how to fight: its leader, an ally, or a good friend. */
	public boolean trusts(Player player) {
		return this.getLeader() == player || NpcFactions.regard(this.npc, player) == FactionRelation.ALLY
			|| Opinions.of(this.npc, player) >= Opinions.FRIEND_THRESHOLD;
	}

	public void start(Player player) {
		this.npc.brain.set(new FollowPlayerBehavior(player.uuid));
	}

	/** Goes back to wandering around home. */
	public void stop() {
		this.npc.brain.set(new WanderBehavior());
	}

	/** A follower that's fallen out with its leader says so and leaves. */
	private void reconsider() {
		Player leader = this.getLeader();
		if (leader == null) {
			return;
		}

		FactionRelation relation = NpcFactions.regard(this.npc, leader);
		if (relation != FactionRelation.ALLY && (relation == FactionRelation.HOSTILE || Opinions.of(this.npc, leader) <= QUIT_OPINION)) {
			this.npc.voice.bark("follow_quit", leader, null, true);
			this.stop();
		}
	}

	/**
	 * A follower keeps an eye on its leader and comments now and then: going for a swim, climbing somewhere the air
	 * is thin, getting badly hurt, or picking up something valuable (or something this NPC loves).
	 */
	private void watchLeader() {
		Player leader = this.getLeader();
		if (leader == null) {
			this.leaderValuables = null;
			return;
		}

		if (this.npc.isBusy()) {
			return;
		}

		if (leader.isInWater()) {
			this.remark("remark_swim", leader, 3000, 0.6F, null);
		} else if (leader.y > this.npc.world.getWorldType().getOceanY() + HIGH_UP_ABOVE_SEA) {
			this.remark("remark_high_up", leader, 6000, 0.6F, null);
		}

		if (leader.getHealth() > 0 && leader.getHealth() < leader.getMaxHealth() * LEADER_HURT_HEALTH) {
			this.remark("remark_leader_hurt", leader, 1200, 0.8F, null);
		}

		Map<Item, Integer> valuables = this.countValuables(leader);
		if (this.leaderValuables != null) {
			for (Map.Entry<Item, Integer> entry : valuables.entrySet()) {
				if (entry.getValue() > this.leaderValuables.getOrDefault(entry.getKey(), 0)) {
					Item item = entry.getKey();
					float like = this.npc.personality.tastes.like(item);
					boolean favorite = item == this.npc.personality.tastes.favoriteThing();
					this.remark("remark_leader_found", leader, 600, favorite ? 1.0F : 0.7F, context -> context
						.word("item", Word.ofThing(item)).fact("taste", Tastes.describe(like)).fact("is_favorite", favorite ? "yes" : "no"));
					break;
				}
			}
		}

		this.leaderValuables = valuables;
	}

	private Map<Item, Integer> countValuables(Player player) {
		Map<Item, Integer> counts = new HashMap<>();
		Item favorite = this.npc.personality.tastes.favoriteThing();
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack != null && (ItemDefaults.of(stack.getItem()).is(TasteCategory.VALUABLE) || stack.getItem() == favorite)) {
				counts.merge(stack.getItem(), stack.stackSize, Integer::sum);
			}
		}

		return counts;
	}

	/** Says something about the leader, if this kind of remark isn't on cooldown and the chance comes up. */
	private void remark(String event, Player leader, int cooldown, float chance, @Nullable Consumer<SpeechContext> extras) {
		if (this.npc.tickCount < this.remarkReady.getOrDefault(event, 0)) {
			return;
		}

		this.remarkReady.put(event, this.npc.tickCount + cooldown);
		if (this.npc.getRandom().nextFloat() < chance) {
			this.npc.voice.bark(event, leader, extras, false);
		}
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putByte("FollowDistance", (byte) this.distance.ordinal());
	}

	@Override
	public void load(CompoundTag tag) {
		this.distance = FollowDistance.byId(tag.getByte("FollowDistance"));
	}
}
