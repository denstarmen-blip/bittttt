package com.example.carcass;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Carcass.MODID)
public class Carcass {
    public static final String MODID = "carcass";

    /**
     * Скільки тіків триває кожна стадія гниття трупа (20 тіків = 1 секунда).
     * 6000 тіків = 5 хвилин. Три стадії: свіжий -> гниє -> згнилий -> зникає (разом ~15 хвилин).
     */
    public static final int ROT_STAGE_TICKS = 6000;

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    public static final RegistryObject<Block> CORPSE = BLOCKS.register("corpse",
            () -> new CorpseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.0f)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<BlockEntityType<CorpseBlockEntity>> CORPSE_BE =
            BLOCK_ENTITIES.register("corpse",
                    () -> BlockEntityType.Builder.of(CorpseBlockEntity::new, CORPSE.get()).build(null));

    public Carcass() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MinecraftForge.EVENT_BUS.register(new DeathHandler());
    }

    /** Шукає місце на землі поруч із точкою смерті. Повертає null, якщо не знайшов. */
    public static BlockPos findSpot(ServerLevel level, BlockPos start) {
        BlockPos p = start;
        Block corpse = CORPSE.get();
        for (int i = 0; i < 12; i++) {
            BlockState here = level.getBlockState(p);
            if (here.canBeReplaced()) {
                if (corpse.defaultBlockState().canSurvive(level, p)) return p;
                p = p.below();
            } else {
                p = p.above();
            }
        }
        return null;
    }
}
