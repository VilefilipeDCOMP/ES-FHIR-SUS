import { normalizarCaminhoFhirParaCatalogo } from '../data/fhirCatalog';
import type {
  DominioValores,
  MapeamentoConfig,
  RecursoFhir,
  RegraCampo,
  SentidoTipo,
} from '../types/domain';
import type {
  AlvoNavegacaoErro,
  CampoFhirSpec,
  ErroValidacao,
  LinhaTerminologiaUI,
} from '../types/ui';

export interface SegmentoCaminho {
  campo: string | null;
  indice: number | null;
}

const SEGMENTO_REGEX = /\.([A-Za-z_][A-Za-z0-9_-]*)|\[(0|[1-9][0-9]*)\]/g;

export function analisarCaminhoOrigem(caminho: string): {
  ok: boolean;
  segmentos: SegmentoCaminho[];
  erro?: string;
} {
  if (!caminho || !caminho.startsWith('$')) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada. O caminho de origem deve iniciar com "$" (ex.: $.codigoExame ou $.identifier[0].value).',
    };
  }
  if (caminho.includes('..') || caminho.includes('*') || caminho.includes('[?')) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada: filtros, curingas e busca recursiva não fazem parte do subconjunto do motor.',
    };
  }
  if (/\[0[0-9]+\]/.test(caminho) || /\[-[0-9]+\]/.test(caminho)) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada: os índices de array devem ser inteiros não negativos iniciando em 0 (sem zeros à esquerda).',
    };
  }
  return analisarSufixoCaminho(caminho.slice(1));
}

export function analisarCaminhoDestino(caminho: string): {
  ok: boolean;
  segmentos: SegmentoCaminho[];
  erro?: string;
} {
  if (!caminho || !caminho.trim()) {
    return {
      ok: false,
      segmentos: [],
      erro: 'O caminho de destino é obrigatório.',
    };
  }
  if (caminho.startsWith('$') || caminho.startsWith('.') || caminho.startsWith('[')) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada. No destino, use Dot Notation iniciando por uma propriedade (ex.: numeroPedido ou code.coding[0].code).',
    };
  }
  if (/\[0[0-9]+\]/.test(caminho) || /\[-[0-9]+\]/.test(caminho)) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada: os índices de array começam em zero.',
    };
  }
  const res = analisarSufixoCaminho(`.${caminho}`);
  if (!res.ok || res.segmentos.length === 0 || res.segmentos[0]?.indice !== null) {
    return {
      ok: false,
      segmentos: [],
      erro: res.erro ?? 'Sintaxe de caminho não suportada.',
    };
  }
  return res;
}

function analisarSufixoCaminho(sufixo: string): {
  ok: boolean;
  segmentos: SegmentoCaminho[];
  erro?: string;
} {
  const segmentos: SegmentoCaminho[] = [];
  const regex = new RegExp(SEGMENTO_REGEX.source, 'g');
  let posicao = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(sufixo)) !== null) {
    if (match.index !== posicao) {
      return {
        ok: false,
        segmentos: [],
        erro: 'Sintaxe de caminho não suportada.',
      };
    }
    if (match[1] !== undefined) {
      segmentos.push({ campo: match[1], indice: null });
    } else if (match[2] !== undefined) {
      const idx = Number.parseInt(match[2], 10);
      if (!Number.isSafeInteger(idx) || idx < 0) {
        return {
          ok: false,
          segmentos: [],
          erro: 'Sintaxe de caminho não suportada.',
        };
      }
      segmentos.push({ campo: null, indice: idx });
    }
    posicao = regex.lastIndex;
  }

  if (posicao !== sufixo.length) {
    return {
      ok: false,
      segmentos: [],
      erro: 'Sintaxe de caminho não suportada.',
    };
  }

  return { ok: true, segmentos };
}

interface NoSaidaValidacao {
  filhos: Map<string, { seg: SegmentoCaminho; no: NoSaidaValidacao }>;
  terminal: boolean;
}

