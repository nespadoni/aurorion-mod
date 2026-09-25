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
    /**
     * A batida de 100 bpm da Presenca Aterradora. Toca em laço, so no cliente de quem esta com medo,
     * e o volume acompanha a distancia ate quem carrega a aura — nao ha pacote de som nenhum.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = register("dread.heartbeat");
    private MagiaSounds() { }
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AurorionMagia.id(name)));
    }
}
