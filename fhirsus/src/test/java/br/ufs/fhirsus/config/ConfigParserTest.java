package br.ufs.fhirsus.config;

import static org.junit.jupiter.api.Assertions.*;

import br.ufs.fhirsus.config.domain.*;
import org.junit.jupiter.api.Test;

class ConfigParserTest {
    private final ConfigParser parser = new ConfigParser();

    @Test
    void jsonValidoGeraOModelo() {
        var c = parser.parse(Fixtures.ler("config-valida.json"));
        assertEquals("cfg-demo-001", c.id());
        assertEquals(3, c.mapeamentos().get(0).regrasToFHIR().size());
        assertEquals(1, c.mapeamentos().get(0).regrasToLegado().size());
        assertEquals(TipoConversao.DOMINIO, c.mapeamentos().get(0).regrasToFHIR().get(1).tipoConversao());
        assertEquals("female", c.dominiosValores().get(0).equivalencia().get("F"));
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

}
