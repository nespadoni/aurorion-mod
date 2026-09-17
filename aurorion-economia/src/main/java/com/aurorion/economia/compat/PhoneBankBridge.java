package com.aurorion.economia.compat;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import com.aurorion.economia.server.Wallet;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Liga o app de banco do telefone MikasRevs/Mattupolis ao saldo do Aurorion.
 *
 * <h2>Por que precisa de mixin</h2>
 *
 * <p>O telefone tem a tela, os pacotes, o extrato, o log de auditoria e o teto diario prontos, mas o
 * saldo em si nunca existiu: no bytecode da versao instalada, {@code getSnapshot} devolve
 * <i>sempre</i> "Phone Bank is running without an economy integration" e {@code transfer} devolve
 * <i>sempre</i> "Bank transfers are disabled because no economy integration is installed" — sem
 * desvio nenhum, sem interface de servico, sem ponto de extensao. Nenhum mod de economia instalado
 * ao lado faz o app funcionar. Interceptar os dois metodos e o unico caminho.</p>
 *
 * <h2>Por que reflexao</h2>
 *
 * <p>O telefone e opcional e nao entra no classpath de compilacao, entao {@code BankSnapshot} e
 * {@code ActionResult} nao existem como tipo aqui. Os construtores sao resolvidos uma vez e
 * guardados; se o telefone nao estiver instalado, tudo isto fica inerte e o mod segue funcionando
 * pelos comandos.</p>
 *
 * <h2>Unidade</h2>
 *
 * <p>O campo de quantia do app e um inteiro, entao o numero que o jogador digita ali e lido em
 * <b>fragmentos</b> — a unidade indivisivel. Digitar 123 paga 12 óbolos e 3 fragmentos. Ler em
 * obolos impediria pagar qualquer troco pelo celular.</p>
 */
public final class PhoneBankBridge {
    private static final String STORE = "com.mattupolis.phone.server.bank.PhoneBankServerStore";

    private static boolean resolved;
    private static Constructor<?> snapshotConstructor;
    private static Constructor<?> actionConstructor;
    private static Method recordTransferDetailed;
    private static Method recordTransferWithNote;
    private static Method recordTransferLegacy;
    private static Method syncToPlayer;

    private PhoneBankBridge() {
    }

    /** @return o {@code BankSnapshot} do telefone, ou null para deixar o comportamento original. */
    public static Object snapshot(ServerPlayer player) {
        if (!resolve()) return null;

        long balance = Wallet.balance(player.server, player.getUUID());
        return construct(snapshotConstructor, true, Money.format(balance), balance, "");
    }

    /** @return o {@code ActionResult} do telefone, ou null para deixar o comportamento original. */
    public static Object transfer(ServerPlayer payer, String targetName, long amount, String note) {
        if (!resolve()) return null;

        ServerPlayer target = payer.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            // O telefone roteia por nick da conta, nao pelo nome do personagem que a tela mostra.
            return construct(actionConstructor, false, "Jogador offline ou inexistente.");
        }

        Transfer.Result result = Wallet.transfer(payer.server, payer.getUUID(), target.getUUID(), amount);
        if (!result.ok()) {
            return construct(actionConstructor, false, switch (result) {
                case INSUFFICIENT -> "Saldo insuficiente: voce tem " + Money.describe(
                        Wallet.balance(payer.server, payer.getUUID())) + ".";
                case SAME_ACCOUNT -> "Nao da para transferir para si mesmo.";
                case TARGET_FULL -> "A conta de destino nao comporta esse valor.";
                case INVALID_AMOUNT, OK -> "Quantia invalida.";
            });
        }

        record(payer, target, amount, note);
        return construct(actionConstructor, true, "Enviado: " + Money.describe(amount) + ".");
    }

    /**
     * Devolve o pagamento ao extrato e a auditoria do proprio telefone, para que a aba de historico
     * e o painel de admin continuem contando a mesma historia que a carteira.
     *
     * <p>Falhar aqui nao desfaz o pagamento: o dinheiro ja mudou de dono na carteira, que e a fonte
     * de verdade. Some so a linha bonita no extrato do telefone, e isso vira aviso no log.</p>
     */
    private static void record(ServerPlayer payer, ServerPlayer target, long amount, String note) {
        try {
            String cleanNote = note == null ? "" : note;
            if (recordTransferDetailed != null) {
                recordTransferDetailed.invoke(null, payer, target.getUUID(), target.getGameProfile().getName(),
                        amount, "obolos", cleanNote, Money.describe(amount));
            } else if (recordTransferWithNote != null) {
                recordTransferWithNote.invoke(null, payer, target, amount, "obolos", cleanNote);
            } else {
                recordTransferLegacy.invoke(null, payer, target, amount, "obolos");
            }
            syncToPlayer.invoke(null, payer);
            syncToPlayer.invoke(null, target);
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.warn("Pagamento feito, mas o extrato do telefone nao registrou.", e);
        }
    }

    private static Object construct(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.error("Nao foi possivel montar a resposta do banco do telefone.", e);
            return null;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return snapshotConstructor != null;
        resolved = true;

        try {
            Class<?> store = Class.forName(STORE);
            snapshotConstructor = store.getClassLoader()
                    .loadClass(STORE + "$BankSnapshot")
                    .getDeclaredConstructor(boolean.class, String.class, long.class, String.class);
            actionConstructor = store.getClassLoader()
                    .loadClass(STORE + "$ActionResult")
                    .getDeclaredConstructor(boolean.class, String.class);
            recordTransferDetailed = findMethod(store, "recordTransfer",
                    ServerPlayer.class, java.util.UUID.class, String.class, long.class, String.class, String.class, String.class);
            recordTransferWithNote = findMethod(store, "recordTransfer",
                    ServerPlayer.class, ServerPlayer.class, long.class, String.class, String.class);
            recordTransferLegacy = store.getMethod("recordTransfer",
                    ServerPlayer.class, ServerPlayer.class, long.class, String.class);
            syncToPlayer = store.getMethod("syncToPlayer", ServerPlayer.class);

            snapshotConstructor.setAccessible(true);
            actionConstructor.setAccessible(true);

            AurorionEconomia.LOGGER.info("Banco do telefone ligado a carteira do Aurorion.");
            return true;
        } catch (ClassNotFoundException e) {
            // O telefone e opcional: sem ele o mod vive dos comandos.
            snapshotConstructor = null;
            return false;
        } catch (ReflectiveOperationException e) {
            AurorionEconomia.LOGGER.error(
                    "O telefone esta instalado, mas o banco dele mudou de forma: o app vai continuar "
                            + "desligado. Conferir PhoneBankServerStore nesta versao do telefone.", e);
            snapshotConstructor = null;
            return false;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
