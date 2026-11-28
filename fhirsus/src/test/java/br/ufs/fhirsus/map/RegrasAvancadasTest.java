package br.ufs.fhirsus.map;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.*;
import static br.ufs.fhirsus.map.SentidoConversao.TO_FHIR;
import static br.ufs.fhirsus.map.TipoConversao.*;
import static org.junit.jupiter.api.Assertions.*;

class RegrasAvancadasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final MotorConversao motor = new MotorConversao();

    @RegisterExtension
    final RelatorioConversao relatorio = new RelatorioConversao();

    @ParameterizedTest
    @CsvSource({
            "apenas_numeros, 00-12, 0012",
            "data_para_iso, 29/02/2024, 2024-02-29",
            "iso_para_data, 2024-02-29, 29/02/2024"
    })
    void aplicaTransformacaoTextualDoCatalogo(String nome, String entrada, String esperado) {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", TRANSFORMACAO, nome, true);
        var configuracao = config(regra);
        var payload = JSON.createObjectNode().put("origem", entrada);
        var saidaEsperada = JSON.createObjectNode().put("saida", esperado);

        // Act
        var resultado = motor.converter(payload, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(payload, saidaEsperada, resultado);
        assertEquals(saidaEsperada, resultado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42.125", "42,125"})
    void gravaDecimalComoNumeroJson(String texto) {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", TRANSFORMACAO, "para_decimal", true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode().put("origem", texto);
        var esperado = new BigDecimal("42.125");

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, JSON.createObjectNode().put("saida", esperado), resultado);
        var valor = resultado.get("saida");
        assertTrue(valor.isNumber());
        assertEquals(esperado, valor.decimalValue());
    }

    @ParameterizedTest
    @CsvSource({
            "data_para_iso, 31/02/2024",
            "iso_para_data, 2023-02-29",
            "para_decimal, '1.234,56'"
    })
    void rejeitaValorIncompativelComATransformacao(String nome, String entrada) {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", TRANSFORMACAO, nome, true);
        var configuracao = config(regra);
        var payload = JSON.createObjectNode().put("origem", entrada);

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(payload, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(payload, VALOR_INVALIDO, "$.origem", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("$.origem", falha.getCaminho());
    }

    @Test
    void rejeitaNomeDesconhecidoMesmoComEntradaOpcionalAusente() {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", TRANSFORMACAO, "inexistente", false);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, CONFIGURACAO_INVALIDA, "saida", falha);
        assertEquals(CONFIGURACAO_INVALIDA, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @Test
    void expressaoUsaValorRaizERegex() {
        // Arrange
        var regra = new RegraCampo("$.codigo", "saida", EXPRESSAO,
                "$replace($valor, /[^0-9]/, '') & '-' & $raiz.pedido", true);
        var configuracao = config(regra);
        var entrada = JSON.readTree("""
                {"codigo":"LAB-042","pedido":"PED-001"}
                """);
        var esperado = JSON.readTree("""
                {"saida":"042-PED-001"}
                """);

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void expressaoFiltraArrayNoContextoDaRaiz() {
        // Arrange
        var regra = new RegraCampo("$", "saida", EXPRESSAO,
                "identifier[system = 'urn:pedido'].value", true);
        var configuracao = config(regra);
        var entrada = JSON.readTree("""
                {"identifier":[{"system":"urn:paciente","value":"PAC-001"},
                               {"system":"urn:pedido","value":"PED-001"}]}
                """);
        var esperado = JSON.readTree("""
                {"saida":"PED-001"}
                """);

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void expressaoDistingueOrigemAusenteDeNuloExplicito() {
        // Arrange
        var regra = new RegraCampo("$.codigo", "saida", EXPRESSAO,
                "$exists($valor) ? ($valor = null ? 'nulo' : $valor) : $raiz.alternativo", true);
        var configuracao = config(regra);
        var entradaSemCodigo = JSON.readTree("""
                {"alternativo":"PED-001"}
                """);
        var entradaComCodigoNulo = JSON.readTree("""
                {"codigo":null,"alternativo":"PED-001"}
                """);
        var esperadoSemCodigo = JSON.createObjectNode().put("saida", "PED-001");
        var esperadoComCodigoNulo = JSON.createObjectNode().put("saida", "nulo");

        // Act
        var resultadoSemCodigo = motor.converter(entradaSemCodigo, "Observation", TO_FHIR, configuracao);
        var resultadoComCodigoNulo = motor.converter(entradaComCodigoNulo, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entradaSemCodigo, esperadoSemCodigo, resultadoSemCodigo);
        relatorio.conversao(entradaComCodigoNulo, esperadoComCodigoNulo, resultadoComCodigoNulo);
        assertEquals(esperadoSemCodigo, resultadoSemCodigo);
        assertEquals(esperadoComCodigoNulo, resultadoComCodigoNulo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "['A', 'B']", "{\"valor\": 0}"})
    void preservaOTipoJsonDoResultadoDaExpressao(String expressao) {
        // Arrange
        var regra = new RegraCampo("$", "saida", EXPRESSAO, expressao, true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();
        var esperado = JSON.readTree("{\"saida\":" + expressao.replace('\'', '"') + "}");

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void informaSintaxeInvalidaComOCaminhoDaRegra() {
        // Arrange
        var regra = new RegraCampo("$", "saida", EXPRESSAO, "(", true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, CONFIGURACAO_INVALIDA, "saida", falha);
        assertEquals(CONFIGURACAO_INVALIDA, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$number('texto')", "[1..40000]",
            "($f := function($n){$n > 0 ? 1 + $f($n - 1) : 0}; $f(1000))",
            "$eval('1 + 1')"})
    void informaFalhaDeExecucaoNoCaminhoDeDestino(String expressao) {
        // Arrange
        var regra = new RegraCampo("$", "saida", EXPRESSAO, expressao, true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, VALOR_INVALIDO, "saida", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @Test
    void interrompeExpressaoRecursivaSemTermino() {
        // Arrange
        var regra = new RegraCampo("$", "saida", EXPRESSAO, "($f := function(){$f()}; $f())", true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();
        var limite = Duration.ofSeconds(5);

        // Act
        var falha = assertTimeoutPreemptively(limite, () -> assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao)));

        // Assert
        relatorio.falha(entrada, VALOR_INVALIDO, "saida", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @Test
    void exigeValorSomenteQuandoACondicaoForVerdadeira() {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", DIRETA, null, true, "situacao = 'LIBERADO'");
        var configuracao = config(regra);
        var liberadoSemOrigem = JSON.readTree("""
                {"situacao":"LIBERADO"}
                """);
        var pendenteSemOrigem = JSON.readTree("""
                {"situacao":"PENDENTE"}
                """);
        var pendenteComOrigem = JSON.readTree("""
                {"situacao":"PENDENTE","origem":0}
                """);
        var esperadoSemOrigem = JSON.createObjectNode();
        var esperadoComOrigem = JSON.createObjectNode().put("saida", 0);

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(liberadoSemOrigem, "Observation", TO_FHIR, configuracao));
        var resultadoSemOrigem = motor.converter(pendenteSemOrigem, "Observation", TO_FHIR, configuracao);
        var resultadoComOrigem = motor.converter(pendenteComOrigem, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.falha(liberadoSemOrigem, CAMPO_OBRIGATORIO, "saida", falha);
        relatorio.conversao(pendenteSemOrigem, esperadoSemOrigem, resultadoSemOrigem);
        relatorio.conversao(pendenteComOrigem, esperadoComOrigem, resultadoComOrigem);
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
        assertEquals(esperadoSemOrigem, resultadoSemOrigem);
        assertEquals(esperadoComOrigem, resultadoComOrigem);
    }

    @ParameterizedTest
    @ValueSource(strings = {"'true'", "1", "null", "campoAusente", "$number('texto')"})
    void rejeitaCondicaoInvalidaMesmoComDestinoPreenchido(String condicao) {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", DIRETA, null, true, condicao);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode().put("origem", "valor");

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, VALOR_INVALIDO, "saida", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @Test
    void informaSintaxeInvalidaDaCondicaoComoErroDeConfiguracao() {
        // Arrange
        var regra = new RegraCampo("$", "saida", DIRETA, null, true, "(");
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, CONFIGURACAO_INVALIDA, "saida", falha);
        assertEquals(CONFIGURACAO_INVALIDA, falha.getCodigo());
        assertEquals("saida", falha.getCaminho());
    }

    @Test
    void ignoraCondicaoQuandoObrigatorioForFalso() {
        // Arrange
        var regra = new RegraCampo("$.origem", "saida", DIRETA, null, false, "(");
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();
        var esperado = JSON.createObjectNode();

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void leCondicaoOpcionalDoJsonEDeserializaRegrasAntigas() {
        // Arrange
        String jsonComCondicao = """
                {"caminhoOrigem":"$.origem","caminhoDestino":"saida","tipoConversao":"DIRETA",
                 "obrigatorio":true,"condicaoObrigatoriedade":"false"}
                """;
        String jsonSemCondicao = """
                {"caminhoOrigem":"$.origem","caminhoDestino":"saida","tipoConversao":"DIRETA",
                 "obrigatorio":true}
                """;
        var entrada = JSON.createObjectNode();
        var esperado = JSON.createObjectNode();

        // Act
        var regra = JSON.readValue(jsonComCondicao, RegraCampo.class);
        var anterior = JSON.readValue(jsonSemCondicao, RegraCampo.class);
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, config(regra));
        var configAnterior = config(anterior);
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_FHIR, configAnterior));

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        relatorio.falha(entrada, CAMPO_OBRIGATORIO, null, falha);
        assertEquals("false", regra.getCondicaoObrigatoriedade());
        assertNull(anterior.getCondicaoObrigatoriedade());
        assertEquals(esperado, resultado);
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
    }

    private MapeamentoConfig config(RegraCampo regra) {
        var mapeamento = new Mapeamento("Observation", List.of(), List.of(regra));
        return new MapeamentoConfig(List.of(mapeamento), List.of());
    }
}
