package br.ufs.fhirsus.map;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static br.ufs.fhirsus.map.FalhaConversao.Codigo.*;
import static br.ufs.fhirsus.map.SentidoConversao.*;
import static br.ufs.fhirsus.map.TipoConversao.*;
import static org.junit.jupiter.api.Assertions.*;

class MotorConversaoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MotorConversao motor = new MotorConversao();

    @Test
    void convertePedidoFhirParaLegado() throws Exception {
        JsonNode entrada = JSON.readTree("""
                {
                  "resourceType": "ServiceRequest",
                  "status": "active",
                  "intent": "order",
                  "subject": {"reference": "Patient/PAC-001"},
                  "identifier": [{"value": "PED-001"}],
                  "code": {"coding": [{"code": "EXAME-001"}]}
                }
                """);

        JsonNode resultado = motor.converter(entrada, "ServiceRequest", TO_LEGADO, fixture());

        assertEquals(JSON.readTree("""
                {"numeroPedido":"PED-001","codigoExame":"LAB-042","origem":"CLINICA_DEMO"}
                """), resultado);
    }

    @Test
    void converteResultadoLegadoEAplicaSomenteAsRegrasDoSentidoSolicitado() throws Exception {
        MapeamentoConfig configuracao = fixture();
        JsonNode legado = JSON.readTree("""
                {"codigoExame":"LAB-042","situacao":"LIBERADO","resultado":42.5}
                """);

        JsonNode fhir = motor.converter(legado, "Observation", TO_FHIR, configuracao);

        assertEquals(JSON.readTree("""
                {
                  "resourceType":"Observation",
                  "code":{"coding":[{"system":"https://clinica.example/codigos/exames","code":"EXAME-001"}]},
                  "status":"final",
                  "valueQuantity":{"value":42.5}
                }
                """), fhir);
        assertEquals(JSON.readTree("""
                {"codigoExame":"LAB-042"}
                """), motor.converter(fhir, "Observation", TO_LEGADO, configuracao));
    }

    @Test
    void preservaTiposEcopiaAArvoreSemCompartilharNosMutaveis() {
        var entrada = JSON.readTree("""
                {"ativo":false,"numero":0,"dados":[{"codigo":"A"}]}
                """).asObject();
        var configuracao = config(direta("$", "copia", true), direta("$.ativo", "ativo", true),
                direta("$.numero", "numero", true));

        var resultado = motor.converter(entrada, "Patient", TO_LEGADO, configuracao);

        assertEquals(JSON.readTree("false"), resultado.get("ativo"));
        assertEquals(JSON.readTree("0"), resultado.get("numero"));
        assertEquals(entrada, resultado.get("copia"));
        resultado.get("copia").asObject().remove("dados");
        assertTrue(entrada.has("dados"));
    }

    @Test
    void constanteIgnoraOrigemEGravaTexto() {
        var regra = new RegraCampo("origem propositalmente inválida", "codigo", CONSTANTE, "001", true);
        assertEquals(JSON.readTree("""
                {"codigo":"001"}
                """), converter("{}", regra));
    }

    @Test
    void omiteAusenciaOpcionalEInformaDestinoObrigatorio() {
        String entrada = """
                {"nulo":null,"vazio":" "}
                """;
        var resultado = converter(entrada, direta("$.nulo", "nulo", false),
                direta("$.vazio", "vazio", false), direta("$.ausente", "ausente", false));
        assertTrue(resultado.isEmpty());

        var falha = assertThrows(FalhaConversao.class,
                () -> converter(entrada, direta("$.ausente", "paciente.codigo", true)));
        assertEquals(CAMPO_OBRIGATORIO, falha.getCodigo());
        assertEquals("paciente.codigo", falha.getCaminho());
    }

    @Test
    void distingueOrigemIncompativelDeCampoOpcionalAusente() {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("{\"dados\":true}", direta("$.dados.codigo", "codigo", false)));

        assertEquals(VALOR_INVALIDO, falha.getCodigo());
        assertEquals("$.dados.codigo", falha.getCaminho());
    }

    @Test
    void rejeitaCodigoSemEquivalencia() throws Exception {
        MapeamentoConfig configuracao = fixture();
        JsonNode entrada = JSON.readTree("""
                {"identifier":[{"value":"PED-001"}],"code":{"coding":[{"code":"DESCONHECIDO"}]}}
                """);
        var falha = assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao));

        assertEquals(CODIGO_SEM_EQUIVALENCIA, falha.getCodigo());
        assertEquals("$.code.coding[0].code", falha.getCaminho());
    }

    @Test
    void rejeitaDominioInexistenteMesmoComOrigemOpcionalAusente() {
        var regra = new RegraCampo("$.codigo", "codigo", DOMINIO, "inexistente", false);
        var falha = assertThrows(FalhaConversao.class, () -> converter("{}", regra));

        assertEquals(DOMINIO_INEXISTENTE, falha.getCodigo());
        assertEquals("codigo", falha.getCaminho());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.itens[*].codigo", "$..codigo", "codigo", "$.itens[-1]", "$.itens[2147483648]"})
    void rejeitaOrigemForaDoSubconjunto(String caminho) {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("{}", direta(caminho, "campo", false)));

        assertEquals(CAMINHO_INVALIDO, falha.getCodigo());
        assertEquals(caminho, falha.getCaminho());
    }

    @Test
    void rejeitaDestinoComSintaxeDeJsonPath() {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("{}", direta("$", "$.campo", true)));
        assertEquals(CAMINHO_INVALIDO, falha.getCodigo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dados", "dados[0]", "dados.codigo.valor"})
    void rejeitaColisaoMesmoSePrimeiraRegraForOpcionalEEstiverAusente(String destino) {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("{}", direta("$.ausente", "dados.codigo", false),
                        direta("$", destino, true)));

        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
        assertEquals(destino, falha.getCaminho());
    }

    @Test
    void rejeitaDestinoDuplicado() {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("{}", direta("$", "dados", true), direta("$", "dados", true)));
        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
    }

    @Test
    void constroiArrayPorIndiceMesmoComRegrasForaDeOrdem() {
        var resultado = converter("[12, 34]", direta("$[1]", "valores[1]", true),
                direta("$[0]", "valores[0]", true));

        assertEquals(JSON.readTree("""
                {"valores":[12,34]}
                """), resultado);
    }

    @Test
    void rejeitaIndiceEsparsoNoDestino() {
        var falha = assertThrows(FalhaConversao.class,
                () -> converter("0", direta("$", "valores[1000000000]", true)));
        assertEquals(DESTINO_CONFLITANTE, falha.getCodigo());
        assertEquals("valores[1000000000]", falha.getCaminho());
    }

    @Test
    void rejeitaConversaoForaDoRecorteNoSentidoSelecionado() {
        var regra = new RegraCampo("$", "codigo", EXPRESSAO, "$valor", true);
        var falha = assertThrows(FalhaConversao.class, () -> converter("{}", regra));
        assertEquals(REGRA_NAO_SUPORTADA, falha.getCodigo());
    }

    @Test
    void informaQuandoRecursoOuSentidoNaoTemRegras() {
        var configuracao = config(direta("$", "campo", true));
        JsonNode entrada = JSON.readTree("{}");

        assertEquals(MAPEAMENTO_INEXISTENTE, assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "ServiceRequest", TO_LEGADO, configuracao)).getCodigo());
        assertEquals(MAPEAMENTO_INEXISTENTE, assertThrows(FalhaConversao.class,
                () -> motor.converter(entrada, "Patient", TO_FHIR, configuracao)).getCodigo());
    }

    @Test
    void copiaColecoesDaConfiguracaoEUtilizaAsRegrasRecebidas() {
        var regras = new ArrayList<>(List.of(direta("$.codigo", "antes", true)));
        var mapeamentos = new ArrayList<>(List.of(new Mapeamento("Patient", regras, List.of())));
        var configuracao = new MapeamentoConfig(mapeamentos, List.of());
        regras.clear();
        mapeamentos.clear();
        var entrada = JSON.readTree("""
                {"codigo":"PAC-001"}
                """);

        assertEquals(JSON.readTree("""
                {"antes":"PAC-001"}
                """), motor.converter(entrada, "Patient", TO_LEGADO, configuracao));
        assertEquals(JSON.readTree("""
                {"depois":"PAC-001"}
                """), motor.converter(entrada, "Patient", TO_LEGADO, config(direta("$.codigo", "depois", true))));
    }

    private JsonNode converter(String json, RegraCampo... regras) {
        return motor.converter(JSON.readTree(json), "Patient", TO_LEGADO, config(regras));
    }

    private MapeamentoConfig config(RegraCampo... regras) {
        return new MapeamentoConfig(List.of(new Mapeamento("Patient", List.of(regras), List.of())), List.of());
    }

    private RegraCampo direta(String origem, String destino, boolean obrigatorio) {
        return new RegraCampo(origem, destino, DIRETA, null, obrigatorio);
    }

    private MapeamentoConfig fixture() throws Exception {
        try (InputStream arquivo = getClass().getResourceAsStream("/mapeamento-p3-sintetico.json")) {
            assertNotNull(arquivo, "Fixture de mapeamento ausente.");
            return JSON.readValue(arquivo, MapeamentoConfig.class);
        }
    }
}
