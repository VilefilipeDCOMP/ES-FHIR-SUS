package br.ufs.fhirsus.validator;

import br.ufs.fhirsus.exception.FhirValidationException;
import br.ufs.fhirsus.exception.InvalidPayloadException;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import ca.uhn.fhir.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Filtro 1 do Pipes and Filters
 * Validação estrutural e semântica de entrada da requisição clínica (Bundle / ServiceRequest).
 * Integração oficial com HAPI FHIR Validator (Filipe Ciríaco).
 */
@Component
public class ServiceRequestValidator {

    private final ValidadorFhir validadorFhir;

    public ServiceRequestValidator() {
        this.validadorFhir = null;
    }

    @Autowired
    public ServiceRequestValidator(@Autowired(required = false) ValidadorFhir validadorFhir) {
        this.validadorFhir = validadorFhir;
    }

    public record ValidClinicalInput(
            String numeroPedido,
            String codigoExame,
            String pacienteCodigo,
            JsonNode serviceRequestNode,
            JsonNode patientNode
    ) {}

    public ValidClinicalInput validar(JsonNode root) {
        if (root == null || root.isNull() || root.isEmpty()) {
            throw new InvalidPayloadException("Payload JSON de entrada está vazio ou malformado");
        }

        String resourceType = root.path("resourceType").asText(null);
        if (resourceType == null || resourceType.isBlank()) {
            throw new FhirValidationException("Campo 'resourceType' obrigatório ausente no payload", "resourceType", "required");
        }

        // Validação semântica e estrutural via HAPI FHIR R4 (quando disponível)
        if (validadorFhir != null) {
            ValidationResult resultadoFhir = validadorFhir.validar(root.toString(), resourceType);
            if (!resultadoFhir.isSuccessful() && resultadoFhir.getMessages() != null && !resultadoFhir.getMessages().isEmpty()) {
                var primeiroErro = resultadoFhir.getMessages().stream()
                        .filter(m -> m.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR)
                        .findFirst()
                        .orElse(resultadoFhir.getMessages().get(0));
                throw new FhirValidationException(
                        primeiroErro.getMessage(),
                        primeiroErro.getLocationString() != null ? primeiroErro.getLocationString() : resourceType,
                        "structure"
                );
            }
        }

        JsonNode serviceRequestNode = null;
        JsonNode patientNode = null;

        if ("Bundle".equalsIgnoreCase(resourceType)) {
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                throw new FhirValidationException("Bundle sem entradas clínicas ('entry')", "Bundle.entry", "required");
            }

            Map<String, JsonNode> resourcesByFullUrl = new HashMap<>();
            java.util.List<JsonNode> patientResources = new java.util.ArrayList<>();
            java.util.List<String> patientFullUrls = new java.util.ArrayList<>();

            for (JsonNode entry : entries) {
                String fullUrl = entry.path("fullUrl").asText("");
                JsonNode resource = entry.path("resource");
                if (!fullUrl.isBlank() && !resource.isMissingNode()) {
                    resourcesByFullUrl.put(fullUrl, resource);
                }
                String innerType = resource.path("resourceType").asText("");
                if ("ServiceRequest".equalsIgnoreCase(innerType)) {
                    serviceRequestNode = resource;
                } else if ("Patient".equalsIgnoreCase(innerType)) {
                    patientResources.add(resource);
                    patientFullUrls.add(fullUrl);
                }
            }

            if (serviceRequestNode == null) {
                throw new FhirValidationException("Nenhum recurso ServiceRequest encontrado nas entradas do Bundle", "Bundle.entry.resource", "structure");
            }

            String subjectRef = serviceRequestNode.path("subject").path("reference").asText(null);
            if (subjectRef != null && !subjectRef.isBlank()) {
                // 1. Busca por correspondência exata em fullUrl
                if (resourcesByFullUrl.containsKey(subjectRef)) {
                    JsonNode res = resourcesByFullUrl.get(subjectRef);
                    if ("Patient".equalsIgnoreCase(res.path("resourceType").asText())) {
                        patientNode = res;
                    }
                }

                // 2. Se não encontrou por fullUrl exato, resolve referências relativas (ex: Patient/p1) ou por id
                if (patientNode == null) {
                    String targetId = subjectRef.startsWith("Patient/")
                            ? subjectRef.substring("Patient/".length())
                            : subjectRef;

                    for (int i = 0; i < patientResources.size(); i++) {
                        JsonNode pat = patientResources.get(i);
                        String fUrl = patientFullUrls.get(i);

                        // Correspondência por id do recurso Patient
                        String patId = pat.path("id").asText("");
                        if (!patId.isBlank() && (patId.equals(targetId) || patId.equals(subjectRef))) {
                            patientNode = pat;
                            break;
                        }

                        // Correspondência se fullUrl termina com o subjectRef ou targetId
                        if (!fUrl.isBlank() && (fUrl.endsWith("/" + subjectRef) || fUrl.endsWith("/" + targetId) || fUrl.endsWith(subjectRef))) {
                            patientNode = pat;
                            break;
                        }

                        // Correspondência por identifier
                        JsonNode identifiers = pat.path("identifier");
                        if (identifiers.isArray()) {
                            boolean matchFound = false;
                            for (JsonNode ident : identifiers) {
                                String val = ident.path("value").asText("");
                                if (!val.isBlank() && (val.equals(targetId) || val.equals(subjectRef))) {
                                    patientNode = pat;
                                    matchFound = true;
                                    break;
                                }
                            }
                            if (matchFound) {
                                break;
                            }
                        }
                    }
                }
            } else if (patientResources.size() == 1) {
                // Se o ServiceRequest não informa subject.reference mas existe exatamente um Patient no Bundle
                patientNode = patientResources.get(0);
            }
            // NOTA: Se subjectRef foi informado e nenhum Patient correspondeu no Bundle,
            // patientNode permanece null para evitar vincular a um paciente arbitrário.

        } else if ("ServiceRequest".equalsIgnoreCase(resourceType)) {
            serviceRequestNode = root;
        } else {
            throw new FhirValidationException("Recurso '" + resourceType + "' não suportado como entrada clínica. Esperado 'Bundle' ou 'ServiceRequest'", "resourceType", "invalid");
        }

