package br.ufs.fhirsus.exception;

/**
 * Lançada quando o laboratório remoto excede o tempo limite de resposta configurado (HTTP 504).
 * Referências: RF016, CT-04.
 */
public class LabTimeoutException extends RuntimeException {
    public LabTimeoutException(String message) {
        super(message);
    }

    public LabTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
