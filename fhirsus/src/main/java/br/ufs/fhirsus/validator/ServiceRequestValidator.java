package br.ufs.fhirsus.validator;

import br.ufs.fhirsus.exception.FhirValidationException;
import br.ufs.fhirsus.exception.InvalidPayloadException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Filtro 1 do Pipes and Filters
 * Validação estrutural e semântica de entrada da requisição clínica (Bundle / ServiceRequest).
 */
@Component
public class ServiceRequestValidator {

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

        JsonNode serviceRequestNode = null;
        JsonNode patientNode = null;

        if ("Bundle".equalsIgnoreCase(resourceType)) {
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                throw new FhirValidationException("Bundle sem entradas clínicas ('entry')", "Bundle.entry", "required");
            }

            Map<String, JsonNode> resourcesByFullUrl = new HashMap<>();
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
                    patientNode = resource;
                }
            }

            if (serviceRequestNode == null) {
                throw new FhirValidationException("Nenhum recurso ServiceRequest encontrado nas entradas do Bundle", "Bundle.entry.resource", "structure");
            }

            String subjectRef = serviceRequestNode.path("subject").path("reference").asText("");
            if (!subjectRef.isBlank() && resourcesByFullUrl.containsKey(subjectRef)) {
                patientNode = resourcesByFullUrl.get(subjectRef);
            }

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

        String pacienteCodigo = extrairCodigoPaciente(patientNode);

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

    private String extrairCodigoPaciente(JsonNode pat) {
        if (pat == null || pat.isMissingNode()) return "PAC-001";
        if (pat.has("identifier") && pat.get("identifier").isArray() && !pat.get("identifier").isEmpty()) {
            return pat.get("identifier").get(0).path("value").asText("PAC-001");
        }
        return pat.path("id").asText("PAC-001");
    }
}
