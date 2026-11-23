package br.ufs.fhirsus.config;

import static org.junit.jupiter.api.Assertions.*;


import br.ufs.fhirsus.map.TipoConversao;
import org.junit.jupiter.api.Test;
import br.ufs.fhirsus.config.domain.ConfigParser;
import br.ufs.fhirsus.config.domain.ConfigSintaxeException;

class ConfigParserTest {
    private final ConfigParser parser = new ConfigParser();

    @Test
    void jsonValidoGeraOModelo() {
        var c = parser.parse(Fixtures.ler("config-valida.json"));
        assertEquals(3, c.mapeamentos().get(0).toFHIR().size());
        assertEquals(1, c.mapeamentos().get(0).toLegado().size());
        assertEquals(TipoConversao.DOMINIO, c.mapeamentos().get(0).toFHIR().get(1).getTipoConversao());
        assertEquals("female", c.dominios().get(0).equivalencia().get("F"));
    }

    @Test
    void jsonMalformadoEErroDeSintaxe() {
        assertThrows(ConfigSintaxeException.class,
                () -> parser.parse(Fixtures.ler("config-invalida-sintaxe.json")));
    }

    @Test
    void campoDesconhecidoEErroDeSintaxe() {
        assertThrows(ConfigSintaxeException.class,
                () -> parser.parse(Fixtures.ler("config-invalida-campo-desconhecido.json")));
    }

    @Test
    void tipoConversaoDesconhecidoEErroDeSintaxe() {
        assertThrows(ConfigSintaxeException.class,
                () -> parser.parse(Fixtures.ler("config-invalida-tipo-desconhecido.json")));
    }

    @Test
    void conteudoVazioEErroDeSintaxe() {
        assertThrows(ConfigSintaxeException.class, () -> parser.parse("   "));
    }

    @Test
    void chaveRepetidaEErroDeSintaxe() {
        assertThrows(ConfigSintaxeException.class,
                () -> parser.parse("{\"mapeamentos\":[],\"mapeamentos\":[]}"));
    }
}
