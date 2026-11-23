package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.client.LabLegacyClient;
import br.ufs.fhirsus.exception.LabTimeoutException;
import br.ufs.fhirsus.exception.LabUnavailableException;
import br.ufs.fhirsus.mock.LabMockController;
import br.ufs.fhirsus.validacao.ValidadorFhir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ClinicalGatewayIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired ValidadorFhir validador;
    @MockitoBean LabLegacyClient labClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEMOGRAMA", "COLESTEROL", "COLESTEROL-TOTAL", "HDL"})
    void deveRejeitarExameSemEquivalenciaAntesDeChamarLaboratorio(String codigo) throws Exception {
        var resposta = verificarOperationOutcome(pedido(codigo), 422, "not-supported")
                .andExpect(jsonPath("$.issue[0].diagnostics", containsString("CODIGO_SEM_EQUIVALENCIA")))
                .andExpect(jsonPath("$.issue[0].expression").doesNotExist())
                .andReturn().getResponse();

        var resultado = validador.validar(resposta.getContentAsString(), "OperationOutcome");
        assertTrue(resultado.isSuccessful(), () -> resultado.getMessages().toString());
        verifyNoInteractions(labClient);
    }

    @Test
    void deveManterExameDaDemonstracaoComMapeamentoPadrao() throws Exception {
        simularResultadoLaboratorio();

        var resposta = enviar(pedido("EXAME-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.entry[1].resource.code.coding[0].code").value("EXAME-001"))
                .andReturn().getResponse();

        var resultado = validador.validar(resposta.getContentAsString(), "Bundle");
        assertTrue(resultado.isSuccessful(), () -> resultado.getMessages().toString());

        verify(labClient).enviarPedidoLegado(
                argThat(pedido -> "LAB-042".equals(pedido.path("codigoExame").asText())), anyBoolean(), anyLong());
    }

    @Test
    void deveUsarOperationOutcomeNasFalhasDeEntrada() throws Exception {
        verificarOperationOutcome("{}", 400, "structure");

        verificarOperationOutcome("""
                {"resourceType":"Bundle","type":"collection"}
                """, 422, "required")
                .andExpect(jsonPath("$.issue[0].expression[0]").value("Bundle.entry"));

        verifyNoInteractions(labClient);
    }

    @Test
    void deveRejeitarJsonMalformado() throws Exception {
        verificarOperationOutcome("{\"resourceType\":", 400, "structure")
                .andExpect(jsonPath("$.issue[0].expression[0]").value("request.body"));
        verifyNoInteractions(labClient);
    }

    @Test
    void deveRejeitarRecursoSemResourceType() throws Exception {
        ObjectNode pedido = (ObjectNode) mapper.readTree(pedido("EXAME-001"));
        pedido.remove("resourceType");
        verificarOperationOutcome(pedido.toString(), 400, "required")
                .andExpect(jsonPath("$.issue[0].expression[0]").value("resourceType"));
        verifyNoInteractions(labClient);
    }

    @Test
    void deveRejeitarServiceRequestSemIntent() throws Exception {
        ObjectNode pedido = (ObjectNode) mapper.readTree(pedido("EXAME-001"));
        pedido.remove("intent");
        verificarOperationOutcome(pedido.toString(), 400, "structure")
                .andExpect(jsonPath("$.issue[0].diagnostics", containsString("ServiceRequest.intent")))
                .andExpect(jsonPath("$.issue[0].expression[0]", startsWith("ServiceRequest")));
        verifyNoInteractions(labClient);
    }

    @Test
    void deveUsarOperationOutcomeNasFalhasDeComunicacaoComLaboratorio() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabTimeoutException("Timeout no laboratório"),
                        new LabUnavailableException("Laboratório indisponível"));

        String pedido = pedido("EXAME-001");
        verificarOperationOutcome(pedido, 504, "timeout")
                .andExpect(jsonPath("$.issue[0].expression[0]").value("lab.remote"));

        verificarOperationOutcome(pedido, 502, "transient")
                .andExpect(jsonPath("$.issue[0].expression[0]").value("lab.remote"));
    }

    private void simularResultadoLaboratorio() {
        var labMock = new LabMockController();
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenAnswer(chamada -> labMock.receberPedidoLegado(chamada.getArgument(0), false, 0).getBody());
    }

    private ResultActions verificarOperationOutcome(String pedido, int http, String codigo) throws Exception {
        return enviar(pedido)
                .andExpect(status().is(http))
                .andExpect(content().contentTypeCompatibleWith("application/fhir+json"))
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                .andExpect(jsonPath("$.issue[0].code").value(codigo))
                .andExpect(jsonPath("$.issue[0].diagnostics").isNotEmpty());
    }

    private ResultActions enviar(String pedido) throws Exception {
        return mvc.perform(post("/api/v1/clinical/requests")
                .contentType("application/fhir+json")
                .accept("application/fhir+json")
                .content(pedido));
    }

    private String pedido(String codigoExame) throws Exception {
        try (InputStream entrada = getClass().getResourceAsStream("/fixtures/solicitacao-servicerequest-isolado.json")) {
            ObjectNode pedido = (ObjectNode) mapper.readTree(entrada);
            ((ObjectNode) pedido.at("/code/coding/0")).put("code", codigoExame);
            return pedido.toString();
        }
    }
}
