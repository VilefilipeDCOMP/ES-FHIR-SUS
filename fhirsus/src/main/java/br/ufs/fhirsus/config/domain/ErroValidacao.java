package br.ufs.fhirsus.config.domain;

/** Erro de validação semântica: caminho do campo + mensagem. */
public record ErroValidacao(String caminho, String mensagem) {
}
