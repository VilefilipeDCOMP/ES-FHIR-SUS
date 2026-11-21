package br.ufs.fhirsus.config.domain;

/** Regra campo a campo (diagrama: RegraCampo). */
public record RegraCampo(
        String caminhoOrigem,
        String caminhoDestino,
        TipoConversao tipoConversao,
        String parametroConversao,
        Boolean obrigatorio,
        String condicaoObrigatoriedade) {
}
