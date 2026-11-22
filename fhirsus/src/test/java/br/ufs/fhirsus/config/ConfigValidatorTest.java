package br.ufs.fhirsus.config;

import static org.junit.jupiter.api.Assertions.*;

import br.ufs.fhirsus.config.domain.ConfigParser;
import br.ufs.fhirsus.config.domain.ConfigValidator;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {
    private final ConfigParser parser = new ConfigParser();
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void configValidaNaoTemErros() {
        assertTrue(validator.validar(parser.parse(Fixtures.ler("config-valida.json"))).isEmpty());
    }

    @Test
    void configInvalidaReportaTodosOsErros() {
        var erros = validator.validar(parser.parse(Fixtures.ler("config-invalida-semantica.json")));
        assertEquals(6, erros.size(), erros.toString());
        assertTrue(erros.stream().anyMatch(e -> e.caminho().equals("mapeamentos[0].recurso")));
        assertTrue(erros.stream().anyMatch(e -> e.mensagem().contains("domínio inexistente")));
        assertTrue(erros.stream().anyMatch(e -> e.mensagem().contains("duplicado")));
        assertTrue(erros.stream().anyMatch(e -> e.mensagem().contains("obrigatório em CONSTANTE")));
        assertTrue(erros.stream().anyMatch(e -> e.caminho().endsWith(".obrigatorio")));
        assertTrue(erros.stream().anyMatch(e -> e.mensagem().contains("ao menos 1 regra")));
    }

    @Test
    void parametroEmDiretaEInvalidoETipoEObrigatorio() {
        var json = """
            {"mapeamentos":[{"recurso":"Patient","toFHIR":[
              {"caminhoOrigem":"$.a","caminhoDestino":"gender","tipoConversao":"DIRETA","parametroConversao":"x","obrigatorio":true},
              {"caminhoOrigem":"$.b","caminhoDestino":"active","obrigatorio":true}]}]}
            """;
        var erros = validator.validar(parser.parse(json));
        assertTrue(erros.stream().anyMatch(e -> e.mensagem().contains("vazio em DIRETA")));
        assertTrue(erros.stream().anyMatch(e -> e.caminho().endsWith(".tipoConversao")));
    }

    @Test
    void configNulaEInvalida() {
        assertFalse(validator.validar(null).isEmpty());
    }

    @Test
    void origemSemRaizEDestinoConflitanteSaoErros() {
        var json = """
            {"mapeamentos":[{"recurso":"Patient","toFHIR":[
              {"caminhoOrigem":"paciente.nome","caminhoDestino":"dados.codigo","tipoConversao":"DIRETA","parametroConversao":null,"obrigatorio":true},
              {"caminhoOrigem":"$.b","caminhoDestino":"dados[0]","tipoConversao":"DIRETA","parametroConversao":null,"obrigatorio":true}]}]}
            """;
        var erros = validator.validar(parser.parse(json));
        assertTrue(erros.stream().anyMatch(e -> e.caminho().endsWith("[0].caminhoOrigem")));
        assertTrue(erros.stream().anyMatch(e -> e.caminho().endsWith("[1].caminhoDestino")));
    }
}
