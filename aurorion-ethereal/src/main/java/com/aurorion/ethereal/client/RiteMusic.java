package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** OGG em streaming, volume uniforme na plateia e envelope sincronizado com a cena. */
final class RiteMusic extends AbstractTickableSoundInstance {
    private final RiteClient.Rite rite;

    RiteMusic(ResourceLocation sound, RiteClient.Rite rite) {
        super(SoundEvent.createVariableRangeEvent(sound), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.rite = rite;
        relative = true;
        attenuation = Attenuation.NONE;
        looping = false;
        volume = 0.01F;
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        if (RiteClient.current() != rite) {
            stop();
            return;
        }
        volume = 0.7F * Mth.clamp(rite.tick() / (float) BindingRite.INTRO_TICKS, 0, 1) * rite.fade(0);
    }
}
