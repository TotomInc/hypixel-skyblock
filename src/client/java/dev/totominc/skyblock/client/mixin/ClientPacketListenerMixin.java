package dev.totominc.skyblock.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import dev.totominc.skyblock.client.event.ParticleEvents;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At("RETURN"))
	private void hypixelskyblock$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		ParticleEvents.FROM_SERVER.invoker().onParticleFromServer(packet);
	}
}
