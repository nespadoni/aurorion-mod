package com.aurorion.servicos.client;

import com.aurorion.servicos.AurorionServicos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Tudo o que o app usa do celular, por reflexao: o telefone e fechado e opcional, e nao entra no
 * classpath de compilacao (igual ao essentials e a economia).
 *
 * <p>Cada metodo resolve o alvo uma vez e guarda. Se o telefone mudar e algo sumir, aquele pedaco
 * cai no plano B (cor padrao, tela inicial vazia, sem notificacao) e o aviso vai uma vez para o
 * log — o app continua abrindo. O {@code PhoneAppContractTest} confere os nomes no build.
 */
public final class PhoneBridge {
    private static final String GUI = "com.mattupolis.phone.client.gui.";
    private static final String SETTINGS = GUI + "PhoneSettingsStore";
    private static final String THEME = GUI + "PhoneTheme";

    private static final Map<String, Method> METHODS = new HashMap<>();
    private static final Map<String, Constructor<?>> CONSTRUCTORS = new HashMap<>();
    /** Acessores de registro do telefone por classe e nome; os mixins perguntam isto por quadro. */
    private static final Map<Class<?>, Map<String, Method>> ACCESSORS = new HashMap<>();
    private static boolean reportedFailure;

    /**
     * Um alvo usado por quadro, resolvido uma vez e guardado no proprio campo: a chamada nao monta
     * chave de texto nem consulta mapa. O {@link #method} continua sendo quem resolve (e avisa no log).
     */
    private static final class Target {
        private final String owner;
        private final String name;
        private final Class<?>[] parameters;
        private boolean resolved;
        @Nullable
        private Method method;

        Target(String owner, String name, Class<?>... parameters) {
            this.owner = owner;
            this.name = name;
            this.parameters = parameters;
        }

        @Nullable
        Method get() {
            if (!resolved) {
                method = PhoneBridge.method(owner, name, parameters);
                resolved = true;
            }
            return method;
        }

        String label() {
            return owner + "." + name;
        }
    }

    private static final Target DARK_MODE = new Target(SETTINGS, "isDarkModeEnabled");
    private static final Target BRIGHTNESS = new Target(SETTINGS, "getBrightnessOverlayColor");
    private static final Target AIRPLANE = new Target(SETTINGS, "isAirplaneModeEnabled");
    private static final Target HAS_PHONE = new Target("com.mattupolis.phone.client.PhoneClientInventoryState",
            "hasPhoneInInventory");
    private static final Target SHELL = new Target(GUI + "PhoneCaseStore", "drawPhoneShell",
            GuiGraphics.class, int.class, int.class, int.class, int.class, int.class);
    private static final Target APP_BACKGROUND = new Target(THEME, "getAppBackgroundColor");
    private static final Target CARD = new Target(THEME, "getCardColor");
    private static final Target SECONDARY_CARD = new Target(THEME, "getSecondaryCardColor");
    private static final Target TEXT = new Target(THEME, "getTextColor");
    private static final Target SUB_TEXT = new Target(THEME, "getSubTextColor");
    private static final Target MUTED_TEXT = new Target(THEME, "getMutedTextColor");
    private static final Target ACCENT = new Target(THEME, "getAccentColor");
    private static final Target BORDER = new Target(THEME, "getBorderColor");
    private static final Target SEPARATOR = new Target(THEME, "getSeparatorColor");
    private static final Target HOME_INDICATOR = new Target(THEME, "getHomeIndicatorColor");
    private static final Target SEARCH_BOX = new Target(THEME, "getSearchBoxColor");
    private static final Target SWITCH_OFF = new Target(THEME, "getSwitchOffColor");
    private static final Target SWITCH_KNOB = new Target(THEME, "getSwitchKnobColor");

    private PhoneBridge() {
    }

    public static boolean installed() {
        return ModList.get().isLoaded("mattupolis_phone");
    }

    // ---- aparencia -----------------------------------------------------------------------------

    /** As cores do tema do celular (claro/escuro), lidas uma vez por quadro. */
    public record Theme(int app, int card, int secondaryCard, int text, int sub, int muted, int accent, int border,
                        int separator, int homeIndicator, int input, int switchOff, int knob, boolean dark) {
    }

