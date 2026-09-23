package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagiaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AurorionMagia.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MIND = register("mind.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELEASE = register("spell.release");
    private MagiaSounds() { }
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AurorionMagia.id(name)));
    }
}
