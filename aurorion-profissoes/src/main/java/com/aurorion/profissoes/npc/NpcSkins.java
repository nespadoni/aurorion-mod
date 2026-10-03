package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.data.Profession;

/**
 * A skin de cada oficio, embutida no mod ({@code assets/aurorion_profissoes/textures/entity/npc/}).
 * Todo NPC usa a do seu oficio sem precisar de nada no catalogo; o campo {@code skin} do
 * JSON e o {@code /npc skin} continuam existindo so como excecao.
 */
public final class NpcSkins {
    public record Skin(String texture, boolean slim) {}

    private static final Skin NURSE = skin("enfermeira", true);
    private static final Skin SMITH = skin("ferreira", true);
    private static final Skin CHEF = skin("chef", false);
    private static final Skin ARCANIST = skin("arcanista", false);
    private static final Skin MERCHANT = skin("mercador", false);

    private NpcSkins() {}

    public static Skin of(Profession profession) {
        return switch (profession) {
            case DOCTOR -> NURSE;
            case SMITH -> SMITH;
            case CHEF -> CHEF;
            case ARCANIST -> ARCANIST;
            case NONE, BROKER -> MERCHANT;
        };
    }

    private static Skin skin(String name, boolean slim) {
        return new Skin(AurorionProfissoes.MOD_ID + ":textures/entity/npc/" + name + ".png", slim);
    }
}
