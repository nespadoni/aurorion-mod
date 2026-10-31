package com.aurorion.trama.server;

import com.aurorion.core.character.CharacterAbilities;
import com.aurorion.core.character.CharacterAbilities.Group;
import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.core.character.CharacterData;
import com.aurorion.core.house.HouseGate;
import com.aurorion.trama.config.TramaConfig;
import com.aurorion.trama.progression.ProgressData;
import com.aurorion.trama.progression.ProgressRules;
import com.aurorion.trama.skill.Rating;
import com.aurorion.trama.skill.SkillCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.api.SkillsAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A Trama do Eco do personagem na aba Habilidades da ficha, pelo {@link CharacterAbilities} do core.
 *
 * <p>Só leitura, e nada que crie estado: {@link ProgressData#find} (não {@code TramaRuntime.entry},
 * que apaga a categoria de um personagem trocado) e {@link CharacterData#find}. O Pufferfish entrega os
 * nós comprados da memória do jogador. Roda quando alguém abre a ficha, nunca por tick.
 *
 * <p>Os Fios (+1 num atributo) viram uma linha só com a soma; Início, Princípios, Maestrias e Notáveis
 * aparecem um a um.
 */
public final class TramaAbilities {
    private static final String KIND_THREAD = "Fio";

    private TramaAbilities() {
    }

    public static List<Group> collect(ServerPlayer player) {
        UUID id = player.getUUID();
        Integer houseColor = HouseGate.colorOf(player.server, id);
        String color = houseColor == null ? "#58C8B4" : CharacterAbilities.color(houseColor);
        List<Item> items = new ArrayList<>();
        if (!TramaConfig.ENABLED.get()) {
            items.add(Item.of("Trama do Eco", "A Trama está desligada no servidor agora.", "", State.LOCKED, ""));
            return List.of(new Group("trama", "Trama do Eco", color, 30, items));
        }
        ResourceLocation house = HouseGate.of(player.server, id);
        if (house == null || !HouseGate.exists(house) || !house.getNamespace().equals("aurorion_ethereal")) {
            items.add(Item.of("Trama do Eco", "A árvore abre quando o personagem é vinculado a uma Casa.", "",
                    State.LOCKED, "minecraft:amethyst_shard"));
            return List.of(new Group("trama", "Trama do Eco", color, 30, items));
        }

        // Entrada de outro personagem (reset ainda não reconciliado) conta como formação nova.
        ProgressData.Entry entry = ProgressData.get(player.server).find(id);
        CharacterData.Character character = CharacterData.get(player.server).find(id);
        boolean mine = entry != null && character != null && entry.character.equals(character.id());
        int earned = mine ? entry.earned : ProgressRules.INITIAL_POINTS;
        long experience = mine ? entry.experience : 0;
        long seconds = mine ? entry.activeSeconds : 0;

        Optional<Category> category = SkillsAPI.getCategory(TramaRuntime.CATEGORY).filter(c -> c.isUnlocked(player));
        int left = category.map(c -> c.getPointsLeft(player)).orElse(0);
        items.add(formation(earned, experience, seconds, left, TramaConfig.XP_FIRST.get(), TramaConfig.XP_INCREMENT.get(),
                TramaConfig.MINUTES_FIRST.get(), TramaConfig.MINUTES_INCREMENT.get()));

        if (category.isPresent()) {
            List<String> unlocked = category.get().streamUnlockedSkills(player).map(Skill::getId)
                    .filter(SkillCatalog::contains).sorted().toList();
            items.addAll(nodes(unlocked));
        }
        return List.of(new Group("trama", "Trama do Eco", color, 30, items));
    }

    /** A linha de formação: pontos conquistados e quanto falta para o próximo (XP e tempo, os dois). */
    static Item formation(int earned, long experience, long seconds, int left,
                          int xpFirst, int xpIncrement, int minutesFirst, int minutesIncrement) {
        String level = earned + "/" + ProgressRules.MAX_POINTS;
        String spare = left == 1 ? "1 ponto para gastar." : left + " pontos para gastar.";
        if (earned >= ProgressRules.MAX_POINTS) {
            return Item.of("Formação completa", spare + " Todos os pontos da Trama foram conquistados.", level,
                    State.ACTIVE, "minecraft:amethyst_cluster");
        }
        long xpFrom = ProgressRules.experienceRequired(earned, xpFirst, xpIncrement);
        long xpTo = ProgressRules.experienceRequired(earned + 1, xpFirst, xpIncrement);
        long timeFrom = ProgressRules.secondsRequired(earned, minutesFirst, minutesIncrement);
        long timeTo = ProgressRules.secondsRequired(earned + 1, minutesFirst, minutesIncrement);
        // Os dois requisitos valem juntos: a barra anda pelo que estiver mais atrasado.
        int progress = Math.min(percent(experience - xpFrom, xpTo - xpFrom), percent(seconds - timeFrom, timeTo - timeFrom));
        long xpMissing = Math.max(0, xpTo - experience);
        long minutesMissing = Math.max(0, (timeTo - seconds + 59) / 60);
        String detail = spare + " Próximo ponto: faltam " + xpMissing + " XP de formação e " + minutesMissing
                + " min ativos. Descubra biomas, conclua conquistas e pratique.";
        return Item.of("Formação", detail, level, State.PROGRESS, "minecraft:amethyst_shard").withProgress(progress);
    }

    /** Um item por nó que não é Fio; os Fios somados numa linha só, no fim. */
    static List<Item> nodes(List<String> unlocked) {
        List<Item> special = new ArrayList<>();
        Map<Rating, Integer> threads = new EnumMap<>(Rating.class);
        int threadCount = 0;
        for (String id : unlocked) {
            SkillCatalog.Node node = SkillCatalog.node(id);
            if (node == null) continue;
            if (KIND_THREAD.equals(node.kind())) {
                threadCount++;
                SkillCatalog.ratings(id).forEach((r, v) -> threads.merge(r, v, Integer::sum));
                continue;
            }
            special.add(Item.of(node.name().isEmpty() ? id : node.name(), node.description(), node.kind(),
                    State.ACTIVE, iconOf(node.kind())));
        }
        special.sort(Comparator.comparingInt((Item i) -> rank(i.level())).thenComparing(Item::name));
        List<Item> out = new ArrayList<>(special);
        if (threadCount > 0) {
            StringBuilder sum = new StringBuilder();
            threads.forEach((r, v) -> {
                if (!sum.isEmpty()) sum.append(", ");
                sum.append(r.name()).append(" +").append(v);
            });
            out.add(Item.of(threadCount == 1 ? "1 Fio" : threadCount + " Fios", sum.isEmpty() ? "" : sum + ".",
                    KIND_THREAD, State.ACTIVE, "minecraft:string"));
        }
        return out;
    }

    private static int rank(String kind) {
        return switch (kind) {
            case "Arcano" -> 0;
            case "Início", "Especialidade" -> 0;
            case "Princípio" -> 1;
            case "Maestria" -> 2;
            case "Notável" -> 3;
            default -> 4;
        };
    }

    private static String iconOf(String kind) {
        return switch (kind) {
            case "Início" -> "minecraft:nether_star";
            case "Princípio" -> "minecraft:end_crystal";
            case "Maestria" -> "minecraft:echo_shard";
            case "Arcano" -> "minecraft:lapis_lazuli";
            case "Especialidade" -> "minecraft:enchanted_book";
            default -> "minecraft:amethyst_shard";
        };
    }

    private static int percent(long done, long total) {
        if (total <= 0) return 100;
        return (int) Math.max(0, Math.min(100, done * 100 / total));
    }
}
