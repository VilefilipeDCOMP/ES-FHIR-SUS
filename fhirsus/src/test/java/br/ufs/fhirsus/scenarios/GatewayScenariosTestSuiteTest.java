package br.ufs.fhirsus.scenarios;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.controller.ClinicalGatewayController;
import br.ufs.fhirsus.controller.HealthController;
import br.ufs.fhirsus.engine.MotorConversaoAdapter;
import br.ufs.fhirsus.exception.GlobalExceptionHandler;
import br.ufs.fhirsus.exception.LabTimeoutException;
import br.ufs.fhirsus.exception.LabUnavailableException;
import br.ufs.fhirsus.mock.LabMockController;
import br.ufs.fhirsus.service.ClinicalOrchestratorService;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suíte de Testes Automatizada de Cenários do Gateway (Issue API-05 / R03 / T5.1 / CT-01 / CT-04 / CT-05).
 * Valida a matriz completa de cenários de sucesso, validação, falhas remotas e timeouts utilizando
 * as fixtures sintéticas padronizadas em src/test/resources/fixtures/.
 */
class GatewayScenariosTestSuiteTest {

    private MockMvc clinicalMockMvc;
    private MockMvc healthMockMvc;
    private MockMvc labMockMvc;
    private LabLegacyClient labClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ServiceRequestValidator validator = new ServiceRequestValidator();
        MotorConversaoAdapter engine = new MotorConversaoAdapter(null);
        labClient = mock(LabLegacyClient.class);
        ClinicalOrchestratorService service = new ClinicalOrchestratorService(validator, engine, labClient);

        clinicalMockMvc = MockMvcBuilders.standaloneSetup(new ClinicalGatewayController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        healthMockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
        labMockMvc = MockMvcBuilders.standaloneSetup(new LabMockController()).build();
    }

