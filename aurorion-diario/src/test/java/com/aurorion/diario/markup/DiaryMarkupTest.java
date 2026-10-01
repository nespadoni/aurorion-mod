package com.aurorion.diario.markup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiaryMarkupTest {

    private static JsonArray blocks(String markup) {
        return DiaryMarkup.toDocument(markup).getAsJsonArray("blocks");
    }

    @Test
    void parsesEveryBlockKind() {
        String markup = String.join("\n",
                "## Perto do portal",
                "",
                "Acho que **alguém** sabia.",
                "E não foi o *vento*.",
                "",
                "> Quem abre um portal",
                "> quer ser seguido.",
                "",
                "- o arco brilhou",
                "- a guarda sumiu",
                "",
                "---",
                "",
                "[[imagem:12|O arco|Desenho do arco]]",
                "",
                "### Depois");
        JsonArray b = blocks(markup);
        assertEquals(7, b.size(), b.toString());
        assertEquals("heading", b.get(0).getAsJsonObject().get("type").getAsString());
        JsonObject paragraph = b.get(1).getAsJsonObject();
        assertEquals("[{\"text\":\"Acho que \"},{\"text\":\"alguém\",\"marks\":[\"bold\"]},{\"text\":\" sabia.\\nE não foi o \"},{\"text\":\"vento\",\"marks\":[\"italic\"]},{\"text\":\".\"}]",
                paragraph.get("runs").toString());
        assertEquals("Quem abre um portal\nquer ser seguido.", b.get(2).getAsJsonObject().getAsJsonArray("runs").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(2, b.get(3).getAsJsonObject().getAsJsonArray("items").size());
        assertEquals("divider", b.get(4).getAsJsonObject().get("type").getAsString());
        JsonObject image = b.get(5).getAsJsonObject();
        assertEquals(12, image.get("attachment_id").getAsLong());
        assertEquals("O arco", image.get("caption").getAsString());
        assertEquals(3, b.get(6).getAsJsonObject().get("level").getAsInt());
    }

    @Test
    void unmatchedMarkIsLiteral() {
        JsonArray runs = DiaryMarkup.runs("2 * 3 = 6 e **meio");
        assertEquals("[{\"text\":\"2 * 3 = 6 e **meio\"}]", runs.toString());
    }

    @Test
    void imagesFromTheSiteSurviveAGameSave() {
        String fromSite = "{\"document_version\":1,\"blocks\":[{\"type\":\"paragraph\",\"runs\":[{\"text\":\"Desenhei.\"}]},"
                + "{\"type\":\"image\",\"attachment_id\":77,\"caption\":\"O arco | norte\"}]}";
        String markup = DiaryMarkup.toMarkup(JsonParser.parseString(fromSite).getAsJsonObject());
        JsonArray again = blocks(markup + "\n\nE escrevi mais no jogo.");
        assertEquals(77, again.get(1).getAsJsonObject().get("attachment_id").getAsLong());
        assertEquals("O arco / norte", again.get(1).getAsJsonObject().get("caption").getAsString());
        assertEquals(3, again.size());
    }

    @Test
    void roundTripKeepsLiteralMarkersAsText() {
        String original = "{\"document_version\":1,\"blocks\":["
                + "{\"type\":\"paragraph\",\"runs\":[{\"text\":\"## isto não é título\\n- nem lista\\n*asterisco* e \\\\barra\"}]},"
                + "{\"type\":\"paragraph\",\"runs\":[{\"text\":\"forte e leve\",\"marks\":[\"bold\",\"italic\"]}]},"
                + "{\"type\":\"list\",\"items\":[[{\"text\":\"um\"}],[{\"text\":\"dois\",\"marks\":[\"bold\"]}]]}]}";
        JsonObject doc = JsonParser.parseString(original).getAsJsonObject();
        String markup = DiaryMarkup.toMarkup(doc);
        JsonObject back = DiaryMarkup.toDocument(markup);
        assertEquals(doc.getAsJsonArray("blocks").toString(), back.getAsJsonArray("blocks").toString(), markup);
        // E a segunda volta é estável.
        assertEquals(markup, DiaryMarkup.toMarkup(back));
    }

    @Test
    void invalidImageLineIsDropped() {
        assertEquals(0, blocks("[[imagem:abc|x]]").size());
        assertEquals(0, blocks("[[imagem:-3|x]]").size());
    }
}
