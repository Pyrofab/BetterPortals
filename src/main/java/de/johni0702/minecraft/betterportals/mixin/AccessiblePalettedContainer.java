package de.johni0702.minecraft.betterportals.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

@Mixin(PalettedContainer.class)
public interface AccessiblePalettedContainer<T> {
    @Accessor
    Palette<T> getFallbackPalette();
    @Accessor
    Palette<T> getIdList();
    @Accessor
    Function<CompoundTag, T> getElementDeserializer();
    @Accessor
    Function<T, CompoundTag> getElementSerializer();
    @Accessor("field_12935")
    T getDefaultState();
}
