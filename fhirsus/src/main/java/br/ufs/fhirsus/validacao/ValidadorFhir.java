package br.ufs.fhirsus.validacao;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class ValidadorFhir {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final FhirContext contexto = FhirContext.forR4Cached();
    private final FhirValidator validador;

    public ValidadorFhir() {
        ValidationSupportChain suporte = new ValidationSupportChain(
                new DefaultProfileValidationSupport(contexto),
                new InMemoryTerminologyServerValidationSupport(contexto),
                new CommonCodeSystemsTerminologyService(contexto));
        validador = contexto.newValidator();
        validador.registerValidatorModule(new FhirInstanceValidator(suporte));
    }

    public ValidationResult validar(String json, String tipoEsperado) {
        if (json == null || json.isBlank()) {
            return erro(null, "Informe um recurso FHIR em JSON.");
        }

        JsonNode recurso;
        try {
            recurso = jsonMapper.readTree(json);
        } catch (JacksonException e) {
            return erro(null, "O JSON do recurso FHIR é inválido.");
        }
        if (!recurso.isObject()) {
            return erro(null, "O recurso FHIR deve ser um objeto JSON.");
        }
        if (!tipoEsperado.equals(recurso.path("resourceType").asText())) {
            return erro("resourceType", "O tipo de recurso esperado é " + tipoEsperado + ".");
        }

        try {
            return validador.validateWithResult(json);
        } catch (DataFormatException e) {
            return erro(null, "O JSON não pôde ser interpretado como recurso FHIR R4.");
        }
    }

    private ValidationResult erro(String caminho, String descricao) {
        SingleValidationMessage mensagem = new SingleValidationMessage();
        mensagem.setSeverity(ResultSeverityEnum.ERROR);
        mensagem.setLocationString(caminho);
        mensagem.setMessage(descricao);
        return new ValidationResult(contexto, List.of(mensagem));
    }
}
