package br.ufs.fhirsus.scenarios;

import br.ufs.fhirsus.client.LabClientProperties;
import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class FluxoConfiguracaoClinicaIntegracaoTest {

    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String FHIR_JSON = "application/fhir+json";

    @LocalServerPort
    private int porta;

    @Autowired
    private LabClientProperties laboratorio;

    @Autowired
    private ConfigVersaoRepository configRepo;

    @Autowired
    private AuditoriaMapeamentoRepository auditoriaRepo;

    @Autowired
    private ValidadorFhir validador;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void prepararAmbienteIsolado() {
        auditoriaRepo.deleteAll();
        configRepo.deleteAll();
        laboratorio.setBaseUrl(urlLocal() + "/mock/lab");
    }

    @AfterEach
    void limparBancoEmMemoria() {
        auditoriaRepo.deleteAll();
        configRepo.deleteAll();
    }

    @Test
    void deveAplicarConfiguracaoAtivaPersistidaEAtualizarFluxoClinicoNaTrocaDeVersao() throws Exception {
        HttpResponse<String> inicial = get("/api/config/ativa");
        assertThat(inicial.statusCode()).isEqualTo(404);
        assertThat(json.readTree(inicial.body()).path("erro").asText()).isEqualTo("SEM_CONFIG_ATIVA");

        String configV1 = configuracaoSintetica("EXAME-CI-01", "EXAME-CI-V1");
        HttpResponse<String> validacao = postJson("/api/config/validar", configV1, JSON_MEDIA_TYPE);
        assertThat(validacao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(validacao.body()).path("valida").asBoolean()).isTrue();

        HttpResponse<String> salvamentoV1 = postJson("/api/config", configV1, JSON_MEDIA_TYPE);
        assertThat(salvamentoV1.statusCode()).isEqualTo(201);
        JsonNode bodyV1 = json.readTree(salvamentoV1.body());
        assertThat(bodyV1.path("versao").asInt()).isEqualTo(1);
        assertThat(bodyV1.path("ativa").asBoolean()).isTrue();

        HttpResponse<String> respostaClinicaV1 = postJson(
                "/api/v1/clinical/requests", pedidoComExame("EXAME-CI-01"), FHIR_JSON);
        verificarBundleComCodigoObservacao(respostaClinicaV1, "EXAME-CI-V1");

        String configV2 = configuracaoSintetica("EXAME-CI-01", "EXAME-CI-V2");
        HttpResponse<String> salvamentoV2 = postJson("/api/config", configV2, JSON_MEDIA_TYPE);
        assertThat(salvamentoV2.statusCode()).isEqualTo(201);
        assertThat(json.readTree(salvamentoV2.body()).path("versao").asInt()).isEqualTo(2);

        HttpResponse<String> auditoria = get("/api/auditoria");
        assertThat(auditoria.statusCode()).isEqualTo(200);
        JsonNode registros = json.readTree(auditoria.body());
        assertThat(registros.size()).isEqualTo(2);
        assertThat(registros.get(0).path("versaoAtual").asInt()).isEqualTo(2);
        assertThat(registros.get(0).path("versaoAnterior").asInt()).isEqualTo(1);

        HttpResponse<String> respostaClinicaV2 = postJson(
                "/api/v1/clinical/requests", pedidoComExame("EXAME-CI-01"), FHIR_JSON);
        verificarBundleComCodigoObservacao(respostaClinicaV2, "EXAME-CI-V2");
    }

    @Test
    void devePreservarConfiguracaoAtivaEFluxoClinicoQuandoSalvamentoInvalidoFalhar() throws Exception {
        HttpResponse<String> salvamentoV1 = postJson(
                "/api/config", configuracaoSintetica("EXAME-CI-01", "EXAME-CI-V1"), JSON_MEDIA_TYPE);
        assertThat(salvamentoV1.statusCode()).isEqualTo(201);

        String configInvalida = lerFixture("fixtures/config-invalida-semantica.json");
        HttpResponse<String> rejeicaoSemantica = postJson("/api/config", configInvalida, JSON_MEDIA_TYPE);
        assertThat(rejeicaoSemantica.statusCode()).isEqualTo(422);
        JsonNode erroSemantica = json.readTree(rejeicaoSemantica.body());
        assertThat(erroSemantica.path("erro").asText()).isEqualTo("CONFIG_INVALIDA");
        assertThat(erroSemantica.path("detalhes").size()).isPositive();

        String configExpressaoForaDoRecorte = """
                {
                  "mapeamentos": [
                    {
                      "recurso": "Observation",
                      "toLegado": [],
                      "toFHIR": [
                        {
                          "caminhoOrigem": "$.resultado",
                          "caminhoDestino": "valueString",
                          "tipoConversao": "EXPRESSAO",
                          "parametroConversao": "$string($valor)",
                          "obrigatorio": true
                        }
                      ]
                    }
                  ],
                  "dominios": []
                }
                """;
        HttpResponse<String> rejeicaoExpressao = postJson("/api/config", configExpressaoForaDoRecorte, JSON_MEDIA_TYPE);
        assertThat(rejeicaoExpressao.statusCode()).isEqualTo(422);
        assertThat(json.readTree(rejeicaoExpressao.body()).path("erro").asText()).isEqualTo("CONFIG_INVALIDA");

        HttpResponse<String> ativa = get("/api/config/ativa");
        assertThat(ativa.statusCode()).isEqualTo(200);
        assertThat(json.readTree(ativa.body()).path("versao").asInt()).isEqualTo(1);
        assertThat(json.readTree(get("/api/auditoria").body()).size()).isEqualTo(1);

        HttpResponse<String> sucessoPreservado = postJson(
                "/api/v1/clinical/requests", pedidoComExame("EXAME-CI-01"), FHIR_JSON);
        verificarBundleComCodigoObservacao(sucessoPreservado, "EXAME-CI-V1");

        HttpResponse<String> naoMapeado = postJson(
                "/api/v1/clinical/requests", pedidoComExame("EXAME-NAO-MAPEADO"), FHIR_JSON);
        assertThat(naoMapeado.statusCode()).isEqualTo(422);
        JsonNode outcome = json.readTree(naoMapeado.body());
        assertThat(outcome.path("resourceType").asText()).isEqualTo("OperationOutcome");
        assertThat(outcome.at("/issue/0/code").asText()).isEqualTo("not-supported");
        assertThat(outcome.at("/issue/0/diagnostics").asText()).contains("CODIGO_SEM_EQUIVALENCIA");
        var validacaoOutcome = validador.validar(naoMapeado.body(), "OperationOutcome");
        assertThat(validacaoOutcome.isSuccessful()).isTrue();
    }

    @Test
    void deveValidarConfiguracaoPadraoERejeitarTipoDesconhecidoEDisponibilizarSaude() throws Exception {
        String configPadrao = lerFixture("mapeamento-p3-sintetico.json");
        HttpResponse<String> valida = postJson("/api/config/validar", configPadrao, JSON_MEDIA_TYPE);
        assertThat(valida.statusCode()).isEqualTo(200);
        assertThat(json.readTree(valida.body()).path("valida").asBoolean()).isTrue();

        String tipoDesconhecido = lerFixture("fixtures/config-invalida-tipo-desconhecido.json");
        HttpResponse<String> invalida = postJson("/api/config/validar", tipoDesconhecido, JSON_MEDIA_TYPE);
        assertThat(invalida.statusCode()).isEqualTo(400);
        assertThat(json.readTree(invalida.body()).path("erro").asText()).isEqualTo("CONFIG_SINTAXE");

        HttpResponse<String> healthGateway = get("/api/v1/health");
        assertThat(healthGateway.statusCode()).isEqualTo(200);
        assertThat(json.readTree(healthGateway.body()).path("status").asText()).isEqualTo("UP");

        HttpResponse<String> healthActuator = get("/actuator/health");
        assertThat(healthActuator.statusCode()).isEqualTo(200);
        assertThat(json.readTree(healthActuator.body()).path("status").asText()).isEqualTo("UP");
    }

    private void verificarBundleComCodigoObservacao(HttpResponse<String> resposta, String codigoEsperado) {
        assertThat(resposta.statusCode()).as("Corpo recebido: %s", resposta.body()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValueSatisfying(tipo -> assertThat(tipo).startsWith(FHIR_JSON));

        var validacao = validador.validar(resposta.body(), "Bundle");
        assertThat(validacao.isSuccessful())
                .withFailMessage("Validação FHIR R4: %s", validacao.getMessages())
                .isTrue();

        JsonNode bundle = json.readTree(resposta.body());
        assertThat(bundle.path("resourceType").asText()).isEqualTo("Bundle");
        assertThat(bundle.path("type").asText()).isEqualTo("collection");
        assertThat(bundle.at("/entry/0/resource/resourceType").asText()).isEqualTo("DiagnosticReport");
        assertThat(bundle.at("/entry/1/resource/resourceType").asText()).isEqualTo("Observation");
        assertThat(bundle.at("/entry/1/resource/code/coding/0/code").asText()).isEqualTo(codigoEsperado);
    }

    private String pedidoComExame(String codigoExame) throws IOException {
        ObjectNode pedido = (ObjectNode) json.readTree(lerFixture("fixtures/solicitacao-servicerequest-isolado.json"));
        ((ObjectNode) pedido.at("/code/coding/0")).put("code", codigoExame);
        return pedido.toString();
    }

    private String configuracaoSintetica(String codigoClinicaEntrada, String codigoClinicaSaida) {
        return """
                {
                  "mapeamentos": [
                    {
                      "recurso": "ServiceRequest",
                      "toLegado": [
                        {
                          "caminhoOrigem": "$.identifier[0].value",
                          "caminhoDestino": "numeroPedido",
                          "tipoConversao": "DIRETA",
                          "parametroConversao": null,
                          "obrigatorio": true
                        },
                        {
                          "caminhoOrigem": "$.code.coding[0].code",
                          "caminhoDestino": "codigoExame",
                          "tipoConversao": "DOMINIO",
                          "parametroConversao": "exames_clinica_para_lab",
                          "obrigatorio": true
                        },
                        {
                          "caminhoDestino": "origem",
                          "tipoConversao": "CONSTANTE",
                          "parametroConversao": "CLINICA_CI",
                          "obrigatorio": true
                        }
                      ],
                      "toFHIR": []
                    },
                    {
                      "recurso": "Observation",
                      "toLegado": [
                        {
                          "caminhoOrigem": "$.code.coding[0].code",
                          "caminhoDestino": "codigoExame",
                          "tipoConversao": "DOMINIO",
                          "parametroConversao": "exames_clinica_para_lab",
                          "obrigatorio": true
                        }
                      ],
                      "toFHIR": [
                        {
                          "caminhoDestino": "resourceType",
                          "tipoConversao": "CONSTANTE",
                          "parametroConversao": "Observation",
                          "obrigatorio": true
                        },
                        {
                          "caminhoDestino": "code.coding[0].system",
                          "tipoConversao": "CONSTANTE",
                          "parametroConversao": "https://clinica.example/codigos/exames",
                          "obrigatorio": true
                        },
                        {
                          "caminhoOrigem": "$.codigoExame",
                          "caminhoDestino": "code.coding[0].code",
                          "tipoConversao": "DOMINIO",
                          "parametroConversao": "exames_lab_para_clinica",
                          "obrigatorio": true
                        },
                        {
                          "caminhoOrigem": "$.situacao",
                          "caminhoDestino": "status",
                          "tipoConversao": "DOMINIO",
                          "parametroConversao": "status_lab_para_fhir",
                          "obrigatorio": true
                        },
                        {
                          "caminhoOrigem": "$.resultado",
                          "caminhoDestino": "valueQuantity.value",
                          "tipoConversao": "DIRETA",
                          "parametroConversao": null,
                          "obrigatorio": false
                        }
                      ]
                    }
                  ],
                  "dominios": [
                    {
                      "nome": "exames_clinica_para_lab",
                      "equivalencia": {
                        "%s": "LAB-042"
                      }
                    },
                    {
                      "nome": "exames_lab_para_clinica",
                      "equivalencia": {
                        "LAB-042": "%s"
                      }
                    },
                    {
                      "nome": "status_lab_para_fhir",
                      "equivalencia": {
                        "LIBERADO": "final",
                        "FINAL": "final"
                      }
                    }
                  ]
                }
                """.formatted(codigoClinicaEntrada, codigoClinicaSaida);
    }

    private HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(urlLocal() + caminho))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return cliente.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String caminho, String corpo, String contentType)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(urlLocal() + caminho))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", contentType)
                .header("Accept", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        return cliente.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String lerFixture(String classpath) throws IOException {
        return new ClassPathResource(classpath).getContentAsString(StandardCharsets.UTF_8);
    }

    private String urlLocal() {
        return "http://127.0.0.1:" + porta;
    }
}
