package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Faiscas locais; os circulos e as runas usam geometria para continuarem visiveis de longe. */
final class RiteParticles {
    private RiteParticles() {}

    static void tick(RiteClient.Rite rite) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        int tick = rite.tick();
        if (tick < BindingRite.INTRO_TICKS) return;
        double x = rite.x(1), y = rite.y(1), z = rite.z(1);
        if (tick == BindingRite.REVEAL_TICK) {
            minecraft.level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8F, 0.65F, false);
        }
        if (minecraft.player.distanceToSqr(x, y, z) > 48 * 48) return;
        if (tick == BindingRite.REVEAL_TICK) {
            for (int i = 0; i < 48; i++) {
                double angle = i * Math.PI * 2 / 48;
                spawn(i % 3 == 0 ? rite.accentDust : rite.mainDust, x + Math.cos(angle) * 0.6,
                        y + 1.1, z + Math.sin(angle) * 0.6, Math.cos(angle) * 0.18, 0.08, Math.sin(angle) * 0.18);
            }
        }
        if (tick % 2 != 0 || rite.fade(0) < 0.1F) return;
        double spin = tick * 0.08;
        for (int i = 0; i < 3; i++) {
            double angle = spin + i * Math.PI * 2 / 3;
            double height = ((tick + i * 40) % 100) / 40.0;
            ParticleOptions dust = !rite.revealed() ? ParticleTypes.END_ROD
                    : i == 0 ? rite.mainDust : i == 1 ? rite.secondaryDust : rite.accentDust;
            spawn(dust, x + 1.65 * Math.cos(angle), y + height + 0.1,
                    z + 1.65 * Math.sin(angle), 0, 0.012, 0);
            if (rite.revealed()) {
                spawn(rite.mainDust, x + 0.3 * Math.cos(-angle), y + height * 2,
                        z + 0.3 * Math.sin(-angle), 0, 0.025, 0);
            }
        }
    }

    private static void spawn(ParticleOptions particle, double x, double y, double z,
                              double vx, double vy, double vz) {
        Minecraft.getInstance().level.addParticle(particle, x, y, z, vx, vy, vz);
    }
}
