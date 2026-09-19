import React from 'react';
import { Shield, Globe, Settings, Activity, Terminal, Key } from 'lucide-react';
import { CentiumLogo } from './CentiumLogo.tsx';
import { ConnectionState } from '../types.ts';

interface MobileNavProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
  connectionState: ConnectionState;
}

export const MobileNav: React.FC<MobileNavProps> = ({
  currentTab,
  onSelectTab,
  connectionState,
}) => {
  const isConnected = connectionState === 'CONNECTED';
  const isConnecting = connectionState === 'CONNECTING' || connectionState === 'STARTING';
  const isError = connectionState === 'ERROR';

  const items = [
    { id: 'dashboard', label: 'Home', icon: Shield },
    { id: 'locations', label: 'Locations', icon: Globe },
    { id: 'bridges', label: 'Bridges', icon: Key },
    { id: 'diagnostics', label: 'Diagnostics', icon: Activity },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <>
      {/* Mobile Top Header */}
      <div className="md:hidden h-14 px-4 bg-[#0D0B12] border-b border-[#1B1824] flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-2.5">
          <CentiumLogo size={24} color="#6C4DFF" />
          <span className="font-semibold text-sm tracking-tight text-[#F4F3F7]">
            Centium
          </span>
        </div>
        <div className="flex items-center gap-2 px-2.5 py-1 rounded-full bg-[#121018] border border-[#1B1824]">
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
          <span className="text-[11px] font-medium text-[#F4F3F7]">
            {isConnected
              ? 'Connected'
              : isConnecting
              ? 'Connecting'
              : isError
              ? 'Error'
              : 'Not connected'}
          </span>
        </div>
      </div>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 bg-[#0D0B12]/95 backdrop-blur-sm border-t border-[#1B1824] flex items-center justify-around px-2 z-40">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectTab(item.id)}
              className={`flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-md transition-colors cursor-pointer ${
                isActive ? 'text-[#6C4DFF]' : 'text-[#8E899E] hover:text-[#F4F3F7]'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span className={`text-[10px] ${isActive ? 'font-semibold' : 'font-normal'}`}>
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
