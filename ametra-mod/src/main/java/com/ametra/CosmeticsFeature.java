package com.ametra;

import org.joml.Matrix4f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Dibuja los cosméticos del launcher (capa, alas, sombrero, gafas, mochila, espalda, botas, puños, aura, cara) sobre el jugador local.
 * Se lee de config/ametra.json -> "cosmetics". Se ve en tercera persona (F5) y en la vista previa del inventario.
 * Coordenadas: ver tools/gen_cosmetics.py (espacio del modelo del jugador, 1 unidad = 1 píxel).
 */
public class CosmeticsFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    // bits de animación por caja (los mismos que ORBIT/BOB/... de tools/gen_cosmetics.py)
    private static final int ORBIT = 1, BOB = 2, PULSE = 4, SWAY = 8, RISE = 16, SPIN = 32;

    // estado suavizado de la capa (solo hay un jugador local)
    private static float capeAngle = 6f;
    private static long lastNs = System.nanoTime();

    public CosmeticsFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (player != MinecraftClient.getInstance().player) return;
        if (player.isInvisible() || player.isSpectator()) return;
        if (!AmetraConfig.cosmeticsOn) return;

        boolean elytra = player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        boolean chest = !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
        boolean helmet = !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
        float back = chest ? 1.0f : 0f;
        float t = player.age + tickDelta;

        CosmeticData.Item cape = AmetraConfig.cosmetic("cape");
        CosmeticData.Item wings = AmetraConfig.cosmetic("wings");
        CosmeticData.Item pack = AmetraConfig.cosmetic("pack");
        CosmeticData.Item hat = AmetraConfig.cosmetic("hat");
        CosmeticData.Item glasses = AmetraConfig.cosmetic("glasses");
        CosmeticData.Item backp = AmetraConfig.cosmetic("back");
        CosmeticData.Item boots = AmetraConfig.cosmetic("boots");
        CosmeticData.Item fists = AmetraConfig.cosmetic("fists");
        CosmeticData.Item aura = AmetraConfig.cosmetic("aura");
        CosmeticData.Item face = AmetraConfig.cosmetic("face");

        PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();

        // ---------- torso ----------
        if (cape != null && !elytra) {
            float angle = capeAngle(player, tickDelta, limbAngle, limbDistance);
            matrices.push();
            model.body.rotate(matrices);
            matrices.translate(0f, 0f, (2f + back) / 16f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
            VertexConsumer vc = buffer(vertexConsumers, cape);
            for (CosmeticData.B b : cape.boxes) box(matrices, vc, b, 0f, 0f, 2f, 0f, light, false);
            matrices.pop();
        }

        if (wings != null && !elytra) {
            boolean air = !player.isOnGround();
            float base = player.isSneaking() ? 8f : 24f;
            float flap = air ? MathHelper.sin(t * 0.7f) * 26f + 8f : MathHelper.sin(t * 0.12f) * 4f;
            float angle = MathHelper.clamp(base + flap, 0f, 78f);
            matrices.push();
            model.body.rotate(matrices);
            VertexConsumer vc = buffer(vertexConsumers, wings);
            for (CosmeticData.B b : wings.boxes) {
                for (int s = -1; s <= 1; s += 2) { // s = +1 ala izquierda del jugador, -1 derecha
                    matrices.push();
                    matrices.translate(s * 3.0f / 16f, 0.6f / 16f, (2.8f + back) / 16f);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s * angle));
                    box(matrices, vc, b, s, 0f, 0f, 0f, light, s > 0);
                    matrices.pop();
                }
            }
            matrices.pop();
        }

        if (pack != null) {
            matrices.push();
            model.body.rotate(matrices);
            VertexConsumer vc = buffer(vertexConsumers, pack);
            boolean air = !player.isOnGround();
            for (CosmeticData.B b : pack.boxes) {
                float sy = 1f;
                if ((b.flags & 1) != 0) { // llama del jetpack
                    sy = air ? 0.85f + 0.3f * MathHelper.sin(t * 1.9f + b.x) : 0.25f;
                }
                box(matrices, vc, b, 0f, 0f, 0f, back, light, false, sy);
            }
            matrices.pop();
        }

        if (backp != null) {
            matrices.push();
            model.body.rotate(matrices);
            VertexConsumer vc = buffer(vertexConsumers, backp);
            for (CosmeticData.B b : backp.boxes) boxA(matrices, vc, b, 0f, t, back, light, false);
            matrices.pop();
        }

        if (aura != null) {
            matrices.push();
            model.body.rotate(matrices);
            VertexConsumer vc = buffer(vertexConsumers, aura);
            for (CosmeticData.B b : aura.boxes) boxA(matrices, vc, b, 0f, t, 0f, light, false);
            matrices.pop();
        }

        // ---------- piernas y brazos ----------
        if (boots != null) {
            VertexConsumer vc = buffer(vertexConsumers, boots);
            for (int s = -1; s <= 1; s += 2) { // s = +1 pierna izquierda del jugador
                matrices.push();
                (s > 0 ? model.leftLeg : model.rightLeg).rotate(matrices);
                for (CosmeticData.B b : boots.boxes) boxA(matrices, vc, b, s, t, 0f, light, s > 0);
                matrices.pop();
            }
        }
        if (fists != null) {
            VertexConsumer vc = buffer(vertexConsumers, fists);
            for (int s = -1; s <= 1; s += 2) {
                matrices.push();
                (s > 0 ? model.leftArm : model.rightArm).rotate(matrices);
                matrices.translate(s * 0.75f / 16f, 0f, 0f); // centro del brazo (entre clásico y slim)
                for (CosmeticData.B b : fists.boxes) boxA(matrices, vc, b, s, t, 0f, light, s > 0);
                matrices.pop();
            }
        }

        // ---------- cabeza ----------
        if (hat != null || (glasses != null && !helmet) || (face != null && !helmet)) {
            matrices.push();
            model.head.rotate(matrices);
            if (hat != null) {
                VertexConsumer vc = buffer(vertexConsumers, hat);
                float lift = helmet ? -1.0f : 0f;
                for (CosmeticData.B b : hat.boxes) boxA(matrices, vc, b, 0f, t, 0f, light, false, lift);
            }
            if (glasses != null && !helmet) {
                VertexConsumer vc = buffer(vertexConsumers, glasses);
                for (CosmeticData.B b : glasses.boxes) box(matrices, vc, b, 0f, 0f, 0f, 0f, light, false);
            }
            if (face != null && !helmet) {
                VertexConsumer vc = buffer(vertexConsumers, face);
                for (CosmeticData.B b : face.boxes) boxA(matrices, vc, b, 0f, t, 0f, light, false);
            }
            matrices.pop();
        }

        // cosméticos 3D hechos por el jugador en el Estudio del launcher
        VoxelCosmetics.render(matrices, vertexConsumers, light, model, helmet, chest);
    }

    // ---------------------------------------------------------------- animación de la capa
    private static float capeAngle(AbstractClientPlayerEntity p, float tickDelta, float limbAngle, float limbDistance) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        float yaw = MathHelper.lerpAngleDegrees(tickDelta, p.prevBodyYaw, p.bodyYaw);
        double rad = Math.toRadians(yaw);
        double dx = p.getX() - p.prevX, dy = p.getY() - p.prevY, dz = p.getZ() - p.prevZ;
        double fwd = -dx * Math.sin(rad) + dz * Math.cos(rad); // bloques/tick hacia donde mira el cuerpo

        float lift = (float) MathHelper.clamp(fwd * 100.0, 0.0, 150.0) * 0.5f;
        float fall = MathHelper.clamp((float) (-dy) * 40f, -6f, 32f);
        float swing = MathHelper.sin(limbAngle * 0.6662f) * 8f * limbDistance;
        float sneak = p.isSneaking() ? 18f : 0f;
        float target = MathHelper.clamp(6f + lift + fall + swing + sneak, 0f, 85f);

        capeAngle += (target - capeAngle) * (1f - (float) Math.exp(-dt * 12f));
        return capeAngle;
    }

    // ---------------------------------------------------------------- dibujo
    private static VertexConsumer buffer(VertexConsumerProvider vcp, CosmeticData.Item it) {
        Identifier id = Compat.id("ametra", "textures/cosmetics/" + it.tex + ".png");
        return vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(id));
    }

    private static void box(MatrixStack ms, VertexConsumer vc, CosmeticData.B b, float mirror, float dy, float pivotZ, float dz, int light, boolean flipU) {
        box(ms, vc, b, mirror, dy, pivotZ, dz, light, flipU, 1f);
    }

    /**
     * Dibuja una caja. mirror != 0 refleja la posición en x (alas); pivotZ se resta de z (el pivote ya está aplicado);
     * dy/dz son desplazamientos extra; flipU invierte la textura (ala izquierda); sy escala la altura (llama).
     */
    private static void box(MatrixStack ms, VertexConsumer vc, CosmeticData.B b, float mirror, float dy, float pivotZ, float dz,
                            int light, boolean flipU, float sy) {
        float cx = mirror != 0f ? b.x * mirror : b.x;
        float h = b.h * sy;
        float cy = b.y + dy;
        if ((b.flags & 1) != 0) cy = b.y - b.h * 0.5f + h * 0.5f + dy; // la llama crece hacia abajo desde su borde superior
        float cz = b.z - pivotZ + dz;
        boxRaw(ms, vc, b, cx, cy, cz, 0f, 1f, sy, b.glow ? FULL_BRIGHT : light, flipU);
    }

    private static void boxA(MatrixStack ms, VertexConsumer vc, CosmeticData.B b, float mirror, float t, float dz, int light, boolean flipU) {
        boxA(ms, vc, b, mirror, t, dz, light, flipU, 0f);
    }

    /**
     * Caja con animación propia (ver b.an): órbita, vaivén, pulso, balanceo, ascenso con desvanecimiento y giro.
     * La velocidad b.asp son ciclos por segundo, b.aph la fase en grados. dy desplaza todo en vertical (casco puesto).
     */
    private static void boxA(MatrixStack ms, VertexConsumer vc, CosmeticData.B b, float mirror, float t, float dz, int light, boolean flipU, float dy) {
        float cx = mirror != 0f ? b.x * mirror : b.x;
        float cy = b.y + dy, cz = b.z + dz, ery = 0f, sc = 1f;
        if (b.an != 0) {
            float cyc = t / 20f * b.asp + b.aph / 360f;
            float ang = cyc * (float) (Math.PI * 2.0);
            if ((b.an & ORBIT) != 0) {
                cx = b.x * MathHelper.cos(ang);
                cz = b.x * MathHelper.sin(ang) + dz;
            }
            if ((b.an & RISE) != 0) {
                float fr = cyc - MathHelper.floor(cyc);
                cy -= fr * b.ar;
                sc *= Math.max(0.05f, MathHelper.sin(fr * (float) Math.PI));
            }
            if ((b.an & BOB) != 0) cy += MathHelper.sin(ang) * b.ap;
            if ((b.an & PULSE) != 0) sc *= 1f + b.ap * MathHelper.sin(ang);
            if ((b.an & SWAY) != 0) cx += MathHelper.sin(ang) * b.ap;
            if ((b.an & SPIN) != 0) ery = cyc * 360f;
        }
        boxRaw(ms, vc, b, cx, cy, cz, ery, sc, 1f, b.glow ? FULL_BRIGHT : light, flipU);
    }

    /** Dibuja una caja centrada en (cx, cy, cz) píxeles. sc escala todo; sy solo la altura (llama del jetpack). */
    private static void boxRaw(MatrixStack ms, VertexConsumer vc, CosmeticData.B b, float cx, float cy, float cz, float extraRy,
                               float sc, float sy, int lt, boolean flipU) {
        ms.push();
        ms.translate(cx / 16f, cy / 16f, cz / 16f);
        float ry = b.ry + extraRy;
        if (ry != 0f) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();

        float hx = b.w * sc / 32f, hy = b.h * sy * sc / 32f, hz = b.d * sc / 32f;
        float u0 = flipU ? b.u1 : b.u0, u1 = flipU ? b.u0 : b.u1;

        // frente (-z): u crece con +x
        quad(vc, e, m, lt, 0, 0, -1,
            -hx, -hy, -hz, hx, -hy, -hz, hx, hy, -hz, -hx, hy, -hz, u0, b.v0, u1, b.v1);
        // espalda (+z): u crece con -x
        quad(vc, e, m, lt, 0, 0, 1,
            hx, -hy, hz, -hx, -hy, hz, -hx, hy, hz, hx, hy, hz, u0, b.v0, u1, b.v1);
        // lados y tapas
        quad(vc, e, m, lt, -1, 0, 0,
            -hx, -hy, hz, -hx, -hy, -hz, -hx, hy, -hz, -hx, hy, hz, b.s0, b.t0, b.s1, b.t1);
        quad(vc, e, m, lt, 1, 0, 0,
            hx, -hy, -hz, hx, -hy, hz, hx, hy, hz, hx, hy, -hz, b.s0, b.t0, b.s1, b.t1);
        quad(vc, e, m, lt, 0, -1, 0,
            -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, b.s0, b.t0, b.s1, b.t1);
        quad(vc, e, m, lt, 0, 1, 0,
            -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, b.s0, b.t0, b.s1, b.t1);
        ms.pop();
    }

    /** Un cuadrado: esquinas en orden arriba-izq, arriba-der, abajo-der, abajo-izq. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        v(vc, e, m, light, nx, ny, nz, ax, ay, az, u0, v0);
        v(vc, e, m, light, nx, ny, nz, bx, by, bz, u1, v0);
        v(vc, e, m, light, nx, ny, nz, cx, cy, cz, u1, v1);
        v(vc, e, m, light, nx, ny, nz, dx, dy, dz, u0, v1);
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int light, float nx, float ny, float nz,
                          float x, float y, float z, float u, float v) {
        Compat.end(vc.vertex(m, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz));
    }
}
