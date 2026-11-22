import { afterEach, describe, expect, it, vi } from 'vitest';
import { FHIR_CATALOG } from '../data/fhirCatalog';
import {
  CONFIG_INICIAL_SINTETICA,
  EXEMPLO_LEGADO_SINTETICO,
  EXEMPLOS_FHIR_SINTETICOS,
} from '../data/syntheticExamples';
import { httpMappingApi } from '../services/httpMappingApi';
import { criarMockMappingApi } from '../services/mockMappingApi';
import type { MapeamentoConfig } from '../types/domain';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';
import {
  importarArquivoConfig,
  MAX_JSON_BYTES,
  validarEProcessarJsonTexto,
} from '../utils/jsonIngest';
import {
  avaliarPendenciasRecurso,
  criarTabelaInversa,
  detectarChavesDuplicadas,
} from '../utils/mappingValidation';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('1. Validação estrutural em tempo de execução (Zod — RF011/RF012/RF013)', () => {
  it('rejeita todos os 5 payloads malformados sem lançar exceção', () => {
    const casos: Array<{ nome: string; payload: unknown }> = [
      {
        nome: 'recurso inexistente ("Invented")',
        payload: {
          mapeamentos: [
            {
              recurso: 'Invented',
              sentidos: [{ tipo: 'LEGADO_PARA_FHIR', regras: [] }],
            },
          ],
          tabelas: [],
        },
      },
      {
        nome: 'dois sentidos com o mesmo tipo',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              sentidos: [
                {
                  tipo: 'LEGADO_PARA_FHIR',
                  regras: [
                    {
                      caminhoOrigem: 'p.id',
                      caminhoDestino: 'Patient.identifier.value',
                      regra: 'COPIAR',
                      obrigatorio: true,
                    },
                  ],
                },
                {
                  tipo: 'LEGADO_PARA_FHIR',
                  regras: [
                    {
                      caminhoOrigem: 'p.nome',
                      caminhoDestino: 'Patient.name.text',
                      regra: 'COPIAR',
                      obrigatorio: false,
                    },
                  ],
                },
              ],
            },
          ],
          tabelas: [],
        },
      },
      {
        nome: 'regras: null',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              sentidos: [{ tipo: 'LEGADO_PARA_FHIR', regras: null }],
            },
          ],
          tabelas: [],
        },
      },
      {
        nome: 'origem vazia e destino FHIR fora do catálogo',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              sentidos: [
                {
                  tipo: 'LEGADO_PARA_FHIR',
                  regras: [
                    {
                      caminhoOrigem: '',
                      caminhoDestino: 'Patient.campoInexistente',
                      regra: 'COPIAR',
                      obrigatorio: false,
                    },
                  ],
                },
              ],
            },
          ],
          tabelas: [],
        },
      },
      {
        nome: 'item null em mapeamentos',
        payload: { mapeamentos: [null], tabelas: [] },
      },
    ];

    for (const caso of casos) {
      const res = validarConfiguracaoDesconhecida(caso.payload, FHIR_CATALOG);
      expect(res.ok, `Deveria rejeitar: ${caso.nome}`).toBe(false);
      expect(res.erros.length).toBeGreaterThan(0);
      expect(res.erros[0]!.local).toBeTruthy();
      expect(res.erros[0]!.motivo).toBeTruthy();
    }
  });
});

describe('2. Obrigatoriedades FHIR R4 (folhas 1..1 vs grupos complexos vs regras do projeto)', () => {
  it('Observation.code é satisfeito por Observation.code.text sem exigir coding.code', () => {
    const cfg: MapeamentoConfig = {
      mapeamentos: [
        {
          recurso: 'Observation',
          sentidos: [
            {
              tipo: 'LEGADO_PARA_FHIR',
              regras: [
                {
                  caminhoOrigem: 'laudo.situacao',
                  caminhoDestino: 'Observation.status',
                  regra: 'COPIAR',
                  obrigatorio: true,
                },
                {
                  caminhoOrigem: 'laudo.codigo_painel',
                  caminhoDestino: 'Observation.code.text',
                  regra: 'COPIAR',
                  obrigatorio: true,
                },
              ],
            },
          ],
        },
      ],
      tabelas: [],
    };

    const resumo = avaliarPendenciasRecurso(
      cfg,
      'Observation',
      'LEGADO_PARA_FHIR',
      FHIR_CATALOG.Observation
    );
    expect(resumo.pendentes).toHaveLength(0);
    expect(resumo.mapeados).toBe(resumo.total);
  });
});

