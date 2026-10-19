package com.aurorion.magia.passive;

import com.aurorion.core.compat.TwilightTeleportCompat;
import com.aurorion.magia.compat.WaystonesCompat;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.spell.AurorionSpell;
import com.aurorion.magia.spell.Binding;
import com.aurorion.magia.spell.Hits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Salto Quantico</b> — Robs, representante do Quantico.
 *
 * <ul>
 *   <li><b>Tecla</b>: o corpo se desfaz em atomos e se refaz onde ele esta olhando, ate
 *       {@code alcanceSalto} blocos. Nao atravessa parede: reaparece antes do primeiro bloco.</li>
 *   <li><b>Shift + tecla</b>: a lista das pedras de teleporte (Waystones) que ele ja ativou, clicavel.
 *       Viajar a uma pedra custa mais; a outra dimensao, mais ainda.</li>
 * </ul>
 *
 * <h2>Energia e peso</h2>
 *
 * <p>Cada teleporte gasta cargas ({@code energiaMaxima}, uma volta a cada {@code segundosPorCarga}). A
 * partir do {@code pesoAPartirDe}-esimo teleporte seguido, o corpo cobra: lentidao, nausea, fraqueza e,
 * insistindo, dano — cada vez mais. O peso alivia com o tempo ({@code segundosPorAlivio} por teleporte).
 * Gastar a ultima carga <b>esgota</b>: {@code segundosEsgotado} so carregando, sem teleporte nenhum.
 *
 * <h2>Custo</h2>
 *
 * <p>Zero fora do aperto da tecla. Energia e peso nao sao contados por tick: guarda-se o valor e o
 * instante da ultima mudanca, e o valor de agora e uma conta ({@link #read}).
 */
public final class QuantumLeap {
    private static final String ENERGY = "QuantumEnergy";
    private static final String STRAIN = "QuantumStrain";
    private static final String AT = "QuantumAt";
    private static final String EXHAUSTED = "QuantumExhaustedUntil";

    private QuantumLeap() {
    }

    /** O estado de agora, ja com a recarga e o alivio desde a ultima mudanca. */
    record State(double energy, double strain, long exhaustedUntil, long now) {
        boolean exhausted() {
            return now < exhaustedUntil;
        }
    }

    public static void use(ServerPlayer player, boolean alternate) {
        if (alternate) listStones(player);
        else blink(player);
    }

    // --- Salto ----------------------------------------------------------------------------------

    private static void blink(ServerPlayer player) {
        int cost = MagiaConfig.QUANTUM_BLINK_COST.get();
        State state = read(player);
        if (!ready(player, state, cost)) return;

        ServerLevel level = player.serverLevel();
        Vec3 target = landing(level, player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.sem_lugar")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // O mesmo evento do enderman e do chorus: a corrente do Vinculum e as areas que vetam
        // teleporte o recusam aqui, antes de gastar energia.
        if (EventHooks.onEnderTeleport(player, target.x, target.y, target.z).isCanceled()) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.recusado")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        Vec3 from = player.position();
        TwilightTeleportCompat.immediateRun(() -> player.teleportTo(level, target.x, target.y, target.z,
                Set.of(), player.getYRot(), player.getXRot()));
        player.resetFallDistance();
        arrive(player, level, from, state, cost);
    }

    /**
     * Onde o corpo se refaz: o ponto mirado, recuado meio bloco para nao nascer dentro da parede, e o
     * primeiro espaco livre do tamanho do corpo ate dois blocos acima (subir num degrau ou num muro).
     */
    @Nullable
    private static Vec3 landing(ServerLevel level, ServerPlayer player) {
        double range = MagiaConfig.QUANTUM_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 aim = Hits.aimPoint(level, player, range);
        double distance = aim.distanceTo(eye);
        for (double back = 0.6; back < Math.min(3.5, distance); back += 0.5) {
            Vec3 point = eye.add(look.scale(distance - back));
            double baseY = Math.floor(point.y - player.getEyeHeight() + 0.5);
            for (int up = 0; up <= 2; up++) {
                Vec3 feet = new Vec3(point.x, baseY + up, point.z);
                if (!level.isLoaded(BlockPos.containing(feet)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(feet))) continue;
                AABB body = player.getDimensions(player.getPose()).makeBoundingBox(feet);
                if (level.noCollision(player, body)) return feet;
            }
        }
        return null;
    }

    // --- Pedras ----------------------------------------------------------------------------------

    private static void listStones(ServerPlayer player) {
        if (!WaystonesCompat.available()) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.sem_waystones")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<WaystonesCompat.Stone> stones = WaystonesCompat.activated(player);
        if (stones.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aurorion_magia.quantico.nenhuma_pedra")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        State state = read(player);
        player.sendSystemMessage(Component.translatable("aurorion_magia.quantico.pedras",
                energyLine(state)).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (WaystonesCompat.Stone stone : stones) {
            int cost = stoneCost(player, stone);
            String command = "/aurorion quantico ir " + stone.id();
            MutableComponent line = Component.literal(" ⚛ ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(stone.name().copy().withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("aurorion_magia.quantico.custo", cost)))))
                    .append(Component.literal("  " + cost + "⚡").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(line);
        }
    }

    /** {@code /aurorion quantico ir <pedra>}: o clique da lista. Confere tudo de novo. */
    public static boolean travel(ServerPlayer player, UUID stoneId) {
        if (!Passives.has(player, Passive.QUANTUM) || AurorionSpell.castBlocked(player)) return false;
        WaystonesCompat.Stone stone = null;
        for (WaystonesCompat.Stone candidate : WaystonesCompat.activated(player)) {
            if (candidate.id().equals(stoneId)) stone = candidate;
        }
        if (stone == null) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.pedra_sumiu")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int cost = stoneCost(player, stone);
        State state = read(player);
        if (!ready(player, state, cost)) return false;
        WaystonesCompat.Destination destination = WaystonesCompat.destination(player.server, stone);
        if (destination == null) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.pedra_sumiu")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Vec3 at = destination.position();
        ServerLevel from = player.serverLevel();
        if (destination.level() == from && EventHooks.onEnderTeleport(player, at.x, at.y, at.z).isCanceled()) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.recusado")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return false;
        }
        Vec3 origin = player.position();
        TwilightTeleportCompat.immediateRun(() -> player.teleportTo(destination.level(), at.x, at.y, at.z,
                Set.of(), destination.yaw(), player.getXRot()));
        // Troca de dimensao vetada por outro mod (Vinculum, Reino das Almas): nao cobra o que nao houve.
        if (player.level() != destination.level()) return false;
        player.resetFallDistance();
        arrive(player, destination.level(), destination.level() == from ? origin : null, state, cost);
        return true;
    }

    private static int stoneCost(ServerPlayer player, WaystonesCompat.Stone stone) {
        int cost = MagiaConfig.QUANTUM_STONE_COST.get();
        return stone.dimension().equals(player.level().dimension()) ? cost : cost + MagiaConfig.QUANTUM_DIMENSION_COST.get();
    }

    // --- Energia ---------------------------------------------------------------------------------

    private static boolean ready(ServerPlayer player, State state, int cost) {
        if (AurorionSpell.castBlocked(player) || Binding.isBound(player)) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.recusado")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return false;
        }
        if (state.exhausted()) {
            long seconds = (state.exhaustedUntil() - state.now() + 19) / 20;
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.esgotado", seconds)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (state.energy() + 1.0E-6 < cost) {
            player.displayClientMessage(Component.translatable("aurorion_magia.quantico.sem_energia",
                    energyLine(state)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Depois que o corpo chegou: cobra, pesa e mostra. */
    private static void arrive(ServerPlayer player, ServerLevel level, @Nullable Vec3 from, State state, int cost) {
        double energy = Math.max(0, state.energy() - cost);
        double strain = state.strain() + 1;
        long exhaustedUntil = state.exhaustedUntil();
        if (energy < 1.0E-6 && cost > 0) {
            exhaustedUntil = state.now() + MagiaConfig.QUANTUM_EXHAUSTED.get() * 20L;
            player.sendSystemMessage(Component.translatable("aurorion_magia.quantico.esgotou",
                    MagiaConfig.QUANTUM_EXHAUSTED.get()).withStyle(ChatFormatting.RED));
        }
        write(player, energy, strain, exhaustedUntil, state.now());
        weigh(player, strain);

        if (from != null) MagiaNetwork.sendVisualAt(level, player, SpellVisualPayload.Kind.QUANTUM_BLINK, 20, from, 0);
        MagiaNetwork.sendVisualAt(level, player, SpellVisualPayload.Kind.QUANTUM_BLINK, 20, player.position(), 1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.6F);
        player.displayClientMessage(energyLine(new State(energy, strain, exhaustedUntil, state.now()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * O peso de usar demais. Abaixo do limiar, nada; a cada teleporte acima dele, mais forte. Nunca
     * mata sozinho: o dano para em meio coracao.
     */
    private static void weigh(ServerPlayer player, double strain) {
        int over = (int) Math.ceil(strain - MagiaConfig.QUANTUM_STRAIN_FROM.get());
        if (over <= 0) return;
        int ticks = 60 + 40 * over;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, Math.min(3, over / 2)));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, Math.min(2, over / 3)));
        if (over >= 2) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40 + 30 * over, 0));
        if (over >= 3) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, Math.min(2, over / 4)));
        if (over >= 4) {
            float damage = Math.min(player.getHealth() - 1, 1 + over * 0.5F);
            if (damage > 0) player.hurt(player.damageSources().magic(), damage);
        }
        player.sendSystemMessage(Component.translatable("aurorion_magia.quantico.peso", over)
                .withStyle(ChatFormatting.DARK_RED));
    }

    static State read(ServerPlayer player) {
        CompoundTag tag = RepresentativeData.get(player.server).of(player.getUUID());
        long now = player.server.overworld().getGameTime();
        int max = MagiaConfig.QUANTUM_ENERGY.get();
        if (!tag.contains(ENERGY)) return new State(max, 0, 0, now);
        long elapsed = Math.max(0, now - tag.getLong(AT));
        double energy = Math.min(max, tag.getDouble(ENERGY) + elapsed / (MagiaConfig.QUANTUM_RECHARGE.get() * 20.0));
        double strain = Math.max(0, tag.getDouble(STRAIN) - elapsed / (MagiaConfig.QUANTUM_STRAIN_DECAY.get() * 20.0));
        return new State(energy, strain, tag.getLong(EXHAUSTED), now);
    }

    private static void write(ServerPlayer player, double energy, double strain, long exhaustedUntil, long now) {
        RepresentativeData data = RepresentativeData.get(player.server);
        CompoundTag tag = data.of(player.getUUID());
        tag.putDouble(ENERGY, energy);
        tag.putDouble(STRAIN, strain);
        tag.putLong(EXHAUSTED, exhaustedUntil);
        tag.putLong(AT, now);
        data.setDirty();
    }

    public static MutableComponent energyLine(ServerPlayer player) {
        return energyLine(read(player));
    }

    private static MutableComponent energyLine(State state) {
        return Component.translatable("aurorion_magia.quantico.energia", (int) Math.floor(state.energy() + 1.0E-6),
                MagiaConfig.QUANTUM_ENERGY.get(), (int) Math.ceil(state.strain() - 1.0E-6),
                MagiaConfig.QUANTUM_STRAIN_FROM.get());
    }
}
