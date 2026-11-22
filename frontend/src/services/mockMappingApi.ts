import type { MapeamentoConfig, RecursoFhir } from '../types/domain';
import type { ErroValidacao } from '../types/ui';
import type { MappingGateway } from './mappingGateway';
import { FHIR_CATALOG } from '../data/fhirCatalog';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';

const STORAGE_KEY = 'fhirsus_p3_demo_active_config_v3';
const COLECOES_FHIR_CONHECIDAS = new Set([
  'identifier',
  'name',
  'coding',
  'telecom',
  'address',
]);

function lerCaminho(obj: Record<string, unknown>, caminho: string): unknown {
  const partes = caminho.split('.');
  let atual: unknown = obj;
  for (const parte of partes) {
    if (atual == null || typeof atual !== 'object') return undefined;
    if (Array.isArray(atual)) atual = atual[0];
    if (atual == null || typeof atual !== 'object') return undefined;
    atual = (atual as Record<string, unknown>)[parte];
  }
  return atual;
}

function escreverCaminhoFhir(
  alvo: Record<string, unknown>,
  recurso: RecursoFhir,
  caminhoFhir: string,
  valor: unknown
) {
  const relativo = caminhoFhir.startsWith(`${recurso}.`)
    ? caminhoFhir.slice(recurso.length + 1)
    : caminhoFhir;
  const partes = relativo.split('.');

  let cursor: Record<string, unknown> = alvo;
  for (let i = 0; i < partes.length - 1; i++) {
    const chave = partes[i]!;
    if (COLECOES_FHIR_CONHECIDAS.has(chave)) {
      if (!Array.isArray(cursor[chave])) cursor[chave] = [{}];
      cursor = (cursor[chave] as Record<string, unknown>[])[0]!;
    } else {
      if (typeof cursor[chave] !== 'object' || cursor[chave] === null) {
        cursor[chave] = {};
      }
      cursor = cursor[chave] as Record<string, unknown>;
    }
  }
  cursor[partes[partes.length - 1]!] = valor;
}

function escreverCaminhoLegado(
  alvo: Record<string, unknown>,
  caminho: string,
  valor: unknown
) {
  const partes = caminho.split('.');
  let cursor = alvo;
  for (let i = 0; i < partes.length - 1; i++) {
    const p = partes[i]!;
    if (typeof cursor[p] !== 'object' || cursor[p] === null) cursor[p] = {};
    cursor = cursor[p] as Record<string, unknown>;
  }
  cursor[partes[partes.length - 1]!] = valor;
}

const AVISO_MOCK: ErroValidacao = {
  local: 'Modo Mock Local',
  motivo:
    'Executando sem o motor Java: esta simulação serve apenas para testes de interface e NÃO comprova conformidade HAPI FHIR R4 nem atomicidade no servidor.',
  severidade: 'AVISO',
};

