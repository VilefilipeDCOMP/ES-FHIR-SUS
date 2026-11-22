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

        ClinicalReceptionResponseDto response = new ClinicalReceptionResponseDto(
                "RECEBIDO_COM_SUCESSO",
                "Requisicao clinica sintetica validada com sucesso pelo Gateway",
                Map.of(
                        "numeroPedido", input.numeroPedido(),
                        "codigoExame", input.codigoExame(),
                        "pacienteCodigo", input.pacienteCodigo()
                )
        );

        return ResponseEntity.ok(response);
    }
}
