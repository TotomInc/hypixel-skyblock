package dev.totominc.skyblock.client.render;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import dev.totominc.skyblock.SkyblockMod;
import dev.totominc.skyblock.client.config.SkyblockConfig;

/**
 * Draws Skyblocker-style through-wall frames around confirmed Ender Nodes.
 * Uses a custom {@link RenderPipeline} with no depth test so the outline stays
 * visible behind terrain, following the Fabric 26.2 world-rendering guide.
 */
public final class ThroughWallBoxRenderer {
	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(SkyblockMod.id("pipeline/ender_node_filled_through_walls"))
		.withDepthStencilState(Optional.empty())
		.build()
	);

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer BUFFER = new StagedVertexBuffer(() -> "Ender Node Frames", RenderType.TRANSIENT_BUFFER_SIZE);

	private static List<BlockPos> extractedNodes = List.of();

	private ThroughWallBoxRenderer() {
	}

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ThroughWallBoxRenderer::draw);
	}

	public static void extract(List<BlockPos> nodes) {
		extractedNodes = List.copyOf(nodes);
	}

	public static void close() {
		BUFFER.close();
	}

	private static void draw(LevelRenderContext context) {
		if (extractedNodes.isEmpty()) {
			return;
		}

		VertexFormat formatBinding = FILLED_THROUGH_WALLS.getVertexFormatBinding(0);

		if (formatBinding == null) {
			return;
		}

		PrimitiveTopology primitive = FILLED_THROUGH_WALLS.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = BUFFER.appendDraw(
			formatBinding,
			primitive,
			primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
		);

		PoseStack matrices = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		VertexConsumer builder = BUFFER.getVertexBuilder(draw);
		SkyblockConfig config = SkyblockConfig.get();
		float[] color = config.frameColor;

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		Matrix4fc pose = matrices.last().pose();

		for (BlockPos pos : extractedNodes) {
			float minX = pos.getX();
			float minY = pos.getY();
			float minZ = pos.getZ();
			float maxX = minX + 1f;
			float maxY = minY + 1f;
			float maxZ = minZ + 1f;

			renderFilledBox(pose, builder, minX, minY, minZ, maxX, maxY, maxZ, color[0], color[1], color[2], config.fillAlpha);
			renderFrame(pose, builder, minX, minY, minZ, maxX, maxY, maxZ, color[0], color[1], color[2], config.frameAlpha, config.frameThickness);
		}

		matrices.popPose();
		BUFFER.upload();

		StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);

		if (info != null) {
			submit(Minecraft.getInstance(), info, FILLED_THROUGH_WALLS);
		}

		BUFFER.endFrame();
	}

	private static void renderFrame(
		Matrix4fc pose,
		VertexConsumer buffer,
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ,
		float red,
		float green,
		float blue,
		float alpha,
		float thickness
	) {
		renderFilledBox(pose, buffer, minX, minY, minZ, maxX, minY + thickness, minZ + thickness, red, green, blue, alpha);
		renderFilledBox(pose, buffer, minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, red, green, blue, alpha);
		renderFilledBox(pose, buffer, minX, minY, minZ, minX + thickness, minY + thickness, maxZ, red, green, blue, alpha);
		renderFilledBox(pose, buffer, maxX - thickness, minY, minZ, maxX, minY + thickness, maxZ, red, green, blue, alpha);

		renderFilledBox(pose, buffer, minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, red, green, blue, alpha);
		renderFilledBox(pose, buffer, minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, red, green, blue, alpha);
		renderFilledBox(pose, buffer, minX, maxY - thickness, minZ, minX + thickness, maxY, maxZ, red, green, blue, alpha);
		renderFilledBox(pose, buffer, maxX - thickness, maxY - thickness, minZ, maxX, maxY, maxZ, red, green, blue, alpha);

		renderFilledBox(pose, buffer, minX, minY, minZ, minX + thickness, maxY, minZ + thickness, red, green, blue, alpha);
		renderFilledBox(pose, buffer, maxX - thickness, minY, minZ, maxX, maxY, minZ + thickness, red, green, blue, alpha);
		renderFilledBox(pose, buffer, minX, minY, maxZ - thickness, minX + thickness, maxY, maxZ, red, green, blue, alpha);
		renderFilledBox(pose, buffer, maxX - thickness, minY, maxZ - thickness, maxX, maxY, maxZ, red, green, blue, alpha);
	}

	private static void renderFilledBox(
		Matrix4fc pose,
		VertexConsumer buffer,
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ,
		float red,
		float green,
		float blue,
		float alpha
	) {
		buffer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);

		buffer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

		buffer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

		buffer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
	}

	private static void submit(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();

		if (colorTexture == null) {
			return;
		}

		try (RenderPass renderPass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(() -> SkyblockMod.MOD_ID + " ender node frames", colorTexture, Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
			renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}
}
