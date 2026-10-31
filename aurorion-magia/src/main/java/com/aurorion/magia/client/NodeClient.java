package com.aurorion.magia.client;

import com.aurorion.magia.network.NodePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Os nos liberados, do lado de quem assiste. O servidor manda so onde cada um esta; daqui para frente a
 * cena e toda local: {@link NodeRenderer} desenha, {@link NodeAmbience} solta particula e som.
 *
 * <p>O relogio e o tempo de jogo do mundo, e nao um contador do cliente: o tempo de jogo chega igual a
 * todos, entao o circulo gira no mesmo angulo e o raio do No 2 cai no mesmo ponto para trinta pessoas
 * olhando — como os fogos da cerimonia do {@code aurorion-ethereal}.
 */
public final class NodeClient {
    /** Abertura: a cena nasce e explode nestes ticks; quem chega depois ja a encontra pronta. */
    static final int OPENING = 80;
    /** Desligar nao corta seco: a cena apaga nestes ticks. */
    static final int FADE = 40;
    /** Raio do medo do No 2: dentro dele a mente escurece e as vozes chegam. */
    static final double MIND_RADIUS = 22;
    /** Um raio a cada tanto no No 2. */
    static final int BOLT_PERIOD = 90;
    static final int BOLT_TICKS = 8;

    static final class Scene {
        final int index;
        final ResourceLocation dimension;
        final double x, y, z;
        final long seed;
        final AABB bounds;
        final double reach;
        long start;
        /** -1 enquanto ligado; contagem regressiva depois de desligado. */
        int ending = -1;
        /** A abertura ja tocou (ou o no ja estava ligado quando este cliente chegou). */
        boolean opened;

        Scene(NodePayload.Entry entry, long now) {
            index = entry.index();
            dimension = entry.dimension();
            x = entry.x();
            y = entry.y();
            z = entry.z();
            start = now - entry.age();
            opened = entry.age() > 10;
            seed = Double.doubleToLongBits(x) * 31 + Double.doubleToLongBits(z) * 17 + index;
            double half = index == 3 ? 27 : index == 2 ? 17 : 7;
            double top = index == 3 ? 170 : index == 2 ? 26 : 10;
            bounds = new AABB(x - half, y - 2, z - half, x + half, y + top, z + half);
            reach = index == 3 ? 256 : 112;
        }

        boolean same(NodePayload.Entry entry) {
            return ending < 0 && dimension.equals(entry.dimension()) && x == entry.x() && y == entry.y() && z == entry.z();
        }

        /** Ticks desde a liberacao, com a fracao do quadro. */
        float life(ClientLevel level, float partial) {
            return (float) (level.getGameTime() - start) + partial;
        }

        /** Entrada nos primeiros ticks, apagar depois de desligado. */
        float fade(ClientLevel level, float partial) {
            if (ending >= 0) return Mth.clamp((ending - partial) / FADE, 0, 1);
            return Mth.clamp(life(level, partial) / 20F, 0, 1);
        }

