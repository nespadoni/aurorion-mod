package com.aurorion.profissoes.api;

import com.aurorion.core.character.CharacterAbilities;
import com.aurorion.core.character.CharacterAbilities.Group;
import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.craft.CraftDifficulty;
import com.aurorion.profissoes.craft.CraftRoll;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.data.ProfessionData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A profissão do personagem e o que ela libera, para a aba Habilidades da ficha (pelo
 * {@link CharacterAbilities} do core; este mod não conhece a ficha). Custo: uma busca em hash no
 * {@link ProfessionData} e a leitura de quatro valores do config, só quando a ficha abre.
 *
 * <p>Os números vêm do config em uso, não do README: com {@code specialistEnchantmentMaxLevel = 7}, a
 * ficha diz "até VII".
 */
public final class ProfessionAbilities {
    private ProfessionAbilities() {
    }

    public static List<Group> collect(ServerPlayer player) {
        Profession profession = ProfessionData.get(player.server).of(player.getUUID());
        List<Item> items = items(profession, ProfessionsConfig.enabled(),
                ProfessionsConfig.SPEC.isLoaded() ? ProfessionsConfig.COMMON_ENCHANT_MAX.get() : 3,
                ProfessionsConfig.SPEC.isLoaded() ? ProfessionsConfig.SPECIALIST_ENCHANT_MAX.get() : 10,
                ProfessionsConfig.SPEC.isLoaded() ? ProfessionsConfig.CHEF_QUALITY.get() : 3,
                ProfessionsConfig.SPEC.isLoaded() ? ProfessionsConfig.CHEF_DECAY.get() : .5,
                ModList.get().isLoaded("apotheosis"));
        if (!ProfessionsConfig.craftEnabled()) return List.of(new Group("profissao", "Profissão", "#D9A066", 20, items));
        return List.of(new Group("profissao", "Profissão", "#D9A066", 20, items), craftTest(player, profession));
    }

