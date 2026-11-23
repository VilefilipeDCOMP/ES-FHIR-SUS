import type { MapeamentoConfig } from '../types/domain';
import type { ErroValidacao } from '../types/ui';
import { FHIR_CATALOG } from '../data/fhirCatalog';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';
import {
  analisarCaminhoDestino,
  analisarCaminhoOrigem,
  serializarConfigParaContrato,
  type SegmentoCaminho,
} from '../utils/mappingValidation';
import type { MappingGateway } from './mappingGateway';

const STORAGE_KEY = 'fhirsus_p3_demo_active_config_v4';

function ehObjetoPlano(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function semValor(valor: unknown): boolean {
  return (
    valor === undefined ||
    valor === null ||
    (typeof valor === 'string' && valor.trim() === '')
  );
}

function lerCaminhoJson(
  raiz: Record<string, unknown>,
  caminhoTexto: string
): { ok: true; valor: unknown } | { ok: false; motivo: string } {
  const analise = analisarCaminhoOrigem(caminhoTexto);
  if (!analise.ok) {
    return { ok: false, motivo: analise.erro ?? 'Sintaxe de caminho não suportada.' };
  }

  let atual: unknown = raiz;
  for (const seg of analise.segmentos) {
    if (atual === null || atual === undefined) {
      return { ok: true, valor: atual };
    }
    if (seg.indice !== null) {
      if (!Array.isArray(atual)) {
        return {
          ok: false,
          motivo: 'A estrutura do JSON de origem é incompatível com o caminho (esperado array).',
        };
      }
      atual = atual[seg.indice];
    } else if (seg.campo !== null) {
      if (!ehObjetoPlano(atual)) {
        return {
          ok: false,
          motivo: 'A estrutura do JSON de origem é incompatível com o caminho (esperado objeto).',
        };
      }
      atual = Object.prototype.hasOwnProperty.call(atual, seg.campo)
        ? atual[seg.campo]
        : undefined;
    }
  }
  return { ok: true, valor: atual };
}

interface NoConstrucaoSaida {
  filhos: Map<string, { seg: SegmentoCaminho; no: NoConstrucaoSaida }>;
  terminal: boolean;
  valor: unknown;
}

/**
 * replica a construção de árvore JSON de SaidaJson.java (incluindo omissão de opcionais vazios
 * e detecção de lacunas em arrays de saída em tempo de execução).
 */
function construirSaidaJson(
  registros: Array<{ caminhoDestino: string; valor: unknown }>
): { ok: true; objeto: Record<string, unknown> } | { ok: false; local: string; motivo: string } {
  const raiz: NoConstrucaoSaida = { filhos: new Map(), terminal: false, valor: null };

  for (const reg of registros) {
    const analise = analisarCaminhoDestino(reg.caminhoDestino);
    if (!analise.ok) {
      return {
        ok: false,
        local: reg.caminhoDestino,
        motivo: analise.erro ?? 'Sintaxe de caminho não suportada.',
      };
    }

    let atual = raiz;
    for (const seg of analise.segmentos) {
      const primeiro = atual.filhos.values().next().value as
        | { seg: SegmentoCaminho; no: NoConstrucaoSaida }
        | undefined;
      const tipoDiferente =
        primeiro !== undefined && (primeiro.seg.indice !== null) !== (seg.indice !== null);
      if (atual.terminal || tipoDiferente) {
        return {
          ok: false,
          local: reg.caminhoDestino,
          motivo: 'O destino é duplicado ou incompatível com outra regra.',
        };
      }
      const chave = seg.indice !== null ? `[${seg.indice}]` : `.${seg.campo}`;
      let entrada = atual.filhos.get(chave);
      if (!entrada) {
        entrada = { seg, no: { filhos: new Map(), terminal: false, valor: null } };
        atual.filhos.set(chave, entrada);
      }
      atual = entrada.no;
    }

    if (atual.terminal || atual.filhos.size > 0) {
      return {
        ok: false,
        local: reg.caminhoDestino,
        motivo: 'O destino é duplicado ou incompatível com outra regra.',
      };
    }

    atual.terminal = true;
    atual.valor = reg.valor;
  }

  const materializarNo = (
    no: NoConstrucaoSaida,
    caminhoAtual: string
  ): { ok: true; valor: unknown } | { ok: false; local: string; motivo: string } => {
    if (no.terminal) {
      return { ok: true, valor: no.valor === null ? null : structuredClone(no.valor) };
    }
    if (no.filhos.size === 0) {
      return { ok: true, valor: null };
    }
    const entradas = [...no.filhos.values()];
    const ehArray = entradas[0]?.seg.indice !== null;

    if (ehArray) {
      entradas.sort((a, b) => (a.seg.indice ?? 0) - (b.seg.indice ?? 0));
      const arr: unknown[] = [];
      for (const entrada of entradas) {
        const idx = entrada.seg.indice ?? 0;
        const dest = `${caminhoAtual}[${idx}]`;
        const filhoRes = materializarNo(entrada.no, dest);
        if (!filhoRes.ok) return filhoRes;
        if (filhoRes.valor !== null) {
          if (idx !== arr.length) {
            return {
              ok: false,
              local: dest,
              motivo: 'O índice deixaria uma lacuna no array de destino.',
            };
          }
          arr.push(filhoRes.valor);
        }
      }
      return { ok: true, valor: arr.length === 0 ? null : arr };
    }

    const obj: Record<string, unknown> = {};
    for (const entrada of entradas) {
      const campo = entrada.seg.campo ?? '';
      const dest = caminhoAtual ? `${caminhoAtual}.${campo}` : campo;
      const filhoRes = materializarNo(entrada.no, dest);
      if (!filhoRes.ok) return filhoRes;
      if (filhoRes.valor !== null) {
        obj[campo] = filhoRes.valor;
      }
    }
    return { ok: true, valor: Object.keys(obj).length === 0 ? null : obj };
  };

  const resRaiz = materializarNo(raiz, '');
  if (!resRaiz.ok) return resRaiz;
  return {
    ok: true,
    objeto: ehObjetoPlano(resRaiz.valor) ? resRaiz.valor : {},
  };
}

const AVISO_MOCK: ErroValidacao = {
  local: 'Modo Mock Local',
  motivo:
    'Executando simulação local fiel ao MotorConversao (DIRETA, DOMINIO, CONSTANTE). A validação oficial HAPI FHIR R4 ocorre no servidor Java.',
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

      const map = config.mapeamentos.find((m) => m.recurso === recurso);
      const regras =
        sentido === 'LEGADO_PARA_FHIR' ? (map?.toFHIR ?? []) : (map?.toLegado ?? []);
      const listaNome = sentido === 'LEGADO_PARA_FHIR' ? 'toFHIR' : 'toLegado';

      if (!map || regras.length === 0) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: `MAPEAMENTO_INEXISTENTE: Nenhuma regra configurada para ${recurso}.${listaNome}.`,
          erros: [
            {
              local: `${recurso}.${listaNome}`,
              motivo: 'Não há regras para o sentido solicitado (MAPEAMENTO_INEXISTENTE).',
              severidade: 'ERRO',
              alvo: { recurso, sentido },
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      if (
        sentido === 'FHIR_PARA_LEGADO' &&
        exemploJson.resourceType !== undefined &&
        exemploJson.resourceType !== recurso
      ) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'JSON de entrada não corresponde ao recurso FHIR selecionado.',
          erros: [
            {
              local: `${recurso} > resourceType`,
              motivo: `Esperado resourceType="${recurso}", recebido "${String(exemploJson.resourceType)}".`,
              severidade: 'ERRO',
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      const erros: ErroValidacao[] = [];
      const registrosSaida: Array<{ caminhoDestino: string; valor: unknown }> = [];

      regras.forEach((regra, idx) => {
        const localRegra = `${recurso}.${listaNome}[${idx}]`;

        if (regra.tipoConversao === 'CONSTANTE') {
          const valConst = regra.parametroConversao;
          if (semValor(valConst)) {
            if (regra.obrigatorio) {
              erros.push({
                local: `${localRegra} (${regra.caminhoDestino})`,
                motivo: 'O campo obrigatório ficou sem valor (CAMPO_OBRIGATORIO).',
                severidade: 'ERRO',
                alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
              });
            }
            registrosSaida.push({ caminhoDestino: regra.caminhoDestino, valor: null });
          } else {
            registrosSaida.push({
              caminhoDestino: regra.caminhoDestino,
              valor: String(valConst),
            });
          }
          return;
        }

        const leitura = lerCaminhoJson(exemploJson, regra.caminhoOrigem);
        if (!leitura.ok) {
          erros.push({
            local: `${localRegra} (${regra.caminhoOrigem})`,
            motivo: leitura.motivo,
            severidade: 'ERRO',
            alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
          });
          return;
        }

        const bruto = leitura.valor;
        if (semValor(bruto)) {
          if (regra.obrigatorio) {
            erros.push({
              local: `${localRegra} (${regra.caminhoDestino})`,
              motivo: 'O campo obrigatório ficou sem valor (CAMPO_OBRIGATORIO).',
              severidade: 'ERRO',
              alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
            });
          }
          registrosSaida.push({ caminhoDestino: regra.caminhoDestino, valor: null });
          return;
        }

        if (regra.tipoConversao === 'DIRETA') {
          registrosSaida.push({ caminhoDestino: regra.caminhoDestino, valor: bruto });
          return;
        }

        // DOMINIO
        if (typeof bruto !== 'string') {
          erros.push({
            local: `${localRegra} (${regra.caminhoOrigem})`,
            motivo: 'DOMINIO exige um código textual (VALOR_INVALIDO).',
            severidade: 'ERRO',
            alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
          });
          return;
        }

        const dom = config.dominios.find((d) => d.nome === regra.parametroConversao);
        if (!dom) {
          erros.push({
            local: `${localRegra} (${regra.caminhoDestino})`,
            motivo: `A tabela "${regra.parametroConversao}" referenciada pela regra não foi configurada (DOMINIO_INEXISTENTE).`,
            severidade: 'ERRO',
            alvo: {
              recurso,
              sentido,
              indiceRegra: idx,
              caminhoDestino: regra.caminhoDestino,
              nomeDominio: regra.parametroConversao,
            },
          });
          return;
        }

        const traduzido = Object.prototype.hasOwnProperty.call(dom.equivalencia, bruto)
          ? dom.equivalencia[bruto]
          : undefined;

        if (traduzido === undefined) {
          erros.push({
            local: `${localRegra} (${regra.caminhoOrigem} -> ${regra.caminhoDestino})`,
            motivo: `O código de origem "${bruto}" não possui equivalência na tabela "${dom.nome}" (CODIGO_SEM_EQUIVALENCIA).`,
            severidade: 'ERRO',
            alvo: {
              recurso,
              sentido,
              indiceRegra: idx,
              caminhoDestino: regra.caminhoDestino,
              nomeDominio: dom.nome,
            },
          });
          return;
        }

        registrosSaida.push({ caminhoDestino: regra.caminhoDestino, valor: traduzido });
      });

      if (erros.length > 0) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'Conversão simulada retornou erros.',
          erros,
          avisos: [AVISO_MOCK],
        };
      }

      const saidaRes = construirSaidaJson(registrosSaida);
      if (!saidaRes.ok) {
        return {
          acao: 'TESTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'Falha ao construir JSON de destino.',
          erros: [
            {
              local: `${recurso}.${listaNome} (${saidaRes.local})`,
              motivo: saidaRes.motivo,
              severidade: 'ERRO',
              alvo: { recurso, sentido },
            },
          ],
          avisos: [AVISO_MOCK],
        };
      }

      return {
        acao: 'TESTAR',
        sucesso: true,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Conversão simulada executada com sucesso.',
        erros: [],
        avisos: [AVISO_MOCK],
        payloadSaida: saidaRes.objeto,
      };
    },

    async salvarConfiguracao(config) {
      const validacao = validarConfiguracaoDesconhecida(config, FHIR_CATALOG);
      if (!validacao.ok || !validacao.config) {
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

      const canonica = serializarConfigParaContrato(validacao.config);

      // persiste ANTES de atualizar ativa em memória (fail-closed)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(canonica));
        ativa = structuredClone(canonica);
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
        configConfirmada: structuredClone(canonica),
      };
    },
  };
}