function chaveSegmento(seg: SegmentoCaminho): string {
  return seg.indice !== null ? `[${seg.indice}]` : `.${seg.campo}`;
}

/**
 * replica exatamente a validação de conflitos e lacunas de SaidaJson.java e ValidacaoCaminhos.errosDestino:
 * - destinos duplicados no mesmo recurso e sentido
 * - conflito entre folha escalar e objeto/array intermediário (ex.: "dados" e "dados.codigo")
 * - conflito entre objeto e array no mesmo nó (ex.: "dados.codigo" e "dados[0]")
 * - lacunas em índices de array de destino (ex.: "identifier[1].value" sem o índice 0)
 */
export function validarDestinosDoSentido(destinos: string[]): Map<number, string> {
  const erros = new Map<number, string>();
  const raiz: NoSaidaValidacao = { filhos: new Map(), terminal: false };

  for (let i = 0; i < destinos.length; i++) {
    const dest = destinos[i];
    if (!dest || !dest.trim()) continue;

    const analise = analisarCaminhoDestino(dest);
    if (!analise.ok) {
      erros.set(i, analise.erro ?? 'Sintaxe de caminho não suportada.');
      continue;
    }

    let atual = raiz;
    let conflitoDetectado = false;

    for (const seg of analise.segmentos) {
      const primeiroFilho = atual.filhos.values().next().value as
        | { seg: SegmentoCaminho; no: NoSaidaValidacao }
        | undefined;
      const temFilhosDeOutroTipo =
        primeiroFilho !== undefined &&
        (primeiroFilho.seg.indice !== null) !== (seg.indice !== null);

      if (atual.terminal || temFilhosDeOutroTipo) {
        erros.set(i, 'O destino é duplicado ou incompatível com outra regra.');
        conflitoDetectado = true;
        break;
      }

      const chave = chaveSegmento(seg);
      let entrada = atual.filhos.get(chave);
      if (!entrada) {
        entrada = { seg, no: { filhos: new Map(), terminal: false } };
        atual.filhos.set(chave, entrada);
      }
      atual = entrada.no;
    }

    if (conflitoDetectado) continue;

    if (atual.terminal || atual.filhos.size > 0) {
      erros.set(i, 'O destino é duplicado ou incompatível com outra regra.');
      continue;
    }

    // uma regra opcional ainda reserva o destino para detectar colisões
    atual.terminal = true;
  }

  if (erros.size === 0) {
    const erroLacuna = verificarLacunasArray(raiz, '');
    if (erroLacuna) {
      erros.set(-1, erroLacuna);
    }
  }

  return erros;
}

function verificarLacunasArray(no: NoSaidaValidacao, caminhoAtual: string): string | null {
  if (no.terminal || no.filhos.size === 0) return null;

  const entradas = [...no.filhos.values()];
  const ehArray = entradas[0]?.seg.indice !== null;

  if (ehArray) {
    entradas.sort((a, b) => (a.seg.indice ?? 0) - (b.seg.indice ?? 0));
    let tamanhoEsperado = 0;
    for (const entrada of entradas) {
      const idx = entrada.seg.indice ?? 0;
      const destino = `${caminhoAtual}[${idx}]`;
      if (idx !== tamanhoEsperado) {
        return `O índice deixaria uma lacuna no array de destino. (${destino})`;
      }
      tamanhoEsperado++;
      const erroFilho = verificarLacunasArray(entrada.no, destino);
      if (erroFilho) return erroFilho;
    }
    return null;
  }

  for (const entrada of entradas) {
    const campo = entrada.seg.campo ?? '';
    const destino = caminhoAtual ? `${caminhoAtual}.${campo}` : campo;
    const erroFilho = verificarLacunasArray(entrada.no, destino);
    if (erroFilho) return erroFilho;
  }

  return null;
}

