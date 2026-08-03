package com.aurorion.utils.sound;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * O arquivo de audio em si NAO vem com o codigo: {@code sounds.json} aponta para
 * {@code aurorion_utils:abduction_beam}, e o .ogg correspondente entra depois em
 * {@code assets/aurorion_utils/sounds/abduction_beam.ogg} — soltar o arquivo la basta, sem
 * recompilar nada.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, AurorionUtils.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ABDUCTION_BEAM = SOUND_EVENTS.register(
            "abduction_beam",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(AurorionUtils.MOD_ID, "abduction_beam")));

    private ModSounds() {
    }
}
