package com.aurorion.core.house;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Existe um sistema de casas neste servidor, e em qual casa esta um jogador?
 *
 * <p>Mesmo desenho do {@link com.aurorion.core.character.CharacterGate}, e pela mesma razao: o core
 * guarda a <b>pergunta</b>, um mod responde, e os outros perguntam sem se conhecerem. Quem responde
 * hoje e o {@code aurorion-ethereal} (dono das casas); quem pergunta e o {@code aurorion-areas} (que
 * barra a entrada de quem nao e da casa). Sem o Ethereal no pack, tudo aqui responde "nao ha casa" e
 * as areas de casa simplesmente nao barram ninguem — nenhum dos dois mods deixa de carregar.
 *
 * <p>Esta classe e a excecao consciente a regra "so entra no core o que ja estava duplicado" (ver o
 * README): ela nao deduplica nada, ela e um <b>contrato</b>. A alternativa seria o
 * {@code aurorion-areas} importar o {@code aurorion-ethereal} — o que tornaria um obrigatorio para o
 * outro, contra a SDD §3 — ou alcanca-lo por reflexao, como se faz com mod de terceiro. Entre dois
 * mods nossos, um contrato tipado e conferido pelo compilador e melhor que uma string de reflexao.
 *
 * <p><b>E se o Ethereal esquecer de chamar {@link #provide}?</b> Nada quebra silenciosamente do lado
 * de quem pergunta: {@link #installed()} responde {@code false} e quem depende disso avisa no log em
 * vez de agir como se ninguem tivesse casa. Essa e a pergunta que o README do core manda fazer antes
 * de qualquer coisa entrar aqui.
 *
 * <p>O campo e {@code volatile} porque o registro acontece na construcao do mod e as consultas vem da
 * thread do servidor: a publicacao precisa ser visivel, mas nao ha escrita concorrente para proteger.
 */
public final class HouseGate {

    /** O que o dono das casas precisa saber responder. Tudo aqui e consulta; nada escreve. */
    public interface Houses {
        /** @return a casa do jogador, ou {@code null} se ele nao tem casa. Aceita jogador offline. */
        @Nullable
        ResourceLocation of(MinecraftServer server, UUID player);

        /** Ids do catalogo atual, para autocomplete de comando. Nunca nulo, pode ser vazio. */
        List<ResourceLocation> ids();

        /** @return true se esse id existe no catalogo carregado agora. */
        boolean exists(ResourceLocation house);

        /**
         * @return o nome da casa ja tingido com a cor dela, ou {@code null} se o id nao existe mais
         * no catalogo. Quem pergunta mostra isso a um jogador; sem ele sobraria o id cru na tela.
         */
        @Nullable
        Component nameOf(ResourceLocation house);
    }

    @Nullable
    private static volatile Houses houses;

    private HouseGate() {
    }

    /** Chamado uma vez pelo mod dono das casas, na construcao dele. */
    public static void provide(Houses value) {
        houses = value;
    }

    /** Ha um mod de casas instalado? Quem barra alguem por casa precisa distinguir isto de "sem casa". */
    public static boolean installed() {
        return houses != null;
    }

    @Nullable
    public static ResourceLocation of(MinecraftServer server, UUID player) {
        Houses current = houses;
        return current == null ? null : current.of(server, player);
    }

    public static List<ResourceLocation> ids() {
        Houses current = houses;
        return current == null ? List.of() : current.ids();
    }

    public static boolean exists(ResourceLocation house) {
        Houses current = houses;
        return current != null && current.exists(house);
    }

    /** O nome da casa para mostrar a um jogador; cai no id quando nao ha catalogo que a conheca. */
    public static Component nameOf(ResourceLocation house) {
        Houses current = houses;
        Component name = current == null ? null : current.nameOf(house);
        return name == null ? Component.literal(house.toString()) : name;
    }
}