describe('3. Mock fail-closed (sem falso sucesso e persistência segura)', () => {
  it('rejeita conversão de Observation quando não há regras configuradas para o recurso', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);
    const res = await mock.testarConversao({
      config: CONFIG_INICIAL_SINTETICA, // só contém ServiceRequest
      recurso: 'Observation',
      sentido: 'LEGADO_PARA_FHIR',
      exemploJson: EXEMPLO_LEGADO_SINTETICO,
    });

    expect(res.sucesso).toBe(false);
    expect(res.payloadSaida).toBeUndefined();
    expect(res.erros[0]!.local).toContain('Observation');
  });

  it('preserva configuração ativa se localStorage.setItem falhar ao salvar', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    const novaCfg: MapeamentoConfig = {
      mapeamentos: [
        {
          recurso: 'Patient',
          sentidos: [
            {
              tipo: 'LEGADO_PARA_FHIR',
              regras: [
                {
                  caminhoOrigem: 'paciente.id_sintetico',
                  caminhoDestino: 'Patient.identifier.value',
                  regra: 'COPIAR',
                  obrigatorio: true,
                },
              ],
            },
          ],
        },
      ],
      tabelas: [],
    };

    const res = await mock.salvarConfiguracao(novaCfg);
    expect(res.sucesso).toBe(false);

    const ativaAtual = await mock.buscarConfiguracaoAtiva();
    expect(ativaAtual.mapeamentos[0]!.recurso).toBe('ServiceRequest');
  });

  it('converte FHIR_PARA_LEGADO corretamente quando configurado', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);
    const cfgInversa: MapeamentoConfig = {
      mapeamentos: [
        {
          recurso: 'Patient',
          sentidos: [
            {
              tipo: 'FHIR_PARA_LEGADO',
              regras: [
                {
                  caminhoOrigem: 'Patient.identifier.value',
                  caminhoDestino: 'paciente.id_sintetico',
                  regra: 'COPIAR',
                  obrigatorio: true,
                },
              ],
            },
          ],
        },
      ],
      tabelas: [],
    };

    const payloadFhir = EXEMPLOS_FHIR_SINTETICOS.Patient!;
    const res = await mock.testarConversao({
      config: cfgInversa,
      recurso: 'Patient',
      sentido: 'FHIR_PARA_LEGADO',
      exemploJson: payloadFhir,
    });

    expect(res.sucesso).toBe(true);
    expect(res.payloadSaida).toBeDefined();
    const saida = res.payloadSaida as { paciente?: { id_sintetico?: string } };
    expect(saida?.paciente?.id_sintetico).toBe('DEMO-0001');
  });
});

describe('4. Adapter HTTP fail-closed (RF012)', () => {
  it('rejeita resposta 200 com JSON vazio sem falso sucesso', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('', { status: 200, headers: { 'Content-Type': 'application/json' } })
    );

    const res = await httpMappingApi.validarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.motivo).toContain('Esperado JSON explícito');
  });

  it('rejeita salvamento com status 204 se flag ALLOW_HTTP_204_SAVE for falsa', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(null, { status: 204 })
    );

    const res = await httpMappingApi.salvarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.local).toContain('PUT /config');
  });
});

describe('5. Ingestão de JSON (UC-02) e Importação JSON/YAML (UC-04)', () => {
  it('rejeita JSON maior que 5 MB', () => {
    const fakeGrandao = 'a'.repeat(MAX_JSON_BYTES + 10);
    const res = validarEProcessarJsonTexto(fakeGrandao, 'teste_grande.json');
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.motivo).toContain('5 MB');
  });

  it('processa JSON válido extraindo caminhos simples', () => {
    const res = validarEProcessarJsonTexto(
      JSON.stringify(EXEMPLO_LEGADO_SINTETICO),
      'exemplo.json'
    );
    expect(res.sucesso).toBe(true);
    expect(res.campos.length).toBeGreaterThan(0);
    expect(res.campos.some((c) => c.caminho === 'pedido.status_legado')).toBe(true);
  });

  it('importa arquivo de configuração YAML válido via importarArquivoConfig', async () => {
    const yamlContent = `
mapeamentos:
  - recurso: ServiceRequest
    sentidos:
      - tipo: LEGADO_PARA_FHIR
        regras:
          - caminhoOrigem: pedido.status
            caminhoDestino: ServiceRequest.status
            regra: COPIAR
            obrigatorio: true
          - caminhoOrigem: pedido.intencao
            caminhoDestino: ServiceRequest.intent
            regra: COPIAR
            obrigatorio: true
          - caminhoOrigem: pedido.paciente
            caminhoDestino: ServiceRequest.subject.reference
            regra: COPIAR
            obrigatorio: true
          - caminhoOrigem: pedido.exame
            caminhoDestino: ServiceRequest.code.coding.code
            regra: COPIAR
            obrigatorio: true
tabelas: []
`;
    const file = new File([yamlContent], 'config.yaml', { type: 'text/yaml' });
    const res = await importarArquivoConfig(file, FHIR_CATALOG);
    expect(res.sucesso).toBe(true);
    expect(res.config?.mapeamentos[0]!.recurso).toBe('ServiceRequest');
  });
});

describe('6. Regras de mapeamento e inversão de tabela', () => {
  it('detecta chaves duplicadas na tabela de terminologia', () => {
    const linhas = [
      { id: '1', valorLegado: 'ATIVO', codigoFhir: 'active', rotulo: '' },
      { id: '2', valorLegado: 'ATIVO', codigoFhir: 'registered', rotulo: '' },
      { id: '3', valorLegado: 'CANCELADO', codigoFhir: 'revoked', rotulo: '' },
    ];
    const dups = detectarChavesDuplicadas(linhas);
    expect(dups).toEqual(['ATIVO']);
  });

  it('detecta colisão N:1 ao criar tabela inversa', () => {
    const tabelaOriginal = {
      nome: 'TAB_STATUS',
      equivalencia: {
        ATIVO: 'active',
        EM_ANDAMENTO: 'active', // dois valores legados mapeiam para o mesmo destino
      },
    };
    const { colisoes } = criarTabelaInversa(tabelaOriginal);
    expect(colisoes).toContain('active');
  });
});
