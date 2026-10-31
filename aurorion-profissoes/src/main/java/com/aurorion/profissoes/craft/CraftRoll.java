package com.aurorion.profissoes.craft;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.SheetCheck;
import com.aurorion.core.character.SkillTreeGate;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.data.ProfessionData;
import com.aurorion.profissoes.registry.ProfessionItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O teste de ofício: d20 + Criação da ficha contra a CD do item ({@link CraftDifficulty}).
 *
 * <p>Três mãos possíveis, sempre a mesma regra:
 * <ul>
 *   <li><b>jogador</b> (bancada, mesa de ferraria): a ficha dele, vantagem no domínio da profissão;</li>
 *   <li><b>dono</b> de uma fornalha: a ficha de quem a colocou, mesmo offline (o retrato fica em disco);</li>
 *   <li><b>máquina</b> sem dono (Create, adaptadores): um bônus fixo do config.</li>
 * </ul>
 *
 * <p>Falha: os ingredientes se perdem e sai uma Peça Arruinada. Crítico em obra de CD alta: obra-prima.
 * Custo por fabricação: um {@code HashMap.get} do retrato, um da CD e um ou dois números aleatórios.
 */
public final class CraftRoll {
    /** Chave do dono no {@code getPersistentData()} de uma fornalha. */
    public static final String OWNER_KEY = "aurorion_profissoes:dono";

    public enum Decision {
        /** Falta o nó da Trama que o item exige: nem tenta. */
        BLOCKED,
        FAIL, CRIT_FAIL, SUCCESS, CRIT;

        public boolean failed() {
            return this == FAIL || this == CRIT_FAIL;
        }
    }

    /**
     * Decisão já tomada para a retirada em curso (shift-click), para o {@code onTake} que o vanilla chama
     * depois não testar de novo. Só a thread do servidor mexe; o mixin limpa ao sair do shift-click.
     */
    @Nullable
    private static Decision pending;
    /** A peça extra de uma obra-prima empilhável no shift-click, entregue só se o vanilla moveu algo. */
    @Nullable
    private static ItemStack extra;

    private CraftRoll() {
    }

    // --- Quem faz -----------------------------------------------------------------------------------

    /** O jogador está fabricando {@code result} agora. */
    public static Decision forPlayer(ServerPlayer player, ItemStack result, int extraDc) {
        return forPlayer(player, result, extraDc, Profession.NONE);
    }

    /**
     * @param station profissão dona da estação (as mesas do Apotheosis são do arcanista): rola com vantagem
     *                também, além do domínio do próprio item
     */
    public static Decision forPlayer(ServerPlayer player, ItemStack result, int extraDc, Profession station) {
        if (!ProfessionsConfig.craftEnabled() || result.isEmpty() || ProfessionApi.staff(player)) return Decision.SUCCESS;
        CraftDifficulty.Entry entry = CraftDifficulty.of(result);
        if (!entry.node().isEmpty() && SkillTreeGate.installed() && !SkillTreeGate.has(player, entry.node())) return Decision.BLOCKED;
        Profession profession = ProfessionApi.of(player);
        boolean advantage = profession != Profession.NONE && (profession == entry.domain() || profession == station);
        return judge(player.getRandom(), bonus(player.server, player.getUUID(), false), entry.dc() + extraDc, advantage);
    }

    /** O item exige um nó da Trama que o jogador não tem? (Só a regra do nó; não rola nada.) */
    public static boolean allowed(ServerPlayer player, ItemStack result) {
        if (!ProfessionsConfig.craftEnabled() || result.isEmpty() || ProfessionApi.staff(player)) return true;
        String node = CraftDifficulty.of(result).node();
        return node.isEmpty() || !SkillTreeGate.installed() || SkillTreeGate.has(player, node);
    }

    /** Uma fornalha (ou outra máquina com dono gravado) terminou {@code result}. */
    public static Decision forBlock(BlockEntity block, ItemStack result, int extraDc) {
        return forBlock(block, result, extraDc, null, false);
    }

    public static Decision forBlock(BlockEntity block, ItemStack result, int extraDc, @Nullable UUID hand) {
        return forBlock(block, result, extraDc, hand, false);
    }

    /**
     * Máquina de tecnologia (Create, Oritech): a mesma regra, mas a mão soma a perícia Tecnologia à Criação —
     * quem entende de engrenagem monta máquina que erra menos. Sem bloco conhecido, bônus de máquina.
     */
    public static Decision forTechnology(@Nullable BlockEntity block, RandomSource random, ItemStack result) {
        if (block == null) return forMachine(random, result);
        return forBlock(block, result, 0, null, true);
    }

