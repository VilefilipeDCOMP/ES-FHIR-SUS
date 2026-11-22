package br.ufs.fhirsus.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

import org.springframework.test.util.AopTestUtils;
import br.ufs.fhirsus.config.domain.*;
import br.ufs.fhirsus.config.repository.AuditoriaMapeamentoRepository;
import br.ufs.fhirsus.config.repository.ConfigVersaoRepository;
import br.ufs.fhirsus.config.service.AuditoriaService;
import br.ufs.fhirsus.config.service.ConfigService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void salvamentosSimultaneosMantemUmaAtivaEAuditoria(boolean temVersaoAtiva) throws Exception {
        String conteudo = Fixtures.ler("config-valida.json");
        int primeiraVersao = 1;
        if (temVersaoAtiva) {
            primeiraVersao = service.salvar(conteudo).getVersao() + 1;
        }
        int ultimaVersao = primeiraVersao + 1;

        var inicio = new CyclicBarrier(2);
        Callable<Integer> salvar = () -> {
            inicio.await(10, TimeUnit.SECONDS);
            return service.salvar(conteudo).getVersao();
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var resultados = executor.invokeAll(List.of(salvar, salvar), 20, TimeUnit.SECONDS);
            assertEquals(Set.of(primeiraVersao, ultimaVersao),
                    Set.of(resultados.get(0).get(), resultados.get(1).get()));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(ultimaVersao, configRepo.count());
        assertEquals(ultimaVersao, configRepo.findByAtivaTrue().orElseThrow().getVersao());
        var auditorias = auditoriaRepo.findAllByOrderByIdDesc();
        assertEquals(ultimaVersao, auditorias.size());
        assertEquals(ultimaVersao, auditorias.get(0).getVersaoAtual());
        assertEquals(primeiraVersao, auditorias.get(0).getVersaoAnterior());
        assertEquals(primeiraVersao, auditorias.get(1).getVersaoAtual());
        assertEquals(temVersaoAtiva ? 1 : null, auditorias.get(1).getVersaoAnterior());
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

        var alvo = AopTestUtils.getUltimateTargetObject(auditoriaService);
        doThrow(new IllegalStateException("falha simulada na auditoria"))
                .when((AuditoriaService) alvo).registrar(anyInt(), any());

        assertThrows(IllegalStateException.class,
                () -> service.salvar(Fixtures.ler("config-valida.json")));

        assertEquals(1, configRepo.count(), "nova versão não pode ter sido salva");
        assertEquals(1, configRepo.findByAtivaTrue().orElseThrow().getVersao(), "a anterior continua ativa");
        assertEquals(1, auditoriaRepo.count());
    }
}