    private String carregarFixture(String nomeArquivo) {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + nomeArquivo)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture não encontrada: /fixtures/" + nomeArquivo);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar fixture: " + nomeArquivo, e);
        }
    }

    private ObjectNode respostaLabSimulada() {
        ObjectNode resposta = mapper.createObjectNode();
        resposta.put("status_execucao", "CONCLUIDO");
        resposta.put("numero_protocolo_lab", "LAB-1732308901234");
        resposta.put("codigo_pedido", "PED-001");
        var itens = resposta.putArray("laudo_itens");
        ObjectNode item = itens.addObject();
        item.put("codigo_analise", "EXAME-001");
        item.put("resultado_valor", "99 mg/dL");
        item.put("status_analise", "FINAL");
        item.put("observacao_tecnica", "Resultado dentro da faixa de referência sintética do P3");
        return resposta;
    }

    @Test
    @DisplayName("Cenário 1: Sucesso com Bundle sintético completo (HTTP 200 com Bundle FHIR R4)")
    void cenario01_deveProcessarBundleSinteticoComSucesso() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(respostaLabSimulada());

        String payload = carregarFixture("solicitacao-bundle-entrada.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("DiagnosticReport"))
                .andExpect(jsonPath("$.entry[0].resource.status").value("final"))
                .andExpect(jsonPath("$.entry[0].resource.basedOn[0].reference").value("ServiceRequest/PED-001"))
                .andExpect(jsonPath("$.entry[0].resource.subject.reference").value("urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[1].resource.code.coding[0].code").value("EXAME-001"))
                .andExpect(jsonPath("$.entry[1].resource.valueString").value("99 mg/dL"))
                .andExpect(jsonPath("$.entry[1].resource.subject.reference").value("urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2"));
    }

    @Test
    @DisplayName("Cenário 2: Sucesso com ServiceRequest isolado (HTTP 200 com Bundle FHIR R4)")
    void cenario02_deveProcessarServiceRequestIsoladoComSucesso() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(respostaLabSimulada());

        String payload = carregarFixture("solicitacao-servicerequest-isolado.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType("application/fhir+json")
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("DiagnosticReport"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Observation"));
    }

    @Test
    void deveRejeitarBundleComDoisPedidosAntesDeChamarLaboratorio() throws Exception {
        ObjectNode bundle = (ObjectNode) mapper.readTree(carregarFixture("solicitacao-bundle-entrada.json"));
        var entradas = bundle.withArray("entry");
        ObjectNode segundoPedido = (ObjectNode) entradas.get(0).deepCopy();
        segundoPedido.put("fullUrl", "urn:uuid:5efd8ec0-80b1-4e96-9999-2ae52c587dd0");
        ((ObjectNode) segundoPedido.at("/resource/identifier/0")).put("value", "PED-002");
        entradas.add(segundoPedido);

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bundle.toString()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].diagnostics").value("O Bundle deve conter apenas um ServiceRequest"));

        verifyNoInteractions(labClient);
    }

    @Test
    @DisplayName("Cenário 3: Rejeição de payload vazio (HTTP 400 com OperationOutcome)")
    void cenario03_deveRejeitarPayloadVazioComHttp400() throws Exception {
        String payload = carregarFixture("erro-payload-vazio.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("structure"));
    }

    @Test
    @DisplayName("Cenário 4: Rejeição de recurso incompatível (HTTP 422 com OperationOutcome)")
    void cenario04_deveRejeitarRecursoIncompativelComHttp422() throws Exception {
        String payload = carregarFixture("erro-recurso-incompativel.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("invalid"));
    }

    @Test
    @DisplayName("Cenário 5: Rejeição de ServiceRequest sem exame (HTTP 422 com OperationOutcome)")
    void cenario05_deveRejeitarServiceRequestSemExameComHttp422() throws Exception {
        String payload = carregarFixture("erro-sem-codigo-exame.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].expression[0]").value("ServiceRequest.code"));
    }

    @Test
    @DisplayName("Cenário 6: Rejeição de ServiceRequest sem identificador (HTTP 422 com OperationOutcome)")
    void cenario06_deveRejeitarServiceRequestSemIdentificadorComHttp422() throws Exception {
        String payload = carregarFixture("erro-sem-identificador-pedido.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].expression[0]").value("ServiceRequest.identifier"));
    }

    @Test
    @DisplayName("Cenário 7: Indisponibilidade/falha remota no laboratório (HTTP 502 com OperationOutcome)")
    void cenario07_deveResponderHttp502QuandoLabEstiverIndisponivel() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabUnavailableException("Laboratório remoto respondeu com erro HTTP 500"));

        String payload = carregarFixture("solicitacao-servicerequest-isolado.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("transient"));
    }

    @Test
    @DisplayName("Cenário 8: Timeout na comunicação com laboratório (HTTP 504 com OperationOutcome)")
    void cenario08_deveResponderHttp504QuandoLabExcederTimeout() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabTimeoutException("Tempo limite excedido ao aguardar resposta do laboratório (5000 ms)"));

        String payload = carregarFixture("solicitacao-servicerequest-isolado.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("timeout"));
    }

    @Test
    @DisplayName("Cenário 9: Rota de saúde GET /api/v1/health (HTTP 200 com status UP)")
    void cenario09_deveRetornarStatusOperacionalNaRotaDeSaude() throws Exception {
        healthMockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.gateway").value("FHIR-SUS+"))
                .andExpect(jsonPath("$.versao").value("1.0.0-P3"));
    }

    @Test
    @DisplayName("Cenário 10: Rota do Mock do Laboratório POST /mock/lab/pedidos (HTTP 200 com status CONCLUIDO)")
    void cenario10_deveProcessarMockDoLaboratorioDiretamente() throws Exception {
        String payloadLegado = carregarFixture("pedido-legado.json");

        labMockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadLegado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_execucao").value("CONCLUIDO"))
                .andExpect(jsonPath("$.codigo_pedido").value("PED-001"))
                .andExpect(jsonPath("$.laudo_itens[0].codigo_analise").value("EXAME-001"));
    }

    @Test
    @DisplayName("Cenário 11: Solicitação com múltiplos resultados laboratoriais (retorna 1 DiagnosticReport + 3 Observations)")
    void cenario11_deveProcessarRetornoComMultiplosRecursosObservation() throws Exception {
        String respostaMultiplaJson = carregarFixture("resposta-laboratorio-multiplos-exames.json");
        ObjectNode respostaMultipla = (ObjectNode) mapper.readTree(respostaMultiplaJson);
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong())).thenReturn(respostaMultipla);

        String payloadEntrada = carregarFixture("solicitacao-bundle-entrada.json");

        clinicalMockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadEntrada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry.length()").value(5))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("DiagnosticReport"))
                .andExpect(jsonPath("$.entry[0].resource.result.length()").value(3))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[1].resource.code.coding[0].code").value("GLICOSE"))
                .andExpect(jsonPath("$.entry[1].resource.valueString").value("95 mg/dL"))
                .andExpect(jsonPath("$.entry[2].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[2].resource.code.coding[0].code").value("COLESTEROL-TOTAL"))
                .andExpect(jsonPath("$.entry[2].resource.valueString").value("180 mg/dL"))
                .andExpect(jsonPath("$.entry[3].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[3].resource.code.coding[0].code").value("HDL"))
                .andExpect(jsonPath("$.entry[3].resource.valueString").value("55 mg/dL"))
                .andExpect(jsonPath("$.entry[4].resource.resourceType").value("Patient"));
    }
}
