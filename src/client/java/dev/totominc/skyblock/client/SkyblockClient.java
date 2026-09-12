package dev.totominc.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.totominc.skyblock.SkyblockMod;
import dev.totominc.skyblock.client.config.SkyblockConfig;
import dev.totominc.skyblock.client.end.EnderNodeTracker;
import dev.totominc.skyblock.client.location.SkyblockLocation;
import dev.totominc.skyblock.client.render.ThroughWallBoxRenderer;

public class SkyblockClient implements ClientModInitializer {
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(SkyblockMod.id("skyblock"));

	private static KeyMapping toggleEnderNodesKey;
	private static int locationTickCounter;

	@Override
	public void onInitializeClient() {
		SkyblockConfig.load();
		ThroughWallBoxRenderer.init();
		EnderNodeTracker.init();

		toggleEnderNodesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.hypixelskyblock.toggle_ender_nodes",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleEnderNodesKey.consumeClick()) {
				boolean enabled = SkyblockConfig.get().toggleEnderNodeHelper();
				SkyblockConfig.save();

				if (client.player != null) {
					client.player.sendSystemMessage(statusComponent(enabled));
				}
			}

			locationTickCounter++;

			if (locationTickCounter >= 20) {
				locationTickCounter = 0;
				SkyblockLocation.update(client);
				EnderNodeTracker.tick(client);
			}
		});

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			EnderNodeTracker.remove(pos);
			return InteractionResult.PASS;
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EnderNodeTracker.reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			EnderNodeTracker.reset();
			SkyblockLocation.reset();
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
			ClientCommands.literal("endernodes")
				.executes(context -> {
					context.getSource().sendFeedback(statusComponent(SkyblockConfig.get().enderNodeHelper));
					return 1;
				})
				.then(ClientCommands.literal("toggle").executes(context -> {
					boolean enabled = SkyblockConfig.get().toggleEnderNodeHelper();
					SkyblockConfig.save();
					context.getSource().sendFeedback(statusComponent(enabled));
					return 1;
				}))
				.then(ClientCommands.literal("debug")
					.then(ClientCommands.literal("mark").executes(context -> {
						BlockPos pos = resolveDebugPos();

						if (pos == null) {
							return 0;
						}

						SkyblockConfig.get().debugForceTheEnd = true;
						SkyblockConfig.save();
						EnderNodeTracker.markDebug(pos);
						context.getSource().sendFeedback(Component.translatable(
							"hypixelskyblock.ender_nodes.debug_marked",
							pos.toShortString()
						));
						return 1;
					}))
					.then(ClientCommands.literal("clear").executes(context -> {
						SkyblockConfig.get().debugForceTheEnd = false;
						SkyblockConfig.save();
						EnderNodeTracker.reset();
						context.getSource().sendFeedback(Component.translatable("hypixelskyblock.ender_nodes.debug_cleared"));
						return 1;
					}))
				)
		));
	}

	private static Component statusComponent(boolean enabled) {
		return Component.translatable(
			"hypixelskyblock.ender_nodes.status",
			enabled ? "on" : "off",
			EnderNodeTracker.trackedCount()
		);
	}

	private static BlockPos resolveDebugPos() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return null;
		}

		if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}

		return client.player.blockPosition();
	}
}
