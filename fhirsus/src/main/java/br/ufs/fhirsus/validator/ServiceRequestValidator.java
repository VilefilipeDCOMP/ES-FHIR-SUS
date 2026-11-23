package br.ufs.fhirsus.validator;

import br.ufs.fhirsus.exception.FhirValidationException;
import br.ufs.fhirsus.exception.InvalidPayloadException;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import ca.uhn.fhir.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;


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
            JsonNode subject,
            JsonNode serviceRequestNode,
            String serviceRequestFullUrl,
            JsonNode patientNode,
            String patientFullUrl
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
        JsonNode serviceRequestEntry = null;
        JsonNode patientEntry = null;

        if ("Bundle".equalsIgnoreCase(resourceType)) {
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                throw new FhirValidationException("Bundle sem entradas clínicas ('entry')", "Bundle.entry", "required");
            }

            for (JsonNode entry : entries) {
                JsonNode resource = entry.path("resource");
                if ("ServiceRequest".equals(resource.path("resourceType").asText())) {
                    if (serviceRequestEntry != null) {
                        throw new FhirValidationException("O Bundle deve conter apenas um ServiceRequest", "Bundle.entry.resource", "structure");
                    }
                    serviceRequestNode = resource;
                    serviceRequestEntry = entry;
                }
            }

            if (serviceRequestEntry == null) {
                throw new FhirValidationException("Nenhum recurso ServiceRequest encontrado nas entradas do Bundle", "Bundle.entry.resource", "structure");
            }

            patientEntry = resolverPaciente(entries, serviceRequestEntry);
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

        JsonNode subject = serviceRequestNode.get("subject");
        JsonNode patientNode = patientEntry == null ? null : patientEntry.path("resource");
        String patientFullUrl = patientEntry == null ? null : patientEntry.path("fullUrl").asText(null);
        String serviceRequestFullUrl = serviceRequestEntry == null ? "" : serviceRequestEntry.path("fullUrl").asText("");

        // Resolução de referências contidas (ex: contained Patient com id "p1" e subject.reference "#p1")
        if (patientNode == null && subject != null && subject.isObject()) {
            String ref = subject.path("reference").asText("");
            if (ref.startsWith("#")) {
                String targetId = ref.substring(1);
                JsonNode contained = serviceRequestNode.path("contained");
                if (contained.isArray()) {
                    for (JsonNode rec : contained) {
                        if ("Patient".equals(rec.path("resourceType").asText()) && targetId.equals(rec.path("id").asText(""))) {
                            patientNode = rec;
                            patientFullUrl = "urn:uuid:" + java.util.UUID.randomUUID();
                            subject = ((tools.jackson.databind.node.ObjectNode) subject.deepCopy()).put("reference", patientFullUrl);
                            break;
                        }
                    }
                }
            }
        }

        return new ValidClinicalInput(numeroPedido, codigoExame, subject, serviceRequestNode, serviceRequestFullUrl, patientNode, patientFullUrl);
    }

    private JsonNode resolverPaciente(JsonNode entries, JsonNode serviceRequestEntry) {
        String referencia = serviceRequestEntry.at("/resource/subject/reference").asText("");
        if (referencia.isBlank()) {
            return null;
        }

        String urlPedido = serviceRequestEntry.path("fullUrl").asText("");
        int inicioRecurso = urlPedido.lastIndexOf("/ServiceRequest/");
        boolean relativa = referencia.matches("Patient/[A-Za-z0-9.-]{1,64}");
        String urlAlvo = relativa && inicioRecurso >= 0
                ? urlPedido.substring(0, inicioRecurso + 1) + referencia
                : referencia;

        JsonNode correspondencia = null;
        for (JsonNode entry : entries) {
            JsonNode recurso = entry.path("resource");
            if (!"Patient".equals(recurso.path("resourceType").asText())) {
                continue;
            }
            String fullUrl = entry.path("fullUrl").asText("");
            String id = recurso.path("id").asText("");
            boolean mesmoId = relativa && (inicioRecurso < 0 || fullUrl.isBlank())
                    && referencia.equals("Patient/" + id);
            if (urlAlvo.equals(fullUrl) || mesmoId) {
                if (correspondencia != null) {
                    throw new FhirValidationException("Referência de paciente ambígua no Bundle", "ServiceRequest.subject", "invalid");
                }
                correspondencia = entry;
            }
        }
        return correspondencia;
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
}
