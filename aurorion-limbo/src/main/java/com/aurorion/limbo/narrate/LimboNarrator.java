package com.aurorion.limbo.narrate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Tudo que o Limbo diz a alguem passa por aqui, e nunca por uma chamada direta a um mod de terceiro.
 *
 * <h2>Por que existe</h2>
 *
 * <p>O modpack tem mais de duzentos mods. Qualquer um deles pode sumir numa atualizacao, e o Limbo
 * nao pode cair junto — o exilio e o prazo sao regra de servidor, e regra de servidor nao pode
 * depender de quem desenha a letra na tela. Esta interface e a mesma ideia da SDD §9.1 (dois mods
 * que dependem um do outro sem se conhecerem), levada para fora do ecossistema: <b>a mecanica perde
 * brilho sem o Immersive Messages, nao perde funcionamento</b>.
 *
 * <h2>Por que chat e o fundo do poco, e nao o normal</h2>
 *
 * <p>Com 80 jogadores o chat e mangueira de incendio, e o proprio {@code aurorion-talk} existe para
 * tirar fala do HUD de chat. Um aviso de que alguem esta a seis horas da morte definitiva nao pode
 * disputar espaco com "vendo diamante". O {@link ChatNarrator} existe para o mod continuar
 * funcionando, nao porque chat seja um bom lugar para isto.
 */
public interface LimboNarrator {
    /** A pessoa acabou de cair. Mensagem privada, pesada, para quem caiu. */
    void fall(ServerPlayer player);

    /** O servidor inteiro fica sabendo que <em>alguem</em> caiu — com nome ou sem, ver config. */
    void announceFall(MinecraftServer server, String name, boolean withName);

    /** Chegada, ou volta ao jogo, dentro do Limbo. Lembra onde esta e quanto falta. */
    void arrival(ServerPlayer player, long remainingMillis);

    /** O prazo entrou numa faixa nova (6h, 1h, 15min...). So nas transicoes. */
    void deadlineBand(ServerPlayer player, long remainingMillis);

    /** Bateu na coleira e foi empurrado de volta. */
    void leash(ServerPlayer player);

    /** A janela da Porta abriu: a coleira caiu e caminhar passa a valer alguma coisa. */
    void doorWindowOpen(ServerPlayer player);

    /** A Porta apareceu. */
    void doorAppeared(ServerPlayer player, BlockPos pos);

    /** Saiu pela Porta do Esquecido. */
    void escaped(ServerPlayer player, int timesForgotten);

    /** Alguem devolveu vida: resgate. */
    void rescued(ServerPlayer player);

    /** O prazo venceu com a pessoa online. */
    void expired(ServerPlayer player);

    /**
     * Escolhido uma vez, no primeiro uso.
     *
     * <p>{@code ModList} sozinho nao basta: o mod pode estar presente com uma API diferente da que
     * este codigo conhece. Por isso o {@link ImmersiveNarrator} tambem se recusa a existir se as
     * assinaturas que ele espera nao estiverem la — melhor cair para o chat do que estourar no meio
     * do momento mais dramatico do servidor.
     */
    static LimboNarrator create() {
        if (ModList.get().isLoaded("immersivemessages") && ImmersiveBridge.available()) {
            return new ImmersiveNarrator();
        }
        return new NativeNarrator();
    }
}
