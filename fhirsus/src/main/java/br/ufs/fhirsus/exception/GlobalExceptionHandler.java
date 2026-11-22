package br.ufs.fhirsus.exception;

import br.ufs.fhirsus.dto.fhir.OperationOutcomeDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento global de exceções para a API Gateway.
 * Converte falhas de validação, sintaxe e regras no formato FHIR OperationOutcome padronizado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPayloadException.class)
    public ResponseEntity<OperationOutcomeDto> handleInvalidPayload(InvalidPayloadException ex) {
        OperationOutcomeDto outcome = OperationOutcomeDto.of(
                "error",
                "structure",
                ex.getMessage(),
                "payload"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(outcome);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OperationOutcomeDto> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        OperationOutcomeDto outcome = OperationOutcomeDto.of(
                "error",
                "structure",
                "JSON de requisição malformado ou sintaticamente inválido: " + ex.getMostSpecificCause().getMessage(),
                "request.body"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(outcome);
    }

    @ExceptionHandler(FhirValidationException.class)
    public ResponseEntity<OperationOutcomeDto> handleFhirValidation(FhirValidationException ex) {
        OperationOutcomeDto outcome = OperationOutcomeDto.of(
                "error",
                ex.getCodigoErro(),
                ex.getMessage(),
                ex.getCampo()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(outcome);
    }
}
