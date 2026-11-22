package br.ufs.fhirsus.validacao;

import ca.uhn.fhir.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidadorFhirTest {

    private final ValidadorFhir validador = new ValidadorFhir();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void aceitaSolicitacaoSinteticaDoMotor() {
        ValidationResult resultado = validador.validar("""
                {
                  "resourceType": "ServiceRequest",
                  "status": "active",
                  "intent": "order",
                  "subject": {"reference": "Patient/PAC-001"},
                  "identifier": [{"value": "PED-001"}],
                  "code": {"coding": [{"code": "EXAME-001"}]}
                }
                """, "ServiceRequest");

        assertTrue(resultado.isSuccessful(), resultado::toString);
    }

    @Test
    void rejeitaSolicitacaoSemCampoObrigatorio() {
        ValidationResult resultado = validador.validar("""
                {
                  "resourceType": "ServiceRequest",
                  "status": "active",
                  "intent": "order"
                }
                """, "ServiceRequest");

        assertFalse(resultado.isSuccessful());
        assertTrue(resultado.getMessages().stream()
                .anyMatch(mensagem -> mensagem.getMessage().contains("ServiceRequest.subject")), resultado::toString);
    }

    @Test
    void aceitaBundleDeEntradaDoContrato() throws IOException {
        ValidationResult resultado = validador.validar(bundleDoContrato().toString(), "Bundle");

        assertTrue(resultado.isSuccessful(), resultado::toString);
    }

    @Test
    void aceitaSolicitacaoDoContratoComoRecursoIsolado() throws IOException {
        ObjectNode solicitacao = (ObjectNode) bundleDoContrato().at("/entry/0/resource");
        ((ObjectNode) solicitacao.path("subject")).put("reference", "Patient/PAC-001");

        ValidationResult resultado = validador.validar(solicitacao.toString(), "ServiceRequest");

        assertTrue(resultado.isSuccessful(), resultado::toString);
    }

    @Test
    void rejeitaBundleComSolicitacaoSemReferenciaAoPaciente() throws IOException {
        ObjectNode bundle = bundleDoContrato();
        ObjectNode solicitacao = (ObjectNode) bundle.at("/entry/0/resource");
        solicitacao.remove("subject");

        ValidationResult resultado = validador.validar(bundle.toString(), "Bundle");

        assertFalse(resultado.isSuccessful());
        assertTrue(resultado.getMessages().stream()
                .anyMatch(mensagem -> mensagem.getMessage().contains("ServiceRequest.subject")), resultado::toString);
    }

    @Test
    void aceitaObservacaoSinteticaDoMotor() {
        ValidationResult resultado = validador.validar("""
                {
                  "resourceType": "Observation",
                  "code": {"coding": [{"system": "https://clinica.example/codigos/exames", "code": "EXAME-001"}]},
                  "status": "final",
                  "valueQuantity": {"value": 42.5}
                }
                """, "Observation");

        assertTrue(resultado.isSuccessful(), resultado::toString);
    }

    @Test
    void rejeitaTipoDiferenteDoEsperado() {
        ValidationResult resultado = validador.validar("""
                {"resourceType": "Patient"}
                """, "ServiceRequest");

        assertFalse(resultado.isSuccessful());
        assertTrue(resultado.getMessages().stream()
                .anyMatch(mensagem -> "resourceType".equals(mensagem.getLocationString())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "{", "[]", "null", "{\"resourceType\":\"ServiceRequest\"} {}"})
    void rejeitaCorpoQueNaoRepresentaUmRecurso(String json) {
        assertFalse(validador.validar(json, "ServiceRequest").isSuccessful());
    }

    private ObjectNode bundleDoContrato() throws IOException {
        try (var entrada = getClass().getResourceAsStream("/fixtures/bundle-entrada-ct01.json")) {
            return (ObjectNode) jsonMapper.readTree(entrada);
        }
    }
}
