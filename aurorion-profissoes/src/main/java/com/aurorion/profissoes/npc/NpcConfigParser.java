package com.aurorion.profissoes.npc;

import com.aurorion.economia.money.Money;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.npc.NpcDefinition.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Le o {@code npcs.json} da staff.
 *
 * <p>Arquivo escrito a mao erra, entao o erro fica <b>no menor pedaco possivel</b>: uma oferta
 * quebrada some sozinha, um servico quebrado some sozinho, e so um NPC sem {@code id} valido e
 * descartado inteiro. Cada erro sai com o caminho ({@code npcs[1].trades[0].price_amount}) para a
 * staff achar a linha sem abrir o log inteiro.
 *
 * <p>Aceita os apelidos que aparecem nos pedidos da staff ({@code max_stock}/{@code stock_limit},
 * {@code price_item}/{@code price_item_id}, {@code blacksmith}/{@code ferreiro}): o arquivo e deles,
 * nao nosso, e recusar sinonimo obvio so gera chamado.
 */
public final class NpcConfigParser {
    public static final int MAX_SERVICES = 16, MAX_TRADES = 32, MAX_DIALOGUE = 16, MAX_COMMANDS = 8;
    private static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{1,48}");

    public record Result(List<NpcDefinition> npcs, List<String> errors, boolean readable) {}

    private static final class Invalid extends RuntimeException {
        Invalid(String message) { super(message, null, false, false); }
    }

    private NpcConfigParser() {}

