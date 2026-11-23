import type React from 'react';
import { useState } from 'react';
import type {
  DominioValores,
  MapeamentoConfig,
  RegraCampo,
  SentidoTipo,
  TipoConversao,
} from '../../types/domain';
import type { LinhaTerminologiaUI, ResultadoAcaoLocal } from '../../types/ui';
import {
  detectarChavesDuplicadas,
  listarReferenciasDominio,
} from '../../utils/mappingValidation';

interface Props {
  regra: RegraCampo;
  sentido: SentidoTipo;
  configRascunho: MapeamentoConfig;
  tabelaAtual?: DominioValores;
  onAplicar: (params: {
    regra: RegraCampo;
    caminhoDestinoOriginal?: string;
    tabela?: DominioValores;
    nomeDominioAnterior?: string;
    aplicarInversa: boolean;
  }) => ResultadoAcaoLocal;
  onExcluirDominio?: (nomeDominio: string) => ResultadoAcaoLocal;
  onRemover: () => void;
  onCancelar: () => void;
}

export const RuleDrawer: React.FC<Props> = ({
  regra,
  sentido,
  configRascunho,
  tabelaAtual,
  onAplicar,
  onExcluirDominio,
  onRemover,
  onCancelar,
}) => {
  const [tipoConversao, setTipoConversao] = useState<TipoConversao>(regra.tipoConversao);
  const [caminhoOrigem, setCaminhoOrigem] = useState<string>(
    regra.tipoConversao === 'CONSTANTE' ? '' : regra.caminhoOrigem
  );
  const [caminhoDestino, setCaminhoDestino] = useState<string>(regra.caminhoDestino);
  const [obrigatorio, setObrigatorio] = useState<boolean>(regra.obrigatorio);
  const [valorConstante, setValorConstante] = useState<string>(
    regra.tipoConversao === 'CONSTANTE' ? regra.parametroConversao : ''
  );

  const dominioInicialNome =
    tabelaAtual?.nome ??
    (regra.tipoConversao === 'DOMINIO' && regra.parametroConversao
      ? regra.parametroConversao
      : `dom_${regra.caminhoDestino.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase()}`);

  const [dominioOriginalNome, setDominioOriginalNome] = useState<string | undefined>(
    tabelaAtual?.nome
  );
  const [nomeTabela, setNomeTabela] = useState<string>(dominioInicialNome);
  const [linhas, setLinhas] = useState<LinhaTerminologiaUI[]>(() => {
    if (!tabelaAtual || Object.keys(tabelaAtual.equivalencia).length === 0) {
      return [{ id: crypto.randomUUID(), valorLegado: '', codigoFhir: '', rotulo: '' }];
    }
    return Object.entries(tabelaAtual.equivalencia).map(([k, v]) => ({
      id: crypto.randomUUID(),
      valorLegado: k,
      codigoFhir: v,
      rotulo: '',
    }));
  });
  const [aplicarInversa, setAplicarInversa] = useState(false);
  const [erroLocal, setErroLocal] = useState<string | null>(null);
  const [mensagemInfo, setMensagemInfo] = useState<string | null>(null);

  const referenciasDominio = dominioOriginalNome
    ? listarReferenciasDominio(configRascunho, dominioOriginalNome)
    : [];

  const carregarDominioExistente = (nomeSelecionado: string) => {
    setErroLocal(null);
    setMensagemInfo(null);
    if (nomeSelecionado === '__NOVO__') {
      const novoNome = `dom_${caminhoDestino.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase() || 'novo'}`;
      setDominioOriginalNome(undefined);
      setNomeTabela(novoNome);
      setLinhas([{ id: crypto.randomUUID(), valorLegado: '', codigoFhir: '', rotulo: '' }]);
      return;
    }
    const existente = configRascunho.dominios.find((d) => d.nome === nomeSelecionado);
    if (existente) {
      setDominioOriginalNome(existente.nome);
      setNomeTabela(existente.nome);
      setLinhas(
        Object.entries(existente.equivalencia).map(([k, v]) => ({
          id: crypto.randomUUID(),
          valorLegado: k,
          codigoFhir: v,
          rotulo: '',
        }))
      );
    }
  };

  const handleAplicar = () => {
    setErroLocal(null);
    setMensagemInfo(null);

    const destTrim = caminhoDestino.trim();
    if (!destTrim) {
      setErroLocal('O caminho de destino é obrigatório.');
      return;
    }

    if (tipoConversao === 'CONSTANTE') {
      if (!valorConstante || valorConstante.trim() === '') {
        setErroLocal('Informe o texto literal do parâmetro em CONSTANTE.');
        return;
      }
      // Em CONSTANTE, caminhoOrigem é omitido do JSON enviado e o valor permanece sempre string
      const res = onAplicar({
        regra: {
          tipoConversao: 'CONSTANTE',
          caminhoDestino: destTrim,
          parametroConversao: String(valorConstante),
          obrigatorio,
        },
        caminhoDestinoOriginal: regra.caminhoDestino,
        aplicarInversa: false,
      });
      if (!res.ok) setErroLocal(res.erro);
      return;
    }

    const origTrim = caminhoOrigem.trim();
    if (!origTrim) {
      setErroLocal('O caminho de origem é obrigatório em DIRETA e DOMINIO.');
      return;
    }

    if (tipoConversao === 'DIRETA') {
      // Ao trocar de domínio/constante para direta, limpa o parâmetro antigo e envia null
      const res = onAplicar({
        regra: {
          tipoConversao: 'DIRETA',
          caminhoOrigem: origTrim,
          caminhoDestino: destTrim,
          parametroConversao: null,
          obrigatorio,
        },
        caminhoDestinoOriginal: regra.caminhoDestino,
        aplicarInversa,
      });
      if (!res.ok) setErroLocal(res.erro);
      return;
    }

    // DOMINIO: valida chaves duplicadas ANTES de montar o objeto equivalencia
    const linhasPreenchidas = linhas.filter(
      (l) => l.valorLegado.trim() !== '' || l.codigoFhir.trim() !== ''
    );
    const linhaIncompleta = linhasPreenchidas.some(
      (l) => l.valorLegado.trim() === '' || l.codigoFhir.trim() === ''
    );
    if (linhaIncompleta) {
      setErroLocal(
        'Preencha tanto o código de origem quanto o código de destino em todas as linhas da tabela.'
      );
      return;
    }

    if (!nomeTabela.trim() || linhasPreenchidas.length === 0) {
      setErroLocal(
        'Informe o nome da tabela de domínio e pelo menos um par (código de origem -> código de destino).'
      );
      return;
    }

    const duplicadas = detectarChavesDuplicadas(linhasPreenchidas);
    if (duplicadas.length > 0) {
      setErroLocal(
        `Valores de origem duplicados na tabela: [${duplicadas.join(', ')}]. Cada código de origem deve aparecer uma única vez.`
      );
      return;
    }

    const equivalencia: Record<string, string> = {};
    for (const l of linhasPreenchidas) {
      equivalencia[l.valorLegado.trim()] = l.codigoFhir.trim();
    }

    const res = onAplicar({
      regra: {
        tipoConversao: 'DOMINIO',
        caminhoOrigem: origTrim,
        caminhoDestino: destTrim,
        parametroConversao: nomeTabela.trim(),
        obrigatorio,
      },
      caminhoDestinoOriginal: regra.caminhoDestino,
      tabela: { nome: nomeTabela.trim(), equivalencia },
      nomeDominioAnterior: dominioOriginalNome,
      aplicarInversa,
    });
    if (!res.ok) setErroLocal(res.erro);
  };

  const handleExcluirDominio = () => {
    if (!dominioOriginalNome || !onExcluirDominio) return;
    setErroLocal(null);
    const res = onExcluirDominio(dominioOriginalNome);
    if (!res.ok) {
      setErroLocal(res.erro);
    } else {
      setDominioOriginalNome(undefined);
      setMensagemInfo(`Domínio "${dominioOriginalNome}" removido do rascunho.`);
    }
  };

  const exemploOrigemPlaceholder =
    sentido === 'FHIR_PARA_LEGADO' ? '$.identifier[0].value' : '$.resultado';
  const exemploDestinoPlaceholder =
    sentido === 'FHIR_PARA_LEGADO' ? 'numeroPedido' : 'valueQuantity.value';

  return (
    <aside
      aria-label="Regras de transformação"
      className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l z-40 flex flex-col"
    >
      <div className="p-4 border-b flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Regras de transformação</h2>
          <p className="text-xs font-mono text-slate-500 mt-0.5">
            {tipoConversao === 'CONSTANTE'
              ? `"${valorConstante || '...'}"`
              : caminhoOrigem || '(sem origem)'}{' '}
            → {caminhoDestino || '(sem destino)'}
          </p>
        </div>
        <button type="button" onClick={onCancelar} className="text-xs text-slate-500">
          Fechar
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-5 text-sm">
        <fieldset className="space-y-2">
          <legend className="font-medium text-slate-800">Tipo de conversão (`tipoConversao`)</legend>

          <label className="flex items-center gap-2 p-2.5 border rounded cursor-pointer">
            <input
              type="radio"
              name="tipoRegra"
              checked={tipoConversao === 'DIRETA'}
              onChange={() => {
                setErroLocal(null);
                setTipoConversao('DIRETA');
              }}
            />
            <span>Direta (DIRETA / COPIAR)</span>
          </label>

          <label className="flex items-center gap-2 p-2.5 border rounded cursor-pointer">
            <input
              type="radio"
              name="tipoRegra"
              checked={tipoConversao === 'DOMINIO'}
              onChange={() => {
                setErroLocal(null);
                setTipoConversao('DOMINIO');
              }}
            />
            <span>Tabela de códigos (DOMINIO / TABELA)</span>
          </label>

          <label className="flex items-center gap-2 p-2.5 border rounded cursor-pointer">
            <input
              type="radio"
              name="tipoRegra"
              checked={tipoConversao === 'CONSTANTE'}
              onChange={() => {
                setErroLocal(null);
                setTipoConversao('CONSTANTE');
                setAplicarInversa(false);
              }}
            />
            <span>Constante literal (CONSTANTE)</span>
          </label>

          <label className="flex items-center justify-between p-2.5 border rounded bg-slate-50 text-slate-400 cursor-not-allowed">
            <span className="flex items-center gap-2">
              <input type="radio" disabled />
              Formato de data (TRANSFORMACAO)
            </span>
            <span className="text-[10px] uppercase font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-600">
              planejado
            </span>
          </label>

          <label className="flex items-center justify-between p-2.5 border rounded bg-slate-50 text-slate-400 cursor-not-allowed">
            <span className="flex items-center gap-2">
              <input type="radio" disabled />
              Expressão condicional (EXPRESSAO)
            </span>
            <span className="text-[10px] uppercase font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-600">
              planejado
            </span>
          </label>
        </fieldset>

        <div className="space-y-3 border-t pt-4">
          {tipoConversao !== 'CONSTANTE' && (
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Caminho de origem (`caminhoOrigem` — subconjunto JSONPath)
              </label>
              <input
                type="text"
                aria-label="Caminho de origem"
                value={caminhoOrigem}
                onChange={(e) => {
                  setErroLocal(null);
                  setCaminhoOrigem(e.target.value);
                }}
                placeholder={exemploOrigemPlaceholder}
                className="w-full px-2.5 py-1.5 border rounded text-xs font-mono"
              />
              <p className="text-[11px] text-slate-500 mt-1">
                Inicie com <code>$</code> (ex.: <code>{exemploOrigemPlaceholder}</code>). Índices começam em 0; filtros e curingas não são suportados.
              </p>
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-slate-700 mb-1">
              Caminho de destino (`caminhoDestino` — Dot Notation)
            </label>
            <input
              type="text"
              aria-label="Caminho de destino"
              value={caminhoDestino}
              onChange={(e) => {
                setErroLocal(null);
                setCaminhoDestino(e.target.value);
              }}
              placeholder={exemploDestinoPlaceholder}
              className="w-full px-2.5 py-1.5 border rounded text-xs font-mono"
            />
            <p className="text-[11px] text-slate-500 mt-1">
              Use Dot Notation sem <code>$</code> (ex.: <code>{exemploDestinoPlaceholder}</code>). Preencha índices sem lacunas.
            </p>
          </div>

          <label className="flex items-center gap-2 text-xs text-slate-800 font-medium pt-1">
            <input
              type="checkbox"
              aria-label="Campo obrigatório na conversão"
              checked={obrigatorio}
              onChange={(e) => setObrigatorio(e.target.checked)}
            />
            <span>Campo obrigatório na conversão (`obrigatorio`)</span>
          </label>
        </div>

        {tipoConversao === 'CONSTANTE' && (
          <div className="space-y-2 border-t pt-4">
            <label className="block text-xs font-medium text-slate-700">
              Valor constante (`parametroConversao` — enviado sempre como texto)
            </label>
            <input
              type="text"
              aria-label="Valor constante"
              value={valorConstante}
              onChange={(e) => {
                setErroLocal(null);
                setValorConstante(e.target.value);
              }}
              placeholder="CLINICA_DEMO ou 001"
              className="w-full px-2.5 py-1.5 border rounded text-xs font-mono"
            />
            <p className="text-[11px] text-slate-500">
              Zeros à esquerda (ex.: <code>"001"</code>) são preservados como string literal. O campo <code>caminhoOrigem</code> é omitido do JSON.
            </p>
          </div>
        )}

        {tipoConversao === 'DOMINIO' && (
          <div className="space-y-3 border-t pt-4">
            {configRascunho.dominios.length > 0 && (
              <div>
                <label className="block text-xs font-medium text-slate-700 mb-1">
                  Selecionar domínio existente no rascunho
                </label>
                <select
                  aria-label="Selecionar domínio existente"
                  value={
                    dominioOriginalNome &&
                    configRascunho.dominios.some((d) => d.nome === dominioOriginalNome)
                      ? dominioOriginalNome
                      : '__NOVO__'
                  }
                  onChange={(e) => carregarDominioExistente(e.target.value)}
                  className="w-full px-2.5 py-1.5 border rounded text-xs bg-white"
                >
                  <option value="__NOVO__">+ Criar nova tabela de domínio</option>
                  {configRascunho.dominios.map((dom) => {
                    const amostra = Object.entries(dom.equivalencia)
                      .slice(0, 2)
                      .map(([k, v]) => `${k}→${v}`)
                      .join(', ');
                    return (
                      <option key={dom.nome} value={dom.nome}>
                        {dom.nome} {amostra ? `(${amostra})` : ''}
                      </option>
                    );
                  })}
                </select>
              </div>
            )}

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-medium text-slate-700">
                  Nome da tabela
                </label>
                {dominioOriginalNome && onExcluirDominio && (
                  <button
                    type="button"
                    onClick={handleExcluirDominio}
                    className="text-[11px] text-red-600 hover:underline"
                  >
                    Excluir domínio
                  </button>
                )}
              </div>
              <input
                type="text"
                aria-label="Nome da tabela"
                value={nomeTabela}
                onChange={(e) => setNomeTabela(e.target.value)}
                className="w-full px-2.5 py-1.5 border rounded text-xs font-mono"
              />
              {dominioOriginalNome && (
                <p className="text-[11px] text-slate-500 mt-1">
                  Referenciada por <strong>{referenciasDominio.length}</strong> regra(s) no rascunho.
                  {nomeTabela.trim() !== dominioOriginalNome &&
                    ' Ao aplicar, todas as referências nos dois sentidos serão atualizadas.'}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <div className="grid grid-cols-3 gap-2 text-[11px] font-semibold text-slate-600">
                <span>Valor origem</span>
                <span>Código destino</span>
                <span>Rótulo (UI)</span>
              </div>
              {linhas.map((linha, idx) => (
                <div key={linha.id} className="grid grid-cols-3 gap-2">
                  <input
                    aria-label={`Valor origem linha ${idx + 1}`}
                    value={linha.valorLegado}
                    onChange={(e) => {
                      setErroLocal(null);
                      setLinhas((prev) =>
                        prev.map((l) =>
                          l.id === linha.id ? { ...l, valorLegado: e.target.value } : l
                        )
                      );
                    }}
                    placeholder="EXAME-001"
                    className="px-2 py-1 border rounded text-xs font-mono"
                  />
                  <input
                    aria-label={`Código destino linha ${idx + 1}`}
                    value={linha.codigoFhir}
                    onChange={(e) => {
                      setErroLocal(null);
                      setLinhas((prev) =>
                        prev.map((l) =>
                          l.id === linha.id ? { ...l, codigoFhir: e.target.value } : l
                        )
                      );
                    }}
                    placeholder="LAB-042"
                    className="px-2 py-1 border rounded text-xs font-mono"
                  />
                  <input
                    aria-label={`Rótulo linha ${idx + 1}`}
                    value={linha.rotulo}
                    onChange={(e) =>
                      setLinhas((prev) =>
                        prev.map((l) =>
                          l.id === linha.id ? { ...l, rotulo: e.target.value } : l
                        )
                      )
                    }
                    placeholder="Opcional"
                    className="px-2 py-1 border rounded text-xs"
                  />
                </div>
              ))}
              <button
                type="button"
                onClick={() =>
                  setLinhas((prev) => [
                    ...prev,
                    { id: crypto.randomUUID(), valorLegado: '', codigoFhir: '', rotulo: '' },
                  ])
                }
                className="text-xs font-medium text-blue-600 hover:underline"
              >
                + Adicionar linha
              </button>
            </div>
          </div>
        )}

        <div className="space-y-1 border-t pt-4">
          <label className="block text-xs font-medium text-slate-700">
            Se o valor de origem não for encontrado
          </label>
          <select
            disabled
            value="REJEITAR"
            className="w-full px-2.5 py-1.5 border rounded text-xs bg-slate-100 text-slate-600"
          >
            <option value="REJEITAR">Rejeitar com OperationOutcome</option>
          </select>
        </div>

        {tipoConversao !== 'CONSTANTE' && (
          <label className="flex items-start gap-2 text-xs text-slate-700">
            <input
              type="checkbox"
              checked={aplicarInversa}
              onChange={(e) => setAplicarInversa(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              Aplicar inversa automaticamente (cria regra espelhada no sentido oposto; bloqueia
              colisão N:1)
            </span>
          </label>
        )}

        {mensagemInfo && (
          <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 p-2 rounded">
            {mensagemInfo}
          </p>
        )}

        {erroLocal && (
          <p
            role="alert"
            className="text-xs text-red-700 bg-red-50 border border-red-200 p-2 rounded"
          >
            {erroLocal}
          </p>
        )}
      </div>

      <div className="p-4 border-t bg-slate-50 flex items-center justify-between">
        <button
          type="button"
          onClick={onRemover}
          className="text-xs text-red-600 hover:underline"
        >
          Remover conexão
        </button>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancelar}
            className="px-3 py-1.5 text-xs border rounded bg-white"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleAplicar}
            className="px-3 py-1.5 text-xs rounded bg-blue-600 text-white"
          >
            Aplicar regra
          </button>
        </div>
      </div>
    </aside>
  );
};
