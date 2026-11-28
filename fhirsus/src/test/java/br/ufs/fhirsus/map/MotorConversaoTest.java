package br.ufs.fhirsus.map;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.*;
import static br.ufs.fhirsus.map.SentidoConversao.*;
import static br.ufs.fhirsus.map.TipoConversao.*;
import static org.junit.jupiter.api.Assertions.*;

class MotorConversaoTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final MotorConversao motor = new MotorConversao();

    @RegisterExtension
    final RelatorioConversao relatorio = new RelatorioConversao();

    @Test
    void convertePedidoFhirParaLegado() throws Exception {
        // Arrange
        MapeamentoConfig configuracao = fixture();
        JsonNode entrada = JSON.readTree("""
                {
                  "resourceType": "ServiceRequest",
                  "status": "active",
                  "intent": "order",
                  "subject": {"reference": "Patient/PAC-001"},
                  "identifier": [
                    {"system": "https://clinica.example/identificadores/externos", "value": "EXT-999"},
                    {"system": "https://clinica.example/identificadores/pedidos", "value": "PED-001"}
                  ],
                  "code": {"coding": [{"system": "http://loinc.org", "code": "2345-7"}]}
                }
                """);

        JsonNode esperado = JSON.readTree("""
                {
                  "numeroPedido":"PED-001",
                  "codigoExame":"LAB-042",
                  "situacaoPedido":"SOLICITADO",
                  "intencaoPedido":"order",
                  "referenciaPaciente":"Patient/PAC-001",
                  "origem":"CLINICA_DEMO"
                }
                """);

        // Act
        JsonNode resultado = motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void converteResultadoLegadoEAplicaSomenteAsRegrasDoSentidoSolicitado() throws Exception {
        // Arrange
        MapeamentoConfig configuracao = fixture();
        JsonNode legado = JSON.readTree("""
                {"codigoExame":"LAB-042","situacao":"LIBERADO","resultado":"42,5"}
                """);
        JsonNode legadoEsperado = legado.deepCopy();

        JsonNode fhirEsperado = JSON.readTree("""
                {
                  "resourceType":"Observation",
                  "code":{"coding":[{"system":"http://loinc.org","code":"2345-7"}]},
                  "status":"final",
                  "valueQuantity":{"value":42.5}
                }
                """);

        // Act
        JsonNode fhir = motor.converter(legado, "Observation", TO_FHIR, configuracao);
        JsonNode legadoGerado = motor.converter(fhir, "Observation", TO_LEGADO, configuracao);

        // Assert
        relatorio.conversao(legado, fhirEsperado, fhir);
        relatorio.conversao(fhir, legadoEsperado, legadoGerado);
        assertEquals(fhirEsperado, fhir);
        assertEquals(legadoEsperado, legadoGerado);
    }

    @Test
    void preservaTiposEcopiaAArvoreSemCompartilharNosMutaveis() {
        // Arrange
        var entrada = JSON.readTree("""
                {"ativo":false,"numero":0,"dados":[{"codigo":"A"}]}
                """).asObject();
        var configuracao = config(direta("$", "copia", true), direta("$.ativo", "ativo", true),
                direta("$.numero", "numero", true));
        var esperado = JSON.createObjectNode().put("ativo", false).put("numero", 0);
        esperado.set("copia", entrada.deepCopy());

        // Act
        var resultado = motor.converter(entrada, "Patient", TO_LEGADO, configuracao);
        var antesDaMutacao = resultado.deepCopy();
        resultado.get("copia").asObject().remove("dados");

        // Assert
        relatorio.conversao(esperado.get("copia"), esperado, antesDaMutacao);
        assertEquals(esperado, antesDaMutacao);
        assertTrue(entrada.has("dados"));
    }

    @Test
    void constanteIgnoraOrigemEGravaTexto() {
        // Arrange
        var regra = new RegraCampo("origem propositalmente inválida", "codigo", CONSTANTE, "001", true);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();
        var esperado = JSON.readTree("""
                {"codigo":"001"}
                """);

        // Act
        var resultado = motor.converter(entrada, "Patient", TO_LEGADO, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void omiteAusenciaOpcionalEInformaDestinoObrigatorio() {
        // Arrange
        var entrada = JSON.readTree("""
                {"nulo":null,"vazio":" "}
                """);
        var configOpcional = config(direta("$.nulo", "nulo", false),
                direta("$.vazio", "vazio", false), direta("$.ausente", "ausente", false));
        var configObrigatoria = config(direta("$.ausente", "paciente.codigo", true));

        // Act
        var resultado = motor.converter(entrada, "Patient", TO_LEGADO, configOpcional);
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configObrigatoria));

        // Assert
        relatorio.conversao(entrada, JSON.createObjectNode(), resultado);
        relatorio.falha(entrada, CAMPO_OBRIGATORIO, "paciente.codigo", falha);
        assertTrue(resultado.isEmpty());
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
        assertEquals("paciente.codigo", falha.getCaminho());
    }

    @Test
    void distingueOrigemIncompativelDeCampoOpcionalAusente() {
        // Arrange
        var entrada = JSON.readTree("{\"dados\":true}");
        var configuracao = config(direta("$.dados.codigo", "codigo", false));

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, VALOR_INVALIDO, "$.dados.codigo", falha);
        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("$.dados.codigo", falha.getCaminho());
    }

    @Test
    void rejeitaCodigoSemEquivalencia() throws Exception {
        // Arrange
        MapeamentoConfig configuracao = fixture();
        JsonNode entrada = JSON.readTree("""
                {
                  "identifier":[{"system":"https://clinica.example/identificadores/pedidos","value":"PED-001"}],
                  "code":{"coding":[{"code":"DESCONHECIDO"}]}
                }
                """);

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, CODIGO_SEM_EQUIVALENCIA, "$.code.coding[0].code", falha);
        assertEquals(CODIGO_SEM_EQUIVALENCIA, falha.getCodigo());
        assertEquals("$.code.coding[0].code", falha.getCaminho());
    }

    @Test
    void usaUmUnicoParDeDominioNosDoisSentidos() {
        // Arrange
        var dominio = new DominioValores("exames", Map.of("lab100", "glicose01"));
        var paraLegado = new RegraCampo("$.codigoFhir", "codigoExame", DOMINIO, "exames", true);
        var paraFhir = new RegraCampo("$.codigoExame", "codigoFhir", DOMINIO, "exames", true);
        var mapeamento = new Mapeamento("Observation", List.of(paraLegado), List.of(paraFhir));
        var configuracao = new MapeamentoConfig(List.of(mapeamento), List.of(dominio));
        var entrada = JSON.createObjectNode().put("codigoExame", "lab100");
        var fhirEsperado = JSON.createObjectNode().put("codigoFhir", "glicose01");
        var legadoEsperado = entrada.deepCopy();

        // Act
        var fhir = motor.converter(entrada, "Observation", TO_FHIR, configuracao);
        var legado = motor.converter(fhir, "Observation", TO_LEGADO, configuracao);

        // Assert
        relatorio.conversao(entrada, fhirEsperado, fhir);
        relatorio.conversao(fhir, legadoEsperado, legado);
        assertEquals(fhirEsperado, fhir);
        assertEquals(legadoEsperado, legado);
    }

    @Test
    void sinalizaAmbiguidadeQuandoDuasChavesTemOMesmoValor() {
        // Arrange
        var dominio = new DominioValores("exames", Map.of(
                "lab100", "glicose01",
                "lab101", "glicose01"));
        var regra = new RegraCampo("$.codigoFhir", "codigoExame", DOMINIO, "exames", true);
        var mapeamento = new Mapeamento("Observation", List.of(regra), List.of());
        var configuracao = new MapeamentoConfig(List.of(mapeamento), List.of(dominio));
        var entrada = JSON.createObjectNode().put("codigoFhir", "glicose01");

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Observation", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, CONFIGURACAO_INVALIDA, "$.codigoFhir", falha);
        assertEquals(CONFIGURACAO_INVALIDA, falha.getCodigo());
        assertEquals("$.codigoFhir", falha.getCaminho());
    }

    @Test
    void preservaEquivalenciaExplicitaQuandoCodigoTambemApareceComoValor() {
        // Arrange
        var dominio = new DominioValores("exames", Map.of(
                "LAB-042", "EXAME-001",
                "EXAME-001", "EXAME-001"));
        var regra = new RegraCampo("$.codigoExame", "codigoFhir", DOMINIO, "exames", true);
        var mapeamento = new Mapeamento("Observation", List.of(), List.of(regra));
        var configuracao = new MapeamentoConfig(List.of(mapeamento), List.of(dominio));
        var entrada = JSON.createObjectNode().put("codigoExame", "EXAME-001");
        var esperado = JSON.createObjectNode().put("codigoFhir", "EXAME-001");

        // Act
        var resultado = motor.converter(entrada, "Observation", TO_FHIR, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void rejeitaDominioInexistenteMesmoComOrigemOpcionalAusente() {
        // Arrange
        var regra = new RegraCampo("$.codigo", "codigo", DOMINIO, "inexistente", false);
        var configuracao = config(regra);
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, DOMINIO_INEXISTENTE, "codigo", falha);
        assertEquals(DOMINIO_INEXISTENTE, falha.getCodigo());
        assertEquals("codigo", falha.getCaminho());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.itens[*].codigo", "$..codigo", "codigo", "$.itens[-1]", "$.itens[2147483648]"})
    void rejeitaOrigemForaDoSubconjunto(String caminho) {
        // Arrange
        var configuracao = config(direta(caminho, "campo", false));
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, CAMINHO_INVALIDO, caminho, falha);
        assertEquals(CAMINHO_INVALIDO, falha.getCodigo());
        assertEquals(caminho, falha.getCaminho());
    }

    @Test
    void rejeitaDestinoComSintaxeDeJsonPath() {
        // Arrange
        var configuracao = config(direta("$", "$.campo", true));
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, CAMINHO_INVALIDO, null, falha);
        assertEquals(CAMINHO_INVALIDO, falha.getCodigo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dados", "dados[0]", "dados.codigo.valor"})
    void rejeitaColisaoMesmoSePrimeiraRegraForOpcionalEEstiverAusente(String destino) {
        // Arrange
        var configuracao = config(direta("$.ausente", "dados.codigo", false), direta("$", destino, true));
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, DESTINO_CONFLITANTE, destino, falha);
        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
        assertEquals(destino, falha.getCaminho());
    }

    @Test
    void rejeitaDestinoDuplicado() {
        // Arrange
        var configuracao = config(direta("$", "dados", true), direta("$", "dados", true));
        var entrada = JSON.createObjectNode();

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, DESTINO_CONFLITANTE, null, falha);
        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
    }

    @Test
    void constroiArrayPorIndiceMesmoComRegrasForaDeOrdem() {
        // Arrange
        var entrada = JSON.readTree("[12, 34]");
        var configuracao = config(direta("$[1]", "valores[1]", true),
                direta("$[0]", "valores[0]", true));
        var esperado = JSON.readTree("""
                {"valores":[12,34]}
                """);

        // Act
        var resultado = motor.converter(entrada, "Patient", TO_LEGADO, configuracao);

        // Assert
        relatorio.conversao(entrada, esperado, resultado);
        assertEquals(esperado, resultado);
    }

    @Test
    void rejeitaIndiceEsparsoNoDestino() {
        // Arrange
        var entrada = JSON.readTree("0");
        var configuracao = config(direta("$", "valores[1000000000]", true));

        // Act
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_LEGADO, configuracao));

        // Assert
        relatorio.falha(entrada, DESTINO_CONFLITANTE, "valores[1000000000]", falha);
        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
        assertEquals("valores[1000000000]", falha.getCaminho());
    }

    @Test
    void informaQuandoRecursoOuSentidoNaoTemRegras() {
        // Arrange
        var configuracao = config(direta("$", "campo", true));
        var entrada = JSON.createObjectNode();

        // Act
        var falhaRecurso = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao));
        var falhaSentido = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_FHIR, configuracao));

        // Assert
        relatorio.falha(entrada, MAPEAMENTO_INEXISTENTE, null, falhaRecurso);
        relatorio.falha(entrada, MAPEAMENTO_INEXISTENTE, null, falhaSentido);
        assertEquals(MAPEAMENTO_INEXISTENTE, falhaRecurso.getCodigo());
        assertEquals(MAPEAMENTO_INEXISTENTE, falhaSentido.getCodigo());
    }

    @Test
    void copiaColecoesDaConfiguracaoEUtilizaAsRegrasRecebidas() {
        // Arrange
        var regras = new ArrayList<>(List.of(direta("$.codigo", "antes", true)));
        var mapeamentos = new ArrayList<>(List.of(new Mapeamento("Patient", regras, List.of())));
        var configuracao = new MapeamentoConfig(mapeamentos, List.of());
        var configuracaoNova = config(direta("$.codigo", "depois", true));
        regras.clear();
        mapeamentos.clear();
        var entrada = JSON.readTree("""
                {"codigo":"PAC-001"}
                """);

        var esperadoAntes = JSON.readTree("""
                {"antes":"PAC-001"}
                """);
        var esperadoDepois = JSON.readTree("""
                {"depois":"PAC-001"}
                """);

        // Act
        var resultadoAntes = motor.converter(entrada, "Patient", TO_LEGADO, configuracao);
        var resultadoDepois = motor.converter(entrada, "Patient", TO_LEGADO, configuracaoNova);

        // Assert
        relatorio.conversao(entrada, esperadoAntes, resultadoAntes);
        relatorio.conversao(entrada, esperadoDepois, resultadoDepois);
        assertEquals(esperadoAntes, resultadoAntes);
        assertEquals(esperadoDepois, resultadoDepois);
    }

    private MapeamentoConfig config(RegraCampo... regras) {
        return new MapeamentoConfig(List.of(new Mapeamento("Patient", List.of(regras), List.of())), List.of());
    }

    private RegraCampo direta(String origem, String destino, boolean obrigatorio) {
        return new RegraCampo(origem, destino, DIRETA, null, obrigatorio);
    }

    private MapeamentoConfig fixture() throws Exception {
        try (InputStream arquivo = getClass().getResourceAsStream("/fixtures/mapeamento-mvp-sintetico.json")) {
            assertNotNull(arquivo, "Fixture de mapeamento ausente.");
            return JSON.readValue(arquivo, MapeamentoConfig.class);
        }
    }
}
