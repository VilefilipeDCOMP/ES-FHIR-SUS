package br.ufs.fhirsus.config.domain;

import java.util.List;

public record Mapeamento(String recurso, List<RegraCampo> regrasToLegado, List<RegraCampo> regrasToFHIR) {
}
