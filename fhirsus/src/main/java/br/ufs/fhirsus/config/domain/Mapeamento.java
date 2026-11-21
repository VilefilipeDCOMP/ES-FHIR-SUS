package br.ufs.fhirsus.config.domain;

import java.util.List;

/** Mapeamento de um recurso FHIR: regras FHIR->legado (ToLegado) e legado->FHIR (ToFHIR), 0..n cada. */
public record Mapeamento(String recurso, List<RegraCampo> regrasToLegado, List<RegraCampo> regrasToFHIR) {
}
