package br.ufs.fhirsus.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    }

    @Test
    @DisplayName("API-01: Rota de saúde /api/v1/health deve responder HTTP 200 e status UP")
    void healthCheckDeveRetornarStatusUp() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.gateway").value("FHIR-SUS+"))
                .andExpect(jsonPath("$.versao").value("1.0.0-P3"))
                .andExpect(jsonPath("$.detalhes.recorte").value("P3-Demonstracao"))
                .andExpect(jsonPath("$.detalhes.padrao").value("HL7 FHIR R4"));
    }
}
