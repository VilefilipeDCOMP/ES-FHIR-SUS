package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.exception.GlobalExceptionHandler;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClinicalGatewayControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ServiceRequestValidator validator = new ServiceRequestValidator();
        ClinicalGatewayController controller = new ClinicalGatewayController(validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("API-02: Deve aceitar Bundle sintético válido com ServiceRequest e Patient (HTTP 200)")
    void deveAceitarBundleSinteticoValido() throws Exception {
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
                .andExpect(jsonPath("$.status").value("RECEBIDO_COM_SUCESSO"))
                .andExpect(jsonPath("$.dadosExtraidos.numeroPedido").value("PED-001"))
                .andExpect(jsonPath("$.dadosExtraidos.codigoExame").value("EXAME-001"))
                .andExpect(jsonPath("$.dadosExtraidos.pacienteCodigo").value("PAC-001"));
    }

    @Test
    @DisplayName("API-02: Deve aceitar ServiceRequest isolado válido (HTTP 200)")
    void deveAceitarServiceRequestIsoladoValido() throws Exception {
        String serviceRequestJson = """
                {
                  "resourceType": "ServiceRequest",
                  "id": "PED-002",
                  "code": {
                    "coding": [
                      {
                        "code": "GLICEMIA-01"
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/clinical/requests")
                .contentType("application/fhir+json")
                .content(serviceRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEBIDO_COM_SUCESSO"))
                .andExpect(jsonPath("$.dadosExtraidos.numeroPedido").value("PED-002"))
                .andExpect(jsonPath("$.dadosExtraidos.codigoExame").value("GLICEMIA-01"))
                .andExpect(jsonPath("$.dadosExtraidos.pacienteCodigo").doesNotExist());
    }

    @Test
    @DisplayName("API-02: Deve rejeitar payload vazio com HTTP 400 e OperationOutcome")
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
    @DisplayName("API-02: Deve rejeitar recurso incompatível com HTTP 422 e OperationOutcome")
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
    @DisplayName("API-02: Deve rejeitar ServiceRequest sem código de exame com HTTP 422 e OperationOutcome")
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
}
