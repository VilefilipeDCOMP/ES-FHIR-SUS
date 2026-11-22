package br.ufs.fhirsus.engine;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Implementação do motor de conversão para isolamento e desacoplamento do Gateway no P3.
 * Implementa as transformações bidirecionais acordadas em CT-01 e CT-02:
 *  - ServiceRequest (FHIR) -> Pedido Legado (JSON)
 *  - Item de Laudo Legado -> Observation (FHIR)
 *  - Laudo Legado -> DiagnosticReport (FHIR)
 */
@Component
public class MockConversionEngine implements ConversionEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ObjectNode converter(JsonNode payload, String recurso, SentidoConversao sentido) {
        if (payload == null || payload.isNull() || payload.isEmpty()) {
            throw new FalhaConversao("invalid", recurso, "Payload nulo ou vazio para conversao");
        }

        if (sentido == SentidoConversao.TO_LEGADO) {
            if ("ServiceRequest".equalsIgnoreCase(recurso)) {
                return converterServiceRequestParaLegado(payload);
            }
            throw new FalhaConversao("unsupported", recurso, "Recurso '" + recurso + "' nao suportado para conversao para legado");
        } else {
            if ("Observation".equalsIgnoreCase(recurso)) {
                return converterLegadoParaObservation(payload);
            } else if ("DiagnosticReport".equalsIgnoreCase(recurso)) {
                return converterLegadoParaDiagnosticReport(payload);
            }
            throw new FalhaConversao("unsupported", recurso, "Recurso '" + recurso + "' nao suportado para conversao para FHIR");
        }
    }

    private ObjectNode converterServiceRequestParaLegado(JsonNode sr) {
        ObjectNode legado = objectMapper.createObjectNode();

        String codigoPedido = null;
        if (sr.has("identifier") && sr.get("identifier").isArray() && !sr.get("identifier").isEmpty()) {
            codigoPedido = sr.get("identifier").get(0).path("value").asText(null);
        }
        if (codigoPedido == null) {
            codigoPedido = sr.path("id").asText(null);
        }

        String codigoExame = null;
        JsonNode coding = sr.path("code").path("coding");
        if (coding.isArray() && !coding.isEmpty()) {
            codigoExame = coding.get(0).path("code").asText(null);
        } else {
            codigoExame = sr.path("code").path("text").asText(null);
        }

        if (codigoPedido == null || codigoPedido.isBlank()) {
            throw new FalhaConversao("required", "ServiceRequest.identifier", "Identificador do pedido ausente na transformacao para legado");
        }
        if (codigoExame == null || codigoExame.isBlank()) {
            throw new FalhaConversao("required", "ServiceRequest.code", "Codigo do exame ausente na transformacao para legado");
        }

        legado.put("codigo_pedido", codigoPedido);
        legado.put("codigo_exame", codigoExame);
        return legado;
    }

    private ObjectNode converterLegadoParaObservation(JsonNode itemLegado) {
        ObjectNode obs = objectMapper.createObjectNode();
        obs.put("resourceType", "Observation");
        obs.put("status", "final");

        String codigoAnalise = itemLegado.path("codigo_analise").asText("EXAME-001");
        String valor = itemLegado.path("resultado_valor").asText("N/A");

        ObjectNode codeNode = obs.putObject("code");
        var codingArray = codeNode.putArray("coding");
        ObjectNode coding = codingArray.addObject();
        coding.put("system", "https://clinica.example/codigos/exames");
        coding.put("code", codigoAnalise);

        obs.put("valueString", valor);

        String obsTecnica = itemLegado.path("observacao_tecnica").asText(null);
        if (obsTecnica != null) {
            var notes = obs.putArray("note");
            notes.addObject().put("text", obsTecnica);
        }

        return obs;
    }

    private ObjectNode converterLegadoParaDiagnosticReport(JsonNode laudoLegado) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("resourceType", "DiagnosticReport");
        report.put("status", "final");

        String codigoPedido = laudoLegado.path("codigo_pedido").asText("PED-001");

        ObjectNode codeNode = report.putObject("code");
        var codingArray = codeNode.putArray("coding");
        ObjectNode coding = codingArray.addObject();
        coding.put("system", "https://clinica.example/codigos/laudos");
        coding.put("code", "LAUDO-LAB");
        coding.put("display", "Relatorio Laboratorial Sintetico P3");

        var basedOnArray = report.putArray("basedOn");
        basedOnArray.addObject().put("reference", "ServiceRequest/" + codigoPedido);

        return report;
    }
}
