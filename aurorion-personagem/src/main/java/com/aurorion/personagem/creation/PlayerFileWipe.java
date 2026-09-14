package com.aurorion.personagem.creation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Apaga o jogador do mundo como se ele nunca tivesse entrado.
 *
 * <h2>Por que apagar arquivo, e nao zerar campo</h2>
 *
 * <p>A primeira versao limpava item por item: inventario, XP, avancos, estatisticas, e um trecho para
 * cada mod do ecossistema. Isso funcionava para o que <b>conheciamos</b> — e deixava passar tudo o
 * mais. Num modpack pesado, a maior parte da progressao de um jogador nao esta em lugar nenhum que
 * este mod possa listar: esta dentro do proprio {@code playerdata/<uuid>.dat}, em NBT persistente e
 * em data attachments que cada mod grava do seu jeito. Zerar o que se conhece e, por construcao,
 * deixar intacto o que se desconhece.
 *
 * <p>Apagar o arquivo inverte isso: o padrao passa a ser "nao sobrevive", e o que precisa sobreviver
 * e que tem de ser dito em voz alta. Personagem novo e personagem novo.
 *
 * <h2>Por que a pessoa precisa estar desconectada</h2>
 *
 * <p>Com o dono online, o arquivo no disco e uma <b>copia velha</b>: o {@code ServerPlayer} em
 * memoria e a verdade, e ele reescreve o arquivo no logout. Apagar agora devolveria tudo alguns
 * segundos depois. Por isso a troca desconecta primeiro e apaga depois, com a conta ja fora da lista
 * de jogadores — momento em que o vanilla tambem ja soltou os contadores de estatistica e avanco que
 * mantinha em memoria para ela.
 *
 * <h2>O que nao e apagado</h2>
 *
 * <p>Nada que seja da pessoa: OP, whitelist, banimento, UUID de autenticacao. E nada que outro mod
 * guarde <b>fora</b> do arquivo do jogador — {@code SavedData} proprio, arquivo por jogador numa
 * pasta do mod, tabela num banco. Para esses continua valendo o {@code CharacterResetEvent}.
 */
public final class PlayerFileWipe {
    private PlayerFileWipe() {
    }

    /**
     * @throws IOException se algum arquivo resistir. Quem chama <b>nao pode</b> publicar a identidade
     *                     nova nesse caso: melhor repetir a troca no proximo login do que deixar a
     *                     pessoa renascer com o inventario da vida anterior.
     */
    public static void apply(MinecraftServer server, UUID account) throws IOException {
        Path playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);

        // .dat_old e o backup que o vanilla mantem, e .dat_new e o temporario de uma escrita
        // interrompida. Deixar qualquer um dos dois para tras e deixar a vida anterior recuperavel.
        Files.deleteIfExists(playerData.resolve(account + ".dat"));
        Files.deleteIfExists(playerData.resolve(account + ".dat_old"));
        Files.deleteIfExists(playerData.resolve(account + ".dat_new"));

        Files.deleteIfExists(server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(account + ".json"));
        Files.deleteIfExists(server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(account + ".json"));
    }
}
