package com.aurorion.ethereal.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * OGG em streaming, volume uniforme na plateia e envelope sincronizado com a cena.
 *
 * <p>Uma cena pode ter duas: a trilha neutra do inicio e, se a casa usa outra, a dela a partir da
 * revelacao. A que sai e "aposentada" e some num fade em vez de cortar.
 */
final class RiteMusic extends AbstractTickableSoundInstance {
    private final RiteClient.Rite rite;
    private final ResourceLocation sound;
    /** Tick da cena em que esta trilha entrou, e quantos ticks ela leva para chegar ao volume cheio. */
    private final int startTick, rampTicks;
    private int retireTick = -1, retireTicks = 1;

    RiteMusic(ResourceLocation sound, RiteClient.Rite rite, int startTick, int rampTicks) {
        super(SoundEvent.createVariableRangeEvent(sound), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.rite = rite;
        this.sound = sound;
        this.startTick = startTick;
        this.rampTicks = Math.max(1, rampTicks);
        relative = true;
        attenuation = Attenuation.NONE;
        looping = false;
        volume = 0.01F;
    }

    ResourceLocation sound() { return sound; }

    /** Comeca a sumir agora; para sozinha quando o fade acaba. */
    void retire(int ticks) {
        if (retireTick < 0) {
            retireTick = rite.tick();
            retireTicks = Math.max(1, ticks);
        }
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        if (RiteClient.current() != rite) {
            stop();
            return;
        }
        float out = 1F;
        if (retireTick >= 0) {
            out = 1F - (rite.tick() - retireTick) / (float) retireTicks;
            if (out <= 0F) {
                stop();
                return;
            }
        }
        volume = 0.7F * Mth.clamp((rite.tick() - startTick) / (float) rampTicks, 0, 1) * rite.fade(0) * out;
    }
}
