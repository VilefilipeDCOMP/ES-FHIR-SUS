import type React from 'react';
import { useRef, useState } from 'react';
import type { MapeamentoConfig, RecursoFhir } from '../../types/domain';
import type { CampoFhirSpec, ErroValidacao } from '../../types/ui';
import { importarArquivoConfig } from '../../utils/jsonIngest';
import { serializarConfigParaContrato } from '../../utils/mappingValidation';

interface Props {
  config: MapeamentoConfig;
  catalogoFhir: Record<RecursoFhir, CampoFhirSpec[]>;
  onImportarEAtivar: (config: MapeamentoConfig) => Promise<void>;
  onCarregarNoRascunho: (config: MapeamentoConfig, avisos: ErroValidacao[]) => void;
  onImportarErro: (erros: ErroValidacao[], avisos: ErroValidacao[]) => void;
}

export const JsonPreviewTab: React.FC<Props> = ({
  config,
  catalogoFhir,
  onImportarEAtivar,
  onCarregarNoRascunho,
  onImportarErro,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [modoImportacao, setModoImportacao] = useState<'ATIVAR' | 'RASCUNHO'>('ATIVAR');
  const jsonTexto = JSON.stringify(serializarConfigParaContrato(config), null, 2);

  const baixarArquivo = () => {
    const blob = new Blob([jsonTexto], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'mapeamento-config.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const abrirSeletor = (modo: 'ATIVAR' | 'RASCUNHO') => {
    setModoImportacao(modo);
    inputRef.current?.click();
  };

  const handleArquivo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const res = await importarArquivoConfig(file, catalogoFhir);
    e.target.value = '';

    if (!res.sucesso || !res.config) {
      onImportarErro(res.erros, res.avisos);
      return;
    }

    if (modoImportacao === 'ATIVAR') {
      await onImportarEAtivar(res.config);
    } else {
      onCarregarNoRascunho(res.config, res.avisos);
    }
  };

  return (
    <div className="bg-white border rounded-lg overflow-hidden">
      <div className="p-3 border-b bg-slate-50 flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-semibold text-slate-700">
          Prévia do MapeamentoConfig (JSON/YAML — tipos suportados: DIRETA, DOMINIO e CONSTANTE)
        </span>
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept=".json,.yaml,.yml,application/json,text/yaml"
            onChange={handleArquivo}
            className="hidden"
          />
          <button
            type="button"
            onClick={() => abrirSeletor('ATIVAR')}
            className="text-xs px-2.5 py-1 rounded bg-blue-600 text-white hover:bg-blue-700"
          >
            Importar e Ativar (UC-04)
          </button>
          <button
            type="button"
            onClick={() => abrirSeletor('RASCUNHO')}
            className="text-xs px-2.5 py-1 rounded border bg-white hover:bg-slate-100"
          >
            Importar para rascunho
          </button>
          <button
            type="button"
            onClick={baixarArquivo}
            className="text-xs px-2.5 py-1 rounded border bg-white hover:bg-slate-100"
          >
            Baixar JSON
          </button>
        </div>
      </div>
      <pre className="p-4 text-xs font-mono overflow-x-auto bg-slate-950 text-emerald-300 max-h-[500px]">
        {jsonTexto}
      </pre>
    </div>
  );
};
