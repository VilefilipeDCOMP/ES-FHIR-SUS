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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        var resposta = enviar(pedido(codigo))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].code").value("not-supported"))
                .andExpect(jsonPath("$.issue[0].diagnostics", containsString("CODIGO_SEM_EQUIVALENCIA")))
                .andExpect(jsonPath("$.issue[0].expression").doesNotExist())
                .andReturn().getResponse();

        var resultado = validador.validar(resposta.getContentAsString(), "OperationOutcome");
        assertTrue(resultado.isSuccessful(), () -> resultado.getMessages().toString());
        verifyNoInteractions(labClient);
    }

    @Test
    void deveManterExameDaDemonstracaoComMapeamentoPadrao() throws Exception {
        var labMock = new LabMockController();
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenAnswer(chamada -> labMock.receberPedidoLegado(chamada.getArgument(0), false, 0).getBody());

        enviar(pedido("EXAME-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry[1].resource.code.coding[0].code").value("EXAME-001"));

        verify(labClient).enviarPedidoLegado(
                argThat(pedido -> "LAB-042".equals(pedido.path("codigoExame").asText())), anyBoolean(), anyLong());
    }

    @Test
    void deveUsarOperationOutcomeNasFalhasDeEntrada() throws Exception {
        enviar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"));

        enviar("""
                {"resourceType":"Bundle","type":"collection"}
                """)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"));

        verifyNoInteractions(labClient);
    }

    @Test
    void deveUsarOperationOutcomeNasFalhasDeComunicacaoComLaboratorio() throws Exception {
        when(labClient.enviarPedidoLegado(any(), anyBoolean(), anyLong()))
                .thenThrow(new LabTimeoutException("Timeout no laboratório"),
                        new LabUnavailableException("Laboratório indisponível"));

        String pedido = pedido("EXAME-001");
        enviar(pedido)
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].code").value("timeout"));

        enviar(pedido)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].code").value("transient"));
    }

    private ResultActions enviar(String pedido) throws Exception {
        return mvc.perform(post("/api/v1/clinical/requests")
                .contentType("application/fhir+json")
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
