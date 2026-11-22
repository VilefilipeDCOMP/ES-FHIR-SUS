package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.dto.ClinicalReceptionResponseDto;
import br.ufs.fhirsus.validator.ServiceRequestValidator;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// Controller de entrada clínica do Gateway.
@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalGatewayController {

    private final ServiceRequestValidator validator;

    public ClinicalGatewayController(ServiceRequestValidator validator) {
        this.validator = validator;
    }

    @PostMapping(value = "/requests", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"})
    public ResponseEntity<ClinicalReceptionResponseDto> receberRequisicaoClinica(@RequestBody JsonNode payload) {
        ServiceRequestValidator.ValidClinicalInput input = validator.validar(payload);

        Map<String, String> dados = new LinkedHashMap<>();
        if (input.numeroPedido() != null) {
            dados.put("numeroPedido", input.numeroPedido());
        }
        if (input.codigoExame() != null) {
            dados.put("codigoExame", input.codigoExame());
        }
        if (input.pacienteCodigo() != null) {
            dados.put("pacienteCodigo", input.pacienteCodigo());
        }

        ClinicalReceptionResponseDto response = new ClinicalReceptionResponseDto(
                "RECEBIDO_COM_SUCESSO",
                "Requisicao clinica sintetica validada com sucesso pelo Gateway",
                dados
        );

        return ResponseEntity.ok(response);
    }
}
