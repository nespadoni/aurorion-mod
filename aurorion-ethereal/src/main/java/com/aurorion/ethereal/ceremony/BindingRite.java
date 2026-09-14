package com.aurorion.ethereal.ceremony;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.network.RitePayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O Rito de Vinculacao: a cena em que uma pessoa ganha sua casa.
 *
 * <h2>O que mudou, e por que</h2>
 *
 * <p>A cerimonia antiga era um questionario: oito perguntas, uma contagem de pontos e uma decisao da
 * staff em cima do resultado. A casa ja e escolhida fora do jogo — as perguntas estavam decidindo uma
 * coisa que ninguem queria que elas decidissem, e custavam oito telas de leitura antes do unico
 * momento que importa. Ficou so esse momento, e ele ganhou os treze segundos que as perguntas
 * tomavam.
 *
 * <h2>Quatro tempos</h2>
 *
 * <table>
 *   <tr><th>Fase</th><th>Duracao</th><th>O que se ve</th></tr>
 *   <tr><td>Convocacao</td><td>3 s</td><td>um anel de runas se fecha em volta da pessoa</td></tr>
 *   <tr><td>Reuniao</td><td>3 s</td><td>a luz converge para dentro dela e cresce</td></tr>
 *   <tr><td>Revelacao</td><td>1 s</td><td>estouro nas cores da casa; o simbolo aparece</td></tr>
 *   <tr><td>Coroacao</td><td>6 s</td><td>a coluna de luz se sustenta sob o simbolo</td></tr>
 * </table>
 *
 * <h2>Onde cada metade e desenhada</h2>
 *
 * <p>A <b>luz e as particulas</b> sao do servidor, e nao do cliente de quem esta sendo vinculado: um
 * rito que so o dono enxerga nao e um rito, e uma tela. Todo mundo por perto ve. O <b>simbolo, o nome
 * e o lema</b> sao do cliente, porque sao imagem e texto que precisam sempre encarar a camera de quem
 * olha — e eles chegam em <b>dois pacotes</b> (um no inicio, um no fim), com o cliente contando os
 * proprios ticks. Nada de um pacote por quadro.
 *
 * <h2>Custo</h2>
 *
 * <p>Este e o unico lugar do mod que roda por tick, e ele sai na primeira linha enquanto nao houver
 * rito nenhum — que e o estado do servidor em 99,9% do tempo. Com um rito em andamento sao no maximo
 * seis chamadas de particula por tick, alcancando so quem esta a 32 blocos. Um personagem passa por
 * isto uma vez na vida (SDD §7.1).
 */
public final class BindingRite {
    public static final int RISE_TICKS = 60;
    public static final int GATHER_TICKS = 60;
    public static final int BURST_TICKS = 20;
    public static final int CROWN_TICKS = 120;
    public static final int TOTAL_TICKS = RISE_TICKS + GATHER_TICKS + BURST_TICKS + CROWN_TICKS;

    /** Tick em que a casa deixa de ser segredo. Cliente e servidor precisam concordar nele. */
    public static final int REVEAL_TICK = RISE_TICKS + GATHER_TICKS;

    private static final float RING_START = 4.0F;
    private static final float RING_END = 1.2F;
    private static final int BEAM_HEIGHT = 7;

    private static final class Rite {
        private final ServerPlayer player;
        private final House house;
        private final Vec3 anchor;
        private final boolean wasInvulnerable;
        private int tick;

        private Rite(ServerPlayer player, House house) {
            this.player = player;
            this.house = house;
            this.anchor = player.position();
            this.wasInvulnerable = player.isInvulnerable();
        }
    }

    private static final Map<UUID, Rite> ACTIVE = new HashMap<>();

    private BindingRite() {
    }

    // --- Ciclo de vida -------------------------------------------------------------------------

    /** @return false se essa pessoa ja esta no meio de um rito. */
    public static boolean start(ServerPlayer player, House house) {
        if (ACTIVE.containsKey(player.getUUID())) return false;

        Rite rite = new Rite(player, house);
        ACTIVE.put(player.getUUID(), rite);
        hold(rite);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                RitePayload.start(player.getId(), house));

