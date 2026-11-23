package com.example.carcass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CorpseBlock extends BaseEntityBlock {
    /** 0 = свіжий, 1 = гниє, 2 = згнилий */
    public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, 2);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public CorpseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock за замовчуванням невидимий
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CorpseBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // ---------- Гниття ----------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            level.scheduleTick(pos, this, Carcass.ROT_STAGE_TICKS);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CorpseBlockEntity corpse = level.getBlockEntity(pos) instanceof CorpseBlockEntity c ? c : null;
        int rot = state.getValue(ROT);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;

        if (rot < 2) {
            int next = rot + 1;
            if (corpse != null) corpse.rot(next, random);
            level.setBlock(pos, state.setValue(ROT, next), 3);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_STEP, SoundSource.BLOCKS, 0.8f, 0.5f);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 12, 0.3, 0.2, 0.3, 0.02);
            level.scheduleTick(pos, this, Carcass.ROT_STAGE_TICKS);
        } else {
            // повне розкладання: їжа зникає, кістки та інше неїстівне лишається
            if (corpse != null) corpse.removeFood();
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 0.5f);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 20, 0.3, 0.2, 0.3, 0.02);
            level.removeBlock(pos, false); // onRemove викине те, що лишилось
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int rot = state.getValue(ROT);
        if (rot > 0 && random.nextInt(rot == 1 ? 12 : 5) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6,
                    pos.getY() + 0.4,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.02, 0.0);
        }
    }

    // ---------- Розрізання ----------

    public static boolean isCuttingTool(ItemStack stack) {
        return KnifeHelper.isKnife(stack);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack tool = player.getItemInHand(hand);

        if (!isCuttingTool(tool)) {
            // ніж у лівій руці: пропускаємо, щоб спрацювала ліва рука
            if (hand == InteractionHand.MAIN_HAND && isCuttingTool(player.getOffhandItem())) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.carcass.need_tool"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS; // рука махає

        if (!(level.getBlockEntity(pos) instanceof CorpseBlockEntity be)) return InteractionResult.SUCCESS;
        ServerLevel serverLevel = (ServerLevel) level;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;

        // Утримання ПКМ повторює use кожні ~4 тіки. Пауза довша за 10 тіків скидає прогрес.
        long now = level.getGameTime();
        if (now - be.lastCutTime > 10) be.cutProgress = 0;
        be.lastCutTime = now;
        be.cutProgress++;
        be.syncCut();

        int steps = KnifeHelper.cutSteps(tool);
        int rot = state.getValue(ROT);

        if (be.cutProgress < steps) {
            // проміжний "удар": звук, бризки, шкала прогресу
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.BLOCKS,
                    0.7f, 0.9f + level.random.nextFloat() * 0.3f);
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    x, y, z, 4, 0.25, 0.1, 0.25, 0.03);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);

            if (be.cutProgress == 1 && rot > 0) {
                player.displayClientMessage(Component.translatable("message.carcass.rot" + rot), true);
            } else {
                String bar = "\u25AE".repeat(be.cutProgress) + "\u25AF".repeat(steps - be.cutProgress);
                player.displayClientMessage(Component.literal(bar), true);
            }
            return InteractionResult.SUCCESS;
        }

        // шматок відрізано
        be.cutProgress = 0;
        ItemStack piece = be.takeNext();
        be.syncCut();
        if (!piece.isEmpty()) {
            Block.popResource(level, pos, piece);

            // Кращий ніж: шанс отримати ще один такий самий предмет
            if (level.random.nextFloat() < KnifeHelper.extraLootChance(tool)) {
                ItemStack extra = piece.copy();
                extra.setCount(1);
                Block.popResource(level, pos, extra);
            }

            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS,
                    0.8f, 0.8f + level.random.nextFloat() * 0.4f);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.7f, 0.7f);
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    x, y, z, 16, 0.3, 0.15, 0.3, 0.05);

            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));

            if (be.isEmpty()) {
                level.removeBlock(pos, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Якщо труп зламали руками чи прибрали блок під ним, лут не губиться. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CorpseBlockEntity be) {
                for (ItemStack s : be.getLoot()) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, s.copy()));
                }
                be.getLoot().clear();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
