package dev.totominc.skyblock.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import dev.totominc.skyblock.client.end.EnderNodeTracker;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void hypixelskyblock$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		EnderNodeTracker.onParticle(packet);
	}
}
