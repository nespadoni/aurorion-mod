package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O celular muda de dono quando o personagem muda.
 *
 * <p>O telefone guarda o estado do cliente em campos estaticos que vivem enquanto o jogo estiver
 * aberto: agenda, conversas, notificacoes, notas, capa, PIN. Nada disso e zerado ao sair de um
 * servidor. Com o {@code /personagem trocar} (que desconecta e reconecta) o alt abria o celular e via
 * as conversas e notificacoes do principal ate reiniciar o jogo.
 *
 * <p>Na entrada, a pasta do telefone passa a ser a do personagem ({@link PhoneCharacterStorage}) e o
 * estado em memoria volta ao de um celular recem-ligado; cada store relê a propria pasta na primeira
 * vez que for usado. Na saida, o que estava pendente e gravado na pasta que ainda e a certa, e a
 * memoria e zerada de novo.
 *
 * <p>Tudo por reflexao, como o {@link PhoneContactCleanup}: o telefone e opcional e fechado. Cada
 * campo e tratado sozinho — um nome que mude numa versao nova do telefone deixa so aquele pedaco sem
 * limpar, e o aviso vai uma vez para o log. O {@code PhoneMixinContractTest} confere os nomes no build.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID, value = Dist.CLIENT)
public final class PhoneSession {
    private static final String GUI = "com.mattupolis.phone.client.gui.";

    private static boolean reportedFailure;
    private static int saveTicks;

