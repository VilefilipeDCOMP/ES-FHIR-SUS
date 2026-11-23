package br.ufs.fhirsus.service;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.ConversionEngine;
import br.ufs.fhirsus.engine.MotorConversaoAdapter;
import br.ufs.fhirsus.map.SentidoConversao;
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
        MotorConversaoAdapter engine = new MotorConversaoAdapter(null);
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
        assertEquals(4, entries.size());
        assertEquals("urn:uuid:paciente-01", entries.get(2).path("fullUrl").asText());

        // Validação da entry[0]: DiagnosticReport
        JsonNode reportEntry = entries.get(0);
        assertTrue(reportEntry.path("fullUrl").asText().startsWith("urn:uuid:"));
        JsonNode report = reportEntry.path("resource");
        assertEquals("DiagnosticReport", report.path("resourceType").asText());
        assertEquals("final", report.path("status").asText());
        assertEquals("urn:uuid:pedido-01", report.at("/basedOn/0/reference").asText());
        assertEquals(report.at("/basedOn/0/reference").asText(), entries.get(3).path("fullUrl").asText());
        assertEquals(payloadEntrada.at("/entry/0/resource"), entries.get(3).path("resource"));
        assertEquals("urn:uuid:paciente-01", report.path("subject").path("reference").asText());

        // Validação da entry[1]: Observation
        JsonNode obsEntry = entries.get(1);
        assertTrue(obsEntry.path("fullUrl").asText().startsWith("urn:uuid:"));
        JsonNode observation = obsEntry.path("resource");
        assertEquals("Observation", observation.path("resourceType").asText());
        assertEquals("final", observation.path("status").asText());
        assertEquals("GLICOSE-01", observation.path("code").path("coding").get(0).path("code").asText());
        assertEquals("99 mg/dL", observation.path("valueString").asText());
        assertEquals("urn:uuid:paciente-01", observation.path("subject").path("reference").asText());

        // Correlação interna: DiagnosticReport.result aponta para o fullUrl da Observation
        String obsFullUrl = obsEntry.path("fullUrl").asText();
        assertEquals(obsFullUrl, report.path("result").get(0).path("reference").asText(),
                "DiagnosticReport.result deve apontar para o fullUrl da Observation gerada");

        // Verifica regra FHIR R4: Apenas uma representação de value[x]
        assertFalse(observation.has("valueQuantity"), "Observation com valueString não deve emitir valueQuantity concorrente");
    }

    @Test
    @DisplayName("PR Review 2: Deve resolver o paciente solicitado (Patient/p1) mesmo com múltiplos Patients e fullUrls absolutos")
    void deveResolverPacienteCorretoComMultiplosPatientsEReferenciaRelativa() {
        ObjectNode labResponse = mapper.createObjectNode();
        labResponse.put("status_execucao", "CONCLUIDO");
        labResponse.put("codigo_pedido", "PED-002");
        var itens = labResponse.putArray("laudo_itens");
        ObjectNode item = itens.addObject();
        item.put("codigo_analise", "GLICOSE-01");
        item.put("resultado_valor", "100 mg/dL");

        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(labResponse);

        String bundleMultiplosPacientes = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "fullUrl": "urn:uuid:sr-01",
                      "resource": {
                        "resourceType": "ServiceRequest",
                        "identifier": [{ "value": "PED-002" }],
                        "status": "active",
                        "intent": "order",
                        "code": { "coding": [{ "code": "GLICOSE-01" }] },
                        "subject": { "reference": "Patient/p1" }
                      }
                    },
                    {
                      "fullUrl": "http://hospital.example/fhir/Patient/p2",
                      "resource": {
                        "resourceType": "Patient",
                        "id": "p2",
                        "identifier": [{ "value": "p1" }]
                      }
                    },
                    {
                      "fullUrl": "http://hospital.example/fhir/Patient/p1",
                      "resource": {
                        "resourceType": "Patient",
                        "id": "p1",
                        "identifier": [{ "value": "PAC-CORRETO-01" }]
                      }
                    }
                  ]
                }
                """;

        JsonNode payloadEntrada;
        try {
            payloadEntrada = mapper.readTree(bundleMultiplosPacientes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ObjectNode bundleRetorno = service.processarFluxoCompleto(payloadEntrada);
        assertNotNull(bundleRetorno);

        JsonNode entries = bundleRetorno.path("entry");
        JsonNode report = entries.get(0).path("resource");
        JsonNode obs = entries.get(1).path("resource");

        assertEquals("Patient/p1", report.path("subject").path("reference").asText());
        assertEquals("Patient/p1", obs.path("subject").path("reference").asText());
        assertEquals("p1", new ServiceRequestValidator().validar(payloadEntrada).patientNode().path("id").asText());
    }

    @Test
    @DisplayName("PR Review 1 & 3: Deve preservar resultados textuais reais, não substituir por 99 e emitir apenas um value[x]")
    void devePreservarResultadosTextuaisSemSubstituirPor99EEmitirApenasUmValue() {
        ObjectNode labResponse = mapper.createObjectNode();
        labResponse.put("status_execucao", "CONCLUIDO");
        labResponse.put("codigo_pedido", "PED-003");
        var itens = labResponse.putArray("laudo_itens");

        ObjectNode item1 = itens.addObject();
        item1.put("codigo_analise", "GLICOSE-01");
        item1.put("resultado_valor", "95 mg/dL");

        ObjectNode item2 = itens.addObject();
        item2.put("codigo_analise", "COLESTEROL-TOTAL");
        item2.put("resultado_valor", "180 mg/dL");

        ObjectNode item3 = itens.addObject();
        item3.put("codigo_analise", "HDL");
        item3.put("resultado_valor", "55 mg/dL");

        itens.addObject().put("codigo_analise", "HDL");
        itens.addObject().put("codigo_analise", "HDL").put("resultado_valor", " ");
        itens.addObject().put("codigo_analise", "HDL").put("resultado", 0);

        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(labResponse);

        String bundleEntrada = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "fullUrl": "urn:uuid:sr-03",
                      "resource": {
                        "resourceType": "ServiceRequest",
                        "identifier": [{ "value": "PED-003" }],
                        "status": "active",
                        "intent": "order",
                        "code": { "coding": [{ "code": "GLICOSE-01" }] },
                        "subject": { "reference": "Patient/p3" }
                      }
                    },
                    {
                      "fullUrl": "urn:uuid:p3",
                      "resource": {
                        "resourceType": "Patient",
                        "id": "p3",
                        "identifier": [{ "value": "PAC-003" }]
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

        ObjectNode bundleRetorno = service.processarFluxoCompleto(payloadEntrada);
        assertNotNull(bundleRetorno);

        JsonNode entries = bundleRetorno.path("entry");
        assertEquals(8, entries.size(), "1 DiagnosticReport + 6 Observations + 1 ServiceRequest");

        // Checa as 3 observations
        JsonNode obs1 = entries.get(1).path("resource");
        assertEquals("95 mg/dL", obs1.path("valueString").asText());
        assertFalse(obs1.has("valueQuantity"), "Não deve conter valueQuantity junto com valueString");

        JsonNode obs2 = entries.get(2).path("resource");
        assertEquals("180 mg/dL", obs2.path("valueString").asText());
        assertFalse(obs2.has("valueQuantity"), "Não deve conter valueQuantity junto com valueString");

        JsonNode obs3 = entries.get(3).path("resource");
        assertEquals("55 mg/dL", obs3.path("valueString").asText());
        assertFalse(obs3.has("valueQuantity"), "Não deve conter valueQuantity junto com valueString");

        for (int i = 4; i <= 5; i++) {
            JsonNode semResultado = entries.get(i).path("resource");
            assertFalse(semResultado.has("valueQuantity"));
            assertFalse(semResultado.has("valueString"));
        }

        JsonNode zeroMedido = entries.get(6).path("resource");
        assertTrue(zeroMedido.at("/valueQuantity/value").isNumber());
        assertEquals(0, zeroMedido.at("/valueQuantity/value").asInt());
        assertFalse(zeroMedido.has("valueString"));
    }

    @Test
    void deveRemoverOutrosTiposDeValorGeradosPeloMapeamento() {
        ConversionEngine engine = mock(ConversionEngine.class);
        when(engine.converter(any(), eq("DiagnosticReport"), eq(SentidoConversao.TO_FHIR)))
                .thenReturn(mapper.createObjectNode());
        when(engine.converter(any(), eq("Observation"), eq(SentidoConversao.TO_FHIR)))
                .thenReturn(mapper.createObjectNode().put("valueBoolean", true).put("valueInteger", 1));

        ObjectNode laudo = mapper.createObjectNode();
        laudo.putArray("laudo_itens").addObject()
                .put("codigo_analise", "LAB-042")
                .put("resultado", "Reagente");
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(laudo);

        JsonNode pedido = mapper.readTree("""
                {"resourceType":"ServiceRequest","id":"ped-1","code":{"text":"LAB-042"}}
                """);
        var orquestrador = new ClinicalOrchestratorService(new ServiceRequestValidator(), engine, labClient);

        JsonNode observation = orquestrador.processarFluxoCompleto(pedido).at("/entry/1/resource");
        assertEquals("Reagente", observation.path("valueString").asText());
        assertFalse(observation.has("valueBoolean"));
        assertFalse(observation.has("valueInteger"));
    }

    @Test
    @DisplayName("PR Review: Deve rejeitar item do laudo laboratorial sem código de exame")
    void deveRejeitarItemSemCodigoDeExame() {
        ObjectNode laudo = mapper.createObjectNode();
        laudo.put("status_execucao", "CONCLUIDO");
        laudo.put("codigo_pedido", "PED-001");
        laudo.putArray("laudo_itens").addObject().put("resultado_valor", "Reagente");
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(laudo);

        JsonNode pedido = mapper.readTree("""
                {"resourceType":"ServiceRequest","id":"ped-1","code":{"text":"LAB-042"}}
                """);

        assertThrows(br.ufs.fhirsus.exception.InvalidPayloadException.class, () ->
                service.processarFluxoCompleto(pedido));
    }

    @Test
    @DisplayName("PR Review: Deve preservar valores gerados pelo motor (custom rules) quando resultado e resultado_valor forem ausentes")
    void devePreservarValorGeradoPeloMotorQuandoAliasesForemAusentes() {
        ConversionEngine engine = mock(ConversionEngine.class);
        when(engine.converter(any(), eq("DiagnosticReport"), eq(SentidoConversao.TO_FHIR)))
                .thenReturn(mapper.createObjectNode());
        ObjectNode obsComValorCustomizado = mapper.createObjectNode();
        obsComValorCustomizado.putObject("valueQuantity").put("value", 150.0);
        when(engine.converter(any(), eq("Observation"), eq(SentidoConversao.TO_FHIR)))
                .thenReturn(obsComValorCustomizado);

        ObjectNode laudo = mapper.createObjectNode();
        laudo.put("status_execucao", "CONCLUIDO");
        laudo.put("codigo_pedido", "PED-001");
        // Item com $.valor apenas, sem resultado nem resultado_valor
        laudo.putArray("laudo_itens").addObject()
                .put("codigo_analise", "EXAME-001")
                .put("valor", 150.0);
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(laudo);

        JsonNode pedido = mapper.readTree("""
                {"resourceType":"ServiceRequest","id":"ped-1","code":{"text":"EXAME-001"}}
                """);
        var orquestrador = new ClinicalOrchestratorService(new ServiceRequestValidator(), engine, labClient);

        ObjectNode bundleRetorno = orquestrador.processarFluxoCompleto(pedido);
        JsonNode obs = bundleRetorno.at("/entry/1/resource");

        // O valor gerado pela regra customizada deve ser preservado, e não apagado
        assertTrue(obs.has("valueQuantity"));
        assertEquals(150.0, obs.at("/valueQuantity/value").asDouble());
    }

    @Test
    @DisplayName("PR Review: Deve resolver paciente contido (#p1) em ServiceRequest isolado e reescrever referências")
    void deveResolverPacienteContidoEmServiceRequestIsolado() {
        ObjectNode laudo = mapper.createObjectNode();
        laudo.put("status_execucao", "CONCLUIDO");
        laudo.put("codigo_pedido", "PED-001");
        laudo.putArray("laudo_itens").addObject()
                .put("codigo_analise", "EXAME-001")
                .put("resultado_valor", "95 mg/dL");
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(laudo);

        JsonNode pedidoComContained = mapper.readTree("""
                {
                  "resourceType": "ServiceRequest",
                  "id": "ped-contained-01",
                  "contained": [
                    {
                      "resourceType": "Patient",
                      "id": "p1",
                      "identifier": [{ "value": "PAC-CONTAINED-01" }]
                    }
                  ],
                  "subject": { "reference": "#p1" },
                  "code": { "text": "EXAME-001" }
                }
                """);

        ObjectNode bundleRetorno = service.processarFluxoCompleto(pedidoComContained);
        assertNotNull(bundleRetorno);

        JsonNode entries = bundleRetorno.path("entry");
        JsonNode report = entries.get(0).path("resource");
        JsonNode obs = entries.get(1).path("resource");

        // O subject não deve ser "#p1" órfão
        String refReport = report.at("/subject/reference").asText();
        assertFalse(refReport.equals("#p1"), "A referência do subject não pode permanecer #p1 órfão");
        assertTrue(refReport.startsWith("urn:uuid:"));

        String refObs = obs.at("/subject/reference").asText();
        assertEquals(refReport, refObs, "Observation e DiagnosticReport devem compartilhar a referência reescrita");

        // O Bundle deve conter o recurso Patient resolvido
        boolean temPacienteNoBundle = false;
        for (JsonNode entry : entries) {
            if ("Patient".equals(entry.at("/resource/resourceType").asText())) {
                assertEquals(refReport, entry.path("fullUrl").asText());
                assertEquals("p1", entry.at("/resource/id").asText());
                temPacienteNoBundle = true;
                break;
            }
        }
        assertTrue(temPacienteNoBundle, "O paciente contido deve ser adicionado como entrada do Bundle de saída");
    }
}
