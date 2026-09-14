package com.aurorion.personagem.creation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashSet;

/**
 * Apaga do jogador tudo que pertencia ao personagem anterior — a parte que o vanilla guarda.
 *
 * <h2>Por que no jogador vivo, e nao no arquivo</h2>
 *
 * <p>A tentacao obvia e apagar {@code playerdata/<uuid>.dat}. Nao funciona: enquanto a pessoa esta
 * conectada, o arquivo e uma <b>copia velha</b> — o {@link ServerPlayer} em memoria e a verdade, e
 * ele reescreve o arquivo no logout. Apagar o arquivo com o dono online devolve o inventario antigo
 * alguns segundos depois. Zerando a entidade viva, o save seguinte grava o estado limpo sozinho.
 *
 * <h2>O que e do personagem e o que e da conta</h2>
 *
 * <p>Nada aqui toca OP, whitelist, banimento ou o UUID de autenticacao: essas coisas sao da pessoa.
 * Inventario, XP, avancos, estatisticas, receitas, tags e placar sao da historia que acabou.
 *
 * <p>Todos os passos sao idempotentes. Rodar duas vezes — o que acontece quando um reset foi
 * interrompido e retomado no login seguinte — da o mesmo resultado que rodar uma.
 */
public final class VanillaReset {
    private VanillaReset() {
    }

    public static void apply(ServerPlayer player) {
        MinecraftServer server = player.server;

        player.stopRiding();
        player.ejectPassengers();
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();

        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.inventoryMenu.getCraftSlots().clearContent();
        player.inventoryMenu.broadcastChanges();

        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.removeAllEffects();
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);

        clearAdvancements(player, server);
        clearStats(player);
        clearRecipes(player, server);
        clearScoreboard(player, server);

        // Copia da lista: removeTag mexe no mesmo conjunto que estariamos percorrendo.
        new HashSet<>(player.getTags()).forEach(player::removeTag);
    }

    /**
     * Revoga criterio a criterio, que e a unica forma que o vanilla oferece.
     *
     * <p>{@code getCompletedCriteria} devolve uma lista nova a cada chamada, entao revogar durante a
     * iteracao e seguro — e o proprio {@code PlayerAdvancements} reenvia o que mudou para o cliente.
     */
    private static void clearAdvancements(ServerPlayer player, MinecraftServer server) {
        var advancements = player.getAdvancements();

        for (var holder : server.getAdvancements().getAllAdvancements()) {
            var progress = advancements.getOrStartProgress(holder);

            for (String criterion : progress.getCompletedCriteria()) {
                advancements.revoke(holder, criterion);
            }
        }
    }

    /**
     * Nao existe "zerar tudo" no vanilla, e nao da para ler o mapa interno do contador. O caminho
     * possivel e o inverso: percorrer os registros e zerar cada estatistica que poderia existir.
     *
     * <p>Sao ~6 mil chaves — caro para um tick, irrelevante para algo que acontece uma vez na vida de
     * um personagem. O {@code sendStats} no fim evita que o cliente continue exibindo os numeros
     * antigos na tela de estatisticas ate reconectar.
     */
    private static void clearStats(ServerPlayer player) {
        ServerStatsCounter stats = player.getStats();

        for (StatType<?> type : BuiltInRegistries.STAT_TYPE) {
            clearStatType(stats, player, type);
        }
        stats.sendStats(player);
    }

    private static <T> void clearStatType(ServerStatsCounter stats, ServerPlayer player, StatType<T> type) {
        for (T value : type.getRegistry()) {
            stats.setValue(player, type.get(value), 0);
        }
    }

    private static void clearRecipes(ServerPlayer player, MinecraftServer server) {
        player.getRecipeBook().removeRecipes(server.getRecipeManager().getRecipes(), player);
    }

    /**
     * Placar e time acompanham o nome no servidor, entao seguiriam valendo para o personagem novo —
     * inclusive pontuacao de datapack, que e onde muito modpack guarda progressao.
     */
    private static void clearScoreboard(ServerPlayer player, MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        scoreboard.resetAllPlayerScores(player);

        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
    }

    /** O ponto de nascimento: o spawn do mundo, corrigido se ele estiver dentro de algo. */
    public static BlockPos birthplace(net.minecraft.server.level.ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos safe = com.aurorion.core.level.SafeSpot.nearestVertical(overworld, spawn, 8);
        return safe == null ? spawn : safe;
    }
}
