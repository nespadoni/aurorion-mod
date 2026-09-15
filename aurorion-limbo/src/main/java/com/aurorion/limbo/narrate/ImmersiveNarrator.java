package com.aurorion.limbo.narrate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * O Limbo falando pelo Immersive Messages, com o chat como rede de seguranca.
 *
 * <p>Cada momento ganha um peso diferente na tela, e a escolha nao e estetica: e o que separa uma
 * informacao util ("faltam 6h") de um acontecimento ("voce caiu"). Tela cheia gasta a atencao da
 * pessoa, entao so os tres momentos irreversiveis a usam.
 *
 * <p>Herda do {@link ChatNarrator} de proposito: quando uma entrega falha — mod atualizado com API
 * diferente, pacote perdido —, a chamada ao {@code super} faz a mensagem sair do mesmo jeito. Numa
 * mecanica em que a pessoa pode perder o personagem por nao ter visto um aviso, <b>silencio e a pior
 * falha possivel</b>.
 */
final class ImmersiveNarrator extends NativeNarrator {
    /** Momento irreversivel: tela cheia, maquina de escrever. */
    private static final float HEAVY_SECONDS = 7f;
    /** Informacao: passa e sai da frente. */
    private static final float LIGHT_SECONDS = 4f;

    @Override
    public void fall(ServerPlayer player) {
        if (!ImmersiveBridge.center(player, LimboText.fall(), LimboText.RUST, HEAVY_SECONDS)) {
            super.fall(player);
        }
    }

    @Override
    public void announceFall(MinecraftServer server, String name, boolean withName) {
        var text = withName ? LimboText.fallPublicNamed(name) : LimboText.fallPublic();
        if (!ImmersiveBridge.broadcast(server, text, LimboText.RUST, LIGHT_SECONDS)) {
            super.announceFall(server, name, withName);
        }
    }

    @Override
    public void arrival(ServerPlayer player, long remainingMillis) {
        if (!ImmersiveBridge.center(player, LimboText.arrival(remainingMillis), LimboText.COLD, LIGHT_SECONDS)) {
            super.arrival(player, remainingMillis);
        }
    }

    @Override
    public void deadlineBand(ServerPlayer player, long remainingMillis) {
        if (!ImmersiveBridge.top(player, LimboText.deadline(remainingMillis), LimboText.AMBER, LIGHT_SECONDS)) {
            super.deadlineBand(player, remainingMillis);
        }
    }

    @Override
    public void leash(ServerPlayer player) {
        // A coleira dispara em rajada quando alguem fica andando contra a borda. Rodape e curto para
        // nao virar spam de tela cheia em quem esta testando o limite.
        if (!ImmersiveBridge.bottom(player, LimboText.leash(), LimboText.COLD, 2f)) {
            super.leash(player);
        }
    }

    @Override
    public void doorWindowOpen(ServerPlayer player) {
        if (!ImmersiveBridge.center(player, LimboText.doorWindowOpen(), LimboText.COLD, LIGHT_SECONDS)) {
            super.doorWindowOpen(player);
        }
    }

    @Override
    public void doorAppeared(ServerPlayer player, BlockPos pos) {
        if (!ImmersiveBridge.center(player, LimboText.doorAppeared(pos), LimboText.COLD, HEAVY_SECONDS)) {
            super.doorAppeared(player, pos);
        }
    }

    @Override
    public void escaped(ServerPlayer player, int timesForgotten) {
        if (!ImmersiveBridge.center(player, LimboText.escaped(timesForgotten), LimboText.RUST, HEAVY_SECONDS)) {
            super.escaped(player, timesForgotten);
        }
    }

    @Override
    public void rescued(ServerPlayer player) {
        if (!ImmersiveBridge.center(player, LimboText.rescued(), LimboText.AMBER, HEAVY_SECONDS)) {
            super.rescued(player);
        }
    }

    @Override
    public void expired(ServerPlayer player) {
        if (!ImmersiveBridge.center(player, LimboText.expired(), LimboText.RUST, HEAVY_SECONDS)) {
            super.expired(player);
        }
    }

    @Override
    public void passageOpened(ServerPlayer player, String target, int cost) {
        if (!ImmersiveBridge.top(player, LimboText.passageOpened(target, cost),
                LimboText.COLD, LIGHT_SECONDS)) {
            super.passageOpened(player, target, cost);
        }
    }

    @Override
    public void passageCrossed(ServerPlayer player) {
        if (!ImmersiveBridge.center(player, LimboText.passageCrossed(), LimboText.COLD, LIGHT_SECONDS)) {
            super.passageCrossed(player);
        }
    }

    @Override
    public void bondUsed(ServerPlayer player, String target) {
        if (!ImmersiveBridge.center(player, LimboText.bondUsed(target), LimboText.AMBER, HEAVY_SECONDS)) {
            super.bondUsed(player, target);
        }
    }
}
