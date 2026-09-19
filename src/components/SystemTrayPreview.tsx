import React from 'react';
import { Shield, Power, Settings, X, Activity, Globe } from 'lucide-react';
import { CentiumStatus } from '../types.ts';

interface SystemTrayPreviewProps {
  status: CentiumStatus;
  onConnect: () => void;
  onDisconnect: () => void;
  onOpenSettings: () => void;
  onOpenDiagnostics: () => void;
  onClose: () => void;
}

export const SystemTrayPreview: React.FC<SystemTrayPreviewProps> = ({
  status,
  onConnect,
  onDisconnect,
  onOpenSettings,
  onOpenDiagnostics,
  onClose,
}) => {
  const isConnected = status.state === 'CONNECTED';
  const isConnecting = status.state === 'CONNECTING' || status.state === 'STARTING';

  return (
    <div className="fixed bottom-4 right-4 z-50 w-64 bg-zinc-900/95 backdrop-blur-md border border-zinc-700/80 rounded-xl shadow-2xl overflow-hidden font-mono text-xs animate-in fade-in slide-in-from-bottom-2">
      {/* Tray Menu Header */}
      <div className="bg-zinc-950 px-3 py-2 border-b border-zinc-800 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span
            className={`w-2 h-2 rounded-full ${
              isConnected
                ? 'bg-emerald-400'
                : isConnecting
                ? 'bg-amber-400 animate-pulse'
                : status.state === 'ERROR'
                ? 'bg-rose-500'
                : 'bg-zinc-600'
            }`}
          />
          <span className="font-semibold text-zinc-200">Centium</span>
        </div>
        <button
          onClick={onClose}
          className="text-zinc-500 hover:text-zinc-300 p-0.5 rounded cursor-pointer"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Status Item */}
      <div className="px-3 py-2 text-[11px] bg-zinc-900/60 border-b border-zinc-800/80">
        <div className="text-zinc-400">Status:</div>
        <div
          className={`font-semibold ${
            isConnected
              ? 'text-emerald-400'
              : isConnecting
              ? 'text-amber-400'
              : status.state === 'ERROR'
              ? 'text-rose-400'
              : 'text-zinc-300'
          }`}
        >
          {status.state}
          {status.exitCountry && isConnected ? ` (${status.exitCountry})` : ''}
        </div>
      </div>

      {/* Menu Options */}
      <div className="p-1 space-y-0.5">
        {!isConnected ? (
          <button
            onClick={onConnect}
            disabled={isConnecting}
            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded text-left text-zinc-200 hover:bg-zinc-800 hover:text-emerald-400 transition-colors cursor-pointer disabled:opacity-50"
          >
            <Power className="w-3.5 h-3.5 text-emerald-400" />
            <span>Connect</span>
          </button>
        ) : (
          <button
            onClick={onDisconnect}
            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded text-left text-zinc-200 hover:bg-zinc-800 hover:text-rose-400 transition-colors cursor-pointer"
          >
            <Power className="w-3.5 h-3.5 text-rose-400" />
            <span>Disconnect</span>
          </button>
        )}

        <div className="h-px bg-zinc-800 my-1" />

        <button
          onClick={onOpenDiagnostics}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded text-left text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100 transition-colors cursor-pointer"
        >
          <Activity className="w-3.5 h-3.5 text-zinc-400" />
          <span>Status & Diagnostics</span>
        </button>

        <button
          onClick={onOpenSettings}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded text-left text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100 transition-colors cursor-pointer"
        >
          <Settings className="w-3.5 h-3.5 text-zinc-400" />
          <span>Settings</span>
        </button>

        <div className="h-px bg-zinc-800 my-1" />

        <button
          onClick={onClose}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded text-left text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200 transition-colors cursor-pointer"
        >
          <X className="w-3.5 h-3.5 text-zinc-500" />
          <span>Minimize to Tray</span>
        </button>
      </div>
    </div>
  );
};
