package br.ufs.fhirsus.exception;

/**
 * Exceção lançada quando o payload JSON for malformado, sintaticamente quebrado ou vazio (HTTP 400).
 */
public class InvalidPayloadException extends RuntimeException {
    public InvalidPayloadException(String message) {
        super(message);
    }
}