    public static Theme theme() {
        boolean dark = bool(DARK_MODE, false);
        return new Theme(
                integer(APP_BACKGROUND, dark ? 0xFF101216 : 0xFFF2F2F7),
                integer(CARD, dark ? 0xFF1C1F26 : 0xFFFFFFFF),
                integer(SECONDARY_CARD, dark ? 0xFF262A33 : 0xFFE9E9EF),
                integer(TEXT, dark ? 0xFFFFFFFF : 0xFF111111),
                integer(SUB_TEXT, dark ? 0xFFB8BCC6 : 0xFF555A64),
                integer(MUTED_TEXT, dark ? 0xFF7D8290 : 0xFF8A8F99),
                integer(ACCENT, 0xFF2F80ED),
                integer(BORDER, dark ? 0xFF2C313B : 0xFFD8D8DE),
                integer(SEPARATOR, dark ? 0xFF2C313B : 0xFFE3E3E8),
                integer(HOME_INDICATOR, dark ? 0xFFFFFFFF : 0xFF111111),
                integer(SEARCH_BOX, dark ? 0xFF262A33 : 0xFFE9E9EF),
                integer(SWITCH_OFF, dark ? 0xFF3A3F4A : 0xFFC7C7CC),
                integer(SWITCH_KNOB, 0xFFFFFFFF),
                dark);
    }

