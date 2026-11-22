package br.ufs.fhirsus.service;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.ConversionEngine;
import br.ufs.fhirsus.engine.SentidoConversao;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Orquestrador do fluxo clínico síncrono ponta a ponta (UC-01, SD01, Issue API-04).
 * Implementa o pipeline Pipes and Filters:
 *  - Filtro 1: Validação do ServiceRequest/Bundle de entrada
 *  - Filtro 2: Conversão de FHIR para JSON Legado via CT-02
 *  - Filtro 3: Despacho HTTP ao Laboratório com timeout e resiliência via CT-01
 *  - Filtro 4: Conversão de Legado para FHIR (Observation e DiagnosticReport) via CT-02
 *  - Filtro 5: Montagem e correlação do Bundle collection FHIR R4 final
 */
@Service
public class ClinicalOrchestratorService {

    private final ServiceRequestValidator validator;
    private final ConversionEngine conversionEngine;
    private final LabLegacyClient labClient;
    private final ObjectMapper objectMapper;

    public ClinicalOrchestratorService(
            ServiceRequestValidator validator,
            ConversionEngine conversionEngine,
            LabLegacyClient labClient
    ) {
        this.validator = validator;
        this.conversionEngine = conversionEngine;
        this.labClient = labClient;
        this.objectMapper = new ObjectMapper();
    }

    public ObjectNode processarFluxoCompleto(JsonNode payload) {
        return processarFluxoCompleto(payload, false, 0);
    }

    public ObjectNode processarFluxoCompleto(JsonNode payload, boolean simularFalhaRemota, long delaySimuladoMs) {
        // Filtro 1: Validação estrutural e semântica
        ServiceRequestValidator.ValidClinicalInput input = validator.validar(payload);

        // Filtro 2: Conversão FHIR -> Legado
        ObjectNode pedidoLegado = conversionEngine.converter(
                input.serviceRequestNode(),
                "ServiceRequest",
                SentidoConversao.TO_LEGADO
        );

        // Filtro 3: Envio HTTP ao laboratório com timeout configurável
        JsonNode respostaLab = labClient.enviarPedidoLegado(pedidoLegado, simularFalhaRemota, delaySimuladoMs);

        // Filtro 4 & 5: Conversão Legado -> FHIR e Composição do Bundle final
        return montarBundleLaudoFinal(input, respostaLab);
    }

    private ObjectNode montarBundleLaudoFinal(ServiceRequestValidator.ValidClinicalInput input, JsonNode respostaLab) {
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "bundle-laudo-" + UUID.randomUUID());
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());

        ArrayNode entries = bundle.putArray("entry");

        // Identificadores únicos para correlação interna (HL7 FHIR R4)
        String uuidReport = "urn:uuid:" + UUID.randomUUID();
        String uuidObs = "urn:uuid:" + UUID.randomUUID();

        // 1. Converte e enriquece DiagnosticReport
        ObjectNode diagnosticReport = conversionEngine.converter(respostaLab, "DiagnosticReport", SentidoConversao.TO_FHIR);
        diagnosticReport.put("id", uuidReport.replace("urn:uuid:", ""));
        if (input.pacienteCodigo() != null) {
            ObjectNode subjectNode = diagnosticReport.putObject("subject");
            subjectNode.put("reference", "Patient/" + input.pacienteCodigo());
        }

        // Vincula a Observation ao DiagnosticReport (result)
        var resultArray = diagnosticReport.putArray("result");
        resultArray.addObject().put("reference", uuidObs);

        // 2. Converte e enriquece Observation a partir dos itens do laudo
        JsonNode laudoItens = respostaLab.path("laudo_itens");
        JsonNode primeiroItem = laudoItens.isArray() && !laudoItens.isEmpty() ? laudoItens.get(0) : objectMapper.createObjectNode();
        ObjectNode observation = conversionEngine.converter(primeiroItem, "Observation", SentidoConversao.TO_FHIR);
        observation.put("id", uuidObs.replace("urn:uuid:", ""));
        if (input.pacienteCodigo() != null) {
            ObjectNode subjectNode = observation.putObject("subject");
            subjectNode.put("reference", "Patient/" + input.pacienteCodigo());
        }

        // Adiciona DiagnosticReport como entry[0]
        ObjectNode reportEntry = entries.addObject();
        reportEntry.put("fullUrl", uuidReport);
        reportEntry.set("resource", diagnosticReport);

        // Adiciona Observation como entry[1]
        ObjectNode obsEntry = entries.addObject();
        obsEntry.put("fullUrl", uuidObs);
        obsEntry.set("resource", observation);

        return bundle;
    }
}
