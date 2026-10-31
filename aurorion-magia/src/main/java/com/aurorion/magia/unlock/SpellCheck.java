package com.aurorion.magia.unlock;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.SheetCheck;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O teste da ficha a cada conjuração: d20 + bônus de magia da ficha contra a CD da raridade conjurada.
 *
 * <ul>
 *   <li><b>Falha:</b> a magia não sai e o custo é cheio — mana e recarga; o pergaminho se perde.</li>
 *   <li><b>Falha crítica:</b> o mesmo, mais Exausto (lento, sem conjurar) e uma tontura curta.</li>
 *   <li><b>Crítico:</b> a magia sai com nível a mais. Dano, duração, alcance e quantidade crescem com o
 *   nível em toda magia do Iron's e do Aurorion, então vale para todas sem código por magia.</li>
 * </ul>
 *
 * <p>A especialidade dá vantagem e bônus no teste. Custo por tentativa: um {@code HashMap.get} do retrato
 * da ficha, um do perfil arcano e um ou dois números aleatórios — nada por tick.
 *
 * <p>Não testa: criativo, segunda conjuração de magia de ligar e desligar (desligar não pode falhar e prender a
 * pessoa na própria magia) e recast do Iron's (a primeira conjuração já foi testada).
 */
public final class SpellCheck {
    /** Crítico à espera do {@code SpellOnCastEvent}, que pode vir só no fim de uma conjuração longa. */
    private record Crit(String spell, long until) { }
    /** Magia de ligar e desligar que acabou de ligar: a próxima conjuração dela desliga, sem teste. */
    private record Toggle(String spell, long until) { }

    private static final long TOGGLE_MEMORY_TICKS = 20L * 60 * 10;
    private static final Map<UUID, Crit> CRITS = new HashMap<>();
    private static final Map<UUID, Toggle> TOGGLED = new HashMap<>();

    private SpellCheck() {
    }

    /** @return {@code true} se a conjuração segue; {@code false} se falhou (e o custo já foi cobrado). */
    public static boolean attempt(ServerPlayer player, AbstractSpell spell, int level, CastSource source) {
        // Criativo (staff ensaiando aula) não testa; a staff jogando personagem em sobrevivência testa.
        if (!MagiaConfig.TEST_ENABLED.get() || player.isCreative()) return true;
        long now = player.level().getGameTime();
        Toggle toggle = TOGGLED.get(player.getUUID());
        if (toggle != null && toggle.spell().equals(spell.getSpellId())) {
            TOGGLED.remove(player.getUUID());
            if (now <= toggle.until()) return true;
        }
        if (MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spell)) return true;

