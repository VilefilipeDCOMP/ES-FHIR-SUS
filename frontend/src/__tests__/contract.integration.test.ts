import { afterEach, describe, expect, it, vi } from 'vitest';
import backendConfigPadraoRaw from '../../../fhirsus/src/main/resources/mapeamento-p3-sintetico.json?raw';
import backendConfigValidaRaw from '../../../fhirsus/src/test/resources/fixtures/config-valida.json?raw';
import backendConfigInvalidaSemanticaRaw from '../../../fhirsus/src/test/resources/fixtures/config-invalida-semantica.json?raw';
import backendConfigInvalidaTipoRaw from '../../../fhirsus/src/test/resources/fixtures/config-invalida-tipo-desconhecido.json?raw';
import { FHIR_CATALOG } from '../data/fhirCatalog';
import {
  CONFIG_INICIAL_SINTETICA,
  EXEMPLOS_FHIR_SINTETICOS,
} from '../data/syntheticExamples';
import { httpMappingApi } from '../services/httpMappingApi';
import { SemConfigAtivaError } from '../services/mappingGateway';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';
import { serializarConfigParaContrato } from '../utils/mappingValidation';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('Contrato Frontend <-> Backend (ConfigController e fixtures reais)', () => {
  it('mantém paridade entre CONFIG_INICIAL_SINTETICA e a fixture oficial do backend', () => {
    const backendPadrao = JSON.parse(backendConfigPadraoRaw) as unknown;
    const validacaoBackend = validarConfiguracaoDesconhecida(backendPadrao, FHIR_CATALOG);

    expect(validacaoBackend.ok).toBe(true);
    expect(validacaoBackend.erros).toHaveLength(0);
    expect(serializarConfigParaContrato(CONFIG_INICIAL_SINTETICA)).toEqual(backendPadrao);

    const backendValida = JSON.parse(backendConfigValidaRaw) as unknown;
    expect(validarConfiguracaoDesconhecida(backendValida, FHIR_CATALOG).ok).toBe(true);
  });

  it('rejeita as mesmas configurações inválidas do backend (semântica e tipo desconhecido)', () => {
    const invalidaSemantica = JSON.parse(backendConfigInvalidaSemanticaRaw) as unknown;
    const resSemantica = validarConfiguracaoDesconhecida(invalidaSemantica, FHIR_CATALOG);
    expect(resSemantica.ok).toBe(false);
    expect(resSemantica.erros.length).toBeGreaterThan(0);

    const invalidaTipo = JSON.parse(backendConfigInvalidaTipoRaw) as unknown;
    const resTipo = validarConfiguracaoDesconhecida(invalidaTipo, FHIR_CATALOG);
    expect(resTipo.ok).toBe(false);
    expect(resTipo.erros.some((e) => e.local.endsWith('.tipoConversao'))).toBe(true);
  });

  it('consome os formatos exatos de GET /api/config/ativa (200 e 404 SEM_CONFIG_ATIVA)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          versao: 3,
          conteudo: backendConfigPadraoRaw,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );

    const ativa = await httpMappingApi.buscarConfiguracaoAtiva();
    expect(ativa).toEqual(serializarConfigParaContrato(CONFIG_INICIAL_SINTETICA));

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          erro: 'SEM_CONFIG_ATIVA',
          mensagem: 'Nenhuma configuração ativa',
        }),
        { status: 404, headers: { 'Content-Type': 'application/json' } }
      )
    );

    await expect(httpMappingApi.buscarConfiguracaoAtiva()).rejects.toBeInstanceOf(
      SemConfigAtivaError
    );
  });

  it('aceita POST /api/config (201 Created) e propaga erros 400/500 sem mascarar falha', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          versao: 2,
          ativa: true,
          validada: true,
          criadoEm: '2026-11-28T13:00:00',
          criadoPor: 'sistema-demo',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } }
      )
    );

    const salvo = await httpMappingApi.salvarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(salvo.sucesso).toBe(true);
    expect(salvo.mensagemResumo).toContain('versão 2');

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          erro: 'CONFIG_SINTAXE',
          mensagem: 'Erro de JSON: Unexpected character',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )
    );

    const erroSintaxe = await httpMappingApi.validarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(erroSintaxe.sucesso).toBe(false);
    expect(erroSintaxe.erros[0]!.local).toBe('CONFIG_SINTAXE');

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          erro: 'ERRO_INTERNO',
          mensagem: 'Operação não concluída; nada foi salvo',
        }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    );

    const erroInterno = await httpMappingApi.testarConversao({
      config: CONFIG_INICIAL_SINTETICA,
      recurso: 'ServiceRequest',
      sentido: 'FHIR_PARA_LEGADO',
      exemploJson: EXEMPLOS_FHIR_SINTETICOS.ServiceRequest!,
    });
    expect(erroInterno.sucesso).toBe(false);
    expect(erroInterno.erros[0]!.local).toBe('ERRO_INTERNO');
  });

  it('sinaliza aviso explícito quando /api/config/testar responde 404 e cai no simulador local', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('Not Found', { status: 404 })
    );

    const previa = await httpMappingApi.testarConversao({
      config: CONFIG_INICIAL_SINTETICA,
      recurso: 'ServiceRequest',
      sentido: 'FHIR_PARA_LEGADO',
      exemploJson: EXEMPLOS_FHIR_SINTETICOS.ServiceRequest!,
    });

    expect(previa.sucesso).toBe(true);
    expect(previa.payloadSaida).toEqual({
      numeroPedido: 'PED-001',
      codigoExame: 'LAB-042',
      origem: 'CLINICA_DEMO',
    });
    expect(previa.avisos.some((a) => a.severidade === 'AVISO' && a.motivo.includes('404'))).toBe(
      true
    );
  });
});
