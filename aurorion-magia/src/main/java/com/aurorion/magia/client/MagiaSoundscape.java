package com.aurorion.magia.client;

import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Local effect transitions. One heartbeat, one whisper layer, no packet or per-tick allocation. */
final class MagiaSoundscape {
    private static int previous, heartbeat, whisperDelay, caveDelay, kneelDelay;
    private static LocalPlayer owner;
    private static ClientLevel world;
    private static Cue cue;
    private MagiaSoundscape() { }

    static void tick(LocalPlayer player) {
        var mc = Minecraft.getInstance();
        if (owner != player || world != mc.level) { clear(); owner = player; world = mc.level; }
        if (!player.isAlive() || player.isSpectator()) { clear(); return; }
        int state = (player.hasEffect(MagiaEffects.CRUCIATUS) ? 1 : 0)
                | (player.hasEffect(MagiaEffects.DISORIENTED) ? 2 : 0)
                | (player.hasEffect(MagiaEffects.CAPTIVE) ? 4 : 0)
                | (player.hasEffect(MagiaEffects.KNEELING) ? 8 : 0);
        if (state == 0) {
            if (previous != 0) play(MagiaSounds.RELEASE.get(), .16F, 1.05F, false);
            previous = 0; heartbeat = 0; whisperDelay = 0; caveDelay = 0; kneelDelay = 0;
            return;
        }
        if ((state & 5) != 0 && --heartbeat <= 0) {
            boolean pain = (state & 1) != 0;
            player.playSound(SoundEvents.WARDEN_HEARTBEAT, pain ? .35F : .25F, pain ? 1.3F : .7F);
            heartbeat = pain ? 10 : 26;
        }
        if ((state & 6) != 0 && --whisperDelay <= 0) {
            play(MagiaSounds.MIND.get(), .11F, .96F + player.getRandom().nextFloat() * .08F, true);
            whisperDelay = 320 + player.getRandom().nextInt(241);
        }
        if ((state & 2) != 0 && --caveDelay <= 0) {
            // Preserve the old cave cue, spacing it so its long tail cannot stack every 2.5 seconds.
            if ((previous & 2) != 0) player.playSound(SoundEvents.AMBIENT_CAVE.value(), .18F, .85F);
            caveDelay = 400 + player.getRandom().nextInt(201);
        }
        if ((state & 8) != 0 && --kneelDelay <= 0) {
            player.playSound(SoundEvents.SOUL_ESCAPE.value(), .25F, .7F);
            kneelDelay = 100;
        }
        previous = state;
    }

    private static void play(SoundEvent event, float gain, float tone, boolean mind) {
        var mc = Minecraft.getInstance();
        if (cue != null) mc.getSoundManager().stop(cue);
        cue = new Cue(event, gain, tone, mind, mc.player, mc.level);
        mc.getSoundManager().play(cue);
    }

    static void clear() {
        if (cue != null) Minecraft.getInstance().getSoundManager().stop(cue);
        cue = null; owner = null; world = null; previous = 0;
        heartbeat = 0; whisperDelay = 0; caveDelay = 0; kneelDelay = 0;
    }

    private static final class Cue extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private final ClientLevel level;
        private final boolean mind;
        private final float gain;
        private int age, fade;
        Cue(SoundEvent sound, float gain, float tone, boolean mind, LocalPlayer player, ClientLevel level) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player; this.level = level; this.mind = mind; this.gain = gain;
            volume = gain; pitch = tone; relative = true; attenuation = SoundInstance.Attenuation.NONE;
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (mc.player != player || mc.level != level || player == null || !player.isAlive() || ++age > 160) { stop(); return; }
            if (mind && !player.hasEffect(MagiaEffects.DISORIENTED) && !player.hasEffect(MagiaEffects.CAPTIVE)) {
                volume = gain * (1F - ++fade / 8F);
                if (fade >= 8) stop();
            }
        }
    }
}
