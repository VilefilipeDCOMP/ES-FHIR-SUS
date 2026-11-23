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
  listarReferenciasDominio,
  renomearDominioNaConfig,
  serializarRegraParaContrato,
  validarDestinosDoSentido,
} from '../utils/mappingValidation';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('1. Validação estrutural e semântica (ConfigValidator / CT-03 — RF011/RF012/RF013)', () => {
  it('rejeita payloads malformados, tipos incompatíveis (TRANSFORMACAO/EXPRESSAO) e conflitos de destino', () => {
    const casos: Array<{ nome: string; payload: unknown }> = [
      {
        nome: 'recurso inexistente ("Invented")',
        payload: {
          mapeamentos: [
            {
              recurso: 'Invented',
              toLegado: [],
              toFHIR: [],
            },
          ],
          dominios: [],
        },
      },
      {
        nome: 'recurso duplicado no mesmo arquivo',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              toLegado: [],
              toFHIR: [
                {
                  caminhoOrigem: '$.paciente.nome',
                  caminhoDestino: 'name[0].text',
                  tipoConversao: 'DIRETA',
                  parametroConversao: null,
                  obrigatorio: true,
                },
              ],
            },
            {
              recurso: 'Patient',
              toLegado: [],
              toFHIR: [
                {
                  caminhoOrigem: '$.paciente.sexo',
                  caminhoDestino: 'gender',
                  tipoConversao: 'DIRETA',
                  parametroConversao: null,
                  obrigatorio: false,
                },
              ],
            },
          ],
          dominios: [],
        },
      },
      {
        nome: 'toLegado e toFHIR ambos vazios no recurso',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              toLegado: [],
              toFHIR: [],
            },
          ],
          dominios: [],
        },
      },
      {
        nome: 'tipoConversao TRANSFORMACAO fora do recorte P3',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              toLegado: [],
              toFHIR: [
                {
                  caminhoOrigem: '$.dataNasc',
                  caminhoDestino: 'birthDate',
                  tipoConversao: 'TRANSFORMACAO',
                  parametroConversao: 'yyyy-MM-dd',
                  obrigatorio: true,
                },
              ],
            },
          ],
          dominios: [],
        },
      },
      {
        nome: 'DIRETA com parametroConversao preenchido e origem sem $',
        payload: {
          mapeamentos: [
            {
              recurso: 'Patient',
              toLegado: [],
              toFHIR: [
                {
                  caminhoOrigem: 'paciente.nome',
                  caminhoDestino: 'name[0].text',
                  tipoConversao: 'DIRETA',
                  parametroConversao: 'nao_deveria',
                  obrigatorio: true,
                },
              ],
            },
          ],
          dominios: [],
        },
      },
      {
        nome: 'item null em mapeamentos',
        payload: { mapeamentos: [null], dominios: [] },
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

  it('detecta conflitos de destino (dados vs dados.codigo e dados.codigo vs dados[0]) e lacunas em arrays', () => {
    const conflitoEscalarObjeto = validarDestinosDoSentido(['dados', 'dados.codigo']);
    expect(conflitoEscalarObjeto.size).toBeGreaterThan(0);

    const conflitoObjetoArray = validarDestinosDoSentido(['dados.codigo', 'dados[0]']);
    expect(conflitoObjetoArray.size).toBeGreaterThan(0);

    const lacunaArray = validarDestinosDoSentido(['code.coding[1].code']);
    expect(lacunaArray.get(-1)).toContain('lacuna no array de destino');

    const compartilhadoValido = validarDestinosDoSentido([
      'code.coding[0].system',
      'code.coding[0].code',
    ]);
    expect(compartilhadoValido.size).toBe(0);
  });
});