        if (player.level() instanceof ServerLevel level) {
            sound(level, rite.anchor, SoundEvents.BEACON_ACTIVATE, 1.2F, 0.7F);
        }
        return true;
    }

    public static boolean isBinding(UUID player) {
        return ACTIVE.containsKey(player);
    }

    /** Sair no meio corta a cena. A casa ja foi gravada antes de ela comecar: nada se perde. */
    public static void forget(UUID player) {
        Rite rite = ACTIVE.remove(player);
        if (rite != null) release(rite);
    }

    public static void clear() {
        ACTIVE.values().forEach(BindingRite::release);
        ACTIVE.clear();
    }

    // --- O laco --------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        List<UUID> finished = null;

        for (Rite rite : ACTIVE.values()) {
            ServerPlayer player = rite.player;

            if (server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !(player.level() instanceof ServerLevel level)) {
                finished = add(finished, player.getUUID());
                continue;
            }

            step(level, rite);
            if (++rite.tick >= TOTAL_TICKS) finished = add(finished, player.getUUID());
        }

        if (finished == null) return;
        for (int i = 0; i < finished.size(); i++) {
            Rite rite = ACTIVE.remove(finished.get(i));
            if (rite == null) continue;

            release(rite);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(rite.player, RitePayload.end(rite.player.getId()));
            HouseManager.announce(rite.player.server, rite.player.getDisplayName(), rite.house);
        }
    }

    private static List<UUID> add(List<UUID> list, UUID id) {
        List<UUID> target = list == null ? new ArrayList<>(2) : list;
        target.add(id);
        return target;
    }

    private static void step(ServerLevel level, Rite rite) {
        // A pessoa fica onde comecou: a cena e construida em volta de um ponto, nao de quem anda.
        rite.player.setDeltaMovement(Vec3.ZERO);

        int tick = rite.tick;
        if (tick < RISE_TICKS) {
            summon(level, rite, tick);
        } else if (tick < REVEAL_TICK) {
            gather(level, rite, tick - RISE_TICKS);
        } else if (tick < REVEAL_TICK + BURST_TICKS) {
            burst(level, rite, tick - REVEAL_TICK);
        } else {
            crown(level, rite, tick - REVEAL_TICK - BURST_TICKS);
        }
    }

    // --- Fases ---------------------------------------------------------------------------------

    /** Convocacao: o anel de runas se fecha, e nada ainda tem a cor da casa. */
    private static void summon(ServerLevel level, Rite rite, int tick) {
        float progress = tick / (float) RISE_TICKS;
        double radius = RING_START - (RING_START - RING_END) * progress;
        double spin = tick * 0.12;
        Vec3 anchor = rite.anchor;

        for (int i = 0; i < 6; i++) {
            double angle = spin + Math.PI * 2 * i / 6.0;
            level.sendParticles(ParticleTypes.ENCHANT,
                    anchor.x + radius * Math.cos(angle), anchor.y + 0.1, anchor.z + radius * Math.sin(angle),
                    1, 0.0, 0.4, 0.0, 0.01);
        }

        if (tick % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, anchor.x, anchor.y + 0.05, anchor.z,
                    3, radius * 0.4, 0.02, radius * 0.4, 0.0);
        }
        if (tick % 20 == 0) {
            sound(level, anchor, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.6F + progress * 0.6F);
        }
    }

    /**
     * Reuniao: a luz vem de fora para dentro do peito.
     *
     * <p>Particula do vanilla nao persegue alvo. O que faz a convergencia acontecer e {@code count}
     * zero: nesse modo os tres campos de dispersao viram <b>velocidade</b>, e basta aponta-la para o
     * centro. E o mesmo truque do funil do funil de encantamento, ao contrario.
     */
    private static void gather(ServerLevel level, Rite rite, int tick) {
        float progress = tick / (float) GATHER_TICKS;
        Vec3 chest = rite.anchor.add(0.0, 1.1, 0.0);
        double radius = 3.0 - 2.2 * progress;
        double spin = tick * 0.35;

        for (int i = 0; i < 4; i++) {
            double angle = spin + Math.PI * 2 * i / 4.0;
            double height = 0.2 + 1.8 * ((tick * 7 + i * 13) % 20) / 20.0;
            double px = rite.anchor.x + radius * Math.cos(angle);
            double pz = rite.anchor.z + radius * Math.sin(angle);
            double py = rite.anchor.y + height;

            Vec3 pull = chest.subtract(px, py, pz).normalize().scale(0.35 + progress * 0.5);
            level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 0, pull.x, pull.y, pull.z, 1.0);
        }

        if (tick % 3 == 0) {
            level.sendParticles(ParticleTypes.GLOW, chest.x, chest.y, chest.z,
                    2, 0.12, 0.25, 0.12, 0.0);
        }
        if (tick == 0) {
            sound(level, rite.anchor, SoundEvents.CONDUIT_ACTIVATE, 1.0F, 1.4F);
        }
        if (tick == GATHER_TICKS - 20) {
            sound(level, rite.anchor, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F);
        }
    }

    /** Revelacao: a luz guardada sai de dentro, e a partir daqui tudo tem a cor da casa. */
    private static void burst(ServerLevel level, Rite rite, int tick) {
        Vec3 anchor = rite.anchor;
        Vec3 chest = anchor.add(0.0, 1.1, 0.0);
        DustParticleOptions dust = dust(rite.house.color(), 2.2F);

        if (tick == 0) {
            level.sendParticles(ParticleTypes.FLASH, chest.x, chest.y, chest.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(dust, chest.x, chest.y, chest.z, 90, 0.35, 0.5, 0.35, 0.55);
            sound(level, anchor, SoundEvents.TOTEM_USE, 1.4F, 1.0F);
            sound(level, anchor, SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.6F);
            return;
        }

        // A onda no chao: um anel que se abre depressa e some.
        double wave = 0.6 + tick * 0.45;
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2 * i / 10.0 + tick * 0.2;
            level.sendParticles(dust,
                    anchor.x + wave * Math.cos(angle), anchor.y + 0.08, anchor.z + wave * Math.sin(angle),
                    1, 0.0, 0.03, 0.0, 0.0);
        }
        column(level, rite, dust, tick / (float) BURST_TICKS, 8);
    }

    /** Coroacao: a coluna se sustenta e respira sob o simbolo. */
    private static void crown(ServerLevel level, Rite rite, int tick) {
        DustParticleOptions dust = dust(rite.house.color(), 1.6F);
        float fade = 1.0F - tick / (float) CROWN_TICKS;

        column(level, rite, dust, 1.0F, 3);

        if (tick % 2 == 0) {
            double spin = tick * 0.16;
            for (int i = 0; i < 3; i++) {
                double angle = spin + Math.PI * 2 * i / 3.0;
                double radius = 1.1 + 0.25 * Math.sin(tick * 0.08);
                level.sendParticles(dust,
                        rite.anchor.x + radius * Math.cos(angle),
                        rite.anchor.y + 0.4 + 2.4 * ((tick * 3 + i * 40) % 120) / 120.0,
                        rite.anchor.z + radius * Math.sin(angle),
                        1, 0.0, 0.02, 0.0, 0.0);
            }
        }
        if (tick == 0) {
            sound(level, rite.anchor, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
        if (tick % 40 == 0 && fade > 0.2F) {
            sound(level, rite.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F * fade, 1.5F);
        }
    }

    /** A coluna de luz que sobe do peito. {@code density} e quantas particulas por tick. */
    private static void column(ServerLevel level, Rite rite, ParticleOptions dust, float reach, int density) {
        double top = BEAM_HEIGHT * Math.min(1.0F, reach);

        for (int i = 0; i < density; i++) {
            double height = 1.0 + top * ((rite.tick * 11 + i * 29) % 40) / 40.0;
            level.sendParticles(dust,
                    rite.anchor.x, rite.anchor.y + height, rite.anchor.z,
                    1, 0.11, 0.06, 0.11, 0.0);
        }
        if (rite.tick % 5 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    rite.anchor.x, rite.anchor.y + 1.0, rite.anchor.z, 1, 0.06, 0.4, 0.06, 0.02);
        }
    }

    // --- Utilidades ----------------------------------------------------------------------------

    private static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(new Vector3f(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F), scale);
    }

    private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound,
                              float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Parado e intocavel durante a cena.
     *
     * <p>Lentidao no amplificador maximo em vez de teleporte por tick: o teleporte briga com a
     * predicao do cliente e treme. O efeito e invisivel de proposito ({@code showParticles} e
     * {@code showIcon} desligados) — a unica luz na tela precisa ser a do rito.
     */
    private static void hold(Rite rite) {
        rite.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                TOTAL_TICKS + 20, 255, false, false, false));
        rite.player.setInvulnerable(true);
        rite.player.setDeltaMovement(Vec3.ZERO);
    }

    private static void release(Rite rite) {
        rite.player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        rite.player.setInvulnerable(rite.wasInvulnerable);
    }
}
