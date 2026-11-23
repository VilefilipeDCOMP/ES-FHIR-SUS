package br.ufs.fhirsus.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LabMockControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LabMockController()).build();
    }

    @Test
    @DisplayName("API-03: Mock do Laboratório deve retornar laudo concluído com sucesso")
    void deveRetornarLaudoConcluidoComSucesso() throws Exception {
        String pedidoJson = """
                {
                  "codigo_pedido": "PED-001",
                  "codigo_exame": "GLICOSE-01"
                }
                """;

        mockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_execucao").value("CONCLUIDO"))
                .andExpect(jsonPath("$.codigo_pedido").value("PED-001"))
                .andExpect(jsonPath("$.laudo_itens[0].codigo_analise").value("GLICOSE-01"))
                .andExpect(jsonPath("$.laudo_itens[0].status_analise").value("FINAL"));
    }

    @Test
    @DisplayName("API-03: Mock do Laboratório deve retornar laudo com os valores reais enviados (sem hardcode)")
    void deveRetornarValoresReaisSemHardcode() throws Exception {
        String pedidoCustomizado = """
                {
                  "codigo_pedido": "PED-777",
                  "codigo_exame": "HEMOGRAMA-02"
                }
                """;

        mockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoCustomizado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status_execucao").value("CONCLUIDO"))
                .andExpect(jsonPath("$.codigo_pedido").value("PED-777"))
                .andExpect(jsonPath("$.laudo_itens[0].codigo_analise").value("HEMOGRAMA-02"));
    }

    @Test
    @DisplayName("API-03: Mock deve rejeitar pedido sem 'codigo_pedido' com HTTP 400 (sem inventar fallback)")
    void deveRejeitarPedidoSemCodigoPedidoComHttp400() throws Exception {
        String pedidoSemId = """
                {
                  "codigo_exame": "GLICOSE-01"
                }
                """;

        mockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoSemId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Campo obrigatorio 'codigo_pedido' ausente no pedido legado"));
    }

    @Test
    @DisplayName("API-03: Mock deve rejeitar pedido sem 'codigo_exame' com HTTP 400 (sem inventar fallback)")
    void deveRejeitarPedidoSemCodigoExameComHttp400() throws Exception {
        String pedidoSemExame = """
                {
                  "codigo_pedido": "PED-888"
                }
                """;

        mockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pedidoSemExame))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Campo obrigatorio 'codigo_exame' ausente no pedido legado"));
    }

    @Test
    @DisplayName("API-03: Mock do Laboratório deve retornar HTTP 500 quando solicitado via header X-Mock-Fail")
    void deveRetornarErro500QuandoSolicitadoPorHeader() throws Exception {
        mockMvc.perform(post("/mock/lab/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Mock-Fail", "true")
                .content("{\"codigo_pedido\":\"PED-001\"}"))
                .andExpect(status().isInternalServerError());
    }
}
