package dev.totominc.skyblock.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import dev.totominc.skyblock.client.render.ThroughWallBoxRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void hypixelskyblock$onClose(CallbackInfo ci) {
		ThroughWallBoxRenderer.close();
	}
}
