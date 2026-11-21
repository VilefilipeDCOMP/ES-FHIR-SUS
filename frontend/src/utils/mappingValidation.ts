import type {
  MapeamentoConfig,
  RecursoFhir,
  RegraCampo,
  SentidoTipo,
  Tabela,
} from '../types/domain';
import type { CampoFhirSpec, ErroValidacao, LinhaTerminologiaUI } from '../types/ui';

export interface RequisitoObrigatorio {
  id: string;
  descricaoLocal: string;
  origem: 'FHIR_R4' | 'REGRA_PROJETO';
  /** qualquer um destes caminhos satisfaz a exigência */
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
  const s = map?.sentidos.find((item) => item.tipo === sentido);
  return s?.regras ?? [];
}

/**
 * agrupa exigências obrigatórias sem confundir elemento complexo 1..1
 * com obrigatoriedade isolada de um único subcampo.
 */
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
  const regras = obterRegrasDoSentido(config, recurso, sentido);
  const requisitos = extrairRequisitosObrigatorios(especificacaoFhir);
  const pendentes: RequisitoObrigatorio[] = [];
  const errosUx: ErroValidacao[] = [];
  const avisosUx: ErroValidacao[] = [];

  for (const req of requisitos) {
    const satisfeito = req.caminhosAceitos.some((caminhoFhir) =>
      regras.some((r) =>
        sentido === 'LEGADO_PARA_FHIR'
          ? r.caminhoDestino === caminhoFhir && r.caminhoOrigem.trim() !== ''
          : r.caminhoOrigem === caminhoFhir && r.caminhoDestino.trim() !== ''
      )
    );

    if (!satisfeito) {
      pendentes.push(req);
      errosUx.push({
        local: `${recurso} > ${sentido} > ${req.descricaoLocal}`,
        motivo:
          req.origem === 'FHIR_R4'
            ? 'Elemento obrigatório pela especificação base FHIR R4 sem mapeamento.'
            : 'Campo obrigatório por regra do projeto sem mapeamento.',
        severidade: 'ERRO',
        campoDestino: req.caminhosAceitos[0],
      });
    }
  }

  for (const regra of regras) {
    const campoFhir =
      sentido === 'LEGADO_PARA_FHIR' ? regra.caminhoDestino : regra.caminhoOrigem;

    if (!campoFhir.startsWith(`${recurso}.`)) {
      errosUx.push({
        local: `${recurso} > ${sentido} > ${campoFhir}`,
        motivo: `O caminho FHIR deve iniciar com o prefixo "${recurso}.".`,
        severidade: 'ERRO',
      });
    }

    if (regra.regra.startsWith('TABELA:')) {
      const nomeTabela = regra.regra.slice('TABELA:'.length);
      const tabela = config.tabelas.find((t) => t.nome === nomeTabela);
      if (!tabela || Object.keys(tabela.equivalencia).length === 0) {
        errosUx.push({
          local: `${recurso} > ${sentido} > ${campoFhir}`,
          motivo: `Tabela de códigos "${nomeTabela}" inexistente ou vazia.`,
          severidade: 'ERRO',
        });
      }
    } else if (regra.regra !== 'COPIAR') {
      errosUx.push({
        local: `${recurso} > ${sentido} > ${campoFhir}`,
        motivo: `Regra "${regra.regra}" não suportada pelo motor (permitido apenas COPIAR ou TABELA).`,
        severidade: 'ERRO',
      });
    }
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

export function criarTabelaInversa(tabelaOriginal: Tabela): {
  tabelaInversa: Tabela;
  colisoes: string[];
} {
  const equivalenciaInversa: Record<string, string> = {};
  const colisoes = new Set<string>();

  for (const [origem, destino] of Object.entries(tabelaOriginal.equivalencia)) {
    if (equivalenciaInversa[destino] && equivalenciaInversa[destino] !== origem) {
      colisoes.add(destino);
    }
    equivalenciaInversa[destino] = origem;
  }

  return {
    tabelaInversa: {
      nome: `${tabelaOriginal.nome}_INV`,
      equivalencia: equivalenciaInversa,
    },
    colisoes: [...colisoes],
  };
}
