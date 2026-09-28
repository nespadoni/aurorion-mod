package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * O que o {@code PhoneNumberStoreMixin} decide no lugar do telefone.
 *
 * <p>O que estes testes protegem: o atalho so pode pular o {@code ensureNumberFor} original quando o
 * original nao mudaria nada. Se ele aceitasse um nome trocado ou um numero de outra conta, o
 * diretorio do telefone ficaria errado em silencio — e so o reset de personagem descobriria.
 */
class PhoneNumberDirectoryTest {
    private final UUID bella = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private final UUID carlin = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final Map<UUID, String> uuidToNumber = new HashMap<>();
    private final Map<String, UUID> numberToUuid = new HashMap<>();
    private final Map<UUID, String> uuidToName = new HashMap<>();

    private void register(UUID account, String name, String number) {
        uuidToNumber.put(account, number);
        numberToUuid.put(number, account);
        uuidToName.put(account, name);
    }

    @Test
    void contaConhecidaComMesmoNomeDevolveONumeroSemGravar() {
        register(bella, "BellaNoob", "123456");
        assertEquals("123456", PhoneNumberDirectory.unchangedNumber(uuidToNumber, numberToUuid, uuidToName, bella, "BellaNoob"));
    }

    @Test
    void nomeTrocadoDeixaOOriginalRodar() {
        register(bella, "BellaNoob", "123456");
        assertNull(PhoneNumberDirectory.unchangedNumber(uuidToNumber, numberToUuid, uuidToName, bella, "BellaNova"));
    }

    @Test
    void contaSemNumeroDeixaOOriginalRodar() {
        assertNull(PhoneNumberDirectory.unchangedNumber(uuidToNumber, numberToUuid, uuidToName, bella, "BellaNoob"));
    }

    @Test
    void numeroTomadoPorOutraContaDeixaOOriginalRodar() {
        register(bella, "BellaNoob", "123456");
        numberToUuid.put("123456", carlin);
        assertNull(PhoneNumberDirectory.unchangedNumber(uuidToNumber, numberToUuid, uuidToName, bella, "BellaNoob"));
    }

    @Test
    void nomeLongoECortadoComoOTelefoneFaz() {
        register(bella, "NomeComDezesseis", "123456");
        assertEquals("123456", PhoneNumberDirectory.unchangedNumber(uuidToNumber, numberToUuid, uuidToName, bella,
                " NomeComDezesseisEMaisUmPouco "));
    }

    @Test
    void limpezaDoNumeroIgualADoTelefone() {
        assertEquals("123456", PhoneNumberDirectory.cleanNumber(" #123-456 "));
        assertEquals("123456", PhoneNumberDirectory.cleanNumber("1234567"));
        assertEquals("", PhoneNumberDirectory.cleanNumber(null));
    }

    @Test
    void fotoIgualQualquerQueSejaAOrdemDoMapa() {
        register(carlin, "Carlin", "654321");
        register(bella, "Bella", "123456");
        List<String> first = PhoneNumberDirectory.entries(uuidToNumber, uuidToName);

        Map<UUID, String> reversed = new java.util.LinkedHashMap<>();
        reversed.put(bella, "123456");
        reversed.put(carlin, "654321");
        assertEquals(first, PhoneNumberDirectory.entries(reversed, uuidToName));
    }

    @Test
    void numeroInvalidoFicaForaDoArquivoComoNoOriginal() {
        uuidToNumber.put(bella, "12");
        assertEquals(List.of(), PhoneNumberDirectory.entries(uuidToNumber, uuidToName));
    }

    /** O arquivo gravado tem de ser lido pelo {@code ensureLoaded} do telefone exatamente como antes. */
    @Test
    void arquivoGravadoTemOFormatoQueOTelefoneLe(@TempDir Path dir) throws IOException {
        register(bella, "Bella Noob", "123456");
        Path file = dir.resolve("mattupolis_phone").resolve("phone_numbers.properties");

        PhoneNumberSaveQueue.write(file, PhoneNumberDirectory.entries(uuidToNumber, uuidToName));

        Properties read = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            read.load(reader);
        }
        assertEquals("1", read.getProperty("entry.count"));
        String[] parts = read.getProperty("entry.0").split("\\|", -1);
        assertEquals(bella.toString(), parts[0]);
        assertEquals("Bella Noob", new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertEquals("123456", parts[2]);
        assertFalse(Files.exists(file.resolveSibling("phone_numbers.properties.tmp")), "sobrou o .tmp");
    }
}
