package br.ufs.fhirsus.client;

import br.ufs.fhirsus.exception.LabTimeoutException;
import br.ufs.fhirsus.exception.LabUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LabLegacyClientTest {

    private HttpServer server;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("API-03: Deve enviar JSON legado e receber resposta com sucesso (HTTP 200)")
    void deveEnviarPedidoComSucesso() {
        server.createContext("/pedidos", exchange -> {
            String resposta = """
                    {
                      "status_execucao": "CONCLUIDO",
                      "codigo_pedido": "PED-001",
                      "laudo_itens": [
                        { "codigo_analise": "EXAME-001", "resultado_valor": "95 mg/dL" }
                      ]
                    }
                    """;
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        LabClientProperties props = new LabClientProperties("http://localhost:" + port, 3000);
        LabLegacyClient client = new LabLegacyClient(props);

        ObjectNode pedido = mapper.createObjectNode();
        pedido.put("codigo_pedido", "PED-001");
        pedido.put("codigo_exame", "EXAME-001");

        JsonNode resposta = client.enviarPedidoLegado(pedido);

        assertNotNull(resposta);
        assertEquals("CONCLUIDO", resposta.path("status_execucao").asText());
        assertEquals("PED-001", resposta.path("codigo_pedido").asText());
        assertEquals("95 mg/dL", resposta.path("laudo_itens").get(0).path("resultado_valor").asText());
    }

    @Test
    @DisplayName("API-03: Deve lançar LabTimeoutException quando tempo limite configurado for excedido (HTTP 504)")
    void deveLancarLabTimeoutExceptionQuandoExcederTimeout() {
        server.createContext("/pedidos", exchange -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {}
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Configura timeout agressivo de 200 ms para o teste
        LabClientProperties props = new LabClientProperties("http://localhost:" + port, 200);
        LabLegacyClient client = new LabLegacyClient(props);

        ObjectNode pedido = mapper.createObjectNode();
        pedido.put("codigo_pedido", "PED-001");

        assertThrows(LabTimeoutException.class, () -> client.enviarPedidoLegado(pedido));
    }

    @Test
    @DisplayName("API-03: Deve lançar LabUnavailableException quando laboratório responder com HTTP 500 (HTTP 502)")
    void deveLancarLabUnavailableExceptionEmErro500() {
        server.createContext("/pedidos", exchange -> {
            byte[] bytes = "{\"erro\": \"Falha interna no laboratório\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        LabClientProperties props = new LabClientProperties("http://localhost:" + port, 3000);
        LabLegacyClient client = new LabLegacyClient(props);

        ObjectNode pedido = mapper.createObjectNode();
        pedido.put("codigo_pedido", "PED-001");

        assertThrows(LabUnavailableException.class, () -> client.enviarPedidoLegado(pedido));
    }

    @Test
    @DisplayName("API-03: Deve lançar LabUnavailableException quando laboratório estiver fora do ar (indisponível)")
    void deveLancarLabUnavailableExceptionQuandoServidorInacessivel() {
        // Aponta para uma porta fechada/invalida
        LabClientProperties props = new LabClientProperties("http://localhost:65432/mock/lab", 1000);
        LabLegacyClient client = new LabLegacyClient(props);

        ObjectNode pedido = mapper.createObjectNode();
        pedido.put("codigo_pedido", "PED-001");

        assertThrows(LabUnavailableException.class, () -> client.enviarPedidoLegado(pedido));
    }
}
