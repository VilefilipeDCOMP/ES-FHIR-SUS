import type { RecursoFhir, SentidoTipo } from './domain';

export type TipoDadoCampo = 'string' | 'number' | 'boolean' | 'object' | 'array' | 'null';

/**
 * - FHIR_R4: folha primitiva com cardinalidade mínima 1..1 na especificação HL7 FHIR R4.
 * - REGRA_PROJETO: opcional no FHIR R4 base, mas exigido pelo fluxo laboratorial do projeto.
 * - OPCIONAL: facultativo (pode ainda compor um grupoObrigatorioFhir de elemento complexo 1..1).
 */
export type OrigemObrigatoriedade = 'FHIR_R4' | 'REGRA_PROJETO' | 'OPCIONAL';

export type ContextoTesteKey = `${RecursoFhir}:${SentidoTipo}`;

export interface CampoLegado {
  /** caminho em dot notation (ex.: numeroPedido, codigoExame, situacao, resultado) */
  caminho: string;
  /** caminho JSONPath correspondente para leitura na origem (ex.: $.numeroPedido) */
  caminhoJsonPath?: string;
  valorExemplo: string;
  tipo: TipoDadoCampo;
}

export interface CampoFhirSpec {
  /** caminho relativo ao recurso em Dot Notation com índices (ex.: identifier[0].value, code.coding[0].code) */
  caminho: string;
  descricao: string;
  tipo: string;
  origemObrigatoriedade: OrigemObrigatoriedade;
  /**
   * identifica um elemento pai complexo 1..1 na especificação FHIR R4
   * (ex.: "Observation.code" é satisfeito por "code.coding[0].code" OU "code.text").
   */
  grupoObrigatorioFhir?: string;
  /** indica se o caminho envolve uma coleção (0..*) no JSON FHIR R4 */
  colecaoFhir?: boolean;
}

export interface LinhaTerminologiaUI {
  id: string;
  valorLegado: string;
  codigoFhir: string;
  /** auxílio visual apenas na UI; não serializado em DominioValores.equivalencia */
  rotulo: string;
}

/** localização estruturada para permitir navegação direta ao erro em outra aba/recurso */
export interface AlvoNavegacaoErro {
  recurso?: RecursoFhir;
  sentido?: SentidoTipo;
  indiceRegra?: number;
  caminhoDestino?: string;
  nomeDominio?: string;
}

/** RF013: cada erro informa explicitamente local e motivo */
export interface ErroValidacao {
  local: string;
  motivo: string;
  severidade: 'ERRO' | 'AVISO';
  campoDestino?: string;
  alvo?: AlvoNavegacaoErro;
}

export type EstadoConexaoApi = 'SINCRONIZADO' | 'SEM_CONFIG_ATIVA' | 'ERRO_CONSULTA';

export interface ResultadoOperacao {
  acao: 'TESTAR' | 'VALIDAR' | 'SALVAR' | 'IMPORTAR';
  sucesso: boolean;
  timestamp: string;
  mensagemResumo: string;
  erros: ErroValidacao[];
  avisos: ErroValidacao[];
  payloadSaida?: Record<string, unknown>;
}

export type ResultadoAcaoLocal = { ok: true } | { ok: false; erro: string };
