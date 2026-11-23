package com.example.carcass;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.ForgeRegistries;

/** Розпізнає ножі Farmer's Delight (та будь-які ножі з тегом forge:tools/knives). */
public final class KnifeHelper {
    private static final TagKey<Item> KNIVES = ItemTags.create(new ResourceLocation("forge", "tools/knives"));

    private KnifeHelper() {}

    public static boolean isKnife(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIVES)) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null
                && id.getNamespace().equals("farmersdelight")
                && id.getPath().endsWith("_knife");
    }

    /** Шанс додаткового предмета за розріз залежно від матеріалу ножа. */
    public static float extraLootChance(ItemStack stack) {
        if (!(stack.getItem() instanceof TieredItem tiered)) return 0f;
        Tier tier = tiered.getTier();
        if (tier == Tiers.NETHERITE) return 0.40f;
        if (tier == Tiers.DIAMOND) return 0.30f;
        if (tier == Tiers.GOLD) return 0.20f;
        if (tier == Tiers.IRON) return 0.10f;
        if (tier == Tiers.WOOD || tier == Tiers.STONE) return 0f;
        // ножі з інших модів: за міцністю матеріалу
        int uses = tier.getUses();
        if (uses >= 1800) return 0.40f;
        if (uses >= 1000) return 0.30f;
        if (uses >= 200) return 0.10f;
        return 0f;
    }

    /** Скільки "ударів" (утримання ПКМ) треба на один шматок. Кращий ніж ріже швидше. */
    public static int cutSteps(ItemStack stack) {
        float chance = extraLootChance(stack);
        if (chance >= 0.40f) return 3;
        if (chance >= 0.30f) return 4;
        if (chance >= 0.20f) return 5;
        if (chance >= 0.10f) return 6;
        return 8;
    }
}