    /**
     * @param hand quem opera, quando o próprio mod sabe (o Let's Do guarda quem abriu a panela por último);
     *             {@code null} usa quem colocou o bloco, e sem isso o bônus de máquina
     */
    private static Decision forBlock(BlockEntity block, ItemStack result, int extraDc, @Nullable UUID hand, boolean technology) {
        if (!ProfessionsConfig.craftEnabled() || result.isEmpty() || block.getLevel() == null) return Decision.SUCCESS;
        MinecraftServer server = block.getLevel().getServer();
        CraftDifficulty.Entry entry = CraftDifficulty.of(result);
        UUID owner = !ProfessionsConfig.FURNACE_OWNER.get() ? null : hand != null ? hand : owner(block);
        if (server == null || owner == null) return judge(block.getLevel().getRandom(), ProfessionsConfig.MACHINE_BONUS.get(), entry.dc() + extraDc, false);
        boolean advantage = entry.domain() != Profession.NONE && ProfessionData.get(server).of(owner) == entry.domain();
        return judge(block.getLevel().getRandom(), bonus(server, owner, technology), entry.dc() + extraDc, advantage);
    }

    /** Máquina sem dono: o bônus fixo de máquina. */
    public static Decision forMachine(RandomSource random, ItemStack result) {
        if (!ProfessionsConfig.craftEnabled() || result.isEmpty()) return Decision.SUCCESS;
        return judge(random, ProfessionsConfig.MACHINE_BONUS.get(), CraftDifficulty.of(result).dc(), false);
    }

    @Nullable
    public static UUID owner(BlockEntity block) {
        var data = block.getPersistentData();
        return data.hasUUID(OWNER_KEY) ? data.getUUID(OWNER_KEY) : null;
    }

    public static int bonus(MinecraftServer server, UUID profile) {
        return bonus(server, profile, false);
    }

    /** Criação da ficha (ou o padrão, sem ficha lida); em máquina de tecnologia, mais o treino de Tecnologia. */
    public static int bonus(MinecraftServer server, UUID profile, boolean technology) {
        CharacterSheet.Stats stats = CharacterSheet.of(server, profile);
        if (!stats.known()) return ProfessionsConfig.CRAFT_DEFAULT_BONUS.get();
        return technology ? stats.craftBonus() + stats.skill(ProfessionsConfig.TECH_SKILL.get(), 0) : stats.craftBonus();
    }

    private static Decision judge(RandomSource random, int bonus, int dc, boolean advantage) {
        return switch (SheetCheck.roll(random, bonus, dc, advantage, ProfessionsConfig.CRAFT_NATURAL_MIN_DC.get())) {
            case CRIT -> dc >= ProfessionsConfig.MASTERPIECE_DC.get() ? Decision.CRIT : Decision.SUCCESS;
            case SUCCESS -> Decision.SUCCESS;
            case FAIL -> Decision.FAIL;
            case CRIT_FAIL -> Decision.CRIT_FAIL;
        };
    }

    // --- Shift-click: decisão tomada uma vez por peça -------------------------------------------------

    /**
     * Shift-click do resultado, chamado no começo do {@code quickMoveStack} de cada menu coberto. Testa uma
     * vez por peça (o vanilla repete o shift-click em loop, e cada volta é uma peça):
     * <ul>
     *   <li>sem o nó: recusa e para o loop;</li>
     *   <li>falha: {@code consume} gasta os ingredientes sem entregar nada (o {@code onTake} que ele chama
     *   vê a falha marcada, zera a cópia e dá a Peça Arruinada) e o loop para;</li>
     *   <li>sucesso: marca para o {@code onTake} do vanilla não testar de novo. Na obra-prima, o item não
     *   empilhável leva a assinatura (só com espaço livre: sem ele, o vanilla não move e a peça ficaria
     *   assinada no slot); o empilhável ganha a peça extra em {@link #finishQuickMove}, só se o vanilla moveu.</li>
     * </ul>
     *
     * @return {@code null} para seguir com o vanilla; senão o valor a devolver (vazio, que para o loop)
     */
    @Nullable
    public static ItemStack quickMove(ServerPlayer player, ItemStack result, Runnable consume) {
        pending = null;
        extra = null;
        if (result.isEmpty()) return null;
        Decision decision = forPlayer(player, result, 0);
        switch (decision) {
            case BLOCKED -> {
                blocked(player, result);
                return ItemStack.EMPTY;
            }
            case FAIL, CRIT_FAIL -> {
                pending = decision;
                try {
                    consume.run();
                } finally {
                    pending = null;
                }
                return ItemStack.EMPTY;
            }
            case CRIT -> {
                if (result.getMaxStackSize() > 1) extra = result.copyWithCount(1);
                else if (player.getInventory().getFreeSlot() >= 0) masterpiece(player, result);
            }
            default -> {
            }
        }
        pending = Decision.SUCCESS;
        return null;
    }

