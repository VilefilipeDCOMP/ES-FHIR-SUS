import type React from 'react';
import { useState } from 'react';
import type { RegraCampo, Tabela, TipoRegraMotor } from '../../types/domain';
import type { LinhaTerminologiaUI, ResultadoAcaoLocal } from '../../types/ui';
import { detectarChavesDuplicadas } from '../../utils/mappingValidation';

interface Props {
  regra: RegraCampo;
  tabelaAtual?: Tabela;
  onAplicar: (params: {
    regra: RegraCampo;
    tabela?: Tabela;
    aplicarInversa: boolean;
  }) => ResultadoAcaoLocal;
  onRemover: () => void;
  onCancelar: () => void;
}

export const RuleDrawer: React.FC<Props> = ({
  regra,
  tabelaAtual,
  onAplicar,
  onRemover,
  onCancelar,
}) => {
  const [tipoRegra, setTipoRegra] = useState<TipoRegraMotor>(
    regra.regra.startsWith('TABELA:') ? 'TABELA' : 'COPIAR'
  );
  const [nomeTabela, setNomeTabela] = useState(
    tabelaAtual?.nome ??
      `TAB_${regra.caminhoDestino.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase()}`
  );
  const [linhas, setLinhas] = useState<LinhaTerminologiaUI[]>(() => {
    if (!tabelaAtual) {
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

  const handleAplicar = () => {
    setErroLocal(null);

    if (tipoRegra === 'COPIAR') {
      const res = onAplicar({ regra: { ...regra, regra: 'COPIAR' }, aplicarInversa });
      if (!res.ok) setErroLocal(res.erro);
      return;
    }

    const validas = linhas.filter(
      (l) => l.valorLegado.trim() !== '' && l.codigoFhir.trim() !== ''
    );
    if (!nomeTabela.trim() || validas.length === 0) {
      setErroLocal(
        'Informe o nome da tabela e pelo menos um par (valor de origem -> valor de destino).'
      );
      return;
    }

    const duplicadas = detectarChavesDuplicadas(validas);
    if (duplicadas.length > 0) {
      setErroLocal(
        `Valores de origem duplicados na tabela: [${duplicadas.join(', ')}]. Corrija antes de aplicar.`
      );
      return;
    }

    const equivalencia: Record<string, string> = {};
    for (const l of validas) {
      equivalencia[l.valorLegado.trim()] = l.codigoFhir.trim();
    }

    const res = onAplicar({
      regra: { ...regra, regra: `TABELA:${nomeTabela.trim()}` },
      tabela: { nome: nomeTabela.trim(), equivalencia },
      aplicarInversa,
    });
    if (!res.ok) setErroLocal(res.erro);
  };

  return (
    <aside
      aria-label="Regras de transformação"
      className="fixed inset-y-0 right-0 w-full max-w-md bg-white shadow-2xl border-l z-40 flex flex-col"
    >
      <div className="p-4 border-b flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Regras de transformação</h2>
          <p className="text-xs font-mono text-slate-500 mt-0.5">
            {regra.caminhoOrigem} → {regra.caminhoDestino}
          </p>
        </div>
        <button type="button" onClick={onCancelar} className="text-xs text-slate-500">
          Fechar
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-5 text-sm">
        <fieldset className="space-y-2">
          <legend className="font-medium text-slate-800">Tipo de regra</legend>

          <label className="flex items-center gap-2 p-2.5 border rounded cursor-pointer">
            <input
              type="radio"
              name="tipoRegra"
              checked={tipoRegra === 'COPIAR'}
              onChange={() => setTipoRegra('COPIAR')}
            />
            <span>Direta (COPIAR)</span>
          </label>

          <label className="flex items-center gap-2 p-2.5 border rounded cursor-pointer">
            <input
              type="radio"
              name="tipoRegra"
              checked={tipoRegra === 'TABELA'}
              onChange={() => setTipoRegra('TABELA')}
            />
            <span>Tabela de códigos (TABELA)</span>
          </label>

          <label className="flex items-center justify-between p-2.5 border rounded bg-slate-50 text-slate-400 cursor-not-allowed">
            <span className="flex items-center gap-2">
              <input type="radio" disabled />
              Formato de data
            </span>
            <span className="text-[10px] uppercase font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-600">
              planejado
            </span>
          </label>

          <label className="flex items-center justify-between p-2.5 border rounded bg-slate-50 text-slate-400 cursor-not-allowed">
            <span className="flex items-center gap-2">
              <input type="radio" disabled />
              Valor padrão
            </span>
            <span className="text-[10px] uppercase font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-600">
              planejado
            </span>
          </label>
        </fieldset>

        {tipoRegra === 'TABELA' && (
          <div className="space-y-3 border-t pt-4">
            <div>
              <label className="block text-xs font-medium text-slate-700 mb-1">
                Nome da tabela
              </label>
              <input
                type="text"
                aria-label="Nome da tabela"
                value={nomeTabela}
                onChange={(e) => setNomeTabela(e.target.value)}
                className="w-full px-2.5 py-1.5 border rounded text-xs font-mono"
              />
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
                    placeholder="ATIVO"
                    className="px-2 py-1 border rounded text-xs"
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
                    placeholder="active"
                    className="px-2 py-1 border rounded text-xs"
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
                    placeholder="Ativo"
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
