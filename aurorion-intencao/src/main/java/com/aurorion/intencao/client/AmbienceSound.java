package com.aurorion.intencao.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * O som em laco do plano, sem posicao (toca "dentro da cabeca"). Entra e sai em fade: cortar seco
 * na hora do veredito quebraria a cena. Volume e tom seguem o clima escolhido pelo mestre.
 */
final class AmbienceSound extends AbstractTickableSoundInstance {
    private static final float STEP = .02F;

    AmbienceSound(ResourceLocation sound) {
        super(SoundEvent.createVariableRangeEvent(sound), SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = .01F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        float target = ClientIntencao.ambienceVolume();
        float pitchTarget = ClientIntencao.ambiencePitch();
        if (volume < target) volume = Math.min(target, volume + STEP);
        else if (volume > target) volume = Math.max(target, volume - STEP);
        pitch += (pitchTarget - pitch) * .05F;
        if (target <= 0 && volume <= .01F) stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
