package com.aurorion.magia.client;

import com.aurorion.magia.registry.MagiaSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * O que os nos soltam no ar a cada tick: fogo, brasas, fumaça, vultos, faiscas, e os sons. Tudo local,
 * nada volta ao servidor.
 *
 * <p>Custo limitado por construcao: so roda para o no na dimensao de quem olha e dentro do alcance, poucas
 * particulas por tick, e respeita "Particulas: minimo/reduzido" do vanilla. Os sons sao avulsos, com
 * intervalo — nenhum laço que possa ficar preso tocando.
 */
final class NodeAmbience {
    /** Perto o bastante para as particulas valerem; alem disso, so a geometria do renderer aparece. */
    private static final double NEAR = 56;

    private static int whisperDelay, heartDelay, figureDelay;
    private static long lastBolt = Long.MIN_VALUE;

    private NodeAmbience() {
    }

    static void tick(Minecraft minecraft, ClientLevel level, LocalPlayer player, NodeClient.Scene scene) {
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double distanceSqr = scene.distanceSqr(camera);
        if (distanceSqr > scene.reach * scene.reach || scene.fade(level, 0) < .05F) return;
        long life = level.getGameTime() - scene.start;
        RandomSource random = level.random;
        ParticleStatus status = minecraft.options.particles().get();
        boolean near = distanceSqr < NEAR * NEAR;
        boolean opening = !scene.opened && scene.ending < 0 && life < NodeClient.OPENING;
        if (opening) scene.opened = true;
        switch (scene.index) {
            case 1 -> fire(minecraft, level, scene, life, random, status, near, opening);
            case 2 -> fear(minecraft, level, player, camera, scene, life, random, status, near, opening);
            default -> expanse(minecraft, level, scene, life, random, status, near, opening);
        }
    }

    // --- No 1: circulo de fogo ------------------------------------------------------------------

    private static void fire(Minecraft minecraft, ClientLevel level, NodeClient.Scene s, long life, RandomSource random,
                             ParticleStatus status, boolean near, boolean opening) {
        if (opening) {
            sound(level, s.x, s.y + 1, s.z, SoundEvents.BEACON_ACTIVATE, 1.4F, .7F);
            sound(level, s.x, s.y + 1, s.z, SoundEvents.BLAZE_SHOOT, 1.6F, .6F);
            sound(level, s.x, s.y + 1, s.z, SoundEvents.FIRECHARGE_USE, 1.6F, .8F);
            for (int i = 0; i < 64; i++) {
                double angle = i * Math.PI * 2 / 64;
                far(minecraft, ParticleTypes.FLAME, s.x + Math.cos(angle) * 1.2, s.y + .3, s.z + Math.sin(angle) * 1.2,
                        Math.cos(angle) * .32, .06, Math.sin(angle) * .32);
            }
            for (int i = 0; i < 20; i++) far(minecraft, ParticleTypes.LAVA, s.x, s.y + .5, s.z, 0, 0, 0);
        }
        if (life % 34 == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            sound(level, s.x + Math.cos(angle) * 4, s.y + .5, s.z + Math.sin(angle) * 4, SoundEvents.FIRE_AMBIENT, 1.1F,
                    .8F + random.nextFloat() * .3F);
        }
        if (life % 90 == 0) sound(level, s.x, s.y + 1, s.z, SoundEvents.BEACON_AMBIENT, .8F, .6F);
        if (!near || status == ParticleStatus.MINIMAL) return;
        boolean full = status == ParticleStatus.ALL;

        // Anel de chamas na borda do circulo.
        for (int i = 0, n = full ? 3 : 1; i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            spawn(level, ParticleTypes.FLAME, s.x + Math.cos(angle) * 4, s.y + .1, s.z + Math.sin(angle) * 4,
                    0, .03 + random.nextDouble() * .05, 0);
        }
        // A coluna do centro sobe girando.
        double spin = life * .3;
        for (int i = 0; i < 2; i++) {
            double angle = spin + i * Math.PI;
            spawn(level, i == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                    s.x + Math.cos(angle) * .35, s.y + .2, s.z + Math.sin(angle) * .35, 0, .12 + random.nextDouble() * .05, 0);
        }
        // As quatro chamas que orbitam: mesmas contas do renderer, entao a chama cai em cima da esfera.
        for (int i = 0; i < 4; i++) {
            double angle = life * .04 + i * Math.PI / 2;
            double height = 1.8 + .35 * Math.sin(life * .1 + i);
            spawn(level, ParticleTypes.SMALL_FLAME, s.x + Math.cos(angle) * 2.6, s.y + height, s.z + Math.sin(angle) * 2.6,
                    0, .01, 0);
        }
        if (life % 5 == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            spawn(level, ParticleTypes.LAVA, s.x + Math.cos(angle) * 3.8, s.y + .2, s.z + Math.sin(angle) * 3.8, 0, 0, 0);
        }
        if (full && life % 14 == 0) {
            spawn(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, s.x, s.y + 5.5, s.z, 0, .03, 0);
        }
    }

