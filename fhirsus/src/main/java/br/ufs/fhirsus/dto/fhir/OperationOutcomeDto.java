package br.ufs.fhirsus.dto.fhir;

import java.util.List;
 //Representação do recurso HL7 FHIR R4 OperationOutcome para retornos de erro.

public record OperationOutcomeDto(
        String resourceType,
        List<Issue> issue
) {
    public OperationOutcomeDto {
        if (resourceType == null) {
            resourceType = "OperationOutcome";
        }
    }

    public static OperationOutcomeDto of(String severity, String code, String diagnostics, String expression) {
        return new OperationOutcomeDto(
                "OperationOutcome",
                List.of(new Issue(severity, code, diagnostics, expression != null ? List.of(expression) : List.of()))
        );
    }

    public record Issue(
            String severity,
            String code,
            String diagnostics,
            List<String> expression
    ) {}
}
