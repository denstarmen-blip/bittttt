package com.example.carcass.client;

import com.example.carcass.CorpseBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.WeakHashMap;

/** Малює вбитого моба, що лежить на боці (як при смерті у ванілі). */
public class CorpseRenderer implements BlockEntityRenderer<CorpseBlockEntity> {
    private static class Holder {
        String id;
        Entity entity; // null, якщо створити не вдалось
    }

    private final EntityRenderDispatcher dispatcher;
    private final Map<CorpseBlockEntity, Holder> cache = new WeakHashMap<>();

    public CorpseRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.getEntityRenderer();
    }

    @Override
    public void render(CorpseBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String id = be.getMobType();
        Level level = be.getLevel();
        if (id == null || id.isEmpty() || level == null) return;

        Holder holder = cache.get(be);
        if (holder == null || !id.equals(holder.id) || (holder.entity != null && holder.entity.level() != level)) {
            holder = new Holder();
            holder.id = id;
            holder.entity = createDummy(be, level);
            cache.put(be, holder);
        }
        if (holder.entity == null) return;

        try {
            Entity ent = holder.entity;
            poseStack.pushPose();
            // Ванільна анімація смерті крутить моба навколо ніг, тому тіло "відлітає" вбік.
            // Зсуваємо його назад, щоб тіло лежало по центру блока (з урахуванням повороту).
            // Розмір: з кожним відрізаним шматком труп меншає (до ~55%)
            float scale = 0.55f + 0.45f * be.getFill();

            // Тремтіння від ударів ножем (затухає за ~8 тіків)
            double shakeX = 0, shakeZ = 0, bob = 0;
            long since = level.getGameTime() - be.lastCutTime;
            if (since >= 0 && since < 8) {
                double t = since + partialTick;
                double fade = 1.0 - t / 8.0;
                shakeX = Math.sin(t * 3.1) * 0.05 * fade;
                shakeZ = Math.cos(t * 2.7) * 0.03 * fade;
                bob = -Math.abs(Math.sin(t * 1.6)) * 0.04 * fade;
            }

            // Ванільна анімація смерті крутить моба навколо ніг, тому тіло "відлітає" вбік.
            // Зсуваємо його назад, щоб тіло лежало по центру блока (з урахуванням повороту).
            double shift = holder.entity.getBbHeight() * 0.5 * scale;
            double theta = Math.toRadians(180.0 - be.getYaw());
            poseStack.translate(0.5 + shift * Math.cos(theta) + shakeX,
                    0.07 + bob,
                    0.5 - shift * Math.sin(theta) + shakeZ);
            poseStack.scale(scale, scale, scale);
            dispatcher.render(ent, 0.0, 0.0, 0.0, 0.0f, 1.0f, poseStack, buffer, packedLight);
            poseStack.popPose();
        } catch (Exception e) {
            // якщо рендер якогось моба падає, просто не малюємо його
            holder.entity = null;
        }
    }

    private Entity createDummy(CorpseBlockEntity be, Level level) {
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(be.getMobType()));
            if (type == null) return null;
            Entity e = type.create(level);
            if (e == null) return null;

            if (!be.getMobTag().isEmpty()) {
                try {
                    e.load(be.getMobTag().copy());
                } catch (Exception ignored) {
                    // не вдалось застосувати дані: малюємо стандартного моба
                }
            }
            e.setCustomName(null);
            e.setCustomNameVisible(false);
            e.setInvisible(false);

            if (e instanceof LivingEntity le) {
                le.deathTime = 20;   // ванільна анімація смерті: моб лежить на боці, червоний відтінок
                le.hurtTime = 0;
                float yaw = be.getYaw();
                le.yBodyRot = yaw;
                le.yBodyRotO = yaw;
                le.yHeadRot = yaw;
                le.yHeadRotO = yaw;
                le.setXRot(0f);
                le.xRotO = 0f;
            }
            return e;
        } catch (Exception ex) {
            return null;
        }
    }
}