    // --- No 2: o medo ---------------------------------------------------------------------------

    private static void fear(Minecraft minecraft, ClientLevel level, LocalPlayer player, Vec3 camera, NodeClient.Scene s,
                             long life, RandomSource random, ParticleStatus status, boolean near, boolean opening) {
        if (opening) {
            sound(level, s.x, s.y + 1, s.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2F, .55F);
            sound(level, s.x, s.y + 1, s.z, SoundEvents.AMBIENT_CAVE.value(), 2F, .6F);
        }
        bolt(minecraft, level, camera, s, life, random, status);

        float mind = NodeClient.mind(level, s, camera);
        if (mind > 0) {
            whisper(level, camera, random, mind);
            if (mind > .35F && --heartDelay <= 0) {
                player.playSound(SoundEvents.WARDEN_HEARTBEAT, .25F + .4F * mind, .8F);
                heartDelay = mind > .7F ? 18 : 28;
            }
            if (status != ParticleStatus.MINIMAL && --figureDelay <= 0) {
                figure(level, player, s, random);
                figureDelay = 40 + random.nextInt(50);
            }
        } else {
            whisperDelay = Math.min(whisperDelay, 40);
        }

        if (!near || status == ParticleStatus.MINIMAL) return;
        // A poça respira fumaça; das rachaduras sobem almas.
        if (life % 2 == 0) {
            double angle = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 7;
            spawn(level, ParticleTypes.SMOKE, s.x + Math.cos(angle) * r, s.y + .05, s.z + Math.sin(angle) * r,
                    0, .01 + random.nextDouble() * .02, 0);
        }
        if (life % 5 == 0) {
            double angle = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 5;
            spawn(level, ParticleTypes.SCULK_SOUL, s.x + Math.cos(angle) * r, s.y + .1, s.z + Math.sin(angle) * r,
                    0, .03, 0);
        }
    }

    /**
     * O raio da vez: trovao no ponto em que caiu, o ceu pisca para quem esta perto e faisca no chao. A
     * geometria do proprio raio e do renderer, que le o mesmo agendamento ({@link NodeClient#activeBolt}).
     */
    private static void bolt(Minecraft minecraft, ClientLevel level, Vec3 camera, NodeClient.Scene s, long life,
                             RandomSource random, ParticleStatus status) {
        long n = NodeClient.activeBolt(s, life);
        if (n == Long.MIN_VALUE || n == lastBolt) return;
        lastBolt = n;
        float angle = NodeClient.boltAngle(s, n);
        float distance = NodeClient.boltDistance(s, n);
        double x = s.x + Mth.cos(angle) * distance, z = s.z + Mth.sin(angle) * distance;
        sound(level, x, s.y + 2, z, SoundEvents.LIGHTNING_BOLT_THUNDER, 3F, .75F + random.nextFloat() * .3F);
        sound(level, x, s.y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, 2F, .6F + random.nextFloat() * .2F);
        if (camera.distanceToSqr(x, s.y, z) < 64 * 64) level.setSkyFlashTime(2);
        if (status == ParticleStatus.MINIMAL) return;
        for (int i = 0; i < 28; i++) {
            far(minecraft, ParticleTypes.ELECTRIC_SPARK, x, s.y + .2, z,
                    (random.nextDouble() - .5) * .6, random.nextDouble() * .4, (random.nextDouble() - .5) * .6);
        }
        for (int i = 0; i < 6; i++) {
            far(minecraft, ParticleTypes.LARGE_SMOKE, x, s.y + .3, z, (random.nextDouble() - .5) * .05, .06, (random.nextDouble() - .5) * .05);
        }
    }

