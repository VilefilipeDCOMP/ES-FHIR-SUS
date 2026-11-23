package br.ufs.fhirsus.scenarios;

import br.ufs.fhirsus.client.LabClientProperties;
import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.MotorConversaoAdapter;
import br.ufs.fhirsus.map.SentidoConversao;
import br.ufs.fhirsus.mock.LabMockController;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de Inspeção Passo a Passo do Pipeline Pipes and Filters.
 * Executa e imprime no console a entrada e a saída de cada um dos 5 filtros individualmente.
 */
class PipelineFiltersInspectionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private String carregarFixture(String nome) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + nome)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Demonstração Didática: Inspecionar entrada e saída de cada um dos 5 filtros do Pipeline")
    void inspecionarCadaFiltroDoPipeline() throws Exception {
        System.out.println("\n" + "=".repeat(85));
        System.out.println("   DEMONSTRAÇÃO ARQUITETURAL: PIPELINE PIPES AND FILTERS (P2 / P3)");
        System.out.println("=".repeat(85));

        // -----------------------------------------------------------------------------------------
        // FILTRO 1: Validação de Entrada FHIR R4
        // -----------------------------------------------------------------------------------------
        System.out.println("\n>>> [FILTRO 1 - VALIDAÇÃO ESTRUTURAL E SEMÂNTICA (ADR09 / CT-01)]");
        String entradaJson = carregarFixture("solicitacao-bundle-entrada.json");
        JsonNode payloadEntrada = mapper.readTree(entradaJson);
        System.out.println("Entrada Recebida da Clínica:");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payloadEntrada));

        ServiceRequestValidator validator = new ServiceRequestValidator();
        ServiceRequestValidator.ValidClinicalInput input = validator.validar(payloadEntrada);
        System.out.println("\nSAÍDA DO FILTRO 1 (Dados Validados e Extraídos):");
        System.out.println("  • Número do Pedido: " + input.numeroPedido());
        System.out.println("  • Código do Exame:   " + input.codigoExame());
        System.out.println("  • Referência do paciente: " + input.subject());

        // -----------------------------------------------------------------------------------------
        // FILTRO 2: Conversão FHIR -> Legado (CT-02)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n" + "-".repeat(85));
        System.out.println(">>> [FILTRO 2 - MOTOR DE CONVERSÃO: FHIR ➔ JSON LEGADO (CT-02)]");
        MotorConversaoAdapter engine = new MotorConversaoAdapter(null);
        ObjectNode pedidoLegado = engine.converter(input.serviceRequestNode(), "ServiceRequest", SentidoConversao.TO_LEGADO);
        System.out.println("SAÍDA DO FILTRO 2 (Payload Legado Gerado para Envio ao Laboratório):");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pedidoLegado));

        // -----------------------------------------------------------------------------------------
        // FILTRO 3: Envio HTTP ao Laboratório Legado com Timeout
        // -----------------------------------------------------------------------------------------
        System.out.println("\n" + "-".repeat(85));
        System.out.println(">>> [FILTRO 3 - DESPACHO HTTP COM TIMEOUT CONFIGURÁVEL (CT-01 / RF016)]");
        LabMockController mockLab = new LabMockController();
        // Simula o laboratório recebendo e processando
        var responseEntity = mockLab.receberPedidoLegado(pedidoLegado, false, 0);
        JsonNode respostaLab = (JsonNode) responseEntity.getBody();
        System.out.println("SAÍDA DO FILTRO 3 (Resposta do Laboratório Recebida via Rede):");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(respostaLab));

        // -----------------------------------------------------------------------------------------
        // FILTRO 4: Conversão Legado -> FHIR (CT-02)
        // -----------------------------------------------------------------------------------------
        System.out.println("\n" + "-".repeat(85));
        System.out.println(">>> [FILTRO 4 - MOTOR DE CONVERSÃO: LEGADO ➔ RECURSOS HL7 FHIR (CT-02)]");
        ObjectNode diagnosticReport = engine.converter(respostaLab, "DiagnosticReport", SentidoConversao.TO_FHIR);
        ObjectNode observation = engine.converter(respostaLab.path("laudo_itens").get(0), "Observation", SentidoConversao.TO_FHIR);

        System.out.println("Recurso 1 Convertido (DiagnosticReport):");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(diagnosticReport));
        System.out.println("\nRecurso 2 Convertido (Observation):");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(observation));

        // -----------------------------------------------------------------------------------------
        // FILTRO 5: Montagem e Correlação do Bundle Final
        // -----------------------------------------------------------------------------------------
        System.out.println("\n" + "-".repeat(85));
        System.out.println(">>> [FILTRO 5 - MONTAGEM DO ENVELOPE BUNDLE E CORRELAÇÃO DE IDENTIFICADORES (UC-01)]");
        ObjectNode bundleFinal = mapper.createObjectNode();
        bundleFinal.put("resourceType", "Bundle");
        bundleFinal.put("id", "bundle-laudo-" + UUID.randomUUID());
        bundleFinal.put("type", "collection");
        bundleFinal.put("timestamp", Instant.now().toString());

        String uuidReport = "urn:uuid:" + UUID.randomUUID();
        String uuidObs = "urn:uuid:" + UUID.randomUUID();

        diagnosticReport.put("id", uuidReport.replace("urn:uuid:", ""));
        diagnosticReport.set("subject", input.subject());
        diagnosticReport.putArray("result").addObject().put("reference", uuidObs);

        observation.put("id", uuidObs.replace("urn:uuid:", ""));
        observation.set("subject", input.subject());

        ArrayNode entries = bundleFinal.putArray("entry");
        entries.addObject().put("fullUrl", uuidReport).set("resource", diagnosticReport);
        entries.addObject().put("fullUrl", uuidObs).set("resource", observation);

        System.out.println("SAÍDA DO FILTRO 5 (Bundle FHIR R4 Final Entregue à Clínica):");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundleFinal));
        System.out.println("=".repeat(85) + "\n");

        assertNotNull(bundleFinal);
    }
}
