package com.example.carcass;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CorpseBlockEntity extends BlockEntity {
    private final List<ItemStack> loot = new ArrayList<>();

    /** id типу моба (наприклад minecraft:cow), його поворот і дані для малювання на клієнті */
    private String mobType = "";
    private float yaw = 0f;
    private CompoundTag mobTag = new CompoundTag();

    /** Прогрес розрізання (не зберігається). */
    public int cutProgress = 0;
    public long lastCutTime = -100;

    /** Скільки шматків було в трупі спочатку (для зменшення тіла при розрізанні). */
    private int totalPieces = 1;

    public CorpseBlockEntity(BlockPos pos, BlockState state) {
        super(Carcass.CORPSE_BE.get(), pos, state);
    }

    public void setLoot(List<ItemStack> stacks, String mobType, float yaw, CompoundTag mobTag) {
        loot.clear();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) loot.add(s.copy());
        }
        this.totalPieces = Math.max(1, loot.size());
        this.mobType = mobType;
        this.yaw = yaw;
        this.mobTag = mobTag;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Скільки лута лишилось: 1.0 = повний труп, 0 = порожній. */
    public float getFill() {
        return Math.min(1f, loot.size() / (float) totalPieces);
    }

    /** Надсилає клієнтам стан розрізання (для анімації тремтіння). */
    public void syncCut() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<ItemStack> getLoot() {
        return loot;
    }

    public String getMobType() {
        return mobType;
    }

    public float getYaw() {
        return yaw;
    }

    public CompoundTag getMobTag() {
        return mobTag;
    }

    /** Забирає наступний шматок лута (один "розріз"). */
    public ItemStack takeNext() {
        if (loot.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = loot.remove(0);
        setChanged();
        return s;
    }

    public boolean isEmpty() {
        return loot.isEmpty();
    }

    private static boolean isFood(ItemStack s) {
        return s.getItem().isEdible();
    }

    /** Псує їжу в трупі: на стадії 1 частину, на стадії 2 всю (перетворюється на гниле м'ясо). */
    public void rot(int stage, RandomSource random) {
        for (int i = 0; i < loot.size(); i++) {
            ItemStack s = loot.get(i);
            if (!isFood(s) || s.is(Items.ROTTEN_FLESH)) continue;
            if (stage >= 2 || random.nextFloat() < 0.4f) {
                loot.set(i, new ItemStack(Items.ROTTEN_FLESH, s.getCount()));
            }
        }
        setChanged();
    }

    /** Повне розкладання: уся їжа зникає, неїстівне (кістки, шкіра тощо) лишається. */
    public void removeFood() {
        loot.removeIf(CorpseBlockEntity::isFood);
        setChanged();
    }

    // ---------- Синхронізація з клієнтом (щоб намалювати моба) ----------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putInt("CutProgress", cutProgress);
        tag.putLong("CutTime", lastCutTime);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (ItemStack s : loot) {
            list.add(s.save(new CompoundTag()));
        }
        tag.put("Loot", list);
        tag.putString("MobType", mobType);
        tag.putFloat("Yaw", yaw);
        tag.put("MobTag", mobTag);
        tag.putInt("Total", totalPieces);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loot.clear();
        ListTag list = tag.getList("Loot", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.of(list.getCompound(i));
            if (!s.isEmpty()) loot.add(s);
        }
        mobType = tag.getString("MobType");
        yaw = tag.getFloat("Yaw");
        mobTag = tag.contains("MobTag", Tag.TAG_COMPOUND) ? tag.getCompound("MobTag") : new CompoundTag();
        totalPieces = Math.max(1, tag.contains("Total") ? tag.getInt("Total") : loot.size());
        if (tag.contains("CutProgress")) cutProgress = tag.getInt("CutProgress");
        if (tag.contains("CutTime")) lastCutTime = tag.getLong("CutTime");
    }
}