describe('2. Obrigatoriedades FHIR R4 e serialização de tipos (Cenários 2 e 3)', () => {
  it('Observation.code é satisfeito por code.text sem exigir code.coding[0].code', () => {
    const cfg: MapeamentoConfig = {
      mapeamentos: [
        {
          recurso: 'Observation',
          toLegado: [],
          toFHIR: [
            {
              caminhoOrigem: '$.situacao',
              caminhoDestino: 'status',
              tipoConversao: 'DIRETA',
              parametroConversao: null,
              obrigatorio: true,
            },
            {
              caminhoOrigem: '$.codigoExame',
              caminhoDestino: 'code.text',
              tipoConversao: 'DIRETA',
              parametroConversao: null,
              obrigatorio: true,
            },
          ],
        },
      ],
      dominios: [],
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

  it('Cenário 2 e 3: DIRETA envia parametroConversao: null e CONSTANTE preserva "001" como string omitindo caminhoOrigem', () => {
    const regraDireta = serializarRegraParaContrato({
      tipoConversao: 'DIRETA',
      caminhoOrigem: '$.resultado',
      caminhoDestino: 'valueQuantity.value',
      parametroConversao: null,
      obrigatorio: true,
    });
    expect(regraDireta.parametroConversao).toBeNull();

    const regraConstante = serializarRegraParaContrato({
      tipoConversao: 'CONSTANTE',
      caminhoDestino: 'codigoFixo',
      parametroConversao: '001',
      obrigatorio: true,
    });
    expect(regraConstante.parametroConversao).toBe('001');
    expect(typeof regraConstante.parametroConversao).toBe('string');
    expect('caminhoOrigem' in regraConstante).toBe(false);
  });
});

describe('3. Mock fail-closed alinhado ao MotorConversao', () => {
  it('rejeita conversão com MAPEAMENTO_INEXISTENTE quando sentido está vazio ([])', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);
    const res = await mock.testarConversao({
      config: CONFIG_INICIAL_SINTETICA, // ServiceRequest.toFHIR é []
      recurso: 'ServiceRequest',
      sentido: 'LEGADO_PARA_FHIR',
      exemploJson: EXEMPLO_LEGADO_SINTETICO,
    });

    expect(res.sucesso).toBe(false);
    expect(res.payloadSaida).toBeUndefined();
    expect(res.erros[0]!.motivo).toContain('MAPEAMENTO_INEXISTENTE');
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
          toLegado: [],
          toFHIR: [
            {
              caminhoOrigem: '$.paciente.nome',
              caminhoDestino: 'name[0].text',
              tipoConversao: 'DIRETA',
              parametroConversao: null,
              obrigatorio: true,
            },
          ],
        },
      ],
      dominios: [],
    };

    const res = await mock.salvarConfiguracao(novaCfg);
    expect(res.sucesso).toBe(false);

    const ativaAtual = await mock.buscarConfiguracaoAtiva();
    expect(ativaAtual.mapeamentos[0]!.recurso).toBe('ServiceRequest');
  });

  it('converte ServiceRequest.toLegado (FHIR_PARA_LEGADO) e Observation.toFHIR (LEGADO_PARA_FHIR) com DIRETA, DOMINIO e CONSTANTE', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);

    // 1. ServiceRequest FHIR -> Legado
    const resToLegado = await mock.testarConversao({
      config: CONFIG_INICIAL_SINTETICA,
      recurso: 'ServiceRequest',
      sentido: 'FHIR_PARA_LEGADO',
      exemploJson: EXEMPLOS_FHIR_SINTETICOS.ServiceRequest!,
    });
    expect(resToLegado.sucesso).toBe(true);
    expect(resToLegado.payloadSaida).toEqual({
      numeroPedido: 'PED-001',
      codigoExame: 'LAB-042',
      origem: 'CLINICA_DEMO',
    });

    // 2. Observation Legado -> FHIR (preserva tipo numérico 92.4 em DIRETA)
    const resToFhir = await mock.testarConversao({
      config: CONFIG_INICIAL_SINTETICA,
      recurso: 'Observation',
      sentido: 'LEGADO_PARA_FHIR',
      exemploJson: EXEMPLO_LEGADO_SINTETICO,
    });
    expect(resToFhir.sucesso).toBe(true);
    expect(resToFhir.payloadSaida).toEqual({
      resourceType: 'Observation',
      code: {
        coding: [
          {
            system: 'https://clinica.example/codigos/exames',
            code: 'EXAME-001',
          },
        ],
      },
      status: 'final',
      valueQuantity: {
        value: 92.4,
      },
    });
  });

  it('rejeita propriedades herdadas do protótipo (ex.: toString, constructor) não cadastradas na tabela', async () => {
    const mock = criarMockMappingApi(CONFIG_INICIAL_SINTETICA);
    const res = await mock.testarConversao({
      config: CONFIG_INICIAL_SINTETICA,
      recurso: 'Observation',
      sentido: 'LEGADO_PARA_FHIR',
      exemploJson: {
        ...EXEMPLO_LEGADO_SINTETICO,
        situacao: 'toString',
      },
    });

    expect(res.sucesso).toBe(false);
    expect(res.payloadSaida).toBeUndefined();
    expect(res.erros.some((e) => e.motivo.includes('toString'))).toBe(true);
  });
});