    public static Result parse(String json) {
        var errors = new ArrayList<String>();
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException error) {
            errors.add("JSON inválido: " + rootCause(error));
            return new Result(List.of(), errors, false);
        }
        if (!root.isJsonObject() || !root.getAsJsonObject().has("npcs") || !root.getAsJsonObject().get("npcs").isJsonArray()) {
            errors.add("O arquivo precisa ser um objeto com a lista \"npcs\".");
            return new Result(List.of(), errors, false);
        }
        var npcs = new ArrayList<NpcDefinition>();
        var seen = new HashSet<String>();
        var list = root.getAsJsonObject().getAsJsonArray("npcs");
        for (int i = 0; i < list.size(); i++) {
            String path = "npcs[" + i + "]";
            try {
                var npc = npc(object(list.get(i), path), path, errors);
                if (!seen.add(npc.id())) throw new Invalid(path + ".id: \"" + npc.id() + "\" está repetido.");
                npcs.add(npc);
            } catch (Invalid error) {
                errors.add(error.getMessage() + " NPC ignorado.");
            }
        }
        return new Result(List.copyOf(npcs), List.copyOf(errors), true);
    }

    private static NpcDefinition npc(JsonObject json, String path, List<String> errors) {
        String id = text(json, path, null, "id");
        if (id == null || !ID.matcher(id).matches())
            throw new Invalid(path + ".id: obrigatório, só letras minúsculas, números, _ e - (até 48).");
        String name = text(json, path, id, "display_name", "name");
        var profession = profession(text(json, path, "", "profession", "profissao"), path + ".profession");
        String skin = text(json, path, "", "skin");
        if (!skin.isEmpty() && ResourceLocation.tryParse(skin) == null)
            throw new Invalid(path + ".skin: \"" + skin + "\" não é um caminho de textura válido.");

        var services = new ArrayList<Service>();
        var serviceList = array(json, path, "services", "servicos");
        for (int i = 0; i < serviceList.size() && i < MAX_SERVICES; i++) {
            String at = path + ".services[" + i + "]";
            try { services.add(service(object(serviceList.get(i), at), at)); }
            catch (Invalid error) { errors.add(error.getMessage() + " Serviço ignorado."); }
        }
        if (serviceList.size() > MAX_SERVICES) errors.add(path + ".services: só os " + MAX_SERVICES + " primeiros são usados.");

        var trades = new ArrayList<Trade>();
        var keys = new HashSet<String>();
        var tradeList = array(json, path, "trades", "ofertas");
        for (int i = 0; i < tradeList.size() && i < MAX_TRADES; i++) {
            String at = path + ".trades[" + i + "]";
            try {
                var trade = trade(object(tradeList.get(i), at), at, i);
                if (!keys.add(trade.key())) throw new Invalid(at + ".id: \"" + trade.key() + "\" está repetido neste NPC.");
                trades.add(trade);
            } catch (Invalid error) { errors.add(error.getMessage() + " Oferta ignorada."); }
        }
        if (tradeList.size() > MAX_TRADES) errors.add(path + ".trades: só as " + MAX_TRADES + " primeiras são usadas.");

        var dialogue = strings(json, path, "dialogue", "dialogo");
        if (dialogue.size() > MAX_DIALOGUE) dialogue = dialogue.subList(0, MAX_DIALOGUE);
        return new NpcDefinition(id, name, text(json, path, "", "title", "titulo"), profession, skin,
                bool(json, path, false, "slim_skin"), text(json, path, "", "greeting", "saudacao"), dialogue,
                text(json, path, "", "adm_dialogue"), integer(json, path, 64, -1, 1024, "fallback_radius"),
                bool(json, path, false, "fallback_blocks_trades"), services, trades);
    }

    private static Service service(JsonObject json, String path) {
        String name = text(json, path, null, "name", "nome");
        if (name == null || name.isBlank()) throw new Invalid(path + ".name: obrigatório.");
        var commands = new ArrayList<>(strings(json, path, "command", "command_to_execute"));
        commands.addAll(strings(json, path, "commands"));
        commands.replaceAll(NpcConfigParser::stripSlash);
        commands.removeIf(String::isBlank);
        if (commands.size() > MAX_COMMANDS) throw new Invalid(path + ".commands: no máximo " + MAX_COMMANDS + " comandos.");
        String type = text(json, path, commands.isEmpty() ? "" : "command", "action_type", "acao");
        var action = action(type, path + ".action_type");
        if (action == ActionType.COMMAND && commands.isEmpty())
            throw new Invalid(path + ": action_type \"command\" exige \"command\" ou \"commands\".");
        return new Service(name, text(json, path, "", "description", "descricao"), action, commands,
                cost(json, path, "cost"), integer(json, path, 0, 0, 7 * 24 * 3600, "cooldown_seconds"));
    }

    private static Trade trade(JsonObject json, String path, int index) {
        String item = text(json, path, null, "item_id", "item");
        if (item == null || ResourceLocation.tryParse(item) == null) throw new Invalid(path + ".item_id: obrigatório e precisa ser um id válido.");
        String key = text(json, path, index + ":" + item, "id");
        if (!ID.matcher(key).matches() && !key.equals(index + ":" + item))
            throw new Invalid(path + ".id: só letras minúsculas, números, _ e - (até 48).");
        String nbt = json.has("nbt_data") && json.get("nbt_data").isJsonObject()
                ? json.get("nbt_data").toString() : text(json, path, "", "nbt_data", "nbt");
        String components = text(json, path, "", "components", "componentes");
        if (!components.isEmpty() && !components.startsWith("["))
            throw new Invalid(path + ".components: use o formato do /give, entre colchetes: [chave=valor,...].");
        return new Trade(key, item, integer(json, path, 1, 1, 6400, "amount", "quantidade"), components, nbt,
                cost(json, path, "price"), integer(json, path, -1, -1, 1_000_000, "max_stock", "stock_limit", "estoque"),
                restock(text(json, path, "restart", "restock", "reposicao"), path + ".restock"));
    }

    /** {@code prefix_item}, {@code prefix_item_id}, {@code prefix_amount}, {@code prefix_money}. */
    private static Cost cost(JsonObject json, String path, String prefix) {
        String item = text(json, path, "", prefix + "_item", prefix + "_item_id");
        int amount = integer(json, path, item.isEmpty() ? 0 : 1, 0, 6400, prefix + "_amount");
        if (!item.isEmpty() && ResourceLocation.tryParse(item) == null)
            throw new Invalid(path + "." + prefix + "_item: \"" + item + "\" não é um id válido.");
        if (item.isEmpty() && amount > 0) throw new Invalid(path + "." + prefix + "_amount: informe também " + prefix + "_item.");
        String moneyText = text(json, path, "", prefix + "_money");
        long money = 0;
        if (!moneyText.isEmpty() && !moneyText.equals("0")) {
            try { money = Money.parse(moneyText); }
            catch (Money.MoneyFormatException error) {
                throw new Invalid(path + "." + prefix + "_money: \"" + moneyText + "\" não é uma quantia (ex.: \"12\" ou \"2,5\").");
            }
        }
        if (amount == 0) item = "";
        return item.isEmpty() && money == 0 ? Cost.FREE : new Cost(item, amount, money);
    }

    static Profession profession(String value, String path) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "", "nenhuma", "none", "mercador", "merchant", "comerciante", "vendedor" -> Profession.NONE;
            case "medico", "médico", "doctor", "medic", "healer" -> Profession.DOCTOR;
            case "ferreiro", "blacksmith", "smith" -> Profession.SMITH;
            case "cozinheiro", "cozinheira", "chef", "cook" -> Profession.CHEF;
            case "arcanista", "arcanist", "mage", "alquimista", "alchemist" -> Profession.ARCANIST;
            case "corretor", "broker" -> Profession.BROKER;
            default -> throw new Invalid(path + ": profissão \"" + value + "\" desconhecida.");
        };
    }

    private static ActionType action(String value, String path) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "command", "comando" -> ActionType.COMMAND;
            case "heal", "cura", "curar" -> ActionType.HEAL;
            case "repair", "reparo", "reparar" -> ActionType.REPAIR;
            case "finish_food", "finalizar_prato" -> ActionType.FINISH_FOOD;
            default -> throw new Invalid(path + ": \"" + value + "\" desconhecido (command, heal, repair, finish_food).");
        };
    }

    private static Restock restock(String value, String path) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "restart", "reinicio", "reinício" -> Restock.RESTART;
            case "daily", "diario", "diário" -> Restock.DAILY;
            case "never", "nunca" -> Restock.NEVER;
            default -> throw new Invalid(path + ": \"" + value + "\" desconhecido (restart, daily, never).");
        };
    }

    private static String stripSlash(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    // --- leitura de campos ---------------------------------------------------------------------

    private static JsonObject object(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) throw new Invalid(path + ": esperado um objeto { ... }.");
        return element.getAsJsonObject();
    }

    private static JsonElement first(JsonObject json, String... keys) {
        for (String key : keys) if (json.has(key) && !json.get(key).isJsonNull()) return json.get(key);
        return null;
    }

    private static String keyOf(JsonObject json, String... keys) {
        for (String key : keys) if (json.has(key)) return key;
        return keys[0];
    }

    private static String text(JsonObject json, String path, String fallback, String... keys) {
        var value = first(json, keys);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive()) throw new Invalid(path + "." + keyOf(json, keys) + ": esperado texto.");
        return value.getAsString();
    }

    private static int integer(JsonObject json, String path, int fallback, int min, int max, String... keys) {
        var value = first(json, keys);
        if (value == null) return fallback;
        String at = path + "." + keyOf(json, keys);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            double number = value.getAsDouble();
            if (number != Math.rint(number)) throw new NumberFormatException();
            if (number < min || number > max) throw new Invalid(at + ": deve estar entre " + min + " e " + max + ".");
            return (int) number;
        } catch (NumberFormatException error) {
            throw new Invalid(at + ": esperado um número inteiro.");
        }
    }

    private static boolean bool(JsonObject json, String path, boolean fallback, String... keys) {
        var value = first(json, keys);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new Invalid(path + "." + keyOf(json, keys) + ": esperado true ou false.");
        return value.getAsBoolean();
    }

    private static JsonArray array(JsonObject json, String path, String... keys) {
        var value = first(json, keys);
        if (value == null) return new JsonArray();
        if (!value.isJsonArray()) throw new Invalid(path + "." + keyOf(json, keys) + ": esperada uma lista [ ... ].");
        return value.getAsJsonArray();
    }

    /** Aceita um texto so ou uma lista de textos. */
    private static List<String> strings(JsonObject json, String path, String... keys) {
        var value = first(json, keys);
        if (value == null) return List.of();
        if (value.isJsonPrimitive()) return List.of(value.getAsString());
        if (!value.isJsonArray()) throw new Invalid(path + "." + keyOf(json, keys) + ": esperado texto ou lista de textos.");
        var out = new ArrayList<String>();
        for (var element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) throw new Invalid(path + "." + keyOf(json, keys) + ": a lista só pode ter textos.");
            out.add(element.getAsString());
        }
        return out;
    }

    private static String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage();
    }
}