/** garante que a regra serializada envie apenas os campos permitidos pelo contrato CT-03 */
export function serializarRegraParaContrato(regra: RegraCampo): RegraCampo {
  if (regra.tipoConversao === 'CONSTANTE') {
    return {
      caminhoDestino: regra.caminhoDestino.trim(),
      tipoConversao: 'CONSTANTE',
      parametroConversao: String(regra.parametroConversao),
      obrigatorio: Boolean(regra.obrigatorio),
    };
  }
  if (regra.tipoConversao === 'DIRETA') {
    return {
      caminhoOrigem: regra.caminhoOrigem.trim(),
      caminhoDestino: regra.caminhoDestino.trim(),
      tipoConversao: 'DIRETA',
      parametroConversao: null,
      obrigatorio: Boolean(regra.obrigatorio),
    };
  }
  return {
    caminhoOrigem: regra.caminhoOrigem.trim(),
    caminhoDestino: regra.caminhoDestino.trim(),
    tipoConversao: 'DOMINIO',
    parametroConversao: regra.parametroConversao.trim(),
    obrigatorio: Boolean(regra.obrigatorio),
  };
}

export function serializarConfigParaContrato(config: MapeamentoConfig): MapeamentoConfig {
  return {
    mapeamentos: config.mapeamentos.map((m) => ({
      recurso: m.recurso,
      toLegado: m.toLegado.map(serializarRegraParaContrato),
      toFHIR: m.toFHIR.map(serializarRegraParaContrato),
    })),
    dominios: config.dominios.map((d) => ({
      nome: d.nome.trim(),
      equivalencia: { ...d.equivalencia },
    })),
  };
}

export interface ReferenciaDominio {
  recurso: RecursoFhir;
  sentido: SentidoTipo;
  lista: 'toLegado' | 'toFHIR';
  indiceRegra: number;
  caminhoDestino: string;
  local: string;
}

export function listarReferenciasDominio(
  config: MapeamentoConfig,
  nomeDominio: string
): ReferenciaDominio[] {
  const refs: ReferenciaDominio[] = [];
  config.mapeamentos.forEach((map, idxMap) => {
    map.toLegado.forEach((regra, idxRegra) => {
      if (regra.tipoConversao === 'DOMINIO' && regra.parametroConversao === nomeDominio) {
        refs.push({
          recurso: map.recurso,
          sentido: 'FHIR_PARA_LEGADO',
          lista: 'toLegado',
          indiceRegra: idxRegra,
          caminhoDestino: regra.caminhoDestino,
          local: `mapeamentos[${idxMap}].toLegado[${idxRegra}].parametroConversao`,
        });
      }
    });
    map.toFHIR.forEach((regra, idxRegra) => {
      if (regra.tipoConversao === 'DOMINIO' && regra.parametroConversao === nomeDominio) {
        refs.push({
          recurso: map.recurso,
          sentido: 'LEGADO_PARA_FHIR',
          lista: 'toFHIR',
          indiceRegra: idxRegra,
          caminhoDestino: regra.caminhoDestino,
          local: `mapeamentos[${idxMap}].toFHIR[${idxRegra}].parametroConversao`,
        });
      }
    });
  });
  return refs;
}

export function renomearDominioNaConfig(
  config: MapeamentoConfig,
  nomeAntigo: string,
  nomeNovo: string
): { configAtualizada: MapeamentoConfig; regrasAtualizadas: number } {
  const next = structuredClone(config);
  const novoTrim = nomeNovo.trim();
  let regrasAtualizadas = 0;

  for (const dom of next.dominios) {
    if (dom.nome === nomeAntigo) {
      dom.nome = novoTrim;
    }
  }

  for (const map of next.mapeamentos) {
    for (const r of map.toLegado) {
      if (r.tipoConversao === 'DOMINIO' && r.parametroConversao === nomeAntigo) {
        r.parametroConversao = novoTrim;
        regrasAtualizadas++;
      }
    }
    for (const r of map.toFHIR) {
      if (r.tipoConversao === 'DOMINIO' && r.parametroConversao === nomeAntigo) {
        r.parametroConversao = novoTrim;
        regrasAtualizadas++;
      }
    }
  }

  return { configAtualizada: next, regrasAtualizadas };
}