    /** O teste de ofício: a chance de a peça se perder por faixa de CD, com e sem o domínio da profissão. */
    private static Group craftTest(ServerPlayer player, Profession profession) {
        boolean known = com.aurorion.core.character.CharacterSheet.of(player.server, player.getUUID()).known();
        String detail = "Chance de a peça se perder: material comum " + percent(CraftRoll.failChance(player, CraftDifficulty.TRIVIAL, false))
                + ", comida " + percent(CraftRoll.failChance(player, CraftDifficulty.FOOD, false))
                + ", equipamento " + percent(CraftRoll.failChance(player, CraftDifficulty.EQUIPMENT, false))
                + ", equipamento raro " + percent(CraftRoll.failChance(player, CraftDifficulty.EQUIPMENT + 4, false)) + "."
                + (profession == Profession.NONE ? "" : " No domínio da sua profissão você rola com vantagem: equipamento "
                + percent(CraftRoll.failChance(player, CraftDifficulty.EQUIPMENT, true)) + ".")
                + " Nas máquinas que você montar (Create, Oritech) a Tecnologia soma: equipamento "
                + percent(com.aurorion.core.character.SheetCheck.failChance(CraftRoll.bonus(player.server, player.getUUID(), true),
                        CraftDifficulty.EQUIPMENT, false, ProfessionsConfig.CRAFT_NATURAL_MIN_DC.get())) + "."
                + (known ? " Pela perícia Criação da sua ficha." : " Sem ficha lida: valor de iniciante.");
        List<Item> items = List.of(Item.of("Teste de ofício", detail, "Bônus " + CraftRoll.bonus(player.server, player.getUUID()),
                State.UNLOCKED, "minecraft:crafting_table"));
        return new Group("oficio", "Ofício", "#C9A27A", 21, items);
    }

    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    /** O texto da aba, sem Minecraft: a primeira linha é a profissão, as outras o que ela libera. */
    static List<Item> items(Profession profession, boolean enabled, int commonMax, int specialistMax,
                            int chefQuality, double chefDecay, boolean apotheosis) {
        List<Item> items = new ArrayList<>(6);
        if (profession == Profession.NONE) {
            items.add(Item.of("Sem profissão", "A staff atribui uma profissão ao personagem: médico, ferreiro,"
                    + " cozinheiro, arcanista ou corretor.", "", State.LOCKED, "minecraft:book"));
            return items;
        }
        if (!enabled) {
            items.add(Item.of(profession.label(), "As profissões estão desligadas no servidor agora: nada abaixo"
                    + " vale até a staff religar.", "Profissão", State.LOCKED, iconOf(profession)));
            return items;
        }
        items.add(Item.of(profession.label(), "Uma profissão por personagem. Sobrevive à morte comum.",
                "Profissão", State.ACTIVE, iconOf(profession)));
        switch (profession) {
            case DOCTOR -> {
                perk(items, "Curar pessoa", "Shift+G olhando alguém: restaura a saúde e os ferimentos. Vidas perdidas"
                        + " por morte não voltam.", "minecraft:glistering_melon_slice");
                perk(items, "Medkit e tônico", "Só médicos usam os itens que tratam o corpo inteiro e removem"
                        + " lesões graves.", "minecraft:potion");
                perk(items, "Tratar membro", "Pela tela de atendimento, 1 medkit ou tônico restaura um membro de"
                        + " outra pessoa e tira a lesão grave.", "minecraft:paper");
            }
            case SMITH -> {
                perk(items, "Bigorna e mesa de ferraria", "Só ferreiros usam. Encantamentos existentes são"
                        + " preservados no reparo.", "minecraft:anvil");
                perk(items, "Reparo para outros", "Bigorna a até 3 blocos: cada material recupera até 25% da"
                        + " durabilidade e custa 1 nível.", "minecraft:iron_ingot");
            }
            case CHEF -> {
                perk(items, "Comida de qualidade", "O que você cozinha sai com qualidade nível " + chefQuality
                        + " e estraga " + decay(chefDecay) + ".", "minecraft:cooked_beef");
                perk(items, "Finalizar lote", "Até 16 alimentos com 1 frasco de mel, pela tela de atendimento"
                        + " (lote com pelo menos 60% de frescor).", "minecraft:honey_bottle");
            }
            case ARCANIST -> {
                perk(items, "Mesa de encantamentos completa", "As três opções, sem o teto " + roman(commonMax)
                        + " de quem não é arcanista.", "minecraft:enchanting_table");
                perk(items, "Inscrever livro", "Aplica um livro encantado real em equipamento, até o nível "
                        + roman(specialistMax) + ". 2 níveis de XP por nível acrescentado.", "minecraft:enchanted_book");
                perk(items, "Aprimorar poções", "Redstone e glowstone pela tela de atendimento, com suporte a até"
                        + " 3 blocos.", "minecraft:brewing_stand");
                if (apotheosis) {
                    perk(items, "Apotheosis", "Reforja, salvamento, lapidação, gemas, soquetes e sigilos são do"
                            + " arcanista.", "minecraft:amethyst_shard");
                }
            }
            case BROKER -> items.add(Item.of("Venda de terrenos", "Entra na próxima etapa da integração"
                    + " territorial.", "Em breve", State.LOCKED, "minecraft:filled_map"));
            default -> {
            }
        }
        return items;
    }

    private static void perk(List<Item> items, String name, String detail, String icon) {
        items.add(Item.of(name, detail, "", State.UNLOCKED, icon));
    }

    static String iconOf(Profession profession) {
        return switch (profession) {
            case DOCTOR -> "minecraft:golden_apple";
            case SMITH -> "minecraft:anvil";
            case CHEF -> "minecraft:smoker";
            case ARCANIST -> "minecraft:enchanting_table";
            case BROKER -> "minecraft:filled_map";
            case NONE -> "minecraft:book";
        };
    }

    /** {@code 0.5} → "na metade do ritmo normal"; {@code 0.75} → "a 0,75× do ritmo normal". */
    static String decay(double value) {
        if (Math.abs(value - .5) < 1e-9) return "na metade do ritmo normal";
        String number = String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "a " + number.replace('.', ',') + "× do ritmo normal";
    }

    /** 1–3999 em romano; fora disso, o número. */
    static String roman(int value) {
        if (value <= 0 || value >= 4000) return Integer.toString(value);
        int[] n = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n.length; i++) {
            while (value >= n[i]) {
                out.append(r[i]);
                value -= n[i];
            }
        }
        return out.toString();
    }
}
