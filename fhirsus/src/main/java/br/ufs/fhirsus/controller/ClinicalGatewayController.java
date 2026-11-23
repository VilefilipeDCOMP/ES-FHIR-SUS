package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.service.ClinicalOrchestratorService;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de entrada clínica do Gateway.
 * Atende ao fluxo síncrono ponta a ponta (UC-01, SD01, RF001, RF002, Issue API-04).
 * Recebe a solicitação clínica FHIR R4, orquestra a validação, conversão e chamada ao laboratório,
 * e retorna o Bundle FHIR R4 consolidado com DiagnosticReport e Observation.
 */
@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalGatewayController {

    private final ClinicalOrchestratorService orchestratorService;

    public ClinicalGatewayController(ClinicalOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping(
            value = "/requests",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"},
            produces = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"}
    )
    public ResponseEntity<JsonNode> receberRequisicaoClinica(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "X-Simular-Falha-Lab", required = false, defaultValue = "false") boolean simularFalhaLab,
            @RequestHeader(value = "X-Simular-Delay-Lab-Ms", required = false, defaultValue = "0") long delayLabMs
    ) {
        JsonNode bundleResposta = orchestratorService.processarFluxoCompleto(payload, simularFalhaLab, delayLabMs);
        return ResponseEntity.ok(bundleResposta);
    }
}