describe('4. Adapter HTTP e contrato CT-04 (Cenário 6 e Cenário 7)', () => {
  it('rejeita resposta 200 com JSON vazio em /validar sem falso sucesso', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('', { status: 200, headers: { 'Content-Type': 'application/json' } })
    );

    const res = await httpMappingApi.validarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.motivo).toContain('Esperado JSON explícito');
  });

  it('Cenário 7: conclui salvamento com HTTP 204 No Content sem chamar response.json()', async () => {
    const response204 = new Response(null, { status: 204 });
    const jsonSpy = vi.spyOn(response204, 'json');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response204);

    const res = await httpMappingApi.salvarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(res.sucesso).toBe(true);
    expect(jsonSpy).not.toHaveBeenCalled();
  });

  it('Cenário 6: extrai detalhes de erro HTTP 422 com caminho estruturado e alvo de navegação para outra aba', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          erro: 'CONFIG_INVALIDA',
          mensagem: 'Configuração inválida',
          detalhes: [
            {
              caminho: 'mapeamentos[1].toFHIR[2].parametroConversao',
              mensagem: 'domínio inexistente: exames_lab_para_clinica',
            },
          ],
        }),
        { status: 422, headers: { 'Content-Type': 'application/json' } }
      )
    );

    const res = await httpMappingApi.salvarConfiguracao(CONFIG_INICIAL_SINTETICA);
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.local).toBe('mapeamentos[1].toFHIR[2].parametroConversao');
    expect(res.erros[0]!.alvo).toEqual({
      recurso: 'Observation',
      sentido: 'LEGADO_PARA_FHIR',
      indiceRegra: 2,
      caminhoDestino: 'code.coding[0].code',
      nomeDominio: 'exames_lab_para_clinica',
    });
  });
});

describe('5. Ingestão de JSON (UC-02) e Importação JSON/YAML (UC-04 / Cenário 8)', () => {
  it('rejeita JSON maior que 5 MB', () => {
    const fakeGrandao = 'a'.repeat(MAX_JSON_BYTES + 10);
    const res = validarEProcessarJsonTexto(fakeGrandao, 'teste_grande.json');
    expect(res.sucesso).toBe(false);
    expect(res.erros[0]!.motivo).toContain('5 MB');
  });

  it('processa JSON válido extraindo caminhos com índices [0]', () => {
    const res = validarEProcessarJsonTexto(
      JSON.stringify(EXEMPLOS_FHIR_SINTETICOS.ServiceRequest),
      'exemplo_fhir.json'
    );
    expect(res.sucesso).toBe(true);
    expect(res.campos.some((c) => c.caminho === 'identifier[0].value')).toBe(true);
    expect(res.campos.some((c) => c.caminho === 'code.coding[0].code')).toBe(true);
  });

  it('importa arquivo de configuração YAML válido no formato MapeamentoConfig real', async () => {
    const yamlContent = `
mapeamentos:
  - recurso: ServiceRequest
    toLegado:
      - caminhoOrigem: $.identifier[0].value
        caminhoDestino: numeroPedido
        tipoConversao: DIRETA
        parametroConversao: null
        obrigatorio: true
    toFHIR: []
dominios: []
`;
    const file = new File([yamlContent], 'config.yaml', { type: 'text/yaml' });
    const res = await importarArquivoConfig(file, FHIR_CATALOG);
    expect(res.sucesso).toBe(true);
    expect(res.config?.mapeamentos[0]!.recurso).toBe('ServiceRequest');
    expect(res.config?.mapeamentos[0]!.toLegado[0]!.tipoConversao).toBe('DIRETA');
  });
});

describe('6. Tabelas de domínio: duplicidade, renomeação compartilhada e inversão (Cenários 4 e 5)', () => {
  it('Cenário 4: detecta códigos de origem duplicados antes de montar equivalencia', () => {
    const linhas = [
      { id: '1', valorLegado: '001', codigoFhir: 'LAB-01', rotulo: '' },
      { id: '2', valorLegado: '001', codigoFhir: 'LAB-02', rotulo: '' },
      { id: '3', valorLegado: '1', codigoFhir: 'LAB-01', rotulo: '' },
    ];
    const dups = detectarChavesDuplicadas(linhas);
    expect(dups).toEqual(['001']);
  });

  it('Cenário 5: renomeia um domínio compartilhado atualizando todas as referências em todos os recursos e sentidos', () => {
    const refsAntes = listarReferenciasDominio(
      CONFIG_INICIAL_SINTETICA,
      'exames_clinica_para_lab'
    );
    expect(refsAntes).toHaveLength(2); // ServiceRequest.toLegado[1] e Observation.toLegado[0]

    const { configAtualizada, regrasAtualizadas } = renomearDominioNaConfig(
      CONFIG_INICIAL_SINTETICA,
      'exames_clinica_para_lab',
      'exames_clinica_renomeado'
    );

    expect(regrasAtualizadas).toBe(2);
    expect(
      configAtualizada.dominios.some((d) => d.nome === 'exames_clinica_renomeado')
    ).toBe(true);
    expect(
      listarReferenciasDominio(configAtualizada, 'exames_clinica_renomeado')
    ).toHaveLength(2);
  });

  it('detecta colisão N:1 ao criar tabela inversa', () => {
    const tabelaOriginal = {
      nome: 'TAB_STATUS',
      equivalencia: {
        ATIVO: 'active',
        EM_ANDAMENTO: 'active',
      },
    };
    const { colisoes } = criarTabelaInversa(tabelaOriginal);
    expect(colisoes).toContain('active');
  });
});
