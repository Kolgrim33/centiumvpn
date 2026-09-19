import React from 'react';
import { Shield, Globe, Settings, Activity, Terminal, Key, Cpu } from 'lucide-react';
import { CentiumLogo } from './CentiumLogo.tsx';
import { ConnectionState } from '../types.ts';

interface SidebarProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
  connectionState: ConnectionState;
  showTray: boolean;
  onToggleTray: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentTab,
  onSelectTab,
  connectionState,
  showTray,
  onToggleTray,
}) => {
  const isConnected = connectionState === 'CONNECTED';
  const isConnecting = connectionState === 'CONNECTING' || connectionState === 'STARTING';
  const isError = connectionState === 'ERROR';

  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: Shield },
    { id: 'locations', label: 'Locations', icon: Globe },
    { id: 'bridges', label: 'Bridges', icon: Key },
    { id: 'diagnostics', label: 'Diagnostics', icon: Activity },
    { id: 'settings', label: 'Settings', icon: Settings },
    { id: 'about', label: 'Linux Setup', icon: Terminal },
  ];

  return (
    <aside className="w-60 bg-[#0D0B12] border-r border-[#1B1824] flex flex-col justify-between select-none shrink-0 h-screen sticky top-0">
      {/* Top Brand Header */}
      <div>
        <div className="h-16 px-5 flex items-center gap-3 border-b border-[#1B1824]">
          <CentiumLogo size={32} color="#6C4DFF" />
          <div className="flex flex-col">
            <span className="font-semibold text-[15px] tracking-tight text-[#F4F3F7]">
              Centium
            </span>
            <span className="text-[11px] text-[#8E899E] -mt-0.5">
              Tor Privacy VPN
            </span>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="p-3 space-y-1 mt-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentTab === item.id;
            return (
              <button
                key={item.id}
                id={`nav-item-${item.id}`}
                onClick={() => onSelectTab(item.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-[13px] font-medium transition-colors text-left cursor-pointer relative ${
                  isActive
                    ? 'bg-[#17141E] text-[#F4F3F7] font-semibold'
                    : 'text-[#8E899E] hover:text-[#F4F3F7] hover:bg-[#121018]'
                }`}
              >
                {/* Subtle violet indicator on the left for active state */}
                {isActive && (
                  <span className="absolute left-0 top-1.5 bottom-1.5 w-1 bg-[#6C4DFF] rounded-r" />
                )}
                <Icon
                  className={`w-4 h-4 shrink-0 transition-colors ${
                    isActive ? 'text-[#6C4DFF]' : 'text-[#8E899E]'
                  }`}
                />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Bottom Status & System Tray Toggle */}
      <div className="p-3 border-t border-[#1B1824] space-y-2">
        {/* Connection status card */}
        <div className="px-3 py-2.5 rounded-lg bg-[#121018] border border-[#1B1824] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span
              className={`w-2 h-2 rounded-full shrink-0 ${
                isConnected
                  ? 'bg-emerald-400'
                  : isConnecting
                  ? 'bg-[#6C4DFF] animate-pulse'
                  : isError
                  ? 'bg-rose-500'
                  : 'bg-[#555064]'
              }`}
            />
            <span className="text-xs font-medium text-[#F4F3F7]">
              {isConnected
                ? 'Connected'
                : isConnecting
                ? 'Connecting...'
                : isError
                ? 'Error'
                : 'Not connected'}
            </span>
          </div>
          <span className="text-[10px] font-mono text-[#8E899E]">v1.0</span>
        </div>

        {/* System tray simulation toggle */}
        <button
          onClick={onToggleTray}
          className={`w-full flex items-center justify-between px-3 py-1.5 rounded-lg text-xs transition-colors cursor-pointer ${
            showTray
              ? 'bg-[#17141E] text-[#F4F3F7] border border-[#6C4DFF]/30'
              : 'text-[#8E899E] hover:text-[#F4F3F7] hover:bg-[#121018]'
          }`}
          title="Toggle system tray context menu"
        >
          <span className="flex items-center gap-2">
            <Cpu className="w-3.5 h-3.5 text-[#6C4DFF]" />
            <span>Tray Menu</span>
          </span>
          <span className="text-[10px] text-[#555064] font-mono">
            {showTray ? 'Open' : 'Minimized'}
          </span>
        </button>
      </div>
    </aside>
  );
};