export function interpretarLocalErro(
  local: string,
  config: MapeamentoConfig
): AlvoNavegacaoErro | undefined {
  const matchRegra = /^mapeamentos\[(\d+)\]\.(toLegado|toFHIR)(?:\[(\d+)\])?/.exec(local);
  if (matchRegra) {
    const idxMap = Number.parseInt(matchRegra[1]!, 10);
    const lista = matchRegra[2] as 'toLegado' | 'toFHIR';
    const idxRegra = matchRegra[3] !== undefined ? Number.parseInt(matchRegra[3], 10) : undefined;
    const map = config.mapeamentos[idxMap];
    if (!map) return undefined;
    const sentido: SentidoTipo = lista === 'toLegado' ? 'FHIR_PARA_LEGADO' : 'LEGADO_PARA_FHIR';
    const regras = lista === 'toLegado' ? map.toLegado : map.toFHIR;
    const regra = idxRegra !== undefined ? regras[idxRegra] : undefined;
    return {
      recurso: map.recurso,
      sentido,
      indiceRegra: idxRegra,
      caminhoDestino: regra?.caminhoDestino,
      nomeDominio:
        regra?.tipoConversao === 'DOMINIO' ? regra.parametroConversao : undefined,
    };
  }

  const matchMap = /^mapeamentos\[(\d+)\]/.exec(local);
  if (matchMap) {
    const idxMap = Number.parseInt(matchMap[1]!, 10);
    const map = config.mapeamentos[idxMap];
    if (map) {
      const sentido: SentidoTipo =
        map.toFHIR.length > 0 || map.toLegado.length === 0
          ? 'LEGADO_PARA_FHIR'
          : 'FHIR_PARA_LEGADO';
      return { recurso: map.recurso, sentido };
    }
  }

  const matchDom = /^dominios\[(\d+)\]/.exec(local);
  if (matchDom) {
    const idxDom = Number.parseInt(matchDom[1]!, 10);
    const dom = config.dominios[idxDom];
    if (dom) {
      const refs = listarReferenciasDominio(config, dom.nome);
      const primeiraRef = refs[0];
      return {
        nomeDominio: dom.nome,
        recurso: primeiraRef?.recurso,
        sentido: primeiraRef?.sentido,
        indiceRegra: primeiraRef?.indiceRegra,
        caminhoDestino: primeiraRef?.caminhoDestino,
      };
    }
  }

  return undefined;
}

export interface RequisitoObrigatorio {
  id: string;
  descricaoLocal: string;
  origem: 'FHIR_R4' | 'REGRA_PROJETO';
  /** qualquer um destes caminhos canônicos do catálogo satisfaz a exigência */
  caminhosAceitos: string[];
}

export interface ResumoObrigatorios {
  mapeados: number;
  total: number;
  pendentes: RequisitoObrigatorio[];
  errosUx: ErroValidacao[];
  avisosUx: ErroValidacao[];
}

export function obterRegrasDoSentido(
  config: MapeamentoConfig,
  recurso: RecursoFhir,
  sentido: SentidoTipo
): RegraCampo[] {
  const map = config.mapeamentos.find((m) => m.recurso === recurso);
  if (!map) return [];
  return sentido === 'LEGADO_PARA_FHIR' ? map.toFHIR : map.toLegado;
}

export function extrairRequisitosObrigatorios(
  especificacaoFhir: CampoFhirSpec[]
): RequisitoObrigatorio[] {
  const requisitos: RequisitoObrigatorio[] = [];
  const grupos = new Map<string, string[]>();

  for (const campo of especificacaoFhir) {
    if (campo.origemObrigatoriedade === 'FHIR_R4') {
      requisitos.push({
        id: campo.caminho,
        descricaoLocal: campo.caminho,
        origem: 'FHIR_R4',
        caminhosAceitos: [campo.caminho],
      });
    } else if (campo.origemObrigatoriedade === 'REGRA_PROJETO') {
      requisitos.push({
        id: campo.caminho,
        descricaoLocal: campo.caminho,
        origem: 'REGRA_PROJETO',
        caminhosAceitos: [campo.caminho],
      });
    }

    if (campo.grupoObrigatorioFhir) {
      const lista = grupos.get(campo.grupoObrigatorioFhir) ?? [];
      lista.push(campo.caminho);
      grupos.set(campo.grupoObrigatorioFhir, lista);
    }
  }

  for (const [nomeGrupo, caminhos] of grupos.entries()) {
    requisitos.push({
      id: `GRUPO:${nomeGrupo}`,
      descricaoLocal: `${nomeGrupo} (${caminhos.join(' ou ')})`,
      origem: 'FHIR_R4',
      caminhosAceitos: caminhos,
    });
  }

  return requisitos;
}

