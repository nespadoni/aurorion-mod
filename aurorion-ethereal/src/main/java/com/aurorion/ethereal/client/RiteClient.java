package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import com.aurorion.ethereal.network.RitePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Uma cena local, reconstruida do pacote inicial, com custo limitado independente da plateia.
 *
 * <p>O inicio chega com identidade neutra; a casa (nome, lema, cores, trilha) so chega no segundo
 * pacote, mandado pelo servidor no instante da revelacao ({@link Rite#identify}).
 */
public final class RiteClient {
    /**
     * Quantos ticks a cena pode esperar parada na beira da revelacao pelo pacote com a casa. Com
     * latencia, o relogio local chega la antes; sem a espera o selo acenderia na cor neutra.
     */
    private static final int REVEAL_HOLD_TICKS = 20;
    /** Troca da trilha neutra pela da casa, na revelacao. */
    private static final int MUSIC_CROSSFADE_TICKS = 20;

    public static final class Rite {
        final UUID playerId;
        final int entityId;
        final ResourceLocation dimension;
        final double anchorX, anchorY, anchorZ;
        final AABB bounds;
        Component houseName, motto;
        FormattedCharSequence titleText;
        int titleWidth, mottoWidth;
        int color, secondary, accent;
        DustParticleOptions mainDust, secondaryDust, accentDust;
        /** A casa ja chegou do servidor; antes disso tudo acima e a identidade neutra. */
        boolean identified;
        int held;
        /** Semente dos fogos: o mesmo escolhido rende o mesmo espetaculo em todos os clientes. */
        final long seed;
        @Nullable Entity entity;
        @Nullable RiteMusic music;
        int tick;

        Rite(RitePayload payload) {
            playerId = payload.playerId();
            entityId = payload.entityId();
            dimension = payload.dimension();
            anchorX = payload.anchor().x;
            anchorY = payload.anchor().y;
            anchorZ = payload.anchor().z;
            bounds = new AABB(anchorX - 9, anchorY - 2, anchorZ - 9, anchorX + 9, anchorY + 11, anchorZ + 9);
            identify(payload);
            seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
            tick = Mth.clamp(payload.elapsed(), 0, BindingRite.TOTAL_TICKS);
        }

        /** Copia a identidade do pacote; com {@code revealed} falso ela e a neutra. */
        void identify(RitePayload payload) {
            houseName = payload.houseName();
            motto = payload.motto();
            titleText = houseName.getVisualOrderText();
            titleWidth = Minecraft.getInstance().font.width(titleText);
            mottoWidth = Minecraft.getInstance().font.width(motto);
            color = payload.color();
            secondary = payload.style().secondary();
            accent = payload.style().accent();
            mainDust = dust(color, 1.25F);
            secondaryDust = dust(secondary, 1.0F);
            accentDust = dust(accent, 1.1F);
            identified = payload.revealed();
        }

        public Component houseName() { return houseName; }
        public Component motto() { return motto; }
        public int color() { return color; }
        public int tick() { return tick; }
        public int sinceReveal() { return tick - BindingRite.REVEAL_TICK; }
        public boolean revealed() { return tick >= BindingRite.REVEAL_TICK; }
        public float fade(float partial) {
            return Mth.clamp((BindingRite.TOTAL_TICKS - tick - partial) / BindingRite.FADE_TICKS, 0, 1);
        }
        double x(float partial) { return entity == null ? anchorX : Mth.lerp(partial, entity.xo, entity.getX()); }
        double y(float partial) { return entity == null ? anchorY : Mth.lerp(partial, entity.yo, entity.getY()); }
        double z(float partial) { return entity == null ? anchorZ : Mth.lerp(partial, entity.zo, entity.getZ()); }
    }

    @Nullable private static Rite active;

    private RiteClient() {}

    public static void accept(RitePayload payload) {
        if (!payload.active()) {
            if (active != null && active.playerId.equals(payload.playerId())) clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Revelacao de uma cena que ja esta rodando: troca so a identidade, sem reiniciar nada.
        if (active != null && active.playerId.equals(payload.playerId()) && payload.revealed()) {
            if (!active.identified) {
                active.identify(payload);
                swapMusic(minecraft, active, payload.style().music());
            }
            return;
        }
        clear();
        active = new Rite(payload);
        // Um espectador que chegou atrasado recebe a fase atual, sem reiniciar a trilha do zero.
        // A trilha do inicio e a neutra (o pacote ainda nao diz a casa); a da casa entra na revelacao.
        if (payload.elapsed() == 0) {
            active.music = new RiteMusic(payload.style().music(), active, 0, BindingRite.INTRO_TICKS);
            minecraft.getMusicManager().stopPlaying();
            minecraft.getSoundManager().play(active.music);
        }
    }

    /** Cruza a trilha neutra com a da casa; casa que usa a mesma trilha segue sem corte. */
    private static void swapMusic(Minecraft minecraft, Rite rite, ResourceLocation sound) {
        if (rite.music == null || rite.music.sound().equals(sound)) return;
        rite.music.retire(MUSIC_CROSSFADE_TICKS);
        rite.music = new RiteMusic(sound, rite, rite.tick, MUSIC_CROSSFADE_TICKS);
        minecraft.getSoundManager().play(rite.music);
    }

    public static void tick() {
        Rite rite = active;
        if (rite == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(rite.dimension)
                || minecraft.player.distanceToSqr(rite.anchorX, rite.anchorY, rite.anchorZ)
                    > BindingRite.AUDIENCE_RADIUS * BindingRite.AUDIENCE_RADIUS
                || rite.tick >= BindingRite.TOTAL_TICKS) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;
        Entity entity = minecraft.level.getEntity(rite.entityId);
        rite.entity = entity != null && entity.getUUID().equals(rite.playerId) ? entity : null;
        RiteParticles.tick(rite);
        // Na beira da revelacao, espera um pouco pelo pacote com a casa em vez de acender neutro.
        if (!rite.identified && rite.tick == BindingRite.REVEAL_TICK - 1 && rite.held < REVEAL_HOLD_TICKS) {
            rite.held++;
            return;
        }
        rite.tick++;
    }

    @Nullable public static Rite current() { return active; }

    @Nullable public static Rite ofSelf() {
        var player = Minecraft.getInstance().player;
        return active != null && player != null && active.playerId.equals(player.getUUID()) ? active : null;
    }

    public static void clear() {
        if (active != null && active.music != null) Minecraft.getInstance().getSoundManager().stop(active.music);
        active = null;
    }

    private static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255F,
                (color >> 8 & 255) / 255F, (color & 255) / 255F), scale);
    }
}
