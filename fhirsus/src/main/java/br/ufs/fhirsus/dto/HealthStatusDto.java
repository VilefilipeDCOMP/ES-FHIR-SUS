package br.ufs.fhirsus.dto;

import java.time.Instant;
import java.util.Map;

public record HealthStatusDto(
        String status,
        Instant timestamp,
        String gateway,
        String versao,
        Map<String, String> detalhes
) {
    public static HealthStatusDto up() {
        return new HealthStatusDto(
                "UP",
                Instant.now(),
                "FHIR-SUS+",
                "1.0.0-P3",
                Map.of(
                        "recorte", "P3-Demonstracao",
                        "fluxo", "ServiceRequest - Legado - Lab - DiagnosticReport/Observation",
                        "padrao", "HL7 FHIR R4"
                )
        );
    }
}
