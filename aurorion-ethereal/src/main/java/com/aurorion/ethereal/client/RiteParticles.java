package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Faiscas locais; os circulos e as runas usam geometria para continuarem visiveis de longe. */
final class RiteParticles {
    /** Intervalo entre salvas de fogos, em ticks. */
    private static final int SHELL_PERIOD = 26;
    /** Quanto a bomba sobe antes de abrir. */
    private static final int SHELL_RISE = 13;
    /** Ultima abertura possivel, contada da revelacao: o ceu apaga antes da cena sumir. */
    private static final int SHELL_WINDOW =
            BindingRite.TOTAL_TICKS - BindingRite.REVEAL_TICK - BindingRite.FADE_TICKS;

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
        // Os fogos abrem alto e longe do palco, entao vem antes do corte de 48 blocos: quem assiste
        // do fundo perde as faiscas dos pes, mas nao perde o ceu.
        fireworks(minecraft, rite, x, y, z);
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

    /**
     * Fogos nas cores da casa, em volta e acima do palco, a partir da revelacao.
     *
     * <p>Cada bomba e sorteada a partir do id do escolhido e do seu numero de ordem — nunca de um
     * estado que ande junto com o tick. Por isso os trinta clientes abrem a mesma bomba no mesmo
     * ponto do ceu, e quem chega no meio da cena cai no mesmo espetaculo em vez de num paralelo.
     */
    private static void fireworks(Minecraft minecraft, RiteClient.Rite rite, double x, double y, double z) {
        int since = rite.tick() - BindingRite.REVEAL_TICK;
        if (since < 0) return;
        ParticleStatus status = minecraft.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;
        int phase = since % SHELL_PERIOD;
        if (phase != 0 && phase != SHELL_RISE) return;
        int shell = since / SHELL_PERIOD;
        if (shell * SHELL_PERIOD + SHELL_RISE > SHELL_WINDOW) return;
        // A revelacao merece salva tripla; o resto da coroacao segue em dupla.
        for (int i = 0, shells = shell == 0 ? 3 : 2; i < shells; i++) {
            RandomSource random = RandomSource.create(rite.seed + shell * 977L + i);
            double angle = random.nextDouble() * Math.PI * 2;
            double away = 7.5 + random.nextDouble() * 4.5;
            double bx = x + Math.cos(angle) * away, bz = z + Math.sin(angle) * away;
            double top = y + 7.5 + random.nextDouble() * 4;
            if (phase == 0) climb(minecraft, rite, random, bx, y + 1.2, bz);
            else open(minecraft, rite, random, bx, top, bz, status == ParticleStatus.DECREASED ? 36 : 76);
        }
    }

    private static void climb(Minecraft minecraft, RiteClient.Rite rite, RandomSource random,
                              double x, double y, double z) {
        minecraft.level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS, 1.4F, 1F, false);
        for (int i = 0; i < 10; i++) {
            spark(minecraft, i % 2 == 0 ? rite.color : rite.accent, x, y, z,
                    (random.nextDouble() - .5) * .04, .52 + random.nextDouble() * .1,
                    (random.nextDouble() - .5) * .04);
        }
    }

    private static void open(Minecraft minecraft, RiteClient.Rite rite, RandomSource random,
                             double x, double y, double z, int count) {
        minecraft.level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2F, .9F + random.nextFloat() * .2F, false);
        minecraft.level.playLocalSound(x, y, z, SoundEvents.FIREWORK_ROCKET_TWINKLE,
                SoundSource.PLAYERS, 1.2F, 1F, false);
        // Pelo motor, e nao por level.addParticle: aquele descarta tudo depois de 32 blocos, o que
        // apagaria os fogos justamente para quem assiste do fundo da plateia.
        minecraft.particleEngine.createParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
        for (int i = 0; i < count; i++) {
            // Espiral de Fibonacci: espalha os pontos pela esfera sem amontoar nos polos, que e o
            // que acontece quando se sorteia angulo e altura de forma independente.
            double cos = 1 - 2 * (i + .5) / count;
            double ring = Math.sqrt(1 - cos * cos);
            double angle = i * 2.399963229728653;
            double speed = .26 + random.nextDouble() * .12;
            int color = i % 5 == 0 ? rite.accent : i % 3 == 0 ? rite.secondary : rite.color;
            spark(minecraft, color, x, y, z,
                    Math.cos(angle) * ring * speed, cos * speed, Math.sin(angle) * ring * speed);
        }
    }

    /**
     * Faisca de foguete na cor da casa.
     *
     * <p>{@code addParticle} nao permite escolher a cor da faisca — quem tinge a particula do
     * foguete vanilla e a entidade, lendo o item. Criando a particula pelo motor recebe-se a
     * instancia de volta e a cor vira uma chamada direta, sem entidade nem item no meio.
     */
    private static void spark(Minecraft minecraft, int color, double x, double y, double z,
                              double vx, double vy, double vz) {
        Particle particle = minecraft.particleEngine.createParticle(ParticleTypes.FIREWORK, x, y, z, vx, vy, vz);
        if (particle != null) {
            particle.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        }
    }

    private static void spawn(ParticleOptions particle, double x, double y, double z,
                              double vx, double vy, double vz) {
        Minecraft.getInstance().level.addParticle(particle, x, y, z, vx, vy, vz);
    }
}
