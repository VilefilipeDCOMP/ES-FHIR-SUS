package br.ufs.fhirsus.map;

import br.ufs.fhirsus.config.domain.ConfigParser;
import br.ufs.fhirsus.config.domain.ConfigValidator;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.CAMPO_OBRIGATORIO;
import static br.ufs.fhirsus.map.FalhaConversao.Codigo.VALOR_INVALIDO;
import static br.ufs.fhirsus.map.SentidoConversao.TO_FHIR;
import static br.ufs.fhirsus.map.SentidoConversao.TO_LEGADO;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapeamentoMvpTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @RegisterExtension
    final RelatorioConversao relatorio = new RelatorioConversao();

    private final MotorConversao motor = new MotorConversao();
    private final ValidadorFhir validador = new ValidadorFhir();
    private MapeamentoConfig configuracao;
    private JsonNode exemplos;

    @BeforeAll
    void carregarDadosSinteticos() throws IOException {
        configuracao = new ConfigParser().parse(lerArquivo("/fixtures/mapeamento-mvp-sintetico.json"));
        exemplos = JSON.readTree(lerArquivo("/fixtures/exemplos-mapeamento-mvp.json"));
    }

    @Test
    void configuracaoCobreOsRecursosDoMvpETodosOsTiposDeConversao() {
        // Arrange
        var recursosEsperados = ConfigValidator.RECURSOS;
        var tiposEsperados = EnumSet.allOf(TipoConversao.class);

        // Act
        var recursosConfigurados = configuracao.mapeamentos().stream().map(Mapeamento::recurso).collect(toSet());
        var regras = configuracao.mapeamentos().stream()
                .flatMap(mapeamento -> Stream.concat(mapeamento.toLegado().stream(), mapeamento.toFHIR().stream()))
                .toList();
        var tiposConfigurados = regras.stream().map(RegraCampo::getTipoConversao).collect(toSet());

        // Assert
        assertEquals(recursosEsperados, recursosConfigurados);
        assertEquals(recursosEsperados, exemplos.propertyNames());
        assertEquals(tiposEsperados, tiposConfigurados);
        assertTrue(regras.stream().anyMatch(regra -> Boolean.TRUE.equals(regra.getObrigatorio())
                && regra.getCondicaoObrigatoriedade() != null));
    }

    @ParameterizedTest(name = "{0} [{1}] — {2}")
    @MethodSource("casosDeConversao")
    void converteExemploSintetico(String recurso, SentidoConversao sentido, JsonNode exemplo) {
        // Arrange
        JsonNode fhir = exemplo.required("fhir");
        JsonNode legado = exemplo.required("legado");
        boolean paraFhir = sentido == TO_FHIR;
        JsonNode entrada = paraFhir ? legado : fhir;
        JsonNode esperado = paraFhir ? fhir : legado;

        // Act
        JsonNode gerado = motor.converter(entrada, recurso, sentido, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, gerado);
        assertEquals(esperado, gerado, () -> recurso + " [" + sentido + "]");
        if (paraFhir) {
            var resultadoValidacao = validador.validar(gerado.toString(), recurso);
            assertTrue(resultadoValidacao.isSuccessful(), () -> resultadoValidacao.getMessages().toString());
        }
    }

    @ParameterizedTest(name = "Equivalências bidirecionais de {0}")
    @MethodSource("dominiosConfigurados")
    void todosOsCodigosDaTabelaTemEquivalenciaUnicaNosDoisSentidos(DominioValores dominio) {
        // Arrange
        var equivalencias = dominio.equivalencia();

        equivalencias.forEach((legado, fhir) -> {
            // Act
            var convertidoParaFhir = dominio.buscarEquivalente(legado);
            var convertidoParaLegado = dominio.buscarEquivalente(fhir);

            // Assert
            assertEquals(Optional.of(fhir), convertidoParaFhir, dominio.nome() + " / " + legado);
            assertEquals(Optional.of(legado), convertidoParaLegado, dominio.nome() + " / " + fhir);
        });
    }

    @Test
    void rejeitaPedidoSemIdentificadorDoSistemaDaClinica() {
        // Arrange
        var entrada = exemplos.required("ServiceRequest").required("fhir").asObject().deepCopy();
        entrada.putArray("identifier").addObject()
                .put("system", "https://clinica.example/identificadores/externos")
                .put("value", "EXT-999");

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, CAMPO_OBRIGATORIO, "numeroPedido", falha);
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
        assertEquals("numeroPedido", falha.getCaminho());
    }

    @Test
    void rejeitaPedidoComDoisIdentificadoresDoSistemaDaClinica() {
        // Arrange
        var entrada = exemplos.required("ServiceRequest").required("fhir").asObject().deepCopy();
        entrada.required("identifier").asArray().addObject()
                .put("system", "https://clinica.example/identificadores/pedidos")
                .put("value", "PED-002");

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, VALOR_INVALIDO, "numeroPedido", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("numeroPedido", falha.getCaminho());
    }

    @ParameterizedTest(name = "Observation preliminar sem resultado [{0}]")
    @EnumSource(SentidoConversao.class)
    void permiteObservacaoPreliminarSemResultado(SentidoConversao sentido) {
        // Arrange
        var fhir = observacaoFhirSemResultado("preliminary");
        var legado = observacaoLegadaSemResultado("PARCIAL");
        boolean paraFhir = sentido == TO_FHIR;
        JsonNode entrada = paraFhir ? legado : fhir;
        JsonNode esperado = paraFhir ? fhir : legado;

        // Act
        JsonNode gerado = motor.converter(entrada, "Observation", sentido, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, gerado);
        assertEquals(esperado, gerado);
    }

    @ParameterizedTest(name = "Observation final sem resultado [{0}]")
    @CsvSource({"TO_LEGADO, resultado", "TO_FHIR, valueQuantity.value"})
    void rejeitaObservacaoFinalSemResultado(SentidoConversao sentido, String caminho) {
        // Arrange
        JsonNode entrada = sentido == TO_FHIR
                ? observacaoLegadaSemResultado("LIBERADO")
                : observacaoFhirSemResultado("final");

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", sentido, configuracao));

        // Assert
        relatorio.falha(entrada, CAMPO_OBRIGATORIO, caminho, falha);
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
        assertEquals(caminho, falha.getCaminho());
    }

    private ObjectNode observacaoFhirSemResultado(String status) {
        var observacao = exemplos.required("Observation").required("fhir").asObject().deepCopy();
        observacao.put("status", status);
        observacao.remove("valueQuantity");
        return observacao;
    }

    private ObjectNode observacaoLegadaSemResultado(String situacao) {
        var observacao = exemplos.required("Observation").required("legado").asObject().deepCopy();
        observacao.put("situacao", situacao);
        observacao.remove("resultado");
        observacao.remove("unidade");
        return observacao;
    }

    private Stream<Arguments> casosDeConversao() {
        return ConfigValidator.RECURSOS.stream().sorted()
                .flatMap(recurso -> {
                    var exemplo = exemplos.required(recurso);
                    var variantes = StreamSupport.stream(exemplo.path("variantes").spliterator(), false)
                            .map(variante -> Named.named(variante.required("cenario").asString(), variante));
                    return Stream.concat(Stream.of(Named.named("Cenário principal", exemplo)), variantes)
                            .flatMap(caso -> Stream.of(TO_LEGADO, TO_FHIR)
                                    .map(sentido -> Arguments.of(recurso, sentido, caso)));
                });
    }

    private Stream<Named<DominioValores>> dominiosConfigurados() {
        return configuracao.dominios().stream().map(dominio -> Named.named(dominio.nome(), dominio));
    }

    private String lerArquivo(String caminho) throws IOException {
        try (var arquivo = getClass().getResourceAsStream(caminho)) {
            assertNotNull(arquivo, "Arquivo sintético ausente " + caminho);
            return new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