        double distanceSqr(Vec3 at) {
            double dx = at.x - x, dy = at.y - y, dz = at.z - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final List<Scene> scenes = new ArrayList<>(3);

    private NodeClient() {
    }

    public static void accept(NodePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        for (Scene scene : scenes) {
            boolean kept = false;
            for (NodePayload.Entry entry : payload.nodes()) kept |= entry.index() == scene.index && scene.same(entry);
            if (!kept && scene.ending < 0) scene.ending = FADE;
        }
        for (NodePayload.Entry entry : payload.nodes()) {
            boolean present = false;
            for (Scene scene : scenes) present |= scene.index == entry.index() && scene.same(entry);
            if (!present) scenes.add(new Scene(entry, now));
        }
    }

    /** Os nos em cena, desligando inclusive. Lista viva: so ler, na thread do cliente. */
    static List<Scene> scenes() {
        return scenes;
    }

    public static void tick() {
        if (scenes.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) return;
        boolean paused = minecraft.isPaused();
        for (Iterator<Scene> it = scenes.iterator(); it.hasNext(); ) {
            Scene scene = it.next();
            if (scene.ending >= 0 && !paused && --scene.ending <= 0) {
                it.remove();
                continue;
            }
            if (paused || !level.dimension().location().equals(scene.dimension)) continue;
            NodeAmbience.tick(minecraft, level, player, scene);
        }
    }

    /**
     * Quanto o No 2 escurece o ar na camera: zero fora do raio do medo, ate 0,7 colado no centro, e
     * pulsando devagar — a escuridao do Warden, que vai e volta em vez de ficar parada.
     */
    public static float darkness(Vec3 camera) {
        if (scenes.isEmpty()) return 0;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0;
        float strongest = 0;
        for (Scene scene : scenes) {
            float closeness = mind(level, scene, camera);
            if (closeness <= 0) continue;
            float life = scene.life(level, 0);
            float pulse = .75F + .25F * Mth.sin(life * .05F);
            strongest = Math.max(strongest, .7F * closeness * pulse);
        }
        return strongest;
    }

    /** 0 fora do raio do medo do No 2, 1 no centro. */
    static float mind(ClientLevel level, Scene scene, Vec3 at) {
        if (scene.index != 2 || !level.dimension().location().equals(scene.dimension)) return 0;
        double distance = Math.sqrt(scene.distanceSqr(at));
        if (distance >= MIND_RADIUS) return 0;
        float closeness = (float) (1 - distance / MIND_RADIUS);
        return Mth.clamp(closeness * 1.6F, 0, 1) * scene.fade(level, 0);
    }

    /**
     * Quando cai o raio {@code n} do No 2. Os de numero negativo sao a abertura: tres raios seguidos nos
     * primeiros dois segundos. Depois, um por {@link #BOLT_PERIOD}, num instante sorteado dentro dele.
     */
    static long boltStart(Scene scene, long n) {
        if (n < 0) return -n * 16 - 10;
        return OPENING + n * BOLT_PERIOD + (long) (noise(scene.seed, (int) n * 7 + 1) * (BOLT_PERIOD - 20));
    }

    /** Para onde o raio {@code n} cai, em radianos em volta do centro. */
    static float boltAngle(Scene scene, long n) {
        return noise(scene.seed, (int) n * 7 + 2) * Mth.TWO_PI;
    }

    /** A que distancia do centro o raio {@code n} cai. Os da abertura caem colados no circulo. */
    static float boltDistance(Scene scene, long n) {
        return n < 0 ? 1.5F + noise(scene.seed, (int) n * 7 + 3) * 2 : 3 + noise(scene.seed, (int) n * 7 + 3) * 7;
    }

    /** O numero do raio caindo agora no No 2, ou {@link Long#MIN_VALUE} se o ceu esta quieto. */
    static long activeBolt(Scene scene, long life) {
        if (scene.index != 2 || scene.ending >= 0) return Long.MIN_VALUE;
        if (life < OPENING) {
            for (long n = -1; n >= -3; n--) {
                long age = life - boltStart(scene, n);
                if (age >= 0 && age < BOLT_TICKS) return n;
            }
            return Long.MIN_VALUE;
        }
        long k = (life - OPENING) / BOLT_PERIOD;
        for (long n = k; n >= Math.max(0, k - 1); n--) {
            long age = life - boltStart(scene, n);
            if (age >= 0 && age < BOLT_TICKS) return n;
        }
        return Long.MIN_VALUE;
    }

    /**
     * Sorteio sem estado nem alocacao: o mesmo {@code (seed, k)} da o mesmo numero em todo cliente, todo
     * quadro. Mistura do SplitMix64.
     *
     * @return em [0, 1)
     */
    static float noise(long seed, int k) {
        long h = seed * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    public static void clear() {
        scenes.clear();
        NodeAmbience.clear();
    }
}
