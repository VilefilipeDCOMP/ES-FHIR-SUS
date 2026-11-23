import type React from 'react';

export const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="min-h-screen flex bg-slate-100 text-slate-900">
      <aside className="w-60 bg-slate-900 text-slate-200 flex flex-col shrink-0">
        <div className="p-4 border-b border-slate-800 font-bold tracking-tight text-white">
          FHIR SUS+
        </div>
        <nav aria-label="Menu principal" className="p-3 space-y-1 text-sm flex-1">
          <a
            href="#mapeamento"
            aria-current="page"
            className="block px-3 py-2 rounded bg-blue-600 text-white font-medium"
          >
            Mapeamento
          </a>

          <div
            aria-disabled="true"
            className="flex items-center justify-between px-3 py-2 rounded text-slate-500 cursor-not-allowed select-none"
          >
            <span>Credenciais</span>
            <span className="text-[10px] uppercase bg-slate-800 px-1.5 py-0.5 rounded">
              em breve
            </span>
          </div>

          <div
            aria-disabled="true"
            className="flex items-center justify-between px-3 py-2 rounded text-slate-500 cursor-not-allowed select-none"
          >
            <span>Administradores</span>
            <span className="text-[10px] uppercase bg-slate-800 px-1.5 py-0.5 rounded">
              em breve
            </span>
          </div>
        </nav>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="bg-white border-b px-6 py-3 flex flex-wrap items-center justify-between gap-3">
          <div className="text-xs text-slate-500">
            Plataforma <span className="mx-1">&gt;</span>{' '}
            <strong className="text-slate-800">Mapeamento De-Para</strong>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-[11px] font-medium px-2.5 py-1 rounded-full bg-amber-100 text-amber-900 border border-amber-300">
              demo local, sem autenticação, dados sintéticos
            </span>
            <span className="text-xs font-mono bg-slate-100 px-2.5 py-1 rounded border text-slate-700">
              sistema-demo
            </span>
          </div>
        </header>

        <main className="p-6 flex-1 space-y-4">{children}</main>
      </div>
    </div>
  );
};
