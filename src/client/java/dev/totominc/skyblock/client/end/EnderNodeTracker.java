package dev.totominc.skyblock.client.end;

import java.util.ArrayList;
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
import net.minecraft.world.level.Level;

import dev.totominc.skyblock.client.config.SkyblockConfig;
import dev.totominc.skyblock.client.location.SkyblockLocation;
import dev.totominc.skyblock.client.render.ThroughWallBoxRenderer;

/**
 * Confirms Hypixel SkyBlock Ender Nodes from portal/witch particles that spawn
 * 0.25 blocks off an exposed face. Far, unloaded chunks confirm from a single
 * complete face. A one-second clock drops nodes that go silent or whose block
 * is gone.
 */
public final class EnderNodeTracker {
	private static final ParticleType<?> PORTAL = ParticleTypes.PORTAL.getType();
	private static final ParticleType<?> WITCH = ParticleTypes.WITCH.getType();
	private static final long CONFIRM_COOLDOWN_MS = 2_000L;
	private static final long STALE_MS = 10_000L;
	private static final int PARTICLES_PER_FACE = 5;

	private static final Map<BlockPos, EnderNode> NODES = new HashMap<>();
	private static List<BlockPos> visibleNodes = List.of();

	private EnderNodeTracker() {
	}

	public static void init() {
		LevelExtractionEvents.END_EXTRACTION.register(context -> ThroughWallBoxRenderer.extract(visibleNodes));
	}

	public static void reset() {
		NODES.clear();
		visibleNodes = List.of();
	}

	public static void remove(BlockPos pos) {
		if (NODES.remove(pos) != null) {
			refreshVisible();
		}
	}

	public static int trackedCount() {
		return NODES.size();
	}

	public static void markDebug(BlockPos pos) {
		NODES.computeIfAbsent(pos.immutable(), EnderNode::new).forceConfirm();
		refreshVisible();
	}

	public static void tick(Minecraft client) {
		if (!shouldProcess()) {
			if (!visibleNodes.isEmpty()) {
				visibleNodes = List.of();
			}

			return;
		}

		long now = System.currentTimeMillis();
		Iterator<EnderNode> iterator = NODES.values().iterator();

		while (iterator.hasNext()) {
			EnderNode node = iterator.next();
			node.confirmIfReady(client, now);

			if (node.shouldDiscard(client.level, now)) {
				iterator.remove();
			}
		}

		refreshVisible();
	}

	public static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (!shouldProcess()) {
			return;
		}

		ParticleType<?> type = packet.getParticle().getType();
		boolean portal = PORTAL.equals(type);

		if (!portal && !WITCH.equals(type)) {
			return;
		}

		double x = packet.getX();
		double y = packet.getY();
		double z = packet.getZ();
		double xFrac = Mth.positiveModulo(x, 1);
		double yFrac = Mth.positiveModulo(y, 1);
		double zFrac = Mth.positiveModulo(z, 1);
		BlockPos pos;
		int face;

		if (yFrac == 0.25) {
			pos = BlockPos.containing(x, y - 1, z);
			face = Direction.UP.get3DDataValue();
		} else if (yFrac == 0.75) {
			pos = BlockPos.containing(x, y + 1, z);
			face = Direction.DOWN.get3DDataValue();
		} else if (xFrac == 0.25) {
			pos = BlockPos.containing(x - 1, y, z);
			face = Direction.EAST.get3DDataValue();
		} else if (xFrac == 0.75) {
			pos = BlockPos.containing(x + 1, y, z);
			face = Direction.WEST.get3DDataValue();
		} else if (zFrac == 0.25) {
			pos = BlockPos.containing(x, y, z - 1);
			face = Direction.SOUTH.get3DDataValue();
		} else if (zFrac == 0.75) {
			pos = BlockPos.containing(x, y, z + 1);
			face = Direction.NORTH.get3DDataValue();
		} else {
			return;
		}

		EnderNode node = NODES.computeIfAbsent(pos.immutable(), EnderNode::new);
		node.lastParticleAt = System.currentTimeMillis();

		if (portal) {
			node.portal[face]++;
		} else {
			node.witch[face]++;
		}
	}

	private static boolean shouldProcess() {
		SkyblockConfig config = SkyblockConfig.get();
		return config.enderNodeHelper && (SkyblockLocation.isInTheEnd() || config.debugForceTheEnd);
	}

	private static void refreshVisible() {
		if (NODES.isEmpty()) {
			visibleNodes = List.of();
			return;
		}

		List<BlockPos> confirmed = new ArrayList<>(NODES.size());

		for (EnderNode node : NODES.values()) {
			if (node.confirmed) {
				confirmed.add(node.pos);
			}
		}

		visibleNodes = List.copyOf(confirmed);
	}

	private static final class EnderNode {
		private final BlockPos pos;
		private final int[] portal = new int[6];
		private final int[] witch = new int[6];
		private long lastConfirmed;
		private long lastParticleAt;
		private boolean confirmed;
		private boolean pinned;

		private EnderNode(BlockPos pos) {
			this.pos = pos;
		}

		private void forceConfirm() {
			long now = System.currentTimeMillis();
			lastConfirmed = now;
			lastParticleAt = now;
			confirmed = true;
			pinned = true;
		}

		private void confirmIfReady(Minecraft client, long now) {
			if (lastConfirmed + CONFIRM_COOLDOWN_MS > now || client.level == null) {
				return;
			}

			if (!hasEnoughParticles(client.level)) {
				return;
			}

			lastConfirmed = now;
			confirmed = true;

			for (int i = 0; i < 6; i++) {
				portal[i] = 0;
				witch[i] = 0;
			}
		}

		private boolean hasEnoughParticles(Level level) {
			if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				for (int i = 0; i < 6; i++) {
					if (portal[i] >= PARTICLES_PER_FACE && witch[i] >= PARTICLES_PER_FACE) {
						return true;
					}
				}

				return false;
			}

			for (Direction direction : Direction.values()) {
				int i = direction.get3DDataValue();

				if (portal[i] >= PARTICLES_PER_FACE && witch[i] >= PARTICLES_PER_FACE) {
					continue;
				}

				if (level.getBlockState(pos.relative(direction)).isAir()) {
					return false;
				}
			}

			return true;
		}

		private boolean shouldDiscard(Level level, long now) {
			if (pinned) {
				return false;
			}

			if (lastParticleAt + STALE_MS <= now) {
				return true;
			}

			return level != null
				&& level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
				&& level.getBlockState(pos).isAir();
		}
	}
}
