package com.example.carcass;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class DeathHandler {

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();

        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!(entity instanceof Mob)) return;                               // гравців не чіпаємо
        if (entity instanceof WitherBoss || entity instanceof EnderDragon) return; // боси як у ванілі

        List<ItemStack> loot = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack s = drop.getItem();
            if (!s.isEmpty()) loot.add(s.copy());
        }
        if (loot.isEmpty()) return;

        BlockPos spot = Carcass.findSpot(level, entity.blockPosition());
        if (spot == null) return; // нема місця під труп: лут випаде як у ванілі

        // Зберігаємо вигляд моба (колір вівці, розмір слайма, броню...) для малювання трупа
        CompoundTag mobTag = new CompoundTag();
        try {
            entity.saveWithoutId(mobTag);
            for (String key : new String[]{"Passengers", "Leash", "Brain", "Attributes", "Health",
                    "HurtTime", "DeathTime", "ActiveEffects", "Offers", "Inventory", "UUID", "Pos", "Motion"}) {
                mobTag.remove(key);
            }
        } catch (Exception e) {
            mobTag = new CompoundTag();
        }

        var typeKey = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        String typeId = typeKey == null ? "" : typeKey.toString();

        level.setBlockAndUpdate(spot, Carcass.CORPSE.get().defaultBlockState());
        if (level.getBlockEntity(spot) instanceof CorpseBlockEntity be) {
            be.setLoot(loot, typeId, entity.yBodyRot, mobTag);
            event.setCanceled(true); // ванільний лут не випадає
        }
    }
}
