package com.aurorion.limbo.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Streams the local OGG, repeats short test tracks and fades into silence after the final title. */
final class FinaleMusic extends AbstractTickableSoundInstance {
    private int age;
    FinaleMusic(ResourceLocation event) {
        super(SoundEvent.createVariableRangeEvent(event), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        looping = true;
        delay = 0;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = .01F;
    }
    @Override public void tick() {
        if (!ClientFinale.active()) { stop(); return; }
        age++;
        double afterTitle = ClientFinale.elapsedMillis() - ClientFinale.script().deathTitleMillis();
        if (afterTitle >= 10_000) { stop(); return; }
        volume = .8F * Math.min(1F, age / 100F) * (float) Math.clamp(1 - afterTitle / 10_000, 0, 1);
    }
}
