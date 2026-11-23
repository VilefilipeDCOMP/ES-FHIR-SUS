import yaml from 'js-yaml';
import type { MapeamentoConfig, RecursoFhir } from '../types/domain';
import type { CampoFhirSpec, CampoLegado, ErroValidacao, TipoDadoCampo } from '../types/ui';
import { validarConfiguracaoDesconhecida } from './configSchema';

export const MAX_JSON_BYTES = 5 * 1024 * 1024; // 5 MB exatos para JSON de exemplo (UC-02 A2)
export const MAX_CONFIG_CHARS = 1_000_000; // 1.000.000 de caracteres conforme ConfigParser.java

export interface ResultadoIngestao {
  sucesso: boolean;
  campos: CampoLegado[];
  objetoBruto?: Record<string, unknown>;
  erros: ErroValidacao[];
  avisos: ErroValidacao[];
}

export interface ResultadoImportacaoConfig {
  sucesso: boolean;
  config?: MapeamentoConfig;
  erros: ErroValidacao[];
  avisos: ErroValidacao[];
}

function inferirTipo(valor: unknown): TipoDadoCampo {
  if (valor === null) return 'null';
  if (Array.isArray(valor)) return 'array';
  return typeof valor as TipoDadoCampo;
}

/**
 * extrai caminhos folha de um JSON de exemplo usando a sintaxe suportada pelo motor:
 * - caminho: Dot Notation com índice [0] quando houver arrays de objetos (ex.: identifier[0].value, numeroPedido)
 * - caminhoJsonPath: subconjunto JSONPath iniciando com "$." (ex.: $.identifier[0].value, $.numeroPedido)
 */
export function extrairCamposSimples(
  obj: Record<string, unknown>,
  prefixo = '',
  avisos: ErroValidacao[] = []
): CampoLegado[] {
  const campos: CampoLegado[] = [];

  for (const [chave, valor] of Object.entries(obj)) {
    const caminho = prefixo ? `${prefixo}.${chave}` : chave;
    const tipo = inferirTipo(valor);

    if (tipo === 'object' && valor !== null) {
      campos.push(
        ...extrairCamposSimples(valor as Record<string, unknown>, caminho, avisos)
      );
    } else if (tipo === 'array') {
      const lista = valor as unknown[];
      if (lista.length > 0 && typeof lista[0] === 'object' && lista[0] !== null) {
        const caminhoIndexado = `${caminho}[0]`;
        campos.push(
          ...extrairCamposSimples(lista[0] as Record<string, unknown>, caminhoIndexado, avisos)
        );
      } else if (lista.length > 0) {
        const caminhoIndexado = `${caminho}[0]`;
        campos.push({
          caminho: caminhoIndexado,
          caminhoJsonPath: `$.${caminhoIndexado}`,
          valorExemplo: lista[0] === null ? 'null' : String(lista[0]),
          tipo: inferirTipo(lista[0]),
        });
      } else {
        avisos.push({
          local: caminho,
          motivo: 'Array vazio no JSON de exemplo: nenhum índice [0] pôde ser inspecionado.',
          severidade: 'AVISO',
        });
      }
    } else {
      campos.push({
        caminho,
        caminhoJsonPath: `$.${caminho}`,
        valorExemplo: valor === null ? 'null' : String(valor),
        tipo,
      });
    }
  }

  return campos;
}

