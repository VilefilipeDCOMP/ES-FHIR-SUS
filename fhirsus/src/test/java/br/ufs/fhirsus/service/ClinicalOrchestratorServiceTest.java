package br.ufs.fhirsus.service;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.MockConversionEngine;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalOrchestratorServiceTest {

    private ClinicalOrchestratorService service;
    private LabLegacyClient labClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ServiceRequestValidator validator = new ServiceRequestValidator();
        MockConversionEngine engine = new MockConversionEngine();
        labClient = mock(LabLegacyClient.class);
        service = new ClinicalOrchestratorService(validator, engine, labClient);
    }

    @Test
    @DisplayName("API-04: Orquestrador deve executar o pipeline completo e retornar Bundle collection FHIR R4")
    void deveExecutarFluxoCompletoERetornarBundle() {
        // 1. Simula retorno do laboratório
        ObjectNode labResponse = mapper.createObjectNode();
        labResponse.put("status_execucao", "CONCLUIDO");
        labResponse.put("codigo_pedido", "PED-001");
        var itens = labResponse.putArray("laudo_itens");
        ObjectNode item = itens.addObject();
        item.put("codigo_analise", "GLICOSE-01");
        item.put("resultado_valor", "99 mg/dL");
        item.put("observacao_tecnica", "Glicemia em jejum sintetica normal");

        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(labResponse);

        // 2. Payload de entrada (Bundle sintético com ServiceRequest e Patient)
        String bundleEntrada = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "fullUrl": "urn:uuid:pedido-01",
                      "resource": {
                        "resourceType": "ServiceRequest",
                        "identifier": [{ "value": "PED-001" }],
                        "status": "active",
                        "intent": "order",
                        "code": { "coding": [{ "code": "GLICOSE-01" }] },
                        "subject": { "reference": "urn:uuid:paciente-01" }
                      }
                    },
                    {
                      "fullUrl": "urn:uuid:paciente-01",
                      "resource": {
                        "resourceType": "Patient",
                        "identifier": [{ "value": "PAC-001" }]
                      }
                    }
                  ]
                }
                """;

        JsonNode payloadEntrada;
        try {
            payloadEntrada = mapper.readTree(bundleEntrada);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 3. Execução
        ObjectNode bundleRetorno = service.processarFluxoCompleto(payloadEntrada);

        // 4. Asserções do Bundle de saída (CT-01 / UC-01)
        assertNotNull(bundleRetorno);
        assertEquals("Bundle", bundleRetorno.path("resourceType").asText());
        assertEquals("collection", bundleRetorno.path("type").asText());

        JsonNode entries = bundleRetorno.path("entry");
        assertEquals(2, entries.size(), "Deve conter DiagnosticReport e Observation no Bundle");

        // Validação da entry[0]: DiagnosticReport
        JsonNode reportEntry = entries.get(0);
        assertTrue(reportEntry.path("fullUrl").asText().startsWith("urn:uuid:"));
        JsonNode report = reportEntry.path("resource");
        assertEquals("DiagnosticReport", report.path("resourceType").asText());
        assertEquals("final", report.path("status").asText());
        assertEquals("ServiceRequest/PED-001", report.path("basedOn").get(0).path("reference").asText());
        assertEquals("Patient/PAC-001", report.path("subject").path("reference").asText());

        // Validação da entry[1]: Observation
        JsonNode obsEntry = entries.get(1);
        assertTrue(obsEntry.path("fullUrl").asText().startsWith("urn:uuid:"));
        JsonNode observation = obsEntry.path("resource");
        assertEquals("Observation", observation.path("resourceType").asText());
        assertEquals("final", observation.path("status").asText());
        assertEquals("GLICOSE-01", observation.path("code").path("coding").get(0).path("code").asText());
        assertEquals("99 mg/dL", observation.path("valueString").asText());
        assertEquals("Patient/PAC-001", observation.path("subject").path("reference").asText());

        // Correlação interna: DiagnosticReport.result aponta para o fullUrl da Observation
        String obsFullUrl = obsEntry.path("fullUrl").asText();
        assertEquals(obsFullUrl, report.path("result").get(0).path("reference").asText(),
                "DiagnosticReport.result deve apontar para o fullUrl da Observation gerada");
    }
}
