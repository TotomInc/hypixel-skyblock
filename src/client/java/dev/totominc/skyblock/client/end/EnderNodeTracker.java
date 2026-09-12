package dev.totominc.skyblock.client.end;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.totominc.skyblock.client.config.SkyblockConfig;
import dev.totominc.skyblock.client.event.ParticleEvents;
import dev.totominc.skyblock.client.location.SkyblockLocation;
import dev.totominc.skyblock.client.render.ThroughWallBoxRenderer;

/**
 * Detects Hypixel SkyBlock Ender Nodes from portal/witch particle packets at
 * any distance the server sends them. Face-offset confirmation follows
 * Skyblocker's {@code EnderNodes} helper: particles spawn 0.25 blocks off an
 * exposed face. Loaded chunks still require every air-adjacent face to reach
 * five portal and five witch particles; unloaded (far) chunks confirm from a
 * single complete face so nodes beyond the player's loaded area still register.
 *
 * <p>A one-second clock drops nodes that stop emitting particles or whose block
 * is gone, so mined or despawned nodes do not linger.
 */
public final class EnderNodeTracker {
	private static final long CONFIRM_COOLDOWN_MS = 2_000L;
	private static final long STALE_MS = 10_000L;
	private static final int PARTICLES_PER_FACE = 5;

	private static final Map<BlockPos, EnderNode> NODES = new HashMap<>();

	private EnderNodeTracker() {
	}

	public static void init() {
		ParticleEvents.FROM_SERVER.register(EnderNodeTracker::onParticle);
		LevelExtractionEvents.END_EXTRACTION.register(context -> ThroughWallBoxRenderer.extract(visibleNodes()));
	}

	public static void reset() {
		NODES.clear();
	}

	public static void remove(BlockPos pos) {
		NODES.remove(pos);
	}

	public static int trackedCount() {
		return NODES.size();
	}

	public static void markDebug(BlockPos pos) {
		EnderNode node = NODES.computeIfAbsent(pos.immutable(), EnderNode::new);
		node.forceConfirm();
	}

	/**
	 * Recurring pass: confirm nodes from accumulated particles, then drop any
	 * that have gone silent or whose block is no longer there.
	 */
	public static void tick(Minecraft client) {
		if (!shouldProcess()) {
			return;
		}

		long now = System.currentTimeMillis();
		Iterator<Map.Entry<BlockPos, EnderNode>> iterator = NODES.entrySet().iterator();

		while (iterator.hasNext()) {
			EnderNode node = iterator.next().getValue();
			node.update(client, now);

			if (node.shouldDiscard(client, now)) {
				iterator.remove();
			}
		}
	}

	private static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (!shouldProcess()) {
			return;
		}

		ParticleType<?> particleType = packet.getParticle().getType();

		if (!ParticleTypes.PORTAL.getType().equals(particleType) && !ParticleTypes.WITCH.getType().equals(particleType)) {
			return;
		}

		double x = packet.getX();
		double y = packet.getY();
		double z = packet.getZ();
		double xFrac = Mth.positiveModulo(x, 1);
		double yFrac = Mth.positiveModulo(y, 1);
		double zFrac = Mth.positiveModulo(z, 1);
		BlockPos pos;
		Direction direction;

		if (yFrac == 0.25) {
			pos = BlockPos.containing(x, y - 1, z);
			direction = Direction.UP;
		} else if (yFrac == 0.75) {
			pos = BlockPos.containing(x, y + 1, z);
			direction = Direction.DOWN;
		} else if (xFrac == 0.25) {
			pos = BlockPos.containing(x - 1, y, z);
			direction = Direction.EAST;
		} else if (xFrac == 0.75) {
			pos = BlockPos.containing(x + 1, y, z);
			direction = Direction.WEST;
		} else if (zFrac == 0.25) {
			pos = BlockPos.containing(x, y, z - 1);
			direction = Direction.SOUTH;
		} else if (zFrac == 0.75) {
			pos = BlockPos.containing(x, y, z + 1);
			direction = Direction.NORTH;
		} else {
			return;
		}

		EnderNode node = NODES.computeIfAbsent(pos.immutable(), EnderNode::new);
		ParticleCounts counts = node.particles.get(direction);
		node.lastParticleAt = System.currentTimeMillis();

		if (ParticleTypes.PORTAL.getType().equals(particleType)) {
			counts.portal++;
		} else {
			counts.witch++;
		}
	}

	private static boolean shouldProcess() {
		SkyblockConfig config = SkyblockConfig.get();
		return config.enderNodeHelper && (SkyblockLocation.isInTheEnd() || config.debugForceTheEnd);
	}

	private static List<BlockPos> visibleNodes() {
		if (!shouldProcess()) {
			return List.of();
		}

		return NODES.values().stream()
			.filter(EnderNode::shouldRender)
			.map(node -> node.pos)
			.toList();
	}

	private static final class EnderNode {
		private final BlockPos pos;
		private final Map<Direction, ParticleCounts> particles = new EnumMap<>(Direction.class);
		private long lastConfirmed;
		private long lastParticleAt;
		private boolean seen;
		private boolean pinned;

		private EnderNode(BlockPos pos) {
			this.pos = pos;

			for (Direction direction : Direction.values()) {
				particles.put(direction, new ParticleCounts());
			}
		}

		private void forceConfirm() {
			long now = System.currentTimeMillis();
			lastConfirmed = now;
			lastParticleAt = now;
			seen = true;
			pinned = true;
		}

		private void update(Minecraft client, long now) {
			updateSeen(client);

			if (lastConfirmed + CONFIRM_COOLDOWN_MS > now || client.level == null) {
				return;
			}

			if (!hasEnoughParticles(client)) {
				return;
			}

			lastConfirmed = now;

			for (ParticleCounts counts : particles.values()) {
				counts.portal = 0;
				counts.witch = 0;
			}
		}

		private boolean hasEnoughParticles(Minecraft client) {
			boolean chunkLoaded = client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);

			if (!chunkLoaded) {
				return particles.values().stream()
					.anyMatch(counts -> counts.portal >= PARTICLES_PER_FACE && counts.witch >= PARTICLES_PER_FACE);
			}

			return particles.entrySet().stream().allMatch(entry -> {
				ParticleCounts counts = entry.getValue();
				boolean enoughParticles = counts.portal >= PARTICLES_PER_FACE && counts.witch >= PARTICLES_PER_FACE;
				boolean faceBlocked = !client.level.getBlockState(pos.relative(entry.getKey())).isAir();
				return enoughParticles || faceBlocked;
			});
		}

		private void updateSeen(Minecraft client) {
			if (SkyblockConfig.get().throughWalls || !SkyblockConfig.get().requireLineOfSightOnce) {
				seen = true;
				return;
			}

			if (seen || client.level == null || client.player == null) {
				return;
			}

			if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				return;
			}

			BlockHitResult hit = client.level.clip(new ClipContext(
				client.player.getEyePosition(),
				Vec3.atCenterOf(pos),
				ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE,
				client.player
			));

			if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
				seen = true;
			}
		}

		private boolean shouldDiscard(Minecraft client, long now) {
			if (pinned) {
				return false;
			}

			if (lastParticleAt + STALE_MS <= now) {
				return true;
			}

			return client.level != null
				&& client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
				&& client.level.getBlockState(pos).isAir();
		}

		private boolean shouldRender() {
			if (lastConfirmed == 0) {
				return false;
			}

			return SkyblockConfig.get().throughWalls || seen;
		}
	}

	private static final class ParticleCounts {
		private int portal;
		private int witch;
	}
}