        // Validação dos campos obrigatórios do ServiceRequest
        String numeroPedido = extrairNumeroPedido(serviceRequestNode);
        if (numeroPedido == null || numeroPedido.isBlank()) {
            throw new FhirValidationException("Identificador do pedido (identifier/id) ausente no ServiceRequest", "ServiceRequest.identifier", "required");
        }

        String codigoExame = extrairCodigoExame(serviceRequestNode);
        if (codigoExame == null || codigoExame.isBlank()) {
            throw new FhirValidationException("Código do exame (code.coding.code) ausente no ServiceRequest", "ServiceRequest.code", "required");
        }

        String subjectRefSolicitado = serviceRequestNode.path("subject").path("reference").asText(null);
        String pacienteCodigo = extrairCodigoPaciente(patientNode, subjectRefSolicitado);

        return new ValidClinicalInput(numeroPedido, codigoExame, pacienteCodigo, serviceRequestNode, patientNode);
    }

    private String extrairNumeroPedido(JsonNode sr) {
        if (sr.has("identifier") && sr.get("identifier").isArray() && !sr.get("identifier").isEmpty()) {
            return sr.get("identifier").get(0).path("value").asText(null);
        }
        return sr.path("id").asText(null);
    }

    private String extrairCodigoExame(JsonNode sr) {
        JsonNode coding = sr.path("code").path("coding");
        if (coding.isArray() && !coding.isEmpty()) {
            return coding.get(0).path("code").asText(null);
        }
        return sr.path("code").path("text").asText(null);
    }

    private String extrairCodigoPaciente(JsonNode pat, String subjectRefSolicitado) {
        if (pat != null && !pat.isMissingNode()) {
            if (pat.has("identifier") && pat.get("identifier").isArray() && !pat.get("identifier").isEmpty()) {
                String val = pat.get("identifier").get(0).path("value").asText(null);
                if (val != null && !val.isBlank()) {
                    return val;
                }
            }
            String id = pat.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        // Se não encontrou recurso Patient no Bundle mas o ServiceRequest tinha subject explícito
        if (subjectRefSolicitado != null && !subjectRefSolicitado.isBlank()) {
            if (subjectRefSolicitado.startsWith("Patient/")) {
                return subjectRefSolicitado.substring("Patient/".length());
            }
            if (!subjectRefSolicitado.startsWith("urn:") && !subjectRefSolicitado.startsWith("http")) {
                return subjectRefSolicitado;
            }
        }
        return null;
    }
}
