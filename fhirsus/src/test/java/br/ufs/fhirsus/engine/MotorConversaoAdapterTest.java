package br.ufs.fhirsus.engine;

import br.ufs.fhirsus.config.service.ConfigService;
import br.ufs.fhirsus.map.FalhaConversao;
import br.ufs.fhirsus.map.MapeamentoConfig;
import br.ufs.fhirsus.map.SentidoConversao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MotorConversaoAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ConfigService configService;
    private MotorConversaoAdapter adapter;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        when(configService.ativaComoModelo()).thenReturn(Optional.empty());
        adapter = new MotorConversaoAdapter(configService);
    }

    @Test
    @DisplayName("Deve converter ServiceRequest para Legado usando o MotorConversao oficial com regras sintéticas")
    void deveConverterServiceRequestParaLegadoOficial() throws Exception {
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

        ObjectNode legado = adapter.converter(entrada, "ServiceRequest", SentidoConversao.TO_LEGADO);

        assertNotNull(legado);
        assertEquals("PED-001", legado.path("numeroPedido").asText());
        assertEquals("LAB-042", legado.path("codigoExame").asText());
        assertEquals("CLINICA_DEMO", legado.path("origem").asText());
    }

    @Test
    @DisplayName("Deve converter Observation de Legado para FHIR usando o MotorConversao oficial")
    void deveConverterObservationParaFhirOficial() throws Exception {
        JsonNode legado = JSON.readTree("""
                {"codigoExame":"LAB-042","situacao":"LIBERADO","resultado":42.5}
                """);

        ObjectNode fhir = adapter.converter(legado, "Observation", SentidoConversao.TO_FHIR);

        assertNotNull(fhir);
        assertEquals("Observation", fhir.path("resourceType").asText());
        assertEquals("final", fhir.path("status").asText());
        assertEquals("EXAME-001", fhir.at("/code/coding/0/code").asText());
        assertEquals(42.5, fhir.at("/valueQuantity/value").asDouble());
    }

    @Test
    void deveRejeitarStatusSemEquivalenciaComExameConhecidoOuLocal() {
        for (String codigo : new String[]{"LAB-042", "LAB-LOCAL"}) {
            ObjectNode item = JSON.createObjectNode()
                    .put("codigoExame", codigo)
                    .put("situacao", "PRELIMINAR")
                    .put("resultado", 42.5);
            FalhaConversao falha = assertThrows(FalhaConversao.class,
                    () -> adapter.converter(item, "Observation", SentidoConversao.TO_FHIR));

            assertEquals(FalhaConversao.Codigo.CODIGO_SEM_EQUIVALENCIA, falha.getCodigo());
            assertEquals("$.situacao", falha.getCaminho());
        }
    }

    @Test
    void devePreservarCodigoLocalQuandoStatusTemEquivalencia() {
        ObjectNode item = JSON.createObjectNode()
                .put("codigoExame", "LAB-LOCAL")
                .put("situacao", "LIBERADO")
                .put("resultado", 42.5);

        ObjectNode observation = adapter.converter(item, "Observation", SentidoConversao.TO_FHIR);

        assertEquals("LAB-LOCAL", observation.at("/code/coding/0/code").asText());
        assertEquals("final", observation.path("status").asText());
    }

    @Test
    @DisplayName("Deve construir cabeçalho do recurso envelope DiagnosticReport de forma resiliente")
    void deveConstruirDiagnosticReportResiliente() throws Exception {
        JsonNode respostaLab = JSON.readTree("""
                {
                  "status_execucao": "CONCLUIDO",
                  "numero_protocolo_lab": "LAB-12345",
                  "codigo_pedido": "PED-001"
                }
                """);

        ObjectNode report = adapter.converter(respostaLab, "DiagnosticReport", SentidoConversao.TO_FHIR);

        assertNotNull(report);
        assertEquals("DiagnosticReport", report.path("resourceType").asText());
        assertEquals("final", report.path("status").asText());
        assertFalse(report.has("basedOn"));
    }

    @Test
    @DisplayName("Deve propagar FalhaConversao oficial quando código não possuir equivalência no domínio")
    void devePropagarFalhaConversaoCodigoSemEquivalencia() throws Exception {
        JsonNode entrada = JSON.readTree("""
                {
                  "resourceType": "ServiceRequest",
                  "identifier": [{"value": "PED-999"}],
                  "code": {"coding": [{"code": "CODIGO_INEXISTENTE"}]}
                }
                """);

        FalhaConversao ex = assertThrows(FalhaConversao.class, () ->
                adapter.converter(entrada, "ServiceRequest", SentidoConversao.TO_LEGADO)
        );

        assertEquals(FalhaConversao.Codigo.CODIGO_SEM_EQUIVALENCIA, ex.getCodigo());
    }

    @Test
    @DisplayName("Deve utilizar configuração customizada ativa vinda do ConfigService (PostgreSQL)")
    void deveUtilizarConfiguracaoAtivaDoPostgres() throws Exception {
        MapeamentoConfig customConfig = JSON.readValue("""
                {
                  "mapeamentos": [
                    {
                      "recurso": "ServiceRequest",
                      "toLegado": [
                        {
                          "caminhoOrigem": "$.identifier[0].value",
                          "caminhoDestino": "pedidoCustom",
                          "tipoConversao": "DIRETA",
                          "obrigatorio": true
                        }
                      ],
                      "toFHIR": []
                    }
                  ],
                  "dominios": []
                }
                """, MapeamentoConfig.class);

        when(configService.ativaComoModelo()).thenReturn(Optional.of(customConfig));

        JsonNode entrada = JSON.readTree("""
                {
                  "resourceType": "ServiceRequest",
                  "identifier": [{"value": "PED-CUSTOM-77"}]
                }
                """);

        ObjectNode legado = adapter.converter(entrada, "ServiceRequest", SentidoConversao.TO_LEGADO);

        assertEquals("PED-CUSTOM-77", legado.path("pedidoCustom").asText());
        verify(configService, atLeastOnce()).ativaComoModelo();
    }
}