    /** Vozes que nao vem de lugar nenhum: cada uma de uma direcao diferente, colada na cabeca. */
    private static void whisper(ClientLevel level, Vec3 camera, RandomSource random, float mind) {
        if (--whisperDelay > 0) return;
        whisperDelay = (int) ((60 + random.nextInt(120)) * (1.25F - .6F * mind));
        double yaw = random.nextDouble() * Math.PI * 2;
        double away = 2.5 + random.nextDouble() * 2;
        double x = camera.x + Math.cos(yaw) * away, y = camera.y + (random.nextDouble() - .3), z = camera.z + Math.sin(yaw) * away;
        float volume = .35F + .5F * mind;
        switch (random.nextInt(6)) {
            case 0 -> sound(level, x, y, z, MagiaSounds.MIND.get(), volume, .9F + random.nextFloat() * .15F);
            case 1 -> sound(level, x, y, z, SoundEvents.VEX_AMBIENT, volume, .4F + random.nextFloat() * .15F);
            case 2 -> sound(level, x, y, z, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, volume * .8F, .45F);
            case 3 -> sound(level, x, y, z, SoundEvents.EVOKER_PREPARE_SUMMON, volume * .6F, .55F);
            case 4 -> sound(level, x, y, z, SoundEvents.SOUL_ESCAPE.value(), volume, .6F + random.nextFloat() * .2F);
            default -> sound(level, x, y, z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), volume, .8F);
        }
    }

    /**
     * Um vulto no canto do olho: uma coluna de tinta preta, da altura de uma pessoa, que se desfaz. Nasce
     * a frente e de lado de quem esta no medo, nunca em cima dele, e no chao do no.
     */
    private static void figure(ClientLevel level, LocalPlayer player, NodeClient.Scene s, RandomSource random) {
        double yaw = Math.toRadians(player.getYRot() + 90 + (random.nextBoolean() ? 1 : -1) * (35 + random.nextDouble() * 45));
        double away = 7 + random.nextDouble() * 7;
        double x = player.getX() + Math.cos(yaw) * away, z = player.getZ() + Math.sin(yaw) * away;
        for (int i = 0; i < 9; i++) {
            spawn(level, ParticleTypes.SQUID_INK, x + (random.nextDouble() - .5) * .25, s.y + i * .21,
                    z + (random.nextDouble() - .5) * .25, 0, .004, 0);
        }
        spawn(level, ParticleTypes.LARGE_SMOKE, x, s.y + 1, z, 0, .01, 0);
        sound(level, x, s.y + 1.5, z, SoundEvents.SOUL_ESCAPE.value(), .5F, .5F);
    }

    // --- No 3: a expansao ------------------------------------------------------------------------

    private static final int[] AURORA = {0x5CF2C6, 0x7AA8FF, 0xC58BFF, 0xF4FBFF};

    private static void expanse(Minecraft minecraft, ClientLevel level, NodeClient.Scene s, long life,
                                RandomSource random, ParticleStatus status, boolean near, boolean opening) {
        if (opening) {
            sound(level, s.x, s.y + 2, s.z, SoundEvents.END_PORTAL_SPAWN, 1.2F, 1.1F);
            sound(level, s.x, s.y + 2, s.z, SoundEvents.BEACON_ACTIVATE, 2F, 1.2F);
            sound(level, s.x, s.y + 2, s.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 2F, .7F);
            far(minecraft, ParticleTypes.FLASH, s.x, s.y + 2, s.z, 0, 0, 0);
            for (int i = 0; i < 140; i++) {
                double cos = 1 - 2 * (i + .5) / 140, ring = Math.sqrt(1 - cos * cos), angle = i * 2.399963229728653;
                far(minecraft, ParticleTypes.END_ROD, s.x, s.y + 2, s.z,
                        Math.cos(angle) * ring * .55, cos * .55, Math.sin(angle) * ring * .55);
            }
        }
        long since = life - NodeClient.OPENING;
        if (since >= 0 && since % NodeRenderer.WAVE_PERIOD == 0) {
            sound(level, s.x, s.y + 1, s.z, SoundEvents.AMETHYST_BLOCK_RESONATE, 2F, .9F);
        }
        if (life % 37 == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            sound(level, s.x + Math.cos(angle) * 10, s.y + 2, s.z + Math.sin(angle) * 10, SoundEvents.AMETHYST_BLOCK_CHIME,
                    1.6F, .6F + random.nextFloat() * .8F);
        }
        if (life % 80 == 0) sound(level, s.x, s.y + 2, s.z, SoundEvents.BEACON_AMBIENT, 1.2F, 1.25F);
        if (status == ParticleStatus.MINIMAL) return;
        boolean full = status == ParticleStatus.ALL;

        // Brilhos caindo da aurora: pelo motor, para quem olha de longe nao perder o ceu.
        if (life % (full ? 2 : 4) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = NodeRenderer.AURORA_RADIUS + (random.nextDouble() - .5) * 4;
            colored(minecraft, AURORA[random.nextInt(AURORA.length)], s.x + Math.cos(angle) * r,
                    s.y + NodeRenderer.AURORA_BASE + random.nextDouble() * 10, s.z + Math.sin(angle) * r, 0, -.06, 0);
        }
        // As oito esferas deixam rastro; mesmas contas do renderer.
        if (life % 2 == 0) {
            for (int i = 0; i < NodeRenderer.ORBS; i++) {
                double angle = life * .02 + i * Math.PI * 2 / NodeRenderer.ORBS;
                double height = 3 + 1.5 * Math.sin(life * .05 + i);
                far(minecraft, ParticleTypes.END_ROD, s.x + Math.cos(angle) * 9, s.y + height, s.z + Math.sin(angle) * 9,
                        0, -.01, 0);
            }
        }
        if (!near) return;
        for (int i = 0, n = full ? 2 : 1; i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 16;
            spawn(level, ParticleTypes.END_ROD, s.x + Math.cos(angle) * r, s.y + .2 + random.nextDouble() * 2.5,
                    s.z + Math.sin(angle) * r, 0, .02 + random.nextDouble() * .03, 0);
        }
        spawn(level, ParticleTypes.GLOW, s.x + (random.nextDouble() - .5), s.y + .5, s.z + (random.nextDouble() - .5),
                0, .25, 0);
    }

    // --- Ajudantes -------------------------------------------------------------------------------

    private static void sound(ClientLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    /** {@code addParticle}: descartada alem de 32 blocos, que e o certo para o que e miudo. */
    private static void spawn(ClientLevel level, ParticleOptions particle, double x, double y, double z,
                              double vx, double vy, double vz) {
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }

    /** Pelo motor, sem o corte de 32 blocos: o que tem que ser visto do fundo da plateia. */
    private static void far(Minecraft minecraft, ParticleOptions particle, double x, double y, double z,
                            double vx, double vy, double vz) {
        minecraft.particleEngine.createParticle(particle, x, y, z, vx, vy, vz);
    }

    private static void colored(Minecraft minecraft, int color, double x, double y, double z, double vx, double vy, double vz) {
        Particle particle = minecraft.particleEngine.createParticle(ParticleTypes.FIREWORK, x, y, z, vx, vy, vz);
        if (particle != null) {
            particle.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        }
    }

    static void clear() {
        whisperDelay = 0;
        heartDelay = 0;
        figureDelay = 0;
        lastBolt = Long.MIN_VALUE;
    }
}
