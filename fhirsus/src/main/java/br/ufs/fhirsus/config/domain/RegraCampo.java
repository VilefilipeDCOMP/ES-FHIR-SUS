package br.ufs.fhirsus.config.domain;

public record RegraCampo(
        String caminhoOrigem,
        String caminhoDestino,
        TipoConversao tipoConversao,
        String parametroConversao,
        Boolean obrigatorio,
        String condicaoObrigatoriedade) {
}
