package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.MotorConversaoAdapter;
import br.ufs.fhirsus.exception.GlobalExceptionHandler;
import br.ufs.fhirsus.exception.LabTimeoutException;
import br.ufs.fhirsus.exception.LabUnavailableException;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClinicalGatewayControllerTest {

    private MockMvc mockMvc;
    private LabLegacyClient labClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ServiceRequestValidator validator = new ServiceRequestValidator();
        MotorConversaoAdapter engine = new MotorConversaoAdapter(null);
        labClient = mock(LabLegacyClient.class);
        ClinicalOrchestratorService service = new ClinicalOrchestratorService(validator, engine, labClient);
        ClinicalGatewayController controller = new ClinicalGatewayController(service);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ObjectNode criarRespostaLabPadrao() {
        ObjectNode resposta = mapper.createObjectNode();
        resposta.put("status_execucao", "CONCLUIDO");
        resposta.put("codigo_pedido", "PED-001");
        var itens = resposta.putArray("laudo_itens");
        ObjectNode item = itens.addObject();
        item.put("codigo_analise", "EXAME-001");
        item.put("resultado_valor", "99 mg/dL");
        item.put("observacao_tecnica", "Resultado normal");
        return resposta;
    }

    @Test
    @DisplayName("API-04: Deve processar fluxo ponta a ponta e responder com Bundle FHIR R4 (HTTP 200)")
    void deveProcessarFluxoPontaAPontaComSucesso() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenReturn(criarRespostaLabPadrao());

        String bundleJson = """
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    {
                      "fullUrl": "urn:uuid:d24c3ced-2413-4dbd-9cd1-f389253e441e",
                      "resource": {
                        "resourceType": "ServiceRequest",
                        "identifier": [
                          {
                            "system": "https://clinica.example/identificadores/pedidos",
                            "value": "PED-001"
                          }
                        ],
                        "status": "active",
                        "intent": "order",
                        "code": {
                          "coding": [
                            {
                              "system": "https://clinica.example/codigos/exames",
                              "code": "EXAME-001"
                            }
                          ]
                        },
                        "subject": {
                          "reference": "urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2"
                        }
                      }
                    },
                    {
                      "fullUrl": "urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2",
                      "resource": {
                        "resourceType": "Patient",
                        "identifier": [
                          {
                            "system": "https://clinica.example/identificadores/pacientes",
                            "value": "PAC-001"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bundleJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("DiagnosticReport"))
                .andExpect(jsonPath("$.entry[0].resource.basedOn[0].reference").value("ServiceRequest/PED-001"))
                .andExpect(jsonPath("$.entry[0].resource.subject.reference").value("Patient/PAC-001"))
                .andExpect(jsonPath("$.entry[1].resource.resourceType").value("Observation"))
                .andExpect(jsonPath("$.entry[1].resource.valueString").value("99 mg/dL"))
                .andExpect(jsonPath("$.entry[1].resource.subject.reference").value("Patient/PAC-001"));
    }

    @Test
    @DisplayName("API-04: Deve aceitar ServiceRequest isolado e retornar Bundle (HTTP 200)")
    void deveAceitarServiceRequestIsolado() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenReturn(criarRespostaLabPadrao());

        String serviceRequestJson = """
                {
                  "resourceType": "ServiceRequest",
                  "id": "PED-002",
                  "code": {
                    "coding": [{ "code": "GLICEMIA-01" }]
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType("application/fhir+json")
                .content(serviceRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("DiagnosticReport"));
    }

    @Test
    @DisplayName("API-04: Deve responder HTTP 400 com OperationOutcome para payload vazio")
    void deveRejeitarPayloadVazioComHttp400() throws Exception {
        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("structure"));
    }

    @Test
    @DisplayName("API-04: Deve responder HTTP 422 com OperationOutcome para recurso não suportado")
    void deveRejeitarRecursoInvalidoComHttp422() throws Exception {
        String invalidResourceJson = """
                {
                  "resourceType": "Observation",
                  "id": "obs-999"
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidResourceJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("invalid"));
    }

    @Test
    @DisplayName("API-04: Deve responder HTTP 422 com OperationOutcome quando faltar código do exame")
    void deveRejeitarServiceRequestSemExameComHttp422() throws Exception {
        String semExameJson = """
                {
                  "resourceType": "ServiceRequest",
                  "id": "PED-003"
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(semExameJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].expression[0]").value("ServiceRequest.code"));
    }

    @Test
    @DisplayName("API-04: Deve responder HTTP 502 com OperationOutcome quando o laboratório falhar")
    void deveResponderHttp502QuandoLabFalhar() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabUnavailableException("Sistema laboratorial indisponível no momento"));

        String serviceRequestJson = """
                {
                  "resourceType": "ServiceRequest",
                  "id": "PED-004",
                  "code": { "coding": [{ "code": "EXAME-001" }] }
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceRequestJson))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("transient"));
    }

    @Test
    @DisplayName("API-04: Deve responder HTTP 504 com OperationOutcome quando o laboratório der timeout")
    void deveResponderHttp504QuandoLabDerTimeout() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabTimeoutException("Tempo limite excedido ao aguardar resposta do laboratório (5000 ms)"));

        String serviceRequestJson = """
                {
                  "resourceType": "ServiceRequest",
                  "id": "PED-005",
                  "code": { "coding": [{ "code": "EXAME-001" }] }
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceRequestJson))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value("timeout"));
    }
}