    /** A moldura com a capinha que o jogador escolheu. Sem o telefone, uma moldura neutra. */
    public static void drawShell(GuiGraphics graphics, int x, int y, int width, int height, int screenColor) {
        Method method = SHELL.get();
        if (method != null) {
            try {
                method.invoke(null, graphics, x, y, width, height, screenColor);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneCaseStore.drawPhoneShell", e);
            }
        }
        graphics.fill(x - 10, y - 8, x + width + 10, y + height + 10, 0x66000000);
        graphics.fill(x - 6, y - 6, x + width + 6, y + height + 6, 0xFF030303);
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xFF1F2937);
        graphics.fill(x, y, x + width, y + height, screenColor);
    }

    public static int brightnessOverlay() {
        return integer(BRIGHTNESS, 0);
    }

    public static boolean airplaneMode() {
        return bool(AIRPLANE, false);
    }

    public static boolean hasPhone() {
        return bool(HAS_PHONE, false);
    }

    // ---- sons ----------------------------------------------------------------------------------

    public static void click() {
        run(GUI + "PhoneSoundManager", "playClick");
    }

    public static void back() {
        run(GUI + "PhoneSoundManager", "playBack");
    }

    public static void error() {
        run(GUI + "PhoneSoundManager", "playError");
    }

    // ---- navegacao -----------------------------------------------------------------------------

    /** Volta para a tela inicial do celular; sem ela, fecha. */
    public static void home() {
        Screen home = create(GUI + "PhoneHomeScreen", new Class<?>[0]);
        Minecraft.getInstance().setScreen(home);
    }

    /**
     * Abre a conversa com {@code nick} no app de mensagens, com {@code draft} ja escrito. E o mesmo
     * caminho do "mandar mensagem ao vendedor" do marketplace do proprio telefone.
     *
     * @return se abriu
     */
    public static boolean openChat(String nick, String draft) {
        if (nick == null || nick.isBlank()) return false;
        Method create = method(GUI + "PhoneMessagesStore", "createPlayerThread", String.class);
        if (create == null) return false;
        try {
            Object thread = create.invoke(null, nick);
            if (thread == null) return false;
            Method id = thread.getClass().getMethod("id");
            id.setAccessible(true);
            String threadId = (String) id.invoke(thread);
            Screen chat = create(GUI + "PhoneMessageChatScreen", new Class<?>[]{String.class, String.class}, threadId,
                    draft == null ? "" : draft);
            if (chat == null) return false;
            Minecraft.getInstance().setScreen(chat);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("PhoneMessagesStore.createPlayerThread", e);
            return false;
        }
    }

    /** Notificacao no celular (tela de bloqueio, painel, ilha) e o som de mensagem. */
    public static void notification(String app, String title, String text) {
        Method add = method(GUI + "PhoneNotificationStore", "addPlayerNotification", String.class, String.class, String.class);
        if (add == null) return;
        try {
            add.invoke(null, app, title, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("PhoneNotificationStore.addPlayerNotification", e);
            return;
        }
        run(GUI + "PhoneSoundManager", "playMessageNotification");
    }

    // ---- pecas do telefone para os mixins ------------------------------------------------------

    /** Um {@code PhoneHomeAppCatalog.AppInfo}, ou {@code null} se o construtor mudou. */
    @Nullable
    public static Object appInfo(String id, String searchTerms, String label, ResourceLocation icon, int color, int badge,
                                 boolean available, boolean removable) {
        return construct(GUI + "PhoneHomeAppCatalog$AppInfo", new Class<?>[]{String.class, String.class, String.class,
                ResourceLocation.class, int.class, int.class, boolean.class, boolean.class},
                id, searchTerms, label, icon, color, badge, available, removable);
    }

    /** Um {@code PhoneAppLibraryScreen.LibraryApp}, ou {@code null} se o construtor mudou. */
    @Nullable
    public static Object libraryApp(String id, String name, String category, ResourceLocation icon, int color) {
        return construct(GUI + "PhoneAppLibraryScreen$LibraryApp", new Class<?>[]{String.class, String.class, String.class,
                ResourceLocation.class, int.class}, id, name, category, icon, color);
    }

    /** O {@code id()} de um registro do telefone ({@code HomeApp}), ou vazio. */
    public static String accessor(Object record, String name) {
        if (record == null) return "";
        try {
            Map<String, Method> byName = ACCESSORS.computeIfAbsent(record.getClass(), type -> new HashMap<>());
            Method method = byName.get(name);
            if (method == null) {
                method = record.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                byName.put(name, method);
            }
            Object value = method.invoke(record);
            return value instanceof String text ? text : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(record.getClass().getSimpleName() + "." + name, e);
            return "";
        }
    }

    // ---- reflexao ------------------------------------------------------------------------------

    private static int integer(Target target, int fallback) {
        Method method = target.get();
        if (method == null) return fallback;
        try {
            return (int) method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(target.label(), e);
            return fallback;
        }
    }

    private static boolean bool(Target target, boolean fallback) {
        Method method = target.get();
        if (method == null) return fallback;
        try {
            return (boolean) method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(target.label(), e);
            return fallback;
        }
    }

    private static void run(String owner, String name) {
        Method method = method(owner, name);
        if (method == null) return;
        try {
            method.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(owner + "." + name, e);
        }
    }

    @Nullable
    private static Method method(String owner, String name, Class<?>... parameters) {
        String key = owner + "#" + name + "/" + parameters.length;
        if (METHODS.containsKey(key)) return METHODS.get(key);
        Method method = null;
        if (installed()) {
            try {
                method = Class.forName(owner).getDeclaredMethod(name, parameters);
                method.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                report(owner + "." + name, e);
            }
        }
        METHODS.put(key, method);
        return method;
    }

    @Nullable
    private static Screen create(String owner, Class<?>[] parameters, Object... arguments) {
        Object screen = construct(owner, parameters, arguments);
        return screen instanceof Screen value ? value : null;
    }

    @Nullable
    private static Object construct(String owner, Class<?>[] parameters, Object... arguments) {
        String key = owner + "/" + parameters.length;
        Constructor<?> constructor;
        if (CONSTRUCTORS.containsKey(key)) {
            constructor = CONSTRUCTORS.get(key);
        } else {
            constructor = null;
            if (installed()) {
                try {
                    constructor = Class.forName(owner).getDeclaredConstructor(parameters);
                    constructor.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    report(owner, e);
                }
            }
            CONSTRUCTORS.put(key, constructor);
        }
        if (constructor == null) return null;
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(owner, e);
            return null;
        }
    }

    private static void report(String what, Exception e) {
        if (reportedFailure) return;
        reportedFailure = true;
        AurorionServicos.LOGGER.warn("App Serviços: não achei {} no celular; esse pedaço fica desligado.", what, e);
    }
}