export function criarMockMappingApi(configPadrao: MapeamentoConfig): MappingGateway {
  const carregarPersistencia = (): MapeamentoConfig => {
    try {
      const salvo = localStorage.getItem(STORAGE_KEY);
      if (!salvo) return structuredClone(configPadrao);
      const validado = validarConfiguracaoDesconhecida(JSON.parse(salvo), FHIR_CATALOG);
      return validado.ok && validado.config
        ? structuredClone(validado.config)
        : structuredClone(configPadrao);
    } catch {
      return structuredClone(configPadrao);
    }
  };

  let ativa: MapeamentoConfig = carregarPersistencia();

  return {
    async buscarConfiguracaoAtiva() {
      return structuredClone(ativa);
    },

    async validarConfiguracao(config) {
      const res = validarConfiguracaoDesconhecida(config, FHIR_CATALOG);
      return {
        acao: 'VALIDAR',
        sucesso: res.ok,
        timestamp: new Date().toISOString(),
        mensagemResumo: res.ok
          ? 'Validação local (mock) concluída sem erros.'
          : 'Validação local (mock) encontrou erros.',
        erros: res.erros,
        avisos: [...res.avisos, AVISO_MOCK],
      };
    },

    async testarConversao({ config, recurso, sentido, exemploJson }) {
      const validacao = validarConfiguracaoDesconhecida(config, FHIR_CATALOG);
      if (!validacao.ok) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'Teste abortado: existem inconsistências na configuração.',
          erros: validacao.erros,
          avisos: [AVISO_MOCK],
        };
      }

      // fail-closed: exige mapeamento e sentido explicitamente configurados com regras
      const map = config.mapeamentos.find((m) => m.recurso === recurso);
      const s = map?.sentidos.find((item) => item.tipo === sentido);
      if (!map || !s || s.regras.length === 0) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: `Nenhum mapeamento configurado para ${recurso} (${sentido}).`,
          erros: [
            {
              local: `${recurso} > ${sentido}`,
              motivo:
                'Não há regras configuradas para este recurso e sentido. Nenhuma conversão foi executada.',
              severidade: 'ERRO',
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      if (sentido === 'FHIR_PARA_LEGADO' && exemploJson.resourceType !== recurso) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'JSON de entrada não corresponde ao recurso FHIR selecionado.',
          erros: [
            {
              local: `${recurso} > resourceType`,
              motivo: `Esperado resourceType="${recurso}", recebido "${String(exemploJson.resourceType ?? 'ausente')}".`,
              severidade: 'ERRO',
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      const erros: ErroValidacao[] = [];
      const saida: Record<string, unknown> =
        sentido === 'LEGADO_PARA_FHIR' ? { resourceType: recurso } : {};
      let camposConvertidos = 0;

      for (const regra of s.regras) {
        const caminhoLeitura =
          sentido === 'FHIR_PARA_LEGADO' && regra.caminhoOrigem.startsWith(`${recurso}.`)
            ? regra.caminhoOrigem.slice(recurso.length + 1)
            : regra.caminhoOrigem;

        const bruto = lerCaminho(exemploJson, caminhoLeitura);
        if (bruto === undefined) {
          if (regra.obrigatorio) {
            erros.push({
              local: `${recurso} > ${regra.caminhoOrigem}`,
              motivo: 'Valor ausente no JSON de entrada para campo obrigatório.',
              severidade: 'ERRO',
            });
          }
          continue;
        }

        let convertido: unknown = bruto;
        if (regra.regra.startsWith('TABELA:')) {
          const nomeTabela = regra.regra.slice('TABELA:'.length);
          const tab = config.tabelas.find((t) => t.nome === nomeTabela);
          convertido = tab ? tab.equivalencia[String(bruto)] : undefined;
          if (convertido === undefined) {
            erros.push({
              local: `${recurso} > ${regra.caminhoOrigem} -> ${regra.caminhoDestino}`,
              motivo: `Valor "${String(bruto)}" não encontrado na tabela "${nomeTabela}" (OperationOutcome simulado).`,
              severidade: 'ERRO',
            });
            continue;
          }
        }

        if (sentido === 'LEGADO_PARA_FHIR') {
          escreverCaminhoFhir(saida, recurso, regra.caminhoDestino, convertido);
        } else {
          escreverCaminhoLegado(saida, regra.caminhoDestino, convertido);
        }
        camposConvertidos++;
      }

      if (camposConvertidos === 0 && erros.length === 0) {
        erros.push({
          local: `${recurso} > ${sentido}`,
          motivo: 'Nenhum campo da entrada correspondeu às regras configuradas.',
          severidade: 'ERRO',
        });
      }

      return {
        acao: 'TESTAR',
        sucesso: erros.length === 0,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          erros.length === 0
            ? 'Conversão simulada executada com sucesso.'
            : 'Conversão simulada retornou erros.',
        erros,
        avisos: [AVISO_MOCK],
        payloadSaida: erros.length === 0 ? saida : undefined,
      };
    },

    async salvarConfiguracao(config) {
      const validacao = validarConfiguracaoDesconhecida(config, FHIR_CATALOG);
      if (!validacao.ok) {
        return {
          acao: 'SALVAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo:
            'Configuração inválida rejeitada. A configuração anterior permanece ativa.',
          erros: validacao.erros,
          avisos: [...validacao.avisos, AVISO_MOCK],
        };
      }

      // persiste ANTES de atualizar ativa em memória (fail-closed)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
        ativa = structuredClone(config);
      } catch {
        return {
          acao: 'SALVAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo:
            'Falha ao gravar no armazenamento local de demonstração. A configuração anterior permanece ativa.',
          erros: [
            {
              local: 'Persistência Local (Mock)',
              motivo: 'Não foi possível gravar no localStorage.',
              severidade: 'ERRO',
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      return {
        acao: 'SALVAR',
        sucesso: true,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Configuração salva no armazenamento local de demonstração.',
        erros: [],
        avisos: [...validacao.avisos, AVISO_MOCK],
      };
    },
  };
}
