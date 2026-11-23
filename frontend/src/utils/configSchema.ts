import {
  RECURSOS_OFICIAIS_FHIR,
  type DominioValores,
  type Mapeamento,
  type MapeamentoConfig,
  type RecursoFhir,
  type RegraCampo,
} from '../types/domain';
import type { CampoFhirSpec, ErroValidacao } from '../types/ui';
import {
  analisarCaminhoDestino,
  analisarCaminhoOrigem,
  avaliarPendenciasRecurso,
  interpretarLocalErro,
  listarReferenciasDominio,
  serializarConfigParaContrato,
  validarDestinosDoSentido,
} from './mappingValidation';

export interface ResultadoValidacaoEsquema {
  ok: boolean;
  config?: MapeamentoConfig;
  erros: ErroValidacao[];
  avisos: ErroValidacao[];
}

function ehObjetoPlano(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

const CHAVES_RAIZ_PERMITIDAS = new Set(['mapeamentos', 'dominios']);
const CHAVES_MAPEAMENTO_PERMITIDAS = new Set(['recurso', 'toLegado', 'toFHIR']);
const CHAVES_REGRA_PERMITIDAS = new Set([
  'caminhoOrigem',
  'caminhoDestino',
  'tipoConversao',
  'parametroConversao',
  'obrigatorio',
]);
const CHAVES_DOMINIO_PERMITIDAS = new Set(['nome', 'equivalencia']);
const RECURSOS_SET = new Set<string>(RECURSOS_OFICIAIS_FHIR);

/**
 * validação estrutural e semântica completa em tempo de execução (RF012 / CT-03 / ConfigValidator.java):
 * - rejeita propriedades desconhecidas (FAIL_ON_UNKNOWN_PROPERTIES)
 * - rejeita TRANSFORMACAO, EXPRESSAO e campos de condição fora do recorte P3
 * - valida sintaxe JSONPath na origem ($.campo[0]) e Dot Notation no destino (campo[0].sub)
 * - valida conflitos e lacunas de array nos destinos (SaidaJson)
 * - valida integridade de domínios e referências cruzadas em todos os recursos e sentidos
 */
export function validarConfiguracaoDesconhecida(
  input: unknown,
  catalogo?: Record<RecursoFhir, CampoFhirSpec[]>
): ResultadoValidacaoEsquema {
  if (!ehObjetoPlano(input)) {
    return {
      ok: false,
      erros: [
        {
          local: '$',
          motivo: 'configuração ausente ou raiz não é um objeto JSON.',
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  const erros: ErroValidacao[] = [];
  const avisos: ErroValidacao[] = [];

  for (const chave of Object.keys(input)) {
    if (!CHAVES_RAIZ_PERMITIDAS.has(chave)) {
      erros.push({
        local: chave,
        motivo: `Campo desconhecido na raiz de MapeamentoConfig: "${chave}". Esperado apenas "mapeamentos" e "dominios".`,
        severidade: 'ERRO',
      });
    }
  }

  const rawMapeamentos = input.mapeamentos;
  const rawDominios = input.dominios ?? [];

  if (!Array.isArray(rawMapeamentos)) {
    erros.push({
      local: 'mapeamentos',
      motivo: 'O campo "mapeamentos" deve ser uma lista.',
      severidade: 'ERRO',
    });
  }
  if (!Array.isArray(rawDominios)) {
    erros.push({
      local: 'dominios',
      motivo: 'O campo "dominios" deve ser uma lista.',
      severidade: 'ERRO',
    });
  }

  if (erros.length > 0) {
    return { ok: false, erros, avisos };
  }

  // 1. Validar domínios (ConfigValidator.validarDominios)
  const dominiosValidados: DominioValores[] = [];
  const nomesDominios = new Set<string>();

  (rawDominios as unknown[]).forEach((itemDom, i) => {
    const prefixo = `dominios[${i}]`;
    if (!ehObjetoPlano(itemDom)) {
      erros.push({
        local: prefixo,
        motivo: 'Cada domínio deve ser um objeto com "nome" e "equivalencia".',
        severidade: 'ERRO',
      });
      return;
    }

    for (const k of Object.keys(itemDom)) {
      if (!CHAVES_DOMINIO_PERMITIDAS.has(k)) {
        erros.push({
          local: `${prefixo}.${k}`,
          motivo: `Propriedade desconhecida em domínio: "${k}".`,
          severidade: 'ERRO',
        });
      }
    }

    const nome = typeof itemDom.nome === 'string' ? itemDom.nome.trim() : '';
    if (!nome) {
      erros.push({
        local: `${prefixo}.nome`,
        motivo: 'obrigatório',
        severidade: 'ERRO',
      });
    } else if (nomesDominios.has(nome)) {
      erros.push({
        local: `${prefixo}.nome`,
        motivo: `duplicado: ${nome}`,
        severidade: 'ERRO',
      });
    } else {
      nomesDominios.add(nome);
    }

    const eqRaw = itemDom.equivalencia;
    const equivalencia: Record<string, string> = {};
    if (!ehObjetoPlano(eqRaw) || Object.keys(eqRaw).length === 0) {
      erros.push({
        local: `${prefixo}.equivalencia`,
        motivo: 'deve ter ao menos 1 entrada',
        severidade: 'ERRO',
      });
    } else {
      for (const [k, v] of Object.entries(eqRaw)) {
        if (typeof v !== 'string' || !k.trim() || !v.trim()) {
          erros.push({
            local: `${prefixo}.equivalencia.${k || '(vazio)'}`,
            motivo: 'Os códigos de origem e destino na tabela de domínio devem ser textos não vazios.',
            severidade: 'ERRO',
          });
        } else {
          equivalencia[k] = v;
        }
      }
    }

    dominiosValidados.push({ nome, equivalencia });
  });

  // 2. Validar mapeamentos (ConfigValidator.validarMapeamento)
  const listaMap = rawMapeamentos as unknown[];
  if (listaMap.length === 0) {
    erros.push({
      local: 'mapeamentos',
      motivo: 'deve ter ao menos 1 mapeamento',
      severidade: 'ERRO',
    });
    return { ok: false, erros, avisos };
  }

  const mapeamentosValidados: Mapeamento[] = [];
  const recursosVistos = new Set<string>();

  listaMap.forEach((itemMap, i) => {
    const p = `mapeamentos[${i}]`;
    if (!ehObjetoPlano(itemMap)) {
      erros.push({
        local: p,
        motivo: 'Item de mapeamento inválido: esperado um objeto.',
        severidade: 'ERRO',
      });
      return;
    }

    for (const k of Object.keys(itemMap)) {
      if (!CHAVES_MAPEAMENTO_PERMITIDAS.has(k)) {
        erros.push({
          local: `${p}.${k}`,
          motivo: `Propriedade desconhecida no mapeamento: "${k}". Esperado "recurso", "toLegado" e "toFHIR".`,
          severidade: 'ERRO',
        });
      }
    }

    const recurso = typeof itemMap.recurso === 'string' ? itemMap.recurso.trim() : '';
    if (!recurso || !RECURSOS_SET.has(recurso)) {
      erros.push({
        local: `${p}.recurso`,
        motivo: `deve ser um de [${[...RECURSOS_OFICIAIS_FHIR].sort().join(', ')}]`,
        severidade: 'ERRO',
      });
      return;
    }

    if (recursosVistos.has(recurso)) {
      erros.push({
        local: `${p}.recurso`,
        motivo: `recurso duplicado: ${recurso}`,
        severidade: 'ERRO',
      });
    }
    recursosVistos.add(recurso);

    const rawToLegado = itemMap.toLegado ?? [];
    const rawToFHIR = itemMap.toFHIR ?? [];

    if (!Array.isArray(rawToLegado)) {
      erros.push({
        local: `${p}.toLegado`,
        motivo: 'A lista "toLegado" deve ser um array de regras.',
        severidade: 'ERRO',
      });
      return;
    }
    if (!Array.isArray(rawToFHIR)) {
      erros.push({
        local: `${p}.toFHIR`,
        motivo: 'A lista "toFHIR" deve ser um array de regras.',
        severidade: 'ERRO',
      });
      return;
    }

    if (rawToLegado.length === 0 && rawToFHIR.length === 0) {
      erros.push({
        local: p,
        motivo: 'deve ter ao menos 1 regra em toLegado ou toFHIR',
        severidade: 'ERRO',
      });
    }

    const toLegado = validarListaRegras(rawToLegado, `${p}.toLegado`, nomesDominios, erros);
    const toFHIR = validarListaRegras(rawToFHIR, `${p}.toFHIR`, nomesDominios, erros);

    mapeamentosValidados.push({
      recurso: recurso as RecursoFhir,
      toLegado,
      toFHIR,
    });
  });

  const configParcial: MapeamentoConfig = {
    mapeamentos: mapeamentosValidados,
    dominios: dominiosValidados,
  };

  if (catalogo) {
    for (const map of configParcial.mapeamentos) {
      const spec = catalogo[map.recurso] ?? [];
      if (map.toFHIR.length > 0) {
        const resumoFhir = avaliarPendenciasRecurso(
          configParcial,
          map.recurso,
          'LEGADO_PARA_FHIR',
          spec
        );
        avisos.push(...resumoFhir.avisosUx);
      }
      if (map.toLegado.length > 0) {
        const resumoLegado = avaliarPendenciasRecurso(
          configParcial,
          map.recurso,
          'FHIR_PARA_LEGADO',
          spec
        );
        avisos.push(...resumoLegado.avisosUx);
      }
    }
  }

  // Enriquecer erros com alvo de navegação e referências de domínio compartilhado
  const errosEnriquecidos = erros.map((erro) => {
    const alvo = erro.alvo ?? interpretarLocalErro(erro.local, configParcial);
    let motivo = erro.motivo;
    if (alvo?.nomeDominio && erro.local.startsWith('dominios[')) {
      const refs = listarReferenciasDominio(configParcial, alvo.nomeDominio);
      if (refs.length > 0) {
        const descRefs = refs
          .map((r) => `${r.recurso}.${r.lista} (${r.caminhoDestino})`)
          .join(', ');
        motivo = `${motivo} — Afeta os mapeamentos: ${descRefs}.`;
      }
    }
    return { ...erro, motivo, alvo };
  });

  if (errosEnriquecidos.length > 0) {
    return { ok: false, erros: errosEnriquecidos, avisos };
  }

  return {
    ok: true,
    config: serializarConfigParaContrato(configParcial),
    erros: [],
    avisos,
  };
}

function validarListaRegras(
  listaRaw: unknown[],
  prefixoLista: string,
  nomesDominios: Set<string>,
  erros: ErroValidacao[]
): RegraCampo[] {
  const regrasValidadas: RegraCampo[] = [];
  const destinosParaChecagem: string[] = [];

  listaRaw.forEach((itemRegra, idx) => {
    const rp = `${prefixoLista}[${idx}]`;
    if (!ehObjetoPlano(itemRegra)) {
      erros.push({
        local: rp,
        motivo: 'Cada regra deve ser um objeto válido.',
        severidade: 'ERRO',
      });
      destinosParaChecagem.push('');
      return;
    }

    for (const k of Object.keys(itemRegra)) {
      if (k === 'condicao' || k === 'condicional') {
        erros.push({
          local: `${rp}.${k}`,
          motivo: 'Incompatibilidade: campo de condição está fora do recorte do P3.',
          severidade: 'ERRO',
        });
      } else if (!CHAVES_REGRA_PERMITIDAS.has(k)) {
        erros.push({
          local: `${rp}.${k}`,
          motivo: `Propriedade desconhecida na regra: "${k}".`,
          severidade: 'ERRO',
        });
      }
    }

    const tipoRaw = itemRegra.tipoConversao;
    const obrigatorioRaw = itemRegra.obrigatorio;
    const destinoRaw =
      typeof itemRegra.caminhoDestino === 'string' ? itemRegra.caminhoDestino.trim() : '';
    const origemRaw =
      typeof itemRegra.caminhoOrigem === 'string' ? itemRegra.caminhoOrigem.trim() : '';
    const paramRaw = itemRegra.parametroConversao;

    destinosParaChecagem.push(destinoRaw);

    if (tipoRaw === 'TRANSFORMACAO' || tipoRaw === 'EXPRESSAO') {
      erros.push({
        local: `${rp}.tipoConversao`,
        motivo: 'fora do recorte do P3: use DIRETA, DOMINIO ou CONSTANTE',
        severidade: 'ERRO',
      });
    } else if (tipoRaw !== 'DIRETA' && tipoRaw !== 'DOMINIO' && tipoRaw !== 'CONSTANTE') {
      erros.push({
        local: `${rp}.tipoConversao`,
        motivo: 'obrigatório ([DIRETA, CONSTANTE, DOMINIO, TRANSFORMACAO, EXPRESSAO])',
        severidade: 'ERRO',
      });
    }

    if (typeof obrigatorioRaw !== 'boolean') {
      erros.push({
        local: `${rp}.obrigatorio`,
        motivo: 'obrigatório (true ou false)',
        severidade: 'ERRO',
      });
    }

    if (!destinoRaw) {
      erros.push({
        local: `${rp}.caminhoDestino`,
        motivo: 'obrigatório',
        severidade: 'ERRO',
      });
    }

    if (tipoRaw !== 'CONSTANTE') {
      if (!origemRaw) {
        erros.push({
          local: `${rp}.caminhoOrigem`,
          motivo: 'obrigatório (exceto em CONSTANTE)',
          severidade: 'ERRO',
        });
      } else {
        const resOrigem = analisarCaminhoOrigem(origemRaw);
        if (!resOrigem.ok) {
          erros.push({
            local: `${rp}.caminhoOrigem`,
            motivo: resOrigem.erro ?? 'Sintaxe de caminho não suportada.',
            severidade: 'ERRO',
          });
        }
      }
    }

    if (tipoRaw === 'DIRETA') {
      if (paramRaw !== null && paramRaw !== undefined && String(paramRaw).trim() !== '') {
        erros.push({
          local: `${rp}.parametroConversao`,
          motivo: 'deve ficar vazio em DIRETA',
          severidade: 'ERRO',
        });
      }
      regrasValidadas.push({
        caminhoOrigem: origemRaw,
        caminhoDestino: destinoRaw,
        tipoConversao: 'DIRETA',
        parametroConversao: null,
        obrigatorio: Boolean(obrigatorioRaw),
      });
    } else if (tipoRaw === 'DOMINIO') {
      const nomeDom = typeof paramRaw === 'string' ? paramRaw.trim() : '';
      if (!nomeDom) {
        erros.push({
          local: `${rp}.parametroConversao`,
          motivo: 'obrigatório em DOMINIO (nome do domínio)',
          severidade: 'ERRO',
        });
      } else if (!nomesDominios.has(nomeDom)) {
        erros.push({
          local: `${rp}.parametroConversao`,
          motivo: `domínio inexistente: ${nomeDom}`,
          severidade: 'ERRO',
        });
      }
      regrasValidadas.push({
        caminhoOrigem: origemRaw,
        caminhoDestino: destinoRaw,
        tipoConversao: 'DOMINIO',
        parametroConversao: nomeDom,
        obrigatorio: Boolean(obrigatorioRaw),
      });
    } else if (tipoRaw === 'CONSTANTE') {
      const valorConst = typeof paramRaw === 'string' ? paramRaw : '';
      if (!valorConst || valorConst.trim() === '') {
        erros.push({
          local: `${rp}.parametroConversao`,
          motivo: 'obrigatório em CONSTANTE',
          severidade: 'ERRO',
        });
      }
      regrasValidadas.push({
        caminhoDestino: destinoRaw,
        tipoConversao: 'CONSTANTE',
        parametroConversao: valorConst,
        obrigatorio: Boolean(obrigatorioRaw),
      });
    }
  });

  const errosDestino = validarDestinosDoSentido(destinosParaChecagem);
  for (const [idx, msg] of errosDestino.entries()) {
    const local = idx < 0 ? prefixoLista : `${prefixoLista}[${idx}].caminhoDestino`;
    const dest = idx >= 0 ? destinosParaChecagem[idx] : undefined;
    const analise = dest ? analisarCaminhoDestino(dest) : null;
    erros.push({
      local,
      motivo: analise && !analise.ok ? (analise.erro ?? msg) : msg,
      severidade: 'ERRO',
      campoDestino: dest,
    });
  }

  return regrasValidadas;
}
