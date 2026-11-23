package br.ufs.fhirsus.service;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.engine.ConversionEngine;
import br.ufs.fhirsus.exception.InvalidClinicalOutputException;
import br.ufs.fhirsus.map.SentidoConversao;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *  - Filtro 1: Validação do ServiceRequest/Bundle de entrada (via HAPI FHIR R4)
 *  - Filtro 2: Conversão de FHIR para JSON Legado via CT-02 (via Motor de Conversão)
 *  - Filtro 3: Despacho HTTP ao Laboratório com timeout e resiliência via CT-01
 *  - Filtro 4: Conversão de Legado para FHIR (Observation e DiagnosticReport) via CT-02
 *  - Filtro 5: Montagem, correlação e certificação do Bundle collection FHIR R4 final
 */
@Service
public class ClinicalOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalOrchestratorService.class);

    private final ServiceRequestValidator validator;
    private final ConversionEngine conversionEngine;
    private final LabLegacyClient labClient;
    private final ValidadorFhir validadorFhir;
    private final ObjectMapper objectMapper;

    public ClinicalOrchestratorService(
            ServiceRequestValidator validator,
            ConversionEngine conversionEngine,
            LabLegacyClient labClient
    ) {
        this(validator, conversionEngine, labClient, null);
    }

    @Autowired
    public ClinicalOrchestratorService(
            ServiceRequestValidator validator,
            ConversionEngine conversionEngine,
            LabLegacyClient labClient,
            @Autowired(required = false) ValidadorFhir validadorFhir
    ) {
        this.validator = validator;
        this.conversionEngine = conversionEngine;
        this.labClient = labClient;
        this.validadorFhir = validadorFhir;
        this.objectMapper = new ObjectMapper();
    }

    public ObjectNode processarFluxoCompleto(JsonNode payload) {
        return processarFluxoCompleto(payload, false, 0);
    }

    public ObjectNode processarFluxoCompleto(JsonNode payload, boolean simularFalhaRemota, long delaySimuladoMs) {
        log.info("================================================================================");
        log.info(">>> [PIPELINE PIPES & FILTERS] INICIANDO PROCESSAMENTO DA REQUISICAO");
        log.info("================================================================================");

        // Filtro 1: Validação estrutural e semântica
        ServiceRequestValidator.ValidClinicalInput input = validator.validar(payload);
        log.info("[FILTRO 1 - VALIDACAO FHIR]: Entrada validada com sucesso!");
        log.info("  -> Pedido: {} | Exame: {}",
                input.numeroPedido(), input.codigoExame());

        // Filtro 2: Conversão FHIR -> Legado
        ObjectNode pedidoLegado = conversionEngine.converter(
                input.serviceRequestNode(),
                "ServiceRequest",
                SentidoConversao.TO_LEGADO
        );
        log.info("[FILTRO 2 - CONVERSAO FHIR -> LEGADO (CT-02)]: Payload gerado:");
        log.info("  -> {}", pedidoLegado);

        // Filtro 3: Envio HTTP ao laboratório com timeout configurável
        log.info("[FILTRO 3 - DESPACHO HTTP AO LABORATORIO (CT-01 / RF016)]: Enviando via POST...");
        JsonNode respostaLab = labClient.enviarPedidoLegado(pedidoLegado, simularFalhaRemota, delaySimuladoMs);
        log.info("[FILTRO 3 - RESPOSTA RECEBIDA DO LAB]:");
        log.info("  -> Status: {} | Protocolo: {}",
                respostaLab.path("status_execucao").asText(),
                respostaLab.path("numero_protocolo_lab").asText());

        // Validação do envelope da resposta laboratorial
        JsonNode laudoItens = respostaLab.path("laudo_itens");
        if (laudoItens.isMissingNode() || laudoItens.isNull()) {
            throw new br.ufs.fhirsus.exception.LabUnavailableException(
                    "Resposta do laboratório não contém o campo 'laudo_itens'. Envelope inesperado ou resposta de erro.");
        }
        if (!laudoItens.isArray()) {
            throw new br.ufs.fhirsus.exception.LabUnavailableException(
                    "Campo 'laudo_itens' na resposta do laboratório não é uma lista. Formato inesperado.");
        }

        // Filtros 4 & 5: Conversão Legado -> FHIR e Composição do Bundle final
        log.info("[FILTRO 4 - CONVERSAO LEGADO -> FHIR (CT-02)]: Transformando itens em Observation e DiagnosticReport");
        ObjectNode bundleFinal = montarBundleLaudoFinal(input, respostaLab);

        log.info("[FILTRO 5 - MONTAGEM E CORRELACAO DO BUNDLE FHIR R4 (UC-01)]:");
        log.info("  -> Bundle ID: {} | Tipo: {} | Total Entradas: {}",
                bundleFinal.path("id").asText(),
                bundleFinal.path("type").asText(),
                bundleFinal.path("entry").size());

        if (validadorFhir != null) {
            var validacaoSaida = validadorFhir.validar(bundleFinal.toString(), "Bundle");
            if (!validacaoSaida.isSuccessful()) {
                var erro = validacaoSaida.getMessages().stream()
                        .filter(m -> m.getSeverity() == ResultSeverityEnum.ERROR || m.getSeverity() == ResultSeverityEnum.FATAL)
                        .findFirst()
                        .orElse(null);
                log.warn("[FILTRO 5 - CERTIFICACAO FHIR]: Bundle de laudo rejeitado pelo validador R4");
                if (erro == null) {
                    throw new InvalidClinicalOutputException("Bundle de laudo rejeitado pelo validador FHIR R4", "Bundle");
                }
                throw new InvalidClinicalOutputException(
                        "Bundle de laudo inválido: " + erro.getMessage(), erro.getLocationString());
            }
            log.info("[FILTRO 5 - CERTIFICACAO FHIR]: Bundle R4 collection certificado com sucesso pelo HAPI FHIR Validator!");
        }
        log.info("================================================================================");
        log.info(">>> [PIPELINE CONCLUIDO COM SUCESSO - DEVOLVENDO HTTP 200 A CLINICA]");
        log.info("================================================================================");

        return bundleFinal;
    }

    private ObjectNode montarBundleLaudoFinal(ServiceRequestValidator.ValidClinicalInput input, JsonNode respostaLab) {
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "bundle-laudo-" + UUID.randomUUID());
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());

        ArrayNode entries = bundle.putArray("entry");

        // Identificador único do laudo (DiagnosticReport)
        String uuidReport = "urn:uuid:" + UUID.randomUUID();

        // 1. Converte e enriquece DiagnosticReport
        ObjectNode diagnosticReport = conversionEngine.converter(respostaLab, "DiagnosticReport", SentidoConversao.TO_FHIR);
        diagnosticReport.put("id", uuidReport.replace("urn:uuid:", ""));
        if (input.subject() != null && input.subject().isObject()) {
            diagnosticReport.set("subject", input.subject().deepCopy());
        }

        diagnosticReport.putArray("basedOn").add(referenciaPedido(input));
        ArrayNode resultArray = diagnosticReport.putArray("result");

        // Adiciona DiagnosticReport como entry[0]
        ObjectNode reportEntry = entries.addObject();
        reportEntry.put("fullUrl", uuidReport);
        reportEntry.set("resource", diagnosticReport);

        // 2. Converte e enriquece todas as Observations a partir dos itens do laudo
        JsonNode laudoItens = respostaLab.path("laudo_itens");
        if (laudoItens.isArray() && !laudoItens.isEmpty()) {
            for (JsonNode item : laudoItens) {
                ObjectNode itemNormalizado = (ObjectNode) item.deepCopy();
                String cod = item.hasNonNull("codigoExame")
                        ? item.get("codigoExame").asText()
                        : item.path("codigo_analise").asText(null);
                if (cod == null || cod.isBlank()) {
                    throw new br.ufs.fhirsus.exception.InvalidPayloadException("Item do laudo laboratorial sem código de exame ('codigoExame' ou 'codigo_analise')");
                }
                itemNormalizado.put("codigoExame", cod);

                if (!itemNormalizado.has("situacao")) {
                    String sit = item.path("status_analise").asText("LIBERADO");
                    itemNormalizado.put("situacao", sit);
                }

                JsonNode resultado = obterResultado(item);
                if (resultado == null) {
                    itemNormalizado.remove("resultado");
                } else {
                    itemNormalizado.set("resultado", resultado);
                }

                String uuidObs = "urn:uuid:" + UUID.randomUUID();
                ObjectNode observation = conversionEngine.converter(itemNormalizado, "Observation", SentidoConversao.TO_FHIR);
                observation.put("id", uuidObs.replace("urn:uuid:", ""));
                aplicarResultado(observation, resultado);

                if (input.subject() != null && input.subject().isObject()) {
                    observation.set("subject", input.subject().deepCopy());
                }

                // Vincula o UUID desta Observation ao DiagnosticReport.result
                resultArray.addObject().put("reference", uuidObs);

                // Adiciona esta Observation ao Bundle
                ObjectNode obsEntry = entries.addObject();
                obsEntry.put("fullUrl", uuidObs);
                obsEntry.set("resource", observation);
            }
        }

        if (input.patientNode() != null && input.patientFullUrl() != null
                && input.patientFullUrl().equals(input.subject().path("reference").asText())) {
            entries.addObject().put("fullUrl", input.patientFullUrl()).set("resource", input.patientNode().deepCopy());
        }

        if (input.serviceRequestFullUrl().startsWith("urn:")) {
            entries.addObject().put("fullUrl", input.serviceRequestFullUrl())
                    .set("resource", input.serviceRequestNode().deepCopy());
        }

        return bundle;
    }

    private ObjectNode referenciaPedido(ServiceRequestValidator.ValidClinicalInput input) {
        ObjectNode reference = objectMapper.createObjectNode();
        if (!input.serviceRequestFullUrl().isBlank()) {
            return reference.put("reference", input.serviceRequestFullUrl());
        }
        String id = input.serviceRequestNode().path("id").asText("");
        if (!id.isBlank()) {
            return reference.put("reference", "ServiceRequest/" + id);
        }
        return reference.set("identifier", input.serviceRequestNode().at("/identifier/0").deepCopy());
    }

    private JsonNode obterResultado(JsonNode item) {
        JsonNode valor = item.path("resultado_valor");
        if (valor.isString() && !valor.asText().isBlank()) {
            return valor;
        }
        JsonNode resultado = item.path("resultado");
        if (resultado.isNumber() || (resultado.isString() && !resultado.asText().isBlank())) {
            return resultado;
        }
        return valor.isNumber() ? valor : null;
    }

    private static final java.util.Set<String> VALUE_X_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "valueQuantity", "valueCodeableConcept", "valueString", "valueBoolean",
            "valueInteger", "valueRange", "valueRatio", "valueSampledData",
            "valueTime", "valueDateTime", "valuePeriod"
    ));

    private void aplicarResultado(ObjectNode observation, JsonNode resultado) {
        if (resultado == null) {
            // Motor produziu valor via regra customizada — preserva tudo que ele gerou
            return;
        }

        boolean numerico = resultado.isNumber();
        boolean motorProduziuValueQuantity = observation.has("valueQuantity") && observation.get("valueQuantity").isObject();

        if (motorProduziuValueQuantity && numerico) {
            // Motor gerou valueQuantity via regra configurada e o alias tem valor numérico:
            // complementa o valueQuantity com o valor numérico se ainda não tiver, e remove outros value[x]
            ObjectNode vq = (ObjectNode) observation.get("valueQuantity");
            if (!vq.has("value")) {
                vq.set("value", resultado);
            }
            // Remove outros value[x] incompatíveis (preserva valueQuantity)
            VALUE_X_FIELDS.stream().filter(f -> !f.equals("valueQuantity")).forEach(observation::remove);
            return;
        }

        // Remove todos os value[x] que o motor possa ter gerado (incluindo valueQuantity quando o alias é string)
        VALUE_X_FIELDS.forEach(observation::remove);

        // Aplica o alias como única representação
        if (numerico) {
            observation.putObject("valueQuantity").set("value", resultado);
        } else {
            observation.set("valueString", resultado);
        }
    }
}
