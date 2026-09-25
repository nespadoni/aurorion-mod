package com.aurorion.magia;

import com.aurorion.magia.passive.Passive;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O contrato entre as tres listas que sempre saem do ar quando uma magia nasce ou morre: o icone em
 * {@code textures/gui/spell_icons/}, a traducao em {@code pt_br.json} e a traducao em
 * {@code en_us.json}.
 *
 * <p>Nao e detalhe de acabamento: o Iron's mostra o id cru no lugar do nome quando falta a chave, e
 * a aba do criativo desenha um quadrado rosa quando falta o icone — e as duas coisas so aparecem em
 * jogo, depois do build, quando ninguem esta mais olhando para o commit que as esqueceu.
 *
 * <p>O teste le os arquivos pelo classpath de recursos, entao nao precisa de Minecraft carregado.
 */
class MagiaAssetsTest {
    private static final String ICONS = "assets/aurorion_magia/textures/gui/spell_icons";
    private static final List<String> LANGS = List.of("pt_br", "en_us");

    @Test
    void todaMagiaComIconeTemNomeEDescricaoNosDoisIdiomas() {
        Set<String> spells = spellIds();
        assertTrue(spells.size() >= 17, "poucos icones de magia encontrados: " + spells);

        for (String lang : LANGS) {
            JsonObject keys = lang(lang);
            for (String spell : spells) {
                assertTrue(keys.has("spell.aurorion_magia." + spell),
                        "falta o nome de " + spell + " em " + lang + ".json");
                assertTrue(keys.has("spell.aurorion_magia." + spell + ".guide"),
                        "falta a descricao de " + spell + " em " + lang + ".json");
            }
        }
    }

    /** Traducao de magia sem icone: a magia saiu do mod e a chave ficou para tras. */
    @Test
    void naoSobraTraducaoDeMagiaQueNaoExisteMais() {
        Set<String> spells = spellIds();
        for (String lang : LANGS) {
            for (String key : lang(lang).keySet()) {
                if (!key.startsWith("spell.aurorion_magia.") || key.endsWith(".guide")) continue;
                String id = key.substring("spell.aurorion_magia.".length());
                assertTrue(spells.contains(id), "sobrou " + key + " em " + lang + ".json sem icone em " + ICONS);
            }
        }
    }

    @Test
    void osDoisIdiomasTemExatamenteAsMesmasChaves() {
        assertEquals(new TreeSet<>(lang("pt_br").keySet()), new TreeSet<>(lang("en_us").keySet()));
    }

    /**
     * Passiva sem nome apareceria como {@code passive.aurorion_magia.<id>} cru no chat e no
     * pergaminho — e, ao contrario da magia, ela nao tem icone para denunciar a falta antes.
     */
    @Test
    void todaPassivaTemNomeEDescricaoNosDoisIdiomas() {
        assertTrue(Passive.values().length > 0, "nenhuma passiva registrada");
        for (String language : LANGS) {
            JsonObject keys = lang(language);
            for (Passive passive : Passive.values()) {
                assertTrue(keys.has("passive.aurorion_magia." + passive.key()),
                        "falta o nome de " + passive.key() + " em " + language + ".json");
                assertTrue(keys.has("passive.aurorion_magia." + passive.key() + ".guide"),
                        "falta a descricao de " + passive.key() + " em " + language + ".json");
            }
        }
    }

    /** Traducao de passiva sem passiva: a marca saiu do codigo e a chave ficou para tras. */
    @Test
    void naoSobraTraducaoDePassivaQueNaoExisteMais() {
        Set<String> passives = new TreeSet<>();
        for (Passive passive : Passive.values()) passives.add(passive.key());

        for (String language : LANGS) {
            for (String key : lang(language).keySet()) {
                if (!key.startsWith("passive.aurorion_magia.") || key.endsWith(".guide")) continue;
                String id = key.substring("passive.aurorion_magia.".length());
                assertTrue(passives.contains(id), "sobrou " + key + " em " + language + ".json");
            }
        }
    }

    private static Set<String> spellIds() {
        try {
            Path dir = Path.of(MagiaAssetsTest.class.getClassLoader().getResource(ICONS).toURI());
            try (Stream<Path> files = Files.list(dir)) {
                return files.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".png"))
                        .map(name -> name.substring(0, name.length() - 4))
                        .collect(TreeSet::new, Set::add, Set::addAll);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JsonObject lang(String code) {
        String path = "assets/aurorion_magia/lang/" + code + ".json";
        try (InputStream in = MagiaAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("nao achei " + path + " no classpath de recursos");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
