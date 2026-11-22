package br.ufs.fhirsus.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ConfigControllerTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired ConfigVersaoRepository configRepo;
    @Autowired AuditoriaMapeamentoRepository auditoriaRepo;

    @BeforeEach
    void limpar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        auditoriaRepo.deleteAll();
        configRepo.deleteAll();
    }

    @Test
    void semConfigAtivaRetorna404() throws Exception {
        mvc.perform(get("/api/config/ativa"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("SEM_CONFIG_ATIVA"));
    }

    @Test
    void salvaJsonERetorna201() throws Exception {
        mvc.perform(post("/api/config").contentType("application/json").content(Fixtures.ler("config-valida.json")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/config", "/api/config/validar"})
    void corpoAusenteRetorna400(String rota) throws Exception {
        mvc.perform(post(rota).contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sintaxeInvalidaRetorna400() throws Exception {
        mvc.perform(post("/api/config").contentType("application/json").content(Fixtures.ler("config-invalida-sintaxe.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("CONFIG_SINTAXE"));
    }

    @Test
    void semanticaInvalidaRetorna422ComDetalhes() throws Exception {
        mvc.perform(post("/api/config").contentType("application/json").content(Fixtures.ler("config-invalida-semantica.json")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("CONFIG_INVALIDA"))
                .andExpect(jsonPath("$.detalhes.length()").value(6));
    }

    @Test
    void auditoriaListaOsRegistros() throws Exception {
        mvc.perform(post("/api/config").contentType("application/json").content(Fixtures.ler("config-valida.json")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].responsavel").value("sistema-demo"))
                .andExpect(jsonPath("$[0].versaoAtual").value(1))
                .andExpect(jsonPath("$[0].versaoAnterior").isEmpty());
    }
}
