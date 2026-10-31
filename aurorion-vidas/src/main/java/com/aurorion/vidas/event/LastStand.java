package com.aurorion.vidas.event;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.SheetCheck;
import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Último fôlego: num golpe que mataria, um teste de Fortitude da ficha (o derivado que o site calcula)
 * deixa a pessoa de pé com 1 de vida, uma vez a cada {@code ultimoFolego.horas}. Não cobra vida porque a
 * morte não aconteceu.
 *
 * <p>Prioridade alta: roda antes de quem cobra a vida ({@link VidasServerEvents#onDeath}, LOW) e de quem
 * derruba no chão (PlayerRevive); cancelado aqui, ninguém mais vê a morte. Dano que ignora
 * invulnerabilidade ({@code /kill}, vazio) não tem salvação. O carimbo do último uso fica na parte do NBT
 * do jogador que o NeoForge copia na morte, então morrer de verdade não zera a espera.
 */
@EventBusSubscriber(modid = AurorionVidas.MOD_ID)
public final class LastStand {
    private static final String KEY = "aurorion_vidas:ultimo_folego";

    private LastStand() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !LivesConfig.LAST_STAND.get()) return;
        if (player.isCreative() || player.isSpectator() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long now = System.currentTimeMillis();
        long last = persisted.getLong(KEY);
        if (last > 0 && now - last < LivesConfig.LAST_STAND_HOURS.get() * 3_600_000L) return;

        CharacterSheet.Stats stats = CharacterSheet.of(player.server, player.getUUID());
        int bonus = stats.known() ? stats.derived("fortitude", 0) : LivesConfig.LAST_STAND_DEFAULT_BONUS.get();
        if (!SheetCheck.roll(player.getRandom(), bonus, LivesConfig.LAST_STAND_DC.get(), false, 0).success()) return;

        event.setCanceled(true);
        player.setHealth(1.0f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        persisted.putLong(KEY, now);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 0.7f);
        player.displayClientMessage(Component.translatable("aurorion_vidas.ultimo_folego").withStyle(ChatFormatting.GOLD), true);
    }

    /** Personagem novo: o fôlego do anterior não conta. */
    public static void reset(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(KEY);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
