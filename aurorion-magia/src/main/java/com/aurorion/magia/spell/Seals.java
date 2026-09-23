package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Os lacres do Sigillum Clausum, por dimensao.
 *
 * <h2>Nada roda por tick</h2>
 *
 * <p>Um lacre e so uma entrada num mapa de {@code long} (a posicao) para o dono e o prazo. Ele e
 * conferido quando alguem tenta abrir ou quebrar aquele bloco — uma busca em hash — e o prazo vencido
 * e descoberto nessa hora. Nao ha lista varrida, nem entidade, nem block entity.
 *
 * <h2>Memoria, nao disco</h2>
 *
 * <p>O lacre e temporario (minutos) por definicao; sobreviver a um reinicio do servidor nao faz parte
 * da magia. Um teto por dimensao impede que alguem encha o mapa.
 *
 * <p>Portas duplas de altura e baus duplos: as duas metades apontam para o mesmo lacre, senao a outra
 * metade abriria o conjunto inteiro.
 */
public final class Seals {
    public static final TagKey<Block> SEALABLE = TagKey.create(Registries.BLOCK, AurorionMagia.id("selavel"));
    private static final int MAX_PER_LEVEL = 1024;

    private static final Map<ResourceKey<Level>, Long2ObjectMap<Seal>> BY_LEVEL = new HashMap<>();

    /**
     * @param team  time do dono no momento do lacre (nivel 2+), ou {@code null}: so o dono abre
     * @param mark  ponto da face onde o selo e desenhado
     */
    public record Seal(UUID owner, @Nullable String team, long expiresAt, int level, Vec3 mark, Direction face) {
        int remaining(long now) {
            return (int) Math.max(0, expiresAt - now);
        }
    }

    public enum Outcome { SEALED, DISPELLED, BROKEN, RESISTED, FULL }

    private Seals() {
    }

    public static boolean isSealable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || state.is(SEALABLE) || level.getBlockEntity(pos) instanceof Container;
    }

    @Nullable
    public static Seal get(ServerLevel level, BlockPos pos) {
        Long2ObjectMap<Seal> seals = BY_LEVEL.get(level.dimension());
        if (seals == null) return null;
        Seal seal = seals.get(pos.asLong());
        if (seal != null && seal.expiresAt() <= level.getGameTime()) {
            removeWithPartner(level, seals, pos);
            return null;
        }
        return seal;
    }

    public static Outcome cast(ServerLevel level, LivingEntity caster, BlockPos pos, Direction face, Vec3 mark,
                               int spellLevel, int durationTicks) {
        Seal existing = get(level, pos);
        if (existing != null) {
            if (existing.owner().equals(caster.getUUID())) {
                remove(level, pos, existing, SpellVisualPayload.Kind.SIGILLUM_BREAK);
                return Outcome.DISPELLED;
            }
            // Contra-selo: quebra quem conjura com nivel igual ou maior que o do lacre.
            if (spellLevel >= existing.level()) {
                remove(level, pos, existing, SpellVisualPayload.Kind.SIGILLUM_BREAK);
                return Outcome.BROKEN;
            }
            MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SIGILLUM_DENY, 12, existing.mark(),
                    existing.face().ordinal());
            return Outcome.RESISTED;
        }

        Long2ObjectMap<Seal> seals = BY_LEVEL.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>());
        if (seals.size() >= MAX_PER_LEVEL) {
            // Vencidos que ninguem tocou continuam no mapa ate aqui; so no teto vale a varredura.
            long now = level.getGameTime();
            seals.values().removeIf(seal -> seal.expiresAt() <= now);
            if (seals.size() >= MAX_PER_LEVEL) return Outcome.FULL;
        }

        PlayerTeam team = spellLevel >= 2 && caster.getTeam() instanceof PlayerTeam playerTeam ? playerTeam : null;
        Seal seal = new Seal(caster.getUUID(), team == null ? null : team.getName(),
                level.getGameTime() + durationTicks, spellLevel, mark, face);
        seals.put(pos.asLong(), seal);
        BlockPos partner = partner(level, pos);
        if (partner != null) seals.put(partner.asLong(), seal);

        close(level, pos);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SIGILLUM, durationTicks, mark, face.ordinal());
        return Outcome.SEALED;
    }

    /** Dono, time do dono (nivel 2+) e staff em criativo passam. */
    public static boolean mayOpen(ServerPlayer player, Seal seal) {
        if (seal.owner().equals(player.getUUID())) return true;
        if (player.isCreative() && player.hasPermissions(2)) return true;
        return seal.team() != null && player.getTeam() != null && seal.team().equals(player.getTeam().getName());
    }

    /** Recusa: clarao no selo so para quem tentou, e o aviso. */
    public static void deny(ServerPlayer player, Seal seal) {
        MagiaNetwork.sendVisualTo(player, SpellVisualPayload.Kind.SIGILLUM_DENY, 12, seal.mark(), seal.face().ordinal());
        player.displayClientMessage(Component.translatable("aurorion_magia.lacrado"), true);
        AurorionSpell.sound(player.level(), seal.mark(), SoundEvents.CHEST_LOCKED, 1.0f, 0.7f);
    }

    /** O bloco foi quebrado (pelo dono ou pela staff): o lacre some com ele. */
    public static void onBroken(ServerLevel level, BlockPos pos) {
        Long2ObjectMap<Seal> seals = BY_LEVEL.get(level.dimension());
        if (seals != null && seals.containsKey(pos.asLong())) removeWithPartner(level, seals, pos);
    }

    /**
     * Quem chega na dimensao ve os lacres que ja estavam la. Um pacote por lacre ativo daquela
     * dimensao, com o tempo que falta — nada para quem esta numa dimensao sem lacre.
     */
    public static void sendAll(ServerPlayer player) {
        Long2ObjectMap<Seal> seals = BY_LEVEL.get(player.level().dimension());
        if (seals == null || seals.isEmpty()) return;
        long now = player.level().getGameTime();
        Set<Seal> sent = new HashSet<>();
        for (Seal seal : seals.values()) {
            if (seal.expiresAt() > now && sent.add(seal)) {
                MagiaNetwork.sendVisualTo(player, SpellVisualPayload.Kind.SIGILLUM, seal.remaining(now),
                        seal.mark(), seal.face().ordinal());
            }
        }
    }

    public static void clear() {
        BY_LEVEL.clear();
    }

    private static void remove(ServerLevel level, BlockPos pos, Seal seal, SpellVisualPayload.Kind visual) {
        Long2ObjectMap<Seal> seals = BY_LEVEL.get(level.dimension());
        if (seals != null) removeWithPartner(level, seals, pos);
        MagiaNetwork.sendVisualAt(level, null, visual, 16, seal.mark(), seal.face().ordinal());
        AurorionSpell.sound(level, seal.mark(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f);
    }

    private static void removeWithPartner(ServerLevel level, Long2ObjectMap<Seal> seals, BlockPos pos) {
        seals.remove(pos.asLong());
        BlockPos partner = partner(level, pos);
        if (partner != null) seals.remove(partner.asLong());
    }

    /** A outra metade: de cima/de baixo da porta, ou o outro lado do bau duplo. */
    @Nullable
    private static BlockPos partner(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(BlockStateProperties.CHEST_TYPE)
                && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return null;
    }

    /** Lacrar fecha: porta, alcapao e portao aberto batem na hora. */
    private static void close(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, level, state, pos, false);
            } else {
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
            }
        }
    }
}