export function validarEProcessarJsonTexto(
  texto: string,
  origemLabel: string
): ResultadoIngestao {
  const tamanhoBytes = new Blob([texto]).size;
  if (tamanhoBytes > MAX_JSON_BYTES) {
    return {
      sucesso: false,
      campos: [],
      erros: [
        {
          local: origemLabel,
          motivo: `O conteúdo excede o limite máximo de 5 MB (${(
            tamanhoBytes /
            (1024 * 1024)
          ).toFixed(2)} MB).`,
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  if (!texto.trim()) {
    return {
      sucesso: false,
      campos: [],
      erros: [{ local: origemLabel, motivo: 'O JSON de origem está vazio.', severidade: 'ERRO' }],
      avisos: [],
    };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(texto);
  } catch (err) {
    return {
      sucesso: false,
      campos: [],
      erros: [
        {
          local: origemLabel,
          motivo: `Sintaxe JSON inválida: ${(err as Error).message}`,
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return {
      sucesso: false,
      campos: [],
      erros: [
        {
          local: `${origemLabel} (raiz)`,
          motivo: 'A raiz do JSON de exemplo deve ser um objeto ({ ... }).',
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  const avisos: ErroValidacao[] = [];
  const campos = extrairCamposSimples(parsed as Record<string, unknown>, '', avisos);

  if (campos.length === 0) {
    return {
      sucesso: false,
      campos: [],
      erros: [
        {
          local: origemLabel,
          motivo: 'Nenhum campo mapeável foi encontrado no JSON informado.',
          severidade: 'ERRO',
        },
      ],
      avisos,
    };
  }

  return {
    sucesso: true,
    campos,
    objetoBruto: parsed as Record<string, unknown>,
    erros: [],
    avisos,
  };
}

export function lerArquivoJson(file: File): Promise<ResultadoIngestao> {
  if (file.size > MAX_JSON_BYTES) {
    return Promise.resolve({
      sucesso: false,
      campos: [],
      erros: [
        {
          local: file.name,
          motivo: `Arquivo acima do limite máximo de 5 MB (${(
            file.size /
            (1024 * 1024)
          ).toFixed(2)} MB).`,
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    });
  }

  return new Promise((resolve) => {
    const reader = new FileReader();

    reader.onerror = () => {
      resolve({
        sucesso: false,
        campos: [],
        erros: [
          {
            local: file.name,
            motivo:
              'Não foi possível ler o arquivo selecionado. Verifique permissões ou se o arquivo está corrompido.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      });
    };

    reader.onload = () => {
      const conteudo = typeof reader.result === 'string' ? reader.result : '';
      resolve(validarEProcessarJsonTexto(conteudo, file.name));
    };

    reader.readAsText(file, 'utf-8');
  });
}

/**
 * UC-04 (RF009, RF011, RF012, RF013):
 * importa arquivo de configuração MapeamentoConfig em JSON ou YAML (.json, .yaml, .yml)
 * e executa validação estrutural e semântica completa em tempo de execução.
 */
export async function importarArquivoConfig(
  file: File,
  catalogo: Record<RecursoFhir, CampoFhirSpec[]>
): Promise<ResultadoImportacaoConfig> {
  if (file.size > MAX_JSON_BYTES) {
    return {
      sucesso: false,
      erros: [
        {
          local: file.name,
          motivo: 'Arquivo de configuração excede o limite de 5 MB.',
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  let texto = '';
  try {
    if (typeof file.text === 'function') {
      texto = await file.text();
    } else {
      texto = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
        reader.onerror = reject;
        reader.readAsText(file, 'utf-8');
      });
    }
  } catch {
    return {
      sucesso: false,
      erros: [
        {
          local: file.name,
          motivo: 'Não foi possível ler o arquivo de configuração.',
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  if (texto.length > MAX_CONFIG_CHARS) {
    return {
      sucesso: false,
      erros: [
        {
          local: file.name,
          motivo: `Conteúdo acima do limite de ${MAX_CONFIG_CHARS} caracteres (ConfigParser).`,
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  const ehYaml = /\.ya?ml$/i.test(file.name);
  let parsed: unknown;

  try {
    parsed = ehYaml
      ? yaml.load(texto, { schema: yaml.JSON_SCHEMA })
      : JSON.parse(texto);
  } catch (err) {
    return {
      sucesso: false,
      erros: [
        {
          local: file.name,
          motivo: `Sintaxe ${ehYaml ? 'YAML' : 'JSON'} inválida (RF011): ${(err as Error).message}`,
          severidade: 'ERRO',
        },
      ],
      avisos: [],
    };
  }

  const validacao = validarConfiguracaoDesconhecida(parsed, catalogo);
  if (!validacao.ok || !validacao.config) {
    return {
      sucesso: false,
      erros: validacao.erros,
      avisos: validacao.avisos,
    };
  }

  return {
    sucesso: true,
    config: validacao.config,
    erros: [],
    avisos: validacao.avisos,
  };
}
