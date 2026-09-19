import React from 'react';
import { Power, Settings, X, Activity } from 'lucide-react';
import { CentiumLogo } from './CentiumLogo.tsx';
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
  const isError = status.state === 'ERROR';

  return (
    <div className="fixed bottom-4 right-4 z-50 w-60 bg-[#121018] border border-[#23202E] rounded-xl shadow-xl overflow-hidden text-xs select-none">
      {/* Header with Centium logo */}
      <div className="bg-[#0D0B12] px-3.5 py-2.5 border-b border-[#1B1824] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <CentiumLogo size={18} color="#6C4DFF" />
          <span className="font-semibold text-[#F4F3F7]">Centium</span>
        </div>
        <button
          onClick={onClose}
          className="text-[#8E899E] hover:text-[#F4F3F7] p-0.5 rounded cursor-pointer"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Status Line */}
      <div className="px-3.5 py-2 border-b border-[#1B1824] flex items-center justify-between text-[11px]">
        <span className="text-[#8E899E]">Status:</span>
        <div className="flex items-center gap-1.5 font-medium">
          <span
            className={`w-1.5 h-1.5 rounded-full ${
              isConnected
                ? 'bg-emerald-400'
                : isConnecting
                ? 'bg-[#6C4DFF] animate-pulse'
                : isError
                ? 'bg-rose-500'
                : 'bg-[#555064]'
            }`}
          />
          <span
            className={
              isConnected
                ? 'text-emerald-400'
                : isConnecting
                ? 'text-[#6C4DFF]'
                : isError
                ? 'text-rose-400'
                : 'text-[#8E899E]'
            }
          >
            {isConnected ? 'Connected' : isConnecting ? 'Connecting…' : 'Disconnected'}
          </span>
        </div>
      </div>

      {/* Menu Options (Section 23) */}
      <div className="p-1.5 space-y-0.5">
        {!isConnected ? (
          <button
            onClick={() => {
              onConnect();
              onClose();
            }}
            disabled={isConnecting}
            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-[#F4F3F7] hover:bg-[#17141E] hover:text-[#6C4DFF] transition-colors cursor-pointer disabled:opacity-50"
          >
            <Power className="w-3.5 h-3.5 text-[#6C4DFF]" />
            <span>Connect</span>
          </button>
        ) : (
          <button
            onClick={() => {
              onDisconnect();
              onClose();
            }}
            className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-[#F4F3F7] hover:bg-[#17141E] hover:text-rose-400 transition-colors cursor-pointer"
          >
            <Power className="w-3.5 h-3.5 text-rose-400" />
            <span>Disconnect</span>
          </button>
        )}

        <div className="h-px bg-[#1B1824] my-1" />

        <button
          onClick={() => {
            onOpenDiagnostics();
            onClose();
          }}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-[#8E899E] hover:text-[#F4F3F7] hover:bg-[#17141E] transition-colors cursor-pointer"
        >
          <Activity className="w-3.5 h-3.5" />
          <span>Status & Diagnostics</span>
        </button>

        <button
          onClick={() => {
            onOpenSettings();
            onClose();
          }}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-[#8E899E] hover:text-[#F4F3F7] hover:bg-[#17141E] transition-colors cursor-pointer"
        >
          <Settings className="w-3.5 h-3.5" />
          <span>Settings</span>
        </button>

        <div className="h-px bg-[#1B1824] my-1" />

        <button
          onClick={onClose}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left text-[#555064] hover:text-[#8E899E] hover:bg-[#17141E] transition-colors cursor-pointer"
        >
          <X className="w-3.5 h-3.5" />
          <span>Close Menu</span>
        </button>
      </div>
    </div>
  );
};
