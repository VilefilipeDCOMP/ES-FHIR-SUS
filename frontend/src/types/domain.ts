/** tipos de conversão suportados no editor do P3 (CT-03 / MotorConversao) */
export type TipoConversao = 'DIRETA' | 'DOMINIO' | 'CONSTANTE';

/** tipos previstos no enum do motor mas fora do recorte do P3 */
export type TipoConversaoIncompativel = 'TRANSFORMACAO' | 'EXPRESSAO';

export type SentidoTipo = 'LEGADO_PARA_FHIR' | 'FHIR_PARA_LEGADO';

/**
 * lista oficial dos 8 recursos definidos no P1 (D03), P2 e ConfigValidator.java.
 * Encounter não faz parte do projeto; o oitavo recurso é PractitionerRole.
 */
export const RECURSOS_OFICIAIS_FHIR = [
  'ServiceRequest',
  'Patient',
  'Practitioner',
  'PractitionerRole',
  'Organization',
  'DiagnosticReport',
  'Observation',
  'Specimen',
] as const;

export type RecursoFhir = (typeof RECURSOS_OFICIAIS_FHIR)[number];

/**
 * recorte demonstrável no P3 alinhado ao fluxo laboratorial e fixtures do motor.
 */
export const RECURSOS_ATIVOS_P3: readonly RecursoFhir[] = [
  'ServiceRequest',
  'Observation',
  'DiagnosticReport',
  'Patient',
] as const;

export type RegraBase = {
  caminhoDestino: string;
  obrigatorio: boolean;
};

export type RegraDireta = RegraBase & {
  tipoConversao: 'DIRETA';
  caminhoOrigem: string;
  parametroConversao: null;
};

export type RegraDominio = RegraBase & {
  tipoConversao: 'DOMINIO';
  caminhoOrigem: string;
  parametroConversao: string;
};

export type RegraConstante = RegraBase & {
  tipoConversao: 'CONSTANTE';
  caminhoOrigem?: undefined;
  parametroConversao: string;
};

export type RegraCampo = RegraDireta | RegraDominio | RegraConstante;

export interface Mapeamento {
  recurso: RecursoFhir;
  /** regras FHIR -> Legado (SentidoConversao.TO_LEGADO) */
  toLegado: RegraCampo[];
  /** regras Legado -> FHIR (SentidoConversao.TO_FHIR) */
  toFHIR: RegraCampo[];
}

export interface DominioValores {
  nome: string;
  /** Map<codigoOrigem, codigoDestino> */
  equivalencia: Record<string, string>;
}

/** alias mantido por compatibilidade interna */
export type Tabela = DominioValores;

export interface MapeamentoConfig {
  mapeamentos: Mapeamento[];
  dominios: DominioValores[];
}
