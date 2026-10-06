package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A Possessao: quem conjurou entra no corpo de outro jogador.
 *
 * <h2>Como funciona</h2>
 *
 * <ul>
 *   <li>Quem possui ({@code possuindo}) e levado para dentro do alvo, invisivel, intocavel e sem
 *       atacar, quebrar ou usar nada. Ele anda, pula, agacha e olha normalmente — e a cada tick o
 *       servidor poe o corpo do possuido exatamente onde ele esta, olhando para onde ele olha. Para
 *       quem esta em volta, e o possuido que anda.</li>
 *   <li>O possuido ({@code possuido}) nao age: nao anda por conta propria, nao usa item, nao ataca, nao
 *       conjura. Mas <b>fala</b> — o chat continua dele.</li>
 *   <li>O que quem possui digita no chat sai <b>com o nome do possuido</b>, como fala dele, inclusive no
 *       balao do {@code aurorion-talk}. Os dois falam pela mesma boca. No Voice Chat, a voz de quem
 *       possui sai do mesmo lugar que a do possuido, porque os dois estao no mesmo ponto.</li>
 * </ul>
 *
 * <p>Acaba com a segunda conjuracao, com o tempo, com a morte de qualquer um dos dois, ao deslogar e
 * ao trocar de dimensao. Quem possuia volta para onde estava quando conjurou, na dimensao em que
 * estava.
 *
 * <h2>Custo</h2>
 *
 * <p>O relogio e o efeito {@code possuindo}, que tica so em quem possui. Por tick: um teleporte do
 * possuido (um pacote de posicao para ele). Sem ninguem possuido, nada roda.
 */
public final class Possession {
    private static final String KEY = AurorionMagia.MOD_ID + ":possessao";

    private Possession() {
    }

    public static boolean isPossessing(LivingEntity entity) {
        return entity.hasEffect(MagiaEffects.POSSESSING);
    }

    public static void start(ServerPlayer caster, ServerPlayer target, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Target", target.getUUID());
        tag.putDouble("X", caster.getX());
        tag.putDouble("Y", caster.getY());
        tag.putDouble("Z", caster.getZ());
        tag.putString("Dim", caster.level().dimension().location().toString());
        caster.getPersistentData().put(KEY, tag);

        if (MagicData.getPlayerMagicData(target).isCasting()) Utils.serverSideCancelCast(target);
        target.stopUsingItem();
        target.addEffect(new MobEffectInstance(MagiaEffects.POSSESSED, ticks, 0, false, false, true), caster);
        caster.addEffect(new MobEffectInstance(MagiaEffects.POSSESSING, ticks, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, false));
        caster.addEffect(new MobEffectInstance(MagiaEffects.UNTARGETABLE, ticks, 0, false, false, false));
        caster.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYRot(), target.getXRot());

        AurorionSpell.sound(target, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.POSSESSIO_CORPORIS, ticks);
        target.displayClientMessage(Component.translatable("aurorion_magia.possuido").withStyle(ChatFormatting.DARK_PURPLE), true);
        caster.sendSystemMessage(Component.translatable("aurorion_magia.possuindo", target.getDisplayName())
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    /**
     * Um tick da possessao, chamado pelo efeito {@code possuindo}.
     *
     * @return false quando ela acabou — o efeito devolve isso ao vanilla, que o remove
     */
    public static boolean tick(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer caster) || !caster.isAlive()) return false;
        ServerPlayer target = target(caster);
        if (target == null || !target.isAlive() || target.level() != caster.level()
                || !target.hasEffect(MagiaEffects.POSSESSED)) return false;

        target.connection.teleport(caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), caster.getXRot());
        target.setYHeadRot(caster.getYHeadRot());
        target.setShiftKeyDown(caster.isShiftKeyDown());
        target.setSprinting(caster.isSprinting());
        target.resetFallDistance();
        return true;
    }

    /**
     * Fim da possessao, de qualquer lado. Idempotente: a remocao dos efeitos dispara este mesmo caminho
     * de novo, e a segunda chamada ja nao encontra estado nenhum.
     */
    public static void end(LivingEntity caster) {
        CompoundTag data = caster.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag tag = data.getCompound(KEY);
        data.remove(KEY);

        if (caster instanceof ServerPlayer player) {
            ServerPlayer target = target(player, tag);
            if (target != null) {
                target.removeEffect(MagiaEffects.POSSESSED);
                MagiaNetwork.sendVisual(player, target, SpellVisualPayload.Kind.POSSESSIO_CORPORIS, 0);
                target.displayClientMessage(Component.translatable("aurorion_magia.corpo_devolvido")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            player.removeEffect(MagiaEffects.POSSESSING);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(MagiaEffects.UNTARGETABLE);
            // De volta para onde estava — na dimensao onde estava. Se ela nao existir mais (ou o estado veio
            // de uma versao sem a dimensao gravada), fica onde esta: teleportar coordenadas do Overworld
            // dentro do Nether enterraria a pessoa na pedra.
            ServerLevel home = origin(player, tag);
            if (player.isAlive() && home != null) {
                player.teleportTo(home, tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                        Set.of(), player.getYRot(), player.getXRot());
                player.resetFallDistance();
            }
            AurorionSpell.sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.6f);
        }
    }

    /** O possuido saiu (morreu, deslogou, tomou leite de outro jeito): solta quem o possuia. */
    public static void release(ServerPlayer target) {
        for (ServerPlayer player : target.server.getPlayerList().getPlayers()) {
            if (target.getUUID().equals(targetId(player))) end(player);
        }
    }

    /**
     * Chat de quem esta possuindo: sai como fala do possuido. {@code true} se a mensagem foi
     * redirecionada (e a original deve ser cancelada).
     */
    public static boolean speak(ServerPlayer caster, Component message) {
        ServerPlayer target = target(caster);
        if (target == null) return false;
        // Boca calada (Voz Interdita, Campo Estatico) nao fala nem por dentro.
        if (target.hasEffect(MagiaEffects.SILENCED)) {
            caster.displayClientMessage(Component.translatable("aurorion_magia.sem_voz").withStyle(ChatFormatting.DARK_PURPLE), true);
            return true;
        }
        target.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", target.getDisplayName(), message), false);
        return true;
    }

    /** O corpo em que {@code caster} esta, se esta em algum. */
    @Nullable
    public static ServerPlayer possessedBy(ServerPlayer caster) {
        return target(caster);
    }

    /** Quem esta dentro do corpo de {@code target}, se alguem esta. Uma volta na lista de jogadores. */
    @Nullable
    public static ServerPlayer casterOf(ServerPlayer target) {
        for (ServerPlayer player : target.server.getPlayerList().getPlayers()) {
            if (target.getUUID().equals(targetId(player))) return player;
        }
        return null;
    }

    @Nullable
    private static ServerLevel origin(ServerPlayer player, CompoundTag tag) {
        if (!tag.contains("Dim", Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dim"));
        return id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Nullable
    private static ServerPlayer target(ServerPlayer caster) {
        CompoundTag data = caster.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? target(caster, data.getCompound(KEY)) : null;
    }

    @Nullable
    private static ServerPlayer target(ServerPlayer caster, CompoundTag tag) {
        return tag.hasUUID("Target") ? caster.server.getPlayerList().getPlayer(tag.getUUID("Target")) : null;
    }

    @Nullable
    private static UUID targetId(ServerPlayer caster) {
        CompoundTag data = caster.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = data.getCompound(KEY);
        return tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }
}
