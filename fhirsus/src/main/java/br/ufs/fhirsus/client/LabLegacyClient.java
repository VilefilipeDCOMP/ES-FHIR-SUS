package br.ufs.fhirsus.client;

import br.ufs.fhirsus.exception.LabTimeoutException;
import br.ufs.fhirsus.exception.LabUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Cliente HTTP responsável por despachar pedidos em formato JSON legado para o laboratório simulado.
 * Implementa timeout configurável e tratamento de indisponibilidade conforme CT-04 e ADR03.
 */
@Component
public class LabLegacyClient {

    private final LabClientProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LabLegacyClient(LabClientProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, properties.getTimeoutMs())))
                .build();
    }

    public JsonNode enviarPedidoLegado(JsonNode pedidoLegado) {
        return enviarPedidoLegado(pedidoLegado, false, 0);
    }

    public JsonNode enviarPedidoLegado(JsonNode pedidoLegado, boolean simularFalha, long delayMs) {
        String url = properties.getBaseUrl() + "/pedidos";
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(pedidoLegado);
        } catch (Exception e) {
            throw new LabUnavailableException("Erro ao serializar payload legado para envio: " + e.getMessage(), e);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(Math.max(100, properties.getTimeoutMs())))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (simularFalha) {
            requestBuilder.header("X-Mock-Fail", "true");
        }
        if (delayMs > 0) {
            requestBuilder.header("X-Mock-Delay-Ms", String.valueOf(delayMs));
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 500) {
                throw new LabUnavailableException("Laboratório remoto respondeu com erro de servidor HTTP " + response.statusCode());
            }

            if (response.statusCode() >= 400) {
                throw new LabUnavailableException("Laboratório remoto rejeitou a requisição com HTTP " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readTree(response.body());

        } catch (HttpTimeoutException e) {
            throw new LabTimeoutException("Tempo limite excedido ao aguardar resposta do laboratório (" + properties.getTimeoutMs() + " ms)", e);
        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw new LabTimeoutException("Tempo limite excedido ao aguardar resposta do laboratório (" + properties.getTimeoutMs() + " ms)", e);
            }
            throw new LabUnavailableException("Falha na comunicação ou laboratório indisponível: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LabUnavailableException("Envio de requisição para o laboratório foi interrompido", e);
        }
    }
}
