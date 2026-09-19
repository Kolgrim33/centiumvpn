import React from 'react';
import { Shield, Settings, Activity, Globe, Key, Lock, Terminal, Package, Monitor } from 'lucide-react';
import { ConnectionState } from '../types.ts';

interface HeaderProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
  connectionState: ConnectionState;
  showTray: boolean;
  onToggleTray: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentTab,
  onSelectTab,
  connectionState,
  showTray,
  onToggleTray,
}) => {
  const getDotClass = () => {
    switch (connectionState) {
      case 'CONNECTED':
        return 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)]';
      case 'CONNECTING':
      case 'STARTING':
      case 'RECONNECTING':
        return 'bg-amber-400 animate-pulse';
      case 'DISCONNECTING':
        return 'bg-amber-500 animate-pulse';
      case 'ERROR':
        return 'bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.8)]';
      case 'DISCONNECTED':
      default:
        return 'bg-zinc-600';
    }
  };

  const navItems = [
    { id: 'connect', label: 'VPN', icon: Shield },
    { id: 'exit', label: 'Exit Node', icon: Globe },
    { id: 'bridges', label: 'Bridges', icon: Key },
    { id: 'privacy', label: 'Privacy', icon: Lock },
    { id: 'diagnostics', label: 'Diagnostics', icon: Activity },
    { id: 'network', label: 'Circuit & Logs', icon: Terminal },
    { id: 'settings', label: 'Settings', icon: Settings },
    { id: 'packages', label: 'Linux Packages', icon: Package },
  ];

  return (
    <header className="border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur sticky top-0 z-30 px-4 py-3">
      <div className="max-w-5xl mx-auto flex items-center justify-between gap-3">
        {/* App Title */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full ${getDotClass()} transition-all duration-300`} />
            <h1 className="text-sm font-semibold tracking-wider text-zinc-100 uppercase">
              Centium <span className="text-zinc-500 font-normal text-xs tracking-normal">VPN</span>
            </h1>
          </div>
          <span className="hidden sm:inline-block px-1.5 py-0.5 text-[10px] uppercase font-mono tracking-wider rounded bg-zinc-900 border border-zinc-800 text-zinc-400">
            Tor Tunnel
          </span>
        </div>

        {/* Navigation Tabs */}
        <nav className="flex items-center gap-1 overflow-x-auto no-scrollbar py-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentTab === item.id;
            return (
              <button
                key={item.id}
                id={`nav-${item.id}`}
                onClick={() => onSelectTab(item.id)}
                className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-colors whitespace-nowrap ${
                  isActive
                    ? 'bg-zinc-800 text-zinc-100 border border-zinc-700/60 shadow-xs'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/60'
                }`}
              >
                <Icon className="w-3.5 h-3.5 shrink-0" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>

        {/* System Tray Preview toggle */}
        <div className="flex items-center gap-2">
          <button
            id="btn-toggle-tray"
            onClick={onToggleTray}
            title="Preview Desktop System Tray"
            className={`p-1.5 rounded-md text-xs border transition-colors ${
              showTray
                ? 'bg-zinc-800 text-emerald-400 border-zinc-700'
                : 'text-zinc-400 hover:text-zinc-200 bg-zinc-900 border-zinc-800'
            }`}
          >
            <Monitor className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </header>
  );
};
