package br.ufs.fhirsus.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

import br.ufs.fhirsus.config.domain.*;
import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import br.ufs.fhirsus.config.service.AuditoriaService;
import br.ufs.fhirsus.config.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class ConfigServiceTest {

    @Autowired ConfigService service;
    @Autowired ConfigVersaoRepository configRepo;
    @Autowired AuditoriaMapeamentoRepository auditoriaRepo;
    @MockitoSpyBean AuditoriaService auditoriaService;

    @BeforeEach
    void limpar() {
        org.mockito.Mockito.reset(auditoriaService);
        auditoriaRepo.deleteAll();
        configRepo.deleteAll();
    }

    @Test
    void salvaComoAtivaEAudita() {
        var v = service.salvar(Fixtures.ler("config-valida.json"));
        assertEquals(1, v.getVersao());
        assertTrue(configRepo.findByAtivaTrue().isPresent());
        var auditorias = auditoriaRepo.findAll();
        assertEquals(1, auditorias.size());
        assertEquals("sistema-demo", auditorias.get(0).getResponsavel());
        assertEquals(1, auditorias.get(0).getVersaoAtual());
        assertNull(auditorias.get(0).getVersaoAnterior());
        assertTrue(v.isValidada());
        assertEquals("cfg-demo-001", v.getConfigId());
    }

    @Test
    void novaVersaoSubstituiAAtivaEMantemHistorico() {
        service.salvar(Fixtures.ler("config-valida.json"));
        var v2 = service.salvar(Fixtures.ler("config-valida.json"));
        assertEquals(2, v2.getVersao());
        assertEquals(2, configRepo.count());
        assertEquals(2, configRepo.findByAtivaTrue().orElseThrow().getVersao());
        assertEquals(2, auditoriaRepo.count());
        var ultima = auditoriaRepo.findAllByOrderByIdDesc().get(0);
        assertEquals(2, ultima.getVersaoAtual());
        assertEquals(1, ultima.getVersaoAnterior());
    }

    @Test
    void configInvalidaNuncaSubstituiAAtiva() {
        service.salvar(Fixtures.ler("config-valida.json"));
        assertThrows(ConfigInvalidaException.class,
                () -> service.salvar(Fixtures.ler("config-invalida-semantica.json")));
        assertThrows(ConfigSintaxeException.class,
                () -> service.salvar(Fixtures.ler("config-invalida-sintaxe.json")));
        assertEquals(1, configRepo.count());
        assertEquals(1, configRepo.findByAtivaTrue().orElseThrow().getVersao());
        assertEquals(1, auditoriaRepo.count());
    }

    @Test
    void falhaNaAuditoriaFazRollbackETudo() {
        service.salvar(Fixtures.ler("config-valida.json"));

        doThrow(new IllegalStateException("falha simulada na auditoria"))
                .when(auditoriaService).registrar(anyInt(), any());

        assertThrows(IllegalStateException.class,
                () -> service.salvar(Fixtures.ler("config-valida.json")));

        assertEquals(1, configRepo.count(), "nova versão não pode ter sido salva");
        assertEquals(1, configRepo.findByAtivaTrue().orElseThrow().getVersao(), "a anterior continua ativa");
        assertEquals(1, auditoriaRepo.count());
    }
}
