package br.ufs.fhirsus.exception;

/**
 * Lançada quando o laboratório remoto está indisponível, inacessível ou retorna erro 5xx (HTTP 502).
 * Referências: RF016, CT-04.
 */
public class LabUnavailableException extends RuntimeException {
    public LabUnavailableException(String message) {
        super(message);
    }

    public LabUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
