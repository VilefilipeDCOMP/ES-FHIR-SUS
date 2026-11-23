package br.ufs.fhirsus.exception;

public class FhirStructuralValidationException extends FhirValidationException {
    public FhirStructuralValidationException(String mensagem, String campo, String codigoErro) {
        super(mensagem, campo, codigoErro);
    }
}
