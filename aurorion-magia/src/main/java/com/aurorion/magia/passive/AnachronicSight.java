package com.aurorion.magia.passive;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.core.compat.TwilightTeleportCompat;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.AurorionSpell;
import com.aurorion.magia.spell.Binding;
import com.aurorion.magia.spell.Possession;
import com.aurorion.magia.spell.SoulRealm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * <b>Olhar Anacronico</b> — Gretta, representante do Anacronico.
 *
 * <p>Uma vez por dia ({@code horasEntreUsos}, horas reais), ela sai do proprio corpo e passa a ver o que
 * outra pessoa ve: a camera dela vai para os olhos do alvo, e ela acompanha cada movimento dele. O alvo
 * nao sabe. Os primeiros {@code segundosLeves} (10) sao leves; ela pode ficar ate
 * {@code segundosMaximos} (30), mas cada segundo a mais pesa. Ao voltar, fica <b>fragilizada</b> — menos
 * forca, menos velocidade — por {@code fragilBase} segundos mais {@code fragilPorSegundoExtra} por
 * segundo que passou do tempo leve.
 *
 * <h2>Como</h2>
 *
 * <p>A alma dela vai junto com o alvo: o corpo de Gretta fica invisivel, intocavel e parado, e e levado
 * para perto dele quando ele se afasta — e o que mantem o alvo (e os chunks em volta dele) carregados no
 * cliente dela. A camera em si e do cliente: um visual {@code ANACHRONIC_SIGHT}, so para ela, troca a
 * entidade de camera. A camera do servidor ({@code ServerPlayer#setCamera}) nao serve: ela arrasta o
 * corpo para dentro do alvo a cada tick.
 *
 * <p>Acaba com a tecla de novo, com o tempo, com a morte ou a saida de qualquer um dos dois, e com a troca
 * de dimensao do alvo. Ela volta para onde estava.
 *
 * <h2>Custo</h2>
 *
 * <p>O relogio e o efeito {@code olhar_anacronico}, so nela. Por tick: uma distancia; um pacote de
 * posicao so quando o alvo abre mais de 4 blocos.
 */
public final class AnachronicSight {
    private static final String KEY = AurorionMagia.MOD_ID + ":olhar_anacronico";
    private static final String LAST_USE = "AnachronicAt";
    private static final double FOLLOW_SQR = 6 * 6;

    private AnachronicSight() {
    }

    public static boolean isActive(LivingEntity entity) {
        return entity.hasEffect(MagiaEffects.ANACHRONIC_SIGHT);
    }

    public static void start(ServerPlayer caster) {
        if (AurorionSpell.castBlocked(caster) || Binding.isBound(caster) || SoulRealm.active(caster)) {
            caster.displayClientMessage(Component.translatable("aurorion_magia.anacronico.preso")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        RepresentativeData data = RepresentativeData.get(caster.server);
        CompoundTag record = data.of(caster.getUUID());
        long wait = MagiaConfig.ANACHRONIC_COOLDOWN_HOURS.get() * 3_600_000L;
        long since = System.currentTimeMillis() - record.getLong(LAST_USE);
        if (record.contains(LAST_USE) && since >= 0 && since < wait) {
            long minutes = (wait - since + 59_999) / 60_000;
            caster.displayClientMessage(Component.translatable("aurorion_magia.anacronico.recarga",
                    minutes / 60, minutes % 60).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        ServerPlayer target = PassiveTargets.lookedAt(caster, MagiaConfig.ANACHRONIC_RANGE.get(),
                other -> !other.isCreative() && !isActive(other) && !Possession.isPossessing(other)
                        && !other.hasEffect(MagiaEffects.POSSESSED) && !VoidPocket.isInside(other));
        if (target == null) {
            caster.displayClientMessage(Component.translatable("aurorion_magia.anacronico.sem_alvo")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        int maxTicks = MagiaConfig.ANACHRONIC_MAX_SECONDS.get() * 20;
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Target", target.getUUID());
        tag.putString("Dim", caster.level().dimension().location().toString());
        tag.putDouble("X", caster.getX());
        tag.putDouble("Y", caster.getY());
        tag.putDouble("Z", caster.getZ());
        tag.putFloat("Yaw", caster.getYRot());
        tag.putFloat("Pitch", caster.getXRot());
        tag.putInt("Start", caster.server.getTickCount());
        caster.getPersistentData().put(KEY, tag);
        record.putLong(LAST_USE, System.currentTimeMillis());
        data.setDirty();

        caster.stopUsingItem();
        caster.addEffect(new MobEffectInstance(MagiaEffects.ANACHRONIC_SIGHT, maxTicks, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, maxTicks, 0, false, false, false));
        caster.addEffect(new MobEffectInstance(MagiaEffects.UNTARGETABLE, maxTicks, 0, false, false, false));
        MagiaNetwork.sendVisualTo(caster, caster, target, SpellVisualPayload.Kind.ANACHRONIC_SIGHT, maxTicks);
        caster.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
        caster.sendSystemMessage(Component.translatable("aurorion_magia.anacronico.inicio", target.getDisplayName(),
                MagiaConfig.ANACHRONIC_SAFE_SECONDS.get()).withStyle(ChatFormatting.GOLD));
    }

    /** Um tick, pelo efeito {@code olhar_anacronico}. {@code false} acaba a possessao. */
    public static boolean tick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer caster) || !caster.isAlive()) return false;
        CompoundTag tag = state(caster);
        ServerPlayer target = tag == null ? null : target(caster, tag);
        if (target == null || !target.isAlive() || target.level() != caster.level() || target.isSpectator()) {
            return false;
        }
        if (caster.distanceToSqr(target) > FOLLOW_SQR) {
            // Um passo atras do alvo, e nao dentro dele: dois jogadores sobrepostos se empurram, e o alvo
            // sentiria o corpo invisivel colado nas costas.
            // Olhando reto para cima ou para baixo, o olhar horizontal zera: usa a direcao do corpo.
            Vec3 flat = target.getLookAngle().multiply(1, 0, 1);
            if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, target.getYRot());
            Vec3 behind = target.position().subtract(flat.normalize().scale(1.5));
            // Sincrono: o Twilight Teleport adiaria o passo e mostraria o efeito dele a quem esta perto,
            // revelando o corpo invisivel de Gretta.
            TwilightTeleportCompat.immediateRun(() -> caster.connection.teleport(behind.x, target.getY(), behind.z,
                    caster.getYRot(), caster.getXRot()));
        }
        caster.resetFallDistance();

        int elapsed = caster.server.getTickCount() - tag.getInt("Start");
        int safe = MagiaConfig.ANACHRONIC_SAFE_SECONDS.get() * 20;
        if (elapsed == safe) {
            caster.displayClientMessage(Component.translatable("aurorion_magia.anacronico.pesando")
                    .withStyle(ChatFormatting.RED), true);
        } else if (elapsed > safe && elapsed % 20 == 0) {
            int left = MagiaConfig.ANACHRONIC_MAX_SECONDS.get() - elapsed / 20;
            caster.displayClientMessage(Component.translatable("aurorion_magia.anacronico.restam", Math.max(0, left))
                    .withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /**
     * Volta ao corpo. Idempotente: tirar o efeito chama isto de novo pelo evento de remocao, e a segunda
     * chamada nao acha estado nenhum.
     */
    public static void end(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer caster)) return;
        CompoundTag tag = state(caster);
        if (tag == null) return;
        caster.getPersistentData().remove(KEY);

        caster.removeEffect(MagiaEffects.ANACHRONIC_SIGHT);
        caster.removeEffect(MobEffects.INVISIBILITY);
        caster.removeEffect(MagiaEffects.UNTARGETABLE);
        MagiaNetwork.sendVisualTo(caster, SpellVisualPayload.Kind.ANACHRONIC_SIGHT, 0, caster.position(), 0);

        int elapsed = Math.max(0, caster.server.getTickCount() - tag.getInt("Start"));
        int overSeconds = Math.max(0, elapsed - MagiaConfig.ANACHRONIC_SAFE_SECONDS.get() * 20) / 20;
        if (caster.isAlive()) {
            returnHome(caster, tag);
            int ticks = (MagiaConfig.ANACHRONIC_FRAGILE_BASE.get() + MagiaConfig.ANACHRONIC_FRAGILE_EXTRA.get() * overSeconds) * 20;
            if (ticks > 0) {
                caster.addEffect(new MobEffectInstance(MagiaEffects.FRAGILE, ticks, overSeconds > 10 ? 1 : 0,
                        false, false, true));
            }
            caster.sendSystemMessage(Component.translatable(overSeconds > 0
                    ? "aurorion_magia.anacronico.volta_pesada" : "aurorion_magia.anacronico.volta",
                    ticks / 20).withStyle(ChatFormatting.GOLD));
            caster.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    /** O alvo saiu (morreu, deslogou): quem olhava por ele volta. Uma volta na lista de jogadores. */
    public static void release(ServerPlayer target) {
        for (ServerPlayer player : target.server.getPlayerList().getPlayers()) {
            CompoundTag tag = state(player);
            if (tag != null && tag.hasUUID("Target") && tag.getUUID("Target").equals(target.getUUID())) {
                // Pode estar no meio do tick de entidades da dimensao: o retorno vai para o fim do tick.
                player.server.tell(new TickTask(player.server.getTickCount(), () -> end(player)));
            }
        }
    }

    private static void returnHome(ServerPlayer caster, CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dim"));
        ServerLevel home = id == null ? null : caster.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (home == null) return;
        double x = tag.getDouble("X"), y = tag.getDouble("Y"), z = tag.getDouble("Z");
        float yaw = tag.getFloat("Yaw"), pitch = tag.getFloat("Pitch");
        TwilightTeleportCompat.immediateRun(() -> caster.teleportTo(home, x, y, z, Set.of(), yaw, pitch));
        caster.resetFallDistance();
    }

    @Nullable
    private static CompoundTag state(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }

    @Nullable
    private static ServerPlayer target(ServerPlayer caster, CompoundTag tag) {
        if (!tag.hasUUID("Target")) return null;
        UUID id = tag.getUUID("Target");
        return caster.server.getPlayerList().getPlayer(id);
    }
}
