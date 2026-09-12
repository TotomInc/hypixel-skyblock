package dev.totominc.skyblock.client.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

public final class ParticleEvents {
	public static final Event<FromServer> FROM_SERVER = EventFactory.createArrayBacked(FromServer.class, listeners -> packet -> {
		for (FromServer listener : listeners) {
			listener.onParticleFromServer(packet);
		}
	});

	private ParticleEvents() {
	}

	@FunctionalInterface
	public interface FromServer {
		void onParticleFromServer(ClientboundLevelParticlesPacket packet);
	}
}