    /** Fim do shift-click: entrega a peça extra da obra-prima se algo foi movido, e limpa a marca. */
    public static void finishQuickMove(ServerPlayer player, boolean moved) {
        ItemStack bonus = extra;
        pending = null;
        extra = null;
        if (bonus != null && moved) {
            player.getInventory().placeItemBackInInventory(bonus);
            masterpiece(player, ItemStack.EMPTY);
        }
    }

    /**
     * Retirada com clique (o {@code onTake} do slot de resultado), já com a peça na mão. Se veio do
     * shift-click, a decisão de lá vale; senão, testa aqui.
     */
    public static void take(ServerPlayer player, ItemStack stack) {
        Decision decision = pending;
        pending = null;
        boolean fromQuickMove = decision != null;
        if (decision == null) decision = forPlayer(player, stack, 0);
        if (decision.failed() || decision == Decision.BLOCKED) {
            stack.setCount(0);
            failed(player, decision == Decision.BLOCKED ? Decision.FAIL : decision);
        } else if (decision == Decision.CRIT && !fromQuickMove) {
            masterpiece(player, stack);
        }
    }

    // --- O que acontece ---------------------------------------------------------------------------------

    /** Fumaça e som de uma peça perdida numa máquina (ninguém para ler mensagem). */
    public static void machineFailed(BlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        var pos = block.getBlockPos();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.2f);
    }

    public static ItemStack ruined() {
        return new ItemStack(ProfessionItems.RUINED_PIECE.get());
    }

    /** A peça se perdeu na mão do jogador: a Peça Arruinada vai para o inventário (ou cai no chão). */
    public static void failed(ServerPlayer player, Decision decision) {
        player.getInventory().placeItemBackInInventory(ruined());
        lost(player, decision, Component.translatable(decision == Decision.CRIT_FAIL
                ? "aurorion_profissoes.oficio.falha_critica" : "aurorion_profissoes.oficio.falha"));
    }

    /** Fumaça, som e a mensagem de uma fabricação que falhou (sem a Peça Arruinada: quem chama decide). */
    public static void lost(ServerPlayer player, Decision decision, Component message) {
        boolean critical = decision == Decision.CRIT_FAIL;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.25, 0.2, 0.25, 0.01);
        level.playSound(null, player.blockPosition(), critical ? SoundEvents.ITEM_BREAK : SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.4f, critical ? 0.8f : 1.6f);
        player.displayClientMessage(message.copy().withStyle(critical ? ChatFormatting.DARK_RED : ChatFormatting.GRAY), true);
    }

    public static void blocked(Player player, ItemStack result) {
        CraftDifficulty.Entry entry = CraftDifficulty.of(result);
        player.displayClientMessage(Component.translatable("aurorion_profissoes.oficio.sem_no", result.getHoverName(), entry.node())
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Obra-prima: o item não empilhável ganha a assinatura do personagem; o empilhável sai com um a mais.
     * Muda {@code stack} no lugar (é a peça que o jogador está levando).
     */
    public static void masterpiece(@Nullable ServerPlayer maker, ItemStack stack) {
        // Pilha vazia: só o aviso (a peça extra do shift-click já foi entregue).
        if (stack.getMaxStackSize() > 1) {
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) stack.grow(1);
        } else if (!stack.isEmpty()) {
            Component line = maker == null
                    ? Component.translatable("aurorion_profissoes.oficio.obra_prima_anonima")
                    : Component.translatable("aurorion_profissoes.oficio.obra_prima", maker.getName().getString());
            ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
            List<Component> lines = new ArrayList<>(lore.lines());
            lines.add(line.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (maker != null) {
            maker.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, maker.getX(), maker.getY() + 1.0, maker.getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            maker.serverLevel().playSound(null, maker.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
            maker.displayClientMessage(Component.translatable("aurorion_profissoes.oficio.critico").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Chance de falhar numa CD, para a aba Habilidades. */
    public static double failChance(ServerPlayer player, int dc, boolean advantage) {
        return SheetCheck.failChance(bonus(player.server, player.getUUID()), dc, advantage, ProfessionsConfig.CRAFT_NATURAL_MIN_DC.get());
    }
}
