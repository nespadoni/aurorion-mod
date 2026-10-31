package com.aurorion.ficha.server;

import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.integracao.outbox.SiteApi;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os códigos e mensagens que o servidor tira das respostas do site (sem Minecraft: só os auxiliares puros). */
class FichaServerCodesTest {

    private static SiteApi.Response response(int status, String json) {
        return new SiteApi.Response(status, json == null ? null : JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void responseWithoutCodeIsNotAlwaysSiteDown() {
        assertEquals("ok", FichaServer.codeOf(response(200, "{\"code\":\"ok\"}")));
        assertEquals("unavailable", FichaServer.codeOf(response(0, null)), "sem resposta");
        assertEquals("unavailable", FichaServer.codeOf(response(503, "{\"code\":\"unavailable\"}")));
        assertEquals("unavailable", FichaServer.codeOf(response(502, null)), "proxy sem corpo");
        assertEquals("auth", FichaServer.codeOf(response(401, null)), "credencial da integração");
        assertEquals("auth", FichaServer.codeOf(response(403, "{}")));
        assertEquals("missing", FichaServer.codeOf(response(404, null)), "backend sem as rotas da ficha");
        assertEquals("invalid_request", FichaServer.codeOf(response(400, "{\"code\":\"invalid_request\"}")));
        assertEquals("invalid_request", FichaServer.codeOf(response(400, null)));
        assertEquals(24, FichaServer.codeOf(response(200, "{\"code\":\"" + "x".repeat(40) + "\"}")).length(), "cabe no pacote");
    }

    @Test
    void rollMessagesUseTheSiteReasonWhenItIsASentence() {
        String pending = FichaServer.rollMessage("no_build",
                response(200, "{\"code\":\"no_build\",\"message\":\"resources_need_review: recursos de Lyra aguardam revisão da staff\"}"));
        assertEquals("Esta ficha ainda não pode rolar: recursos de Lyra aguardam revisão da staff.", pending);
        String bare = FichaServer.rollMessage("no_build", response(200, "{\"code\":\"no_build\",\"message\":\"no_approved_build\"}"));
        assertTrue(bare.contains("construção nova aprovada") && bare.contains("rolagem livre"), bare);
        String reused = FichaServer.rollMessage("invalid",
                response(200, "{\"code\":\"invalid\",\"message\":\"idempotency_key_reused: o command_id já foi usado com outro conteúdo\"}"));
        assertTrue(reused.contains("pedido novo"), reused);
        String busy = FichaServer.rollMessage("busy",
                response(200, "{\"code\":\"busy\",\"message\":\"muitas rolagens seguidas; tente de novo em instantes\"}"));
        assertEquals("O site segurou esta rolagem: muitas rolagens seguidas; tente de novo em instantes.", busy);
        assertTrue(FichaServer.codeMessage("missing").contains("staff"));
    }

    @Test
    void versionKeyFollowsTheSiteVersion() {
        FichaSheet a = FichaSheet.parse(sheet("v1", 31));
        FichaSheet b = FichaSheet.parse(sheet("v2", 31));
        FichaServer.SheetData first = new FichaServer.SheetData("ok", a, null, List.of());
        assertEquals("v1", FichaServer.versionKey(first));
        assertNotEquals(FichaServer.versionKey(first), FichaServer.versionKey(new FichaServer.SheetData("ok", b, null, List.of())));
        assertEquals("code:needs_binding", FichaServer.versionKey(new FichaServer.SheetData("needs_binding", null, null, List.of())));
        // Backend sem version: o conteúdo decide.
        String x = FichaServer.versionKey(new FichaServer.SheetData("ok", FichaSheet.parse(sheet("", 31)), null, List.of()));
        String y = FichaServer.versionKey(new FichaServer.SheetData("ok", FichaSheet.parse(sheet("", 12)), null, List.of()));
        assertTrue(x.startsWith("hash:"));
        assertNotEquals(x, y);
    }

    @Test
    void playerFreeRollUsesTheEngineGrammar() {
        FichaServer.RollSpec spec = FichaServer.playerSpec("free", " 2D6 + 3 ", 0, false);
        assertEquals("2d6+3", spec.key(), "vai ao site na forma canônica");
        assertNull(FichaServer.playerSpec("free", "2d6*3", 0, false), "o motor recusaria: nem sai daqui");
        assertNull(FichaServer.playerSpec("free", "(1d6)", 0, false));
        assertEquals("1d100", FichaServer.playerSpec("free", "d%", 0, false).key());
    }

    @Test
    void diceLookAlikeGetsTheFormulaReason() {
        assertTrue(FichaServer.looksLikeDice("2d6*3"));
        assertTrue(FichaServer.looksLikeDice("d20x"));
        assertTrue(FichaServer.looksLikeDice("1d%%"));
        assertFalse(FichaServer.looksLikeDice("furtividade"), "o d de \"furtividade\" não é dado");
        assertFalse(FichaServer.looksLikeDice("destreza"));
    }

    @Test
    void outdatedNoticeCarriesTheKeyPlaceholder() {
        assertTrue(com.aurorion.ficha.sheet.Texts.OUTDATED_NOTICE.contains(com.aurorion.ficha.sheet.Texts.KEY_PLACEHOLDER),
                "o cliente põe o nome da tecla configurada no lugar");
        assertTrue(com.aurorion.ficha.sheet.Texts.OUTDATED_NOTICE.contains("aperte " + com.aurorion.ficha.sheet.Texts.KEY_PLACEHOLDER),
                "sem tecla atribuída, o cliente troca \"aperte {tecla}\" por \"use /ficha\"");
    }

    private static JsonObject sheet(String version, int vida) {
        return JsonParser.parseString("{\"rpg_character_id\": 7, \"subject_type\": \"sheet\", \"subject_id\": 3, \"name\": \"Lyra\","
                + " \"resources\": {\"initialized\": true, \"vida\": " + vida + "}, \"version\": \"" + version + "\"}").getAsJsonObject();
    }
}