export function avaliarPendenciasRecurso(
  config: MapeamentoConfig,
  recurso: RecursoFhir,
  sentido: SentidoTipo,
  especificacaoFhir: CampoFhirSpec[]
): ResumoObrigatorios {
  const idxMap = config.mapeamentos.findIndex((m) => m.recurso === recurso);
  const listaNome = sentido === 'LEGADO_PARA_FHIR' ? 'toFHIR' : 'toLegado';
  const prefixoLocal = idxMap >= 0 ? `mapeamentos[${idxMap}].${listaNome}` : `${recurso}.${listaNome}`;
  const regras = obterRegrasDoSentido(config, recurso, sentido);
  const requisitos = extrairRequisitosObrigatorios(especificacaoFhir);
  const pendentes: RequisitoObrigatorio[] = [];
  const errosUx: ErroValidacao[] = [];
  const avisosUx: ErroValidacao[] = [];

  for (const req of requisitos) {
    const satisfeito = req.caminhosAceitos.some((caminhoCatalogo) =>
      regras.some((r) => {
        if (sentido === 'LEGADO_PARA_FHIR') {
          const destCan = normalizarCaminhoFhirParaCatalogo(recurso, r.caminhoDestino);
          if (destCan !== caminhoCatalogo) return false;
          if (r.tipoConversao === 'CONSTANTE') {
            return r.parametroConversao.trim() !== '';
          }
          return r.caminhoOrigem.trim() !== '';
        }
        if (r.tipoConversao === 'CONSTANTE') return false;
        const origCan = normalizarCaminhoFhirParaCatalogo(recurso, r.caminhoOrigem);
        return origCan === caminhoCatalogo && r.caminhoDestino.trim() !== '';
      })
    );

    if (!satisfeito) {
      pendentes.push(req);
      // se o sentido estiver vazio ([]), é permitido pelo motor (retorna aviso de cobertura visual)
      if (regras.length === 0) {
        avisosUx.push({
          local: `${prefixoLocal} (${req.descricaoLocal})`,
          motivo: 'Nenhuma regra configurada neste sentido.',
          severidade: 'AVISO',
          campoDestino: req.caminhosAceitos[0],
          alvo: { recurso, sentido },
        });
      } else if (sentido === 'LEGADO_PARA_FHIR') {
        avisosUx.push({
          local: `${prefixoLocal} (${req.descricaoLocal})`,
          motivo:
            req.origem === 'FHIR_R4'
              ? 'Elemento obrigatório pela especificação base FHIR R4 ainda não mapeado neste sentido.'
              : 'Campo recomendado pelo fluxo laboratorial do projeto ainda não mapeado.',
          severidade: 'AVISO',
          campoDestino: req.caminhosAceitos[0],
          alvo: { recurso, sentido },
        });
      }
    }
  }

  const nomesDominios = new Set(config.dominios.map((d) => d.nome));

  regras.forEach((regra, idx) => {
    const localRegra = `${prefixoLocal}[${idx}]`;

    if (regra.tipoConversao !== 'CONSTANTE') {
      const origRes = analisarCaminhoOrigem(regra.caminhoOrigem);
      if (!origRes.ok) {
        errosUx.push({
          local: `${localRegra}.caminhoOrigem`,
          motivo: origRes.erro ?? 'Sintaxe de caminho não suportada.',
          severidade: 'ERRO',
          campoDestino: regra.caminhoDestino,
          alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
        });
      }
    }

    if (regra.tipoConversao === 'DIRETA') {
      const paramDireta = (regra as { parametroConversao: unknown }).parametroConversao;
      if (paramDireta !== null && paramDireta !== undefined && String(paramDireta).trim() !== '') {
        errosUx.push({
          local: `${localRegra}.parametroConversao`,
          motivo: 'deve ficar vazio (null) em DIRETA.',
          severidade: 'ERRO',
          campoDestino: regra.caminhoDestino,
          alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
        });
      }
    } else if (regra.tipoConversao === 'DOMINIO') {
      const nomeDom = regra.parametroConversao.trim();
      const dom = config.dominios.find((d) => d.nome === nomeDom);
      if (!nomeDom) {
        errosUx.push({
          local: `${localRegra}.parametroConversao`,
          motivo: 'obrigatório em DOMINIO (nome do domínio).',
          severidade: 'ERRO',
          campoDestino: regra.caminhoDestino,
          alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
        });
      } else if (!nomesDominios.has(nomeDom) || !dom || Object.keys(dom.equivalencia).length === 0) {
        errosUx.push({
          local: `${localRegra}.parametroConversao`,
          motivo: `domínio inexistente ou vazio: ${nomeDom}`,
          severidade: 'ERRO',
          campoDestino: regra.caminhoDestino,
          alvo: {
            recurso,
            sentido,
            indiceRegra: idx,
            caminhoDestino: regra.caminhoDestino,
            nomeDominio: nomeDom,
          },
        });
      }
    } else if (regra.tipoConversao === 'CONSTANTE') {
      if (!regra.parametroConversao || regra.parametroConversao.trim() === '') {
        errosUx.push({
          local: `${localRegra}.parametroConversao`,
          motivo: 'obrigatório em CONSTANTE.',
          severidade: 'ERRO',
          campoDestino: regra.caminhoDestino,
          alvo: { recurso, sentido, indiceRegra: idx, caminhoDestino: regra.caminhoDestino },
        });
      }
    }
  });

  const errosDestinos = validarDestinosDoSentido(regras.map((r) => r.caminhoDestino));
  for (const [idx, msg] of errosDestinos.entries()) {
    const localErro = idx < 0 ? prefixoLocal : `${prefixoLocal}[${idx}].caminhoDestino`;
    const regra = idx >= 0 ? regras[idx] : undefined;
    errosUx.push({
      local: localErro,
      motivo: msg,
      severidade: 'ERRO',
      campoDestino: regra?.caminhoDestino,
      alvo: {
        recurso,
        sentido,
        indiceRegra: idx >= 0 ? idx : undefined,
        caminhoDestino: regra?.caminhoDestino,
      },
    });
  }

  return {
    mapeados: requisitos.length - pendentes.length,
    total: requisitos.length,
    pendentes,
    errosUx,
    avisosUx,
  };
}

export function detectarChavesDuplicadas(linhas: LinhaTerminologiaUI[]): string[] {
  const vistas = new Set<string>();
  const duplicadas = new Set<string>();

  for (const l of linhas) {
    const chave = l.valorLegado.trim();
    if (!chave) continue;
    if (vistas.has(chave)) duplicadas.add(chave);
    vistas.add(chave);
  }

  return [...duplicadas];
}

export function criarTabelaInversa(tabelaOriginal: DominioValores): {
  tabelaInversa: DominioValores;
  colisoes: string[];
} {
  const equivalenciaInversa: Record<string, string> = {};
  const colisoes = new Set<string>();

  for (const [origem, destino] of Object.entries(tabelaOriginal.equivalencia)) {
    if (
      Object.prototype.hasOwnProperty.call(equivalenciaInversa, destino) &&
      equivalenciaInversa[destino] !== origem
    ) {
      colisoes.add(destino);
    }
    equivalenciaInversa[destino] = origem;
  }

  return {
    tabelaInversa: {
      nome: `${tabelaOriginal.nome}_inverso`,
      equivalencia: equivalenciaInversa,
    },
    colisoes: [...colisoes],
  };
}
