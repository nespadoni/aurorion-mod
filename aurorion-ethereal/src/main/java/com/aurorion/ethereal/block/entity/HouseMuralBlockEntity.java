package com.aurorion.ethereal.block.entity;

import com.aurorion.ethereal.registry.EtherealBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Guarda somente a Casa vinculada; saldo e upgrades são globais por Casa. */
public final class HouseMuralBlockEntity extends BlockEntity {
    @Nullable private ResourceLocation house;

    public HouseMuralBlockEntity(BlockPos pos, BlockState state) {
        super(EtherealBlockEntities.HOUSE_MURAL.get(), pos, state);
    }

    @Nullable
    public ResourceLocation house() {
        return house;
    }

    public void link(ResourceLocation house) {
        this.house = house;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (house != null) tag.putString("House", house.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        house = ResourceLocation.tryParse(tag.getString("House"));
    }
}