        boolean specialist = ArcaneProfile.isSpecialty(player, spell.getSchoolType());
        SheetCheck.Outcome outcome = SheetCheck.roll(player.getRandom(), bonus(player, specialist), difficulty(spell, level),
                specialist, MagiaConfig.NATURAL_MIN_DC.get());
        switch (outcome) {
            case CRIT -> {
                if (MagiaConfig.CRIT_LEVELS.get() > 0) {
                    CRITS.put(player.getUUID(), new Crit(spell.getSpellId(), now + spell.getEffectiveCastTime(level, player) + 60));
                }
                golden(player);
                return true;
            }
            case SUCCESS -> {
                return true;
            }
            case FAIL -> {
                charge(player, spell, level, source);
                broken(player, false);
                return false;
            }
            default -> {
                charge(player, spell, level, source);
                broken(player, true);
                int exhausted = MagiaConfig.EXHAUST_SECONDS.get() * 20;
                if (exhausted > 0) player.addEffect(new MobEffectInstance(MagiaEffects.EXHAUSTED, exhausted, 0, false, true, true));
                int dizzy = MagiaConfig.DIZZY_SECONDS.get() * 20;
                if (dizzy > 0) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dizzy, 0, false, false, true));
                return false;
            }
        }
    }

    /** O nível a mais do crítico, quando a magia de fato sai. {@code -1}: sem crítico para esta conjuração. */
    public static int critLevel(ServerPlayer player, AbstractSpell spell, int level) {
        Crit crit = CRITS.remove(player.getUUID());
        if (crit == null || !crit.spell().equals(spell.getSpellId()) || player.level().getGameTime() > crit.until()) return -1;
        int extra = MagiaConfig.CRIT_LEVELS.get();
        return Math.min(level + extra, spell.getMaxLevel() + extra);
    }

    /** A conjuração que terminou ligou uma magia de ligar e desligar (ver {@code ToggleCooldown}). */
    public static void toggledOn(ServerPlayer player, AbstractSpell spell) {
        TOGGLED.put(player.getUUID(), new Toggle(spell.getSpellId(), player.level().getGameTime() + TOGGLE_MEMORY_TICKS));
    }

    public static void forget(UUID player) {
        CRITS.remove(player);
        TOGGLED.remove(player);
    }

    public static void clear() {
        CRITS.clear();
        TOGGLED.clear();
    }

    /** Bônus do teste: o da ficha (ou o padrão, sem ficha lida), mais o do especialista. */
    public static int bonus(ServerPlayer player, boolean specialist) {
        CharacterSheet.Stats stats = CharacterSheet.of(player.server, player.getUUID());
        int bonus = stats.known() ? stats.magicBonus() : MagiaConfig.DEFAULT_BONUS.get();
        return specialist ? bonus + MagiaConfig.SPECIALIST_BONUS.get() : bonus;
    }

    public static int difficulty(AbstractSpell spell, int level) {
        return difficulty(spell.getRarity(Math.max(level, spell.getMinLevel())), SpellAccess.isForbidden(spell));
    }

    public static int difficulty(SpellRarity rarity, boolean forbidden) {
        int dc = switch (rarity) {
            case COMMON -> MagiaConfig.DC_COMMON.get();
            case UNCOMMON -> MagiaConfig.DC_UNCOMMON.get();
            case RARE -> MagiaConfig.DC_RARE.get();
            case EPIC -> MagiaConfig.DC_EPIC.get();
            case LEGENDARY -> MagiaConfig.DC_LEGENDARY.get();
        };
        return forbidden ? dc + MagiaConfig.DC_FORBIDDEN.get() : dc;
    }

    /**
     * Custo cheio de uma magia que não saiu. O cancelamento do {@code SpellPreCastEvent} vem antes de o
     * Iron's cobrar qualquer coisa, então a cobrança é feita aqui, pelos mesmos caminhos dele: a mana
     * (só de quem gasta mana: livro e arma), a recarga pelo {@code MagicManager} (que passa pelo evento de
     * recarga — a redução do especialista vale) e o pergaminho, que se perde.
     */
    private static void charge(ServerPlayer player, AbstractSpell spell, int level, CastSource source) {
        if (source == CastSource.SCROLL) {
            consumeScroll(player);
            return;
        }
        if (source.consumesMana() && !player.getAbilities().instabuild) {
            MagicData magic = MagicData.getPlayerMagicData(player);
            magic.setMana(Math.max(0, magic.getMana() - spell.getManaCost(level)));
        }
        if (source.respectsCooldown()) IronsSpellbooks.MAGIC_MANAGER.addCooldown(player, spell, source);
    }

    private static void consumeScroll(ServerPlayer player) {
        if (player.getAbilities().instabuild) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof IScroll) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static void broken(ServerPlayer player, boolean critical) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(critical ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                player.getX(), player.getY() + player.getBbHeight() * 0.7, player.getZ(), critical ? 18 : 10, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), critical ? SoundEvents.GLASS_BREAK : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.6f, critical ? 0.6f : 1.4f);
        player.displayClientMessage(Component.translatable(critical ? "aurorion_magia.teste.falha_critica" : "aurorion_magia.teste.falha")
                .withStyle(critical ? ChatFormatting.DARK_RED : ChatFormatting.GRAY), true);
    }

    private static void golden(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                24, 0.4, 0.5, 0.4, 0.6);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.displayClientMessage(Component.translatable("aurorion_magia.teste.critico").withStyle(ChatFormatting.GOLD), true);
    }

    /** Chance de falhar, para a aba Habilidades. */
    public static double failChance(ServerPlayer player, SpellRarity rarity, boolean forbidden, boolean specialist) {
        return SheetCheck.failChance(bonus(player, specialist), difficulty(rarity, forbidden), specialist,
                MagiaConfig.NATURAL_MIN_DC.get());
    }
}