    private PhoneSession() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ModList.get().isLoaded("mattupolis_phone")) return;
        LocalPlayer player = event.getPlayer();
        Minecraft minecraft = Minecraft.getInstance();
        UUID profile = player.getGameProfile().getId();
        Path gameDir = minecraft.gameDirectory.toPath();
        Path dir = PhoneCharacterStorage.characterDir(gameDir, profile);

        // Uma saida sem LoggingOut (queda de conexao no meio do login) deixaria o estado do anterior.
        PhoneConversations.flush();
        PhoneConversations.discardSession();
        resetMemory();
        if (PhoneCharacterStorage.isMainProfile(profile, player.getGameProfile().getName(),
                minecraft.getUser().getProfileId(), minecraft.getUser().getName())) {
            PhoneCharacterStorage.migrateLegacy(gameDir, dir);
        }
        PhoneCharacterStorage.activate(dir);
        PhoneConversations.beginSession(dir);
        saveTicks = 0;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!ModList.get().isLoaded("mattupolis_phone")) return;
        if (PhoneCharacterStorage.activeDir() == null) return;
        // Os passos do dia sao gravados com atraso; grava agora, enquanto a pasta ainda e a deste personagem.
        invoke("PhoneHealthStore", "saveNow");
        PhoneConversations.flush();
        PhoneConversations.discardSession();
        resetMemory();
        PhoneCharacterStorage.activate(null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (PhoneCharacterStorage.activeDir() == null) return;
        if (++saveTicks < 20) return;
        saveTicks = 0;
        PhoneConversations.flush();
    }

    /**
     * Devolve o telefone ao estado de recem-ligado. Os valores sao os iniciais de cada store no
     * telefone 1.3.4 — o mesmo que ele teria sem nenhum arquivo gravado.
     */
    static void resetMemory() {
        // Agenda e conversas: sai o que e de jogador; a conversa de sistema do proprio telefone fica.
        forgetPlayerThreads();
        set("PhoneMessagesStore", "playerContactsLoaded", false);
        set("PhoneMessagesStore", "loadingPlayerContacts", false);
        clear("PhoneMessagesStore", "PHONE_ID_TO_PLAYER");
        clear("PhoneMessagesStore", "PLAYER_TO_PHONE_ID");
        set("PhoneMessagesStore", "CURRENT_OPEN_THREAD_ID", "");
        set("PhoneMessagesStore", "CURRENT_PLAYER_PHONE_ID", "");

        invoke("PhoneNotificationStore", "clearPlayerNotifications");
        invoke("PhoneCallHistoryStore", "clear");
        clear("PhoneMailStore", "MAILS");
        invoke("PhoneGpsScreen", "clearGpsTarget");

        clear("PhoneInstagramDmStore", "THREADS");
        clear("PhoneInstagramDmStore", "MESSAGES");
        clear("PhoneInstagramDmStore", "PROCESSED_NETWORK_IDS");
        clear("PhoneInstagramDmStore", "DM_PHOTO_TEXTURES");
        set("PhoneInstagramDmStore", "currentOpenThreadId", "");
        invoke("PhoneInstagramStore", "clearNotifications");
        invoke("PhoneTwitterStore", "clearNotifications");

        set("PhoneNotesStore", "loaded", false);
        clear("PhoneNotesStore", "NOTES");
        set("PhoneCalendarStore", "loaded", false);
        clear("PhoneCalendarStore", "REMINDERS");

        set("PhoneCaseStore", "loaded", false);
        set("PhoneCaseStore", "currentCaseId", "midnight_black");
        set("PhoneWallpaperStore", "loaded", false);
        set("PhoneWallpaperStore", "homeWallpaperId", "mattu_blue");
        set("PhoneWallpaperStore", "lockWallpaperId", "midnight");
        set("PhoneHomeLayoutStore", "loaded", false);
        clear("PhoneHomeLayoutStore", "pages");
        clear("PhoneHomeLayoutStore", "hiddenApps");
        set("PhoneMineStoreState", "loaded", false);
        set("PhoneMineStoreState", "jumpInstalled", false);
        set("PhoneMineStoreState", "calculatorInstalled", false);
        set("PhoneMineStoreState", "colorTapInstalled", false);

        set("PhoneHealthStore", "loaded", false);
        set("PhoneHealthStore", "storedDate", LocalDate.now().toString());
        set("PhoneHealthStore", "todaySteps", 0);
        set("PhoneHealthStore", "distanceMeters", 0.0);
        set("PhoneHealthStore", "activeTicks", 0);
        set("PhoneHealthStore", "foodEaten", 0);
        set("PhoneHealthStore", "dailyGoal", 8000);
        set("PhoneHealthStore", "saveCooldownTicks", 0);
        set("PhoneHealthStore", "lastFoodLevel", -1);

        set("PhoneMarketplaceStore", "loaded", false);
        set("PhoneMarketplaceStore", "extrasLoaded", false);
        clear("PhoneMarketplaceStore", "LISTINGS");
        clear("PhoneMarketplaceStore", "COMMENTS");
        clear("PhoneMarketplaceStore", "FAVORITE_IDS");
        clear("PhoneMarketplaceStore", "VIEW_COUNTS");

        // O PIN e por personagem: sem isto, o do anterior continuaria valendo ate reiniciar o jogo.
        set("PhoneSettingsStore", "pinLoaded", false);
        set("PhoneSettingsStore", "pinContextKey", "");
        set("PhoneSettingsStore", "lockPinCode", "");

        // Banco: saldo e extrato voltam na proxima sincronizacao do servidor; os favoritos, da pasta.
        set("PhoneBankStore", "favoritesLoaded", false);
        clear("PhoneBankStore", "favoriteAccounts");
        set("PhoneBankStore", "unlocked", false);
        set("PhoneBankStore", "available", false);
        set("PhoneBankStore", "balanceText", "0");
        set("PhoneBankStore", "coreBalance", 0L);
        set("PhoneBankStore", "history", List.of());
        set("PhoneBankStore", "accounts", List.of());
        set("PhoneBankStore", "seenInitialSync", false);
        set("PhoneBankStore", "newestIncomingTransferAt", 0L);
    }

    @SuppressWarnings("unchecked")
    private static void forgetPlayerThreads() {
        try {
            Class<?> store = Class.forName(GUI + "PhoneMessagesStore");
            List<Object> threads = (List<Object>) field(store, "THREADS").get(null);
            Map<String, ?> messages = (Map<String, ?>) field(store, "MESSAGES").get(null);
            Map<String, ?> unread = (Map<String, ?>) field(store, "UNREAD_COUNTS").get(null);
            for (Iterator<Object> it = threads.iterator(); it.hasNext(); ) {
                Object thread = it.next();
                if (!(boolean) thread.getClass().getMethod("playerThread").invoke(thread)) continue;
                String id = (String) thread.getClass().getMethod("id").invoke(thread);
                it.remove();
                messages.remove(id);
                unread.remove(id);
            }
        } catch (ClassNotFoundException ignored) {
            // Sem telefone.
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("PhoneMessagesStore.THREADS", e);
        }
    }

    private static void set(String store, String name, Object value) {
        try {
            field(Class.forName(GUI + store), name).set(null, value);
        } catch (ClassNotFoundException ignored) {
            // Sem telefone.
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(store + "." + name, e);
        }
    }

    private static void clear(String store, String name) {
        try {
            Object value = field(Class.forName(GUI + store), name).get(null);
            if (value instanceof Collection<?> collection) collection.clear();
            else if (value instanceof Map<?, ?> map) map.clear();
        } catch (ClassNotFoundException ignored) {
            // Sem telefone.
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(store + "." + name, e);
        }
    }

    private static void invoke(String store, String name) {
        try {
            Method method = Class.forName(GUI + store).getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Sem telefone.
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(store + "." + name + "()", e);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void report(String what, Exception e) {
        if (reportedFailure) return;
        reportedFailure = true;
        AurorionEssentials.LOGGER.warn("Nao consegui zerar {} do telefone na troca de personagem; "
                + "o resto do celular foi separado normalmente.", what, e);
    }
}
