package com.aurorion.core.event;

import com.aurorion.core.AurorionCore;
import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.core.data.SavedDataAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * O listener de ciclo de vida do {@code aurorion-core}, e ele existe para que os outros mods
 * <b>nao</b> precisem de um. (A regra de itens mantidos na morte tem o seu, em
 * {@code death/KeptOnDeath}.)
 *
 * <p>Antes, cada mod que guardava um {@link SavedDataAccess} tinha que lembrar de zera-lo ao parar o
 * servidor — dois dos quatro nao lembravam. Centralizar aqui transforma "cada autor precisa saber
 * disso" em "ja esta resolvido".
 */
@EventBusSubscriber(modid = AurorionCore.MOD_ID)
public final class CoreServerEvents {
    private CoreServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.aurorion.core.character.CharacterData.get(player.server).current(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SavedDataAccess.invalidateAll();
        com.aurorion.core.death.DeathId.reset();
    }

    /**
     * Avisa quando sobrou config no lugar antigo.
     *
     * <p>As configs do ecossistema passaram de {@code config/aurorion_*.toml} para
     * {@code config/aurorion/*.toml}. O NeoForge nao migra nada: ele gera um arquivo <b>novo com os
     * padroes</b> no caminho novo e ignora o velho. O servidor sobe, nada quebra, e todos os ajustes
     * de quem administra viram default em silencio.
     *
     * <p>Esse e o tipo de falha que nao aparece no boot nem no log — aparece uma semana depois,
     * quando alguem nota que o exilio voltou a mandar para o Nether. Por isso o aviso existe, e por
     * isso ele diz <b>exatamente</b> qual arquivo copiar para onde.
     *
     * <p>Nao migramos sozinhos de proposito: mover arquivo de config de um servidor em producao sem
     * ser pedido e pior que o problema que resolveria. O aviso some quando os arquivos antigos forem
     * removidos.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Path configDir = FMLPaths.CONFIGDIR.get();

        try (Stream<Path> files = Files.list(configDir)) {
            List<String> leftovers = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("aurorion_") && name.endsWith(".toml"))
                    .sorted()
                    .toList();

            if (leftovers.isEmpty()) return;

            AurorionCore.LOGGER.warn("As configs do Aurorion agora ficam em config/{}/. Sobraram {} "
                            + "arquivo(s) no lugar antigo, e eles NAO estao mais sendo lidos:",
                    AurorionConfigs.FOLDER, leftovers.size());

            for (String name : leftovers) {
                String moved = name.substring("aurorion_".length());
                AurorionCore.LOGGER.warn("  config/{}  ->  copie os valores para config/{}/{}",
                        name, AurorionConfigs.FOLDER, moved);
            }

            AurorionCore.LOGGER.warn("Depois de copiar, apague os antigos e este aviso some.");
        } catch (IOException e) {
            AurorionCore.LOGGER.debug("Nao consegui listar {} para checar config antiga", configDir, e);
        }
    }
}
