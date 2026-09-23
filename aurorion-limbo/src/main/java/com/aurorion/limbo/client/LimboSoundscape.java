package com.aurorion.limbo.client;

import com.aurorion.limbo.network.LimboNoticePayload;
import com.aurorion.limbo.registry.LimboSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** One quiet local layer; notices already carry everything needed, with no extra network traffic. */
final class LimboSoundscape {
    private static final ResourceLocation LIMBO = ResourceLocation.parse("aurorion_limbo:limbo");
    private static Cue cue;
    private static final long[] LAST_NOTICE = new long[13];
    private static boolean nearDoor;
    private static BlockPos lastDoor = BlockPos.ZERO;
    private LimboSoundscape() { }

    static void notice(int kind) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ClientFinale.active()) return;
        // O ClientLimbo ja confere o tipo, mas este array nao deve depender de outra classe lembrar.
        if (kind < 0 || kind >= LAST_NOTICE.length) return;
        long now = System.nanoTime();
        if (LAST_NOTICE[kind] != 0 && now - LAST_NOTICE[kind] < 2_000_000_000L) return;
        LAST_NOTICE[kind] = now;
        switch (kind) {
            case LimboNoticePayload.ARRIVAL, LimboNoticePayload.PASSAGE_CROSSED,
                    LimboNoticePayload.ESCAPED -> play(LimboSounds.VEIL.get(), .16F, .92F);
            case LimboNoticePayload.DOOR, LimboNoticePayload.RESCUED,
                    LimboNoticePayload.BOND_USED -> play(LimboSounds.GUIDANCE.get(), .13F, 1F);
            case LimboNoticePayload.WINDOW -> play(LimboSounds.WHISPER.get(), .09F, 1F);
            default -> { } // Public notices, leash, deadline and finale keep their existing sound design.
        }
    }

    static void tick(int stage, boolean active, BlockPos door) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (mc.player == null || mc.level == null || ClientFinale.active()) { clear(); return; }
        if (!active || stage != 2 || !mc.level.dimension().location().equals(LIMBO)) {
            nearDoor = false; return;
        }
        if (!lastDoor.equals(door)) { lastDoor = door; nearDoor = false; }
        double distance = mc.player.distanceToSqr(door.getX() + .5, door.getY() + 1, door.getZ() + .5);
        if (distance > 20 * 20) nearDoor = false;
        if (distance < 10 * 10 && !nearDoor) {
            nearDoor = true;
            if (cue == null || !mc.getSoundManager().isActive(cue)) play(LimboSounds.GUIDANCE.get(), .09F, 1.08F);
        }
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (cue != null) mc.getSoundManager().stop(cue);
        cue = new Cue(sound, volume, pitch, mc.level);
        mc.getSoundManager().play(cue);
    }

    static void clear() {
        if (cue != null) Minecraft.getInstance().getSoundManager().stop(cue);
        cue = null; nearDoor = false; lastDoor = BlockPos.ZERO;
        java.util.Arrays.fill(LAST_NOTICE, 0);
    }

    private static final class Cue extends AbstractTickableSoundInstance {
        private final ClientLevel world;
        private int age;
        Cue(SoundEvent sound, float gain, float tone, ClientLevel world) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.world = world; volume = gain; pitch = tone;
            relative = true; attenuation = SoundInstance.Attenuation.NONE;
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (mc.level != world || mc.player == null || ClientFinale.active() || ++age > 180) stop();
        }
    }
}
