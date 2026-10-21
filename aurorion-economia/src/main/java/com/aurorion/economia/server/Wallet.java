package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.character.CharacterGate;
import com.aurorion.core.lives.LivesGate;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.UUID;

/**
 * A porta de entrada do dinheiro no servidor. Todo caminho que move saldo — comando, app de banco
 * do celular, loja futura — passa por aqui, para que exista <b>um</b> lugar onde a conta e conferida.
 *
 * <p>Tambem e aqui que cada mudanca vira linha do {@link MoneyLedger}: a origem ({@code "pagar"},
 * {@code "celular"}, {@code "npc:<id>:loja"}...) chega por parametro de quem chama. As sobrecargas
 * sem origem continuam para compatibilidade e gravam {@code "nao_informada"}.</p>
 *
 * <p>Thread do servidor apenas, como o {@code SavedData} que ela usa.</p>
 */
public final class Wallet {
    public static final long STARTER_BALANCE = 4L * Money.FRAGMENTS_PER_OBOLO;
    private static final String UNSPECIFIED = "nao_informada";

    private Wallet() {
    }

    public static long balance(MinecraftServer server, UUID player) {
        return WalletData.get(server).balance(player);
    }

    /** Define um saldo exato. Negativo vira zero. Uso de staff. */
    public static void set(MinecraftServer server, UUID player, long fragments) {
        set(server, player, fragments, UNSPECIFIED);
    }

    public static void set(MinecraftServer server, UUID player, long fragments, String origin) {
        WalletData data = WalletData.get(server);
        long before = data.balance(player);
        long after = Math.min(Math.max(fragments, 0L), Money.MAX);
        if (data.setBalance(player, after)) {
            ledgerDelta(server, player, after - before, origin);
            EconomyProjectorNotifier.refresh(server);
        }
    }

    /**
     * Soma (ou subtrai, com quantia negativa) sem deixar passar do teto nem ficar negativo.
     *
     * @return o saldo depois da operacao
     */
    public static long add(MinecraftServer server, UUID player, long fragments) {
        return add(server, player, fragments, UNSPECIFIED);
    }

    public static long add(MinecraftServer server, UUID player, long fragments, String origin) {
        WalletData data = WalletData.get(server);
        long before = data.balance(player);
        long after = fragments > 0
                ? Math.min(before + Math.min(fragments, Money.MAX), Money.MAX)
                : Math.max(before + fragments, 0L);

        if (data.setBalance(player, after)) {
            ledgerDelta(server, player, after - before, origin);
            EconomyProjectorNotifier.refresh(server);
        }
        return after;
    }

    /**
     * Move dinheiro de um personagem para outro. Nada e criado nem destruido: e a mesma quantia
     * saindo de um lado e entrando no outro, gravada de uma vez so.
     */
    public static Transfer.Result transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        return transfer(server, from, to, amount, UNSPECIFIED);
    }

    /**
     * Alem da aritmetica do {@link Transfer}, duas regras que dependem do mundo:
     *
     * <ul>
     *   <li><b>O dinheiro morre com o personagem.</b> Quem esta no Limbo (sem vidas), morto ou ainda
     *       sem nome nao envia dinheiro ({@link Transfer.Result#SENDER_BLOCKED}). Sem isso, o jeito
     *       de escapar da regra era passar tudo para um amigo entre a queda e o reset.</li>
     *   <li><b>A bolsa inicial nao circula de imediato</b> ({@link WalletData#STARTER_LOCK_TICKS}):
     *       e o que tira a graca de criar contas secundarias so para doar a bolsa.</li>
     * </ul>
     */
    public static Transfer.Result transfer(MinecraftServer server, UUID from, UUID to, long amount, String origin) {
        if (!from.equals(to) && !canSend(server, from)) return Transfer.Result.SENDER_BLOCKED;
        WalletData data = WalletData.get(server);
        long fromBalance = data.balance(from);
        long toBalance = data.balance(to);

        Transfer.Result result = Transfer.check(fromBalance, toBalance, amount, from.equals(to));
        if (!result.ok()) return result;
        if (data.transferable(from, playTicks(server, from)) < amount) return Transfer.Result.STARTER_LOCKED;

        data.setBalance(from, fromBalance - amount);
        data.setBalance(to, toBalance + amount);
        MoneyLedger.transfer(server, from, to, amount, origin);
        EconomyProjectorNotifier.refresh(server);
        return result;
    }

    /**
     * Esta conta pode mandar dinheiro para outra agora? Nao, se o personagem esta morto, se esta no
     * Limbo (o {@code aurorion-vidas} responde zero vidas pelo {@link LivesGate}) ou se o criador de
     * personagens esta instalado e ele ainda nao tem nome.
     */
    public static boolean canSend(MinecraftServer server, UUID account) {
        CharacterData.Character character = CharacterData.get(server).find(account);
        if (character != null && character.dead()) return false;
        if (CharacterGate.creationEnabled() && (character == null || !character.named())) return false;
        int lives = LivesGate.of(server, account);
        return lives == LivesGate.UNKNOWN || lives > 0;
    }

    /** Bolsa de nascimento: quatro Obolos, uma vez por ID de personagem, presa por tempo de jogo. */
    public static boolean grantStarter(MinecraftServer server, UUID characterId, UUID account) {
        long ticks = playTicks(server, account);
        boolean granted = WalletData.get(server).grantStarter(characterId, account, STARTER_BALANCE, Math.max(0L, ticks));
        if (granted) {
            MoneyLedger.credit(server, account, STARTER_BALANCE, "bolsa_inicial:" + characterId);
            EconomyProjectorNotifier.refresh(server);
        }
        return granted;
    }

    /** O personagem morreu de vez: saldo zerado e trava da bolsa esquecida. */
    public static void resetCharacter(MinecraftServer server, UUID account) {
        set(server, account, 0L, "morte_definitiva");
        WalletData.get(server).clearStarterLock(account);
    }

    /** {@code minecraft:play_time} da conta, em ticks; -1 se offline. */
    static long playTicks(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        return online == null ? -1L : online.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    private static void ledgerDelta(MinecraftServer server, UUID player, long delta, String origin) {
        if (delta > 0) MoneyLedger.credit(server, player, delta, origin);
        else if (delta < 0) MoneyLedger.debit(server, player, -delta, origin);
    }
}
