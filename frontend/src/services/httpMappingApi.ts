import type { MapeamentoConfig } from '../types/domain';
import type { ErroValidacao } from '../types/ui';
import {
  interpretarLocalErro,
  serializarConfigParaContrato,
} from '../utils/mappingValidation';
import { SemConfigAtivaError, type MappingGateway } from './mappingGateway';

/**
 * cliente HTTP alinhado ao ConfigController.java (/api/config, /api/config/ativa, /api/config/validar)
 * e às regras do guia CT-04:
 * - trata 404 SEM_CONFIG_ATIVA como ausência de configuração (distinto de falha de consulta/rede)
 * - trata 204 No Content no salvamento como sucesso sem chamar response.json()
 * - extrai detalhes estruturados [{ caminho, mensagem }] do ConfigInvalidaException (HTTP 422)
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/config';

function ehObjetoPlano(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

export async function safeParseJson<T>(res: Response): Promise<T | null> {
  if (res.status === 204) return null;
  const raw = await res.text();
  if (!raw.trim()) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

async function extrairErrosHttp(
  res: Response,
  configContexto?: MapeamentoConfig
): Promise<ErroValidacao[]> {
  const body = await safeParseJson<Record<string, unknown>>(res);

  if (body && Array.isArray(body.detalhes) && body.detalhes.length > 0) {
    return (body.detalhes as Array<Record<string, unknown>>).map((item) => {
      const local = typeof item.caminho === 'string' ? item.caminho : `API (HTTP ${res.status})`;
      const motivo =
        typeof item.mensagem === 'string'
          ? item.mensagem
          : 'Erro de validação apontado pelo servidor.';
      return {
        local,
        motivo,
        severidade: 'ERRO',
        alvo: configContexto ? interpretarLocalErro(local, configContexto) : undefined,
      };
    });
  }

  if (body && Array.isArray(body.erros) && body.erros.length > 0) {
    return (body.erros as ErroValidacao[]).map((e) => ({
      ...e,
      alvo:
        e.alvo ??
        (configContexto && e.local ? interpretarLocalErro(e.local, configContexto) : undefined),
    }));
  }

  const msg =
    typeof body?.mensagem === 'string'
      ? body.mensagem
      : typeof body?.message === 'string'
      ? body.message
      : 'Erro retornado pelo servidor ao processar a operação.';

  return [
    {
      local: typeof body?.erro === 'string' ? body.erro : `API (HTTP ${res.status})`,
      motivo: msg,
      severidade: 'ERRO',
    },
  ];
}

function extrairConfigDeCorpo(body: unknown): MapeamentoConfig | null {
  if (!ehObjetoPlano(body)) return null;
  if (typeof body.conteudo === 'string') {
    try {
      const parsed = JSON.parse(body.conteudo) as unknown;
      if (ehObjetoPlano(parsed) && Array.isArray(parsed.mapeamentos)) {
        return {
          mapeamentos: parsed.mapeamentos as MapeamentoConfig['mapeamentos'],
          dominios: Array.isArray(parsed.dominios)
            ? (parsed.dominios as MapeamentoConfig['dominios'])
            : [],
        };
      }
    } catch {
      return null;
    }
  }
  if (Array.isArray(body.mapeamentos)) {
    return {
      mapeamentos: body.mapeamentos as MapeamentoConfig['mapeamentos'],
      dominios: Array.isArray(body.dominios)
        ? (body.dominios as MapeamentoConfig['dominios'])
        : [],
    };
  }
  return null;
}

export const httpMappingApi: MappingGateway = {
  async buscarConfiguracaoAtiva(): Promise<MapeamentoConfig> {
    const res = await fetch(`${API_BASE}/ativa`);
    if (res.status === 404) {
      const body = await safeParseJson<Record<string, unknown>>(res);
      const msg =
        typeof body?.mensagem === 'string'
          ? body.mensagem
          : 'Nenhuma configuração ativa cadastrada no servidor.';
      throw new SemConfigAtivaError(msg);
    }
    if (!res.ok) {
      throw new Error(`Falha ao buscar configuração ativa (HTTP ${res.status}).`);
    }
    const body = await safeParseJson<unknown>(res);
    const config = extrairConfigDeCorpo(body);
    if (!config) {
      throw new Error('Servidor retornou resposta vazia ou fora do contrato ao carregar configuração.');
    }
    return config;
  },

  async validarConfiguracao(config) {
    const payload = serializarConfigParaContrato(config);
    const res = await fetch(`${API_BASE}/validar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      return {
        acao: 'VALIDAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Configuração rejeitada pelo validador do servidor.',
        erros: await extrairErrosHttp(res, config),
        avisos: [],
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    const validaExplicita =
      ehObjetoPlano(body) &&
      (body.valida === true ||
        (typeof body.valido === 'boolean' && Array.isArray(body.erros)));

    if (!validaExplicita) {
      return {
        acao: 'VALIDAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Resposta do servidor fora do contrato esperado em /validar.',
        erros: [
          {
            local: 'Contrato HTTP (/validar)',
            motivo: 'Esperado JSON explícito com { valida: true } ou { valido: boolean, erros: [] }.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      };
    }

    const erros = Array.isArray(body.erros) ? (body.erros as ErroValidacao[]) : [];
    const avisos = Array.isArray(body.avisos) ? (body.avisos as ErroValidacao[]) : [];
    const sucesso = (body.valida === true || body.valido === true) && erros.length === 0;

    return {
      acao: 'VALIDAR',
      sucesso,
      timestamp: new Date().toISOString(),
      mensagemResumo: sucesso
        ? 'Configuração validada com sucesso pelo servidor.'
        : 'O validador do servidor apontou inconsistências.',
      erros,
      avisos,
    };
  },

  async testarConversao(params) {
    const res = await fetch(`${API_BASE}/testar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...params,
        config: serializarConfigParaContrato(params.config),
      }),
    });

    if (!res.ok) {
      return {
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Falha ao executar teste de conversão no motor.',
        erros: await extrairErrosHttp(res, params.config),
        avisos: [],
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    const contratoOk =
      ehObjetoPlano(body) &&
      typeof body.valido === 'boolean' &&
      Array.isArray(body.erros) &&
      (!body.valido || ehObjetoPlano(body.resultado));

    if (!contratoOk) {
      return {
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Resposta do servidor fora do contrato esperado em /testar.',
        erros: [
          {
            local: 'Contrato HTTP (/testar)',
            motivo: 'Resposta de sucesso exige { valido: true, erros: [], resultado: { ... } }.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      };
    }

    const erros = body.erros as ErroValidacao[];
    const avisos = Array.isArray(body.avisos) ? (body.avisos as ErroValidacao[]) : [];
    const sucesso = body.valido === true && erros.length === 0;

    return {
      acao: 'TESTAR',
      sucesso,
      timestamp: new Date().toISOString(),
      mensagemResumo: sucesso
        ? 'Teste de conversão concluído com sucesso pelo motor.'
        : 'Teste retornou erros de conversão.',
      erros,
      avisos,
      payloadSaida: ehObjetoPlano(body.resultado) ? body.resultado : undefined,
    };
  },

  async salvarConfiguracao(config) {
    const payload = serializarConfigParaContrato(config);
    const res = await fetch(API_BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      return {
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Falha ao persistir configuração. A configuração anterior permanece ativa.',
        erros: await extrairErrosHttp(res, config),
        avisos: [],
      };
    }

    // Cenário 7 (Seção 6 e 7): 204 No Content é tratado como sucesso sem tentar ler JSON
    if (res.status === 204) {
      return {
        acao: 'SALVAR',
        sucesso: true,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Configuração salva e ativada no servidor (HTTP 204).',
        erros: [],
        avisos: [],
        configConfirmada: payload,
      };
    }

    const body = await safeParseJson<Record<string, unknown>>(res);
    const configRetornada = extrairConfigDeCorpo(body);
    const erros =
      ehObjetoPlano(body) && Array.isArray(body.erros)
        ? (body.erros as ErroValidacao[])
        : [];
    const confirmacaoExplicita =
      ehObjetoPlano(body) &&
      (body.sucesso === true ||
        body.valido === true ||
        body.valida === true ||
        body.ativa === true ||
        configRetornada !== null) &&
      erros.length === 0;

    if (!confirmacaoExplicita) {
      return {
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Servidor não confirmou explicitamente a persistência. A versão anterior permanece ativa.',
        erros:
          erros.length > 0
            ? erros
            : [
                {
                  local: 'Contrato HTTP (/api/config)',
                  motivo: 'Resposta 2xx sem confirmação explícita da versão ativa.',
                  severidade: 'ERRO',
                },
              ],
        avisos: [],
      };
    }

    const versaoInfo =
      typeof body?.versao === 'number' ? ` (versão ${body.versao})` : '';

    return {
      acao: 'SALVAR',
      sucesso: true,
      timestamp: new Date().toISOString(),
      mensagemResumo: `Configuração salva e ativada no servidor${versaoInfo}.`,
      erros: [],
      avisos: Array.isArray(body?.avisos) ? (body.avisos as ErroValidacao[]) : [],
      configConfirmada: configRetornada ?? payload,
    };
  },
};
