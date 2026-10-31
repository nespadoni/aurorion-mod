package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * As magias que alguém tem à mão agora, com o teste já calculado — para o combate com ficha usar como
 * ação sem importar a magia nem o Iron's.
 *
 * <p>O {@code aurorion-magia} responde com as magias do livro equipado que a pessoa pode lançar
 * (Despertar Arcano, liberações), cada uma com a CD e o bônus do teste da magia. Sem a magia no pack,
 * {@link #of} devolve lista vazia e o combate só não oferece magias.
 */
public final class CharacterSpells {
    /**
     * @param id        id da magia (para o log)
     * @param name      nome para mostrar (já traduzível pelo cliente: chave de tradução)
     * @param rarity    0–4 (comum a lendária) no nível em que está no livro
     * @param level     nível da magia no livro
     * @param dc        CD do teste da magia
     * @param bonus     bônus do teste de quem lança
     * @param advantage especialista na escola
     */
    public record Option(String id, String name, int rarity, int level, int dc, int bonus, boolean advantage) {
    }

    @FunctionalInterface
    public interface Spells {
        List<Option> of(ServerPlayer player);
    }

    @Nullable
    private static volatile Spells spells;

    private CharacterSpells() {
    }

    public static void provide(Spells value) {
        spells = value;
    }

    public static boolean installed() {
        return spells != null;
    }

    public static List<Option> of(ServerPlayer player) {
        Spells current = spells;
        if (current == null) return List.of();
        List<Option> options = current.of(player);
        return options == null ? List.of() : options;
    }
}
