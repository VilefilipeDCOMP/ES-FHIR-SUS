package br.ufs.fhirsus.scenarios;

import br.ufs.fhirsus.client.LabClientProperties;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class FluxoClinicoIntegracaoTest {

    private static final String FHIR_JSON = "application/fhir+json";
    private static final String REFERENCIA_PEDIDO = "urn:uuid:d24c3ced-2413-4dbd-9cd1-f389253e441e";
    private static final String REFERENCIA_PACIENTE = "urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2";

    @LocalServerPort
    private int porta;

    @Autowired
    private LabClientProperties laboratorio;

    @Autowired
    private ValidadorFhir validador;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void configurarLaboratorioNaPortaDoServidor() {
        laboratorio.setBaseUrl(urlLocal() + "/mock/lab");
    }

    @Test
    void deveEntregarBundleValidoDepoisDeConsultarLaboratorio() throws Exception {
        verificarBundleFinal(enviarPedido(pedido(carregarPedidoValido()).build()));
    }

    @Test
    void deveRejeitarJsonMalformadoEProcessarProximoPedido() throws Exception {
        HttpRequest invalido = pedido("{\"resourceType\":").build();
        HttpRequest valido = pedido(carregarPedidoValido()).build();

        long inicioErro = System.nanoTime();
        HttpResponse<String> erro = enviarPedido(invalido);
        long inicioRecuperacao = System.nanoTime();
        HttpResponse<String> recuperacao = enviarPedido(valido);
        long fimRecuperacao = System.nanoTime();

        System.out.printf("CQ02: HTTP %d em %d ms, pedido seguinte HTTP %d em %d ms%n",
                erro.statusCode(), Duration.ofNanos(inicioRecuperacao - inicioErro).toMillis(),
                recuperacao.statusCode(), Duration.ofNanos(fimRecuperacao - inicioRecuperacao).toMillis());
        verificarBundleFinal(recuperacao);
        verificarOperationOutcome(erro, 400);
    }

    @Test
    void deveResponderComErroQuandoLaboratorioFalharEProcessarProximoPedido() throws Exception {
        String payload = carregarPedidoValido();
        HttpResponse<String> erro = enviarPedido(pedido(payload)
                .header("X-Simular-Falha-Lab", "true").build());
        verificarBundleFinal(enviarPedido(pedido(payload).build()));
        verificarOperationOutcome(erro, 502);
    }

    private String urlLocal() {
        return "http://127.0.0.1:" + porta;
    }

    private String carregarPedidoValido() throws IOException {
        return new ClassPathResource("fixtures/solicitacao-bundle-entrada.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder pedido(String payload) {
        return HttpRequest.newBuilder(URI.create(urlLocal() + "/api/v1/clinical/requests"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", FHIR_JSON)
                .header("Accept", FHIR_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(payload));
    }

    private HttpResponse<String> enviarPedido(HttpRequest pedido) throws IOException, InterruptedException {
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private void verificarOperationOutcome(HttpResponse<String> resposta, int status) {
        assertThat(resposta.statusCode()).as("Corpo recebido: %s", resposta.body()).isEqualTo(status);
        assertThat(resposta.headers().firstValue("Content-Type")).hasValueSatisfying(tipo ->
                assertThat(tipo).startsWith(FHIR_JSON));

        JsonNode outcome = json.readTree(resposta.body());
        assertThat(outcome.path("resourceType").asText())
                .as("Corpo recebido: %s", resposta.body())
                .isEqualTo("OperationOutcome");
        assertThat(outcome.path("issue").isArray()).isTrue();
        assertThat(outcome.path("issue").size()).isPositive();
    }

    private void verificarBundleFinal(HttpResponse<String> resposta) {
        assertThat(resposta.statusCode()).as("Corpo recebido: %s", resposta.body()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type")).hasValueSatisfying(tipo ->
                assertThat(tipo).startsWith(FHIR_JSON));
        var validacao = validador.validar(resposta.body(), "Bundle");
        assertThat(validacao.isSuccessful()).withFailMessage("Validação FHIR R4: %s", validacao.getMessages()).isTrue();

        JsonNode bundle = json.readTree(resposta.body());
        assertThat(bundle.path("type").asText()).isEqualTo("collection");
        assertThat(bundle.path("entry").size()).isEqualTo(4);
        JsonNode laudo = bundle.at("/entry/0/resource");
        JsonNode observacao = bundle.at("/entry/1/resource");

        assertThat(laudo.path("resourceType").asText()).isEqualTo("DiagnosticReport");
        assertThat(laudo.path("status").asText()).isEqualTo("final");
        assertThat(laudo.at("/basedOn/0/reference").asText()).isEqualTo(REFERENCIA_PEDIDO);
        assertThat(laudo.at("/subject/reference").asText()).isEqualTo(REFERENCIA_PACIENTE);
        assertThat(observacao.path("resourceType").asText()).isEqualTo("Observation");
        assertThat(observacao.path("status").asText()).isEqualTo("final");
        assertThat(observacao.at("/code/coding/0/code").asText()).isEqualTo("EXAME-001");
        assertThat(observacao.path("valueString").asText()).isEqualTo("99 mg/dL");
        assertThat(observacao.at("/subject/reference").asText()).isEqualTo(REFERENCIA_PACIENTE);
        assertThat(laudo.at("/result/0/reference").asText()).isEqualTo(bundle.at("/entry/1/fullUrl").asText());
    }
}
