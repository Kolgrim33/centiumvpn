import React, { useState, useEffect } from 'react';
import { ShieldCheck, ShieldAlert, Check, RefreshCw, Globe, Copy, ExternalLink, Terminal } from 'lucide-react';
import { CentiumLogo } from './CentiumLogo.tsx';
import { CentiumStatus, CentiumConfig } from '../types.ts';

interface MainConnectViewProps {
  status: CentiumStatus;
  config: CentiumConfig;
  onConnect: () => void;
  onDisconnect: () => void;
  onRefreshCircuit: () => void;
  onNavigateToExit: () => void;
  onNavigateToPackages?: () => void;
}

export const MainConnectView: React.FC<MainConnectViewProps> = ({
  status,
  config,
  onConnect,
  onDisconnect,
  onRefreshCircuit,
  onNavigateToExit,
  onNavigateToPackages,
}) => {
  const [uptimeStr, setUptimeStr] = useState('00:00:00');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [copiedIp, setCopiedIp] = useState(false);

  useEffect(() => {
    if (!status.connectedSince || status.state !== 'CONNECTED') {
      setUptimeStr('00:00:00');
      return;
    }
    const updateTimer = () => {
      const elapsed = Math.max(0, Math.floor((Date.now() - status.connectedSince!) / 1000));
      const hours = Math.floor(elapsed / 3600).toString().padStart(2, '0');
      const minutes = Math.floor((elapsed % 3600) / 60).toString().padStart(2, '0');
      const seconds = (elapsed % 60).toString().padStart(2, '0');
      setUptimeStr(`${hours}:${minutes}:${seconds}`);
    };
    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [status.connectedSince, status.state]);

  const isConnected = status.state === 'CONNECTED';
  const isConnecting =
    status.state === 'STARTING_TOR' ||
    status.state === 'WAITING_FOR_BOOTSTRAP' ||
    status.state === 'STARTING_TUN' ||
    status.state === 'STARTING_BRIDGE' ||
    status.state === 'INSTALLING_ROUTING' ||
    status.state === 'INSTALLING_KILLSWITCH' ||
    status.state === 'VERIFYING' ||
    status.state === 'CONNECTING' ||
    status.state === 'STARTING';
  const isDisconnecting = status.state === 'DISCONNECTING';
  const isError = status.state === 'ERROR';

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await onRefreshCircuit();
    setIsRefreshing(false);
  };

  const handleCopyIp = () => {
    if (status.publicIp) {
      navigator.clipboard.writeText(status.publicIp);
      setCopiedIp(true);
      setTimeout(() => setCopiedIp(false), 2000);
    }
  };

  const getExitCountryLabel = () => {
    if (status.exitCountry) {
      return `${status.exitCountry}${status.exitCountryCode ? ` (${status.exitCountryCode})` : ''}`;
    }
    if (config.exitLocation && config.exitLocation !== 'auto' && config.exitLocation !== 'any') {
      return config.exitLocation.toUpperCase();
    }
    return 'Automatic';
  };

  return (
    <div className="w-full max-w-lg mx-auto py-6 px-4 flex flex-col items-center">
      {/* Centium Emblem Logo */}
      <div className="mb-5 flex flex-col items-center">
        <div className="p-4 rounded-2xl bg-[#121018] border border-[#1B1824] mb-3">
          <CentiumLogo
            size={72}
            color={isConnected ? '#6C4DFF' : isConnecting ? '#6C4DFF' : isError ? '#EF4444' : '#555064'}
            animated={true}
          />
        </div>

        {/* Brand & Connection State Headline (Section 7, 8, 9) */}
        <div className="text-center">
          <h1 className="text-lg font-bold text-[#F4F3F7] tracking-tight">
            Centium
          </h1>
          <div className="mt-1 flex items-center justify-center gap-2">
            <span
              className={`w-2 h-2 rounded-full ${
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
              className={`text-sm font-medium ${
                isConnected
                  ? 'text-emerald-400'
                  : isConnecting
                  ? 'text-[#6C4DFF]'
                  : isError
                  ? 'text-rose-400'
                  : 'text-[#8E899E]'
              }`}
            >
              {isConnected
                ? 'Connected'
                : isConnecting
                ? 'Connecting to Tor'
                : isDisconnecting
                ? 'Disconnecting'
                : isError
                ? 'Connection error'
                : 'Not connected'}
            </span>
          </div>

          <p className="mt-1.5 text-xs text-[#8E899E] max-w-xs mx-auto">
            {isConnected
              ? 'Tor connection active. System traffic is routed through encrypted onion relays.'
              : isConnecting
              ? status.statusMessage || 'Establishing encrypted circuit with Tor directory authorities...'
              : 'Your traffic is not currently routed through Tor.'}
          </p>
        </div>
      </div>

      {/* Primary Connect / Disconnect Action Button (Section 11) */}
      <div className="w-full max-w-sm mb-6">
        {!isConnected ? (
          <button
            id="btn-main-connect"
            onClick={onConnect}
            disabled={isConnecting || isDisconnecting}
            className="w-full h-12 rounded-xl bg-[#6C4DFF] hover:bg-[#5B3EE0] active:bg-[#5146D8] text-[#F4F3F7] font-semibold text-sm tracking-wide transition-all shadow-sm cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {isConnecting ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin text-[#F4F3F7]" />
                <span>Connecting…</span>
              </>
            ) : (
              <span>Connect</span>
            )}
          </button>
        ) : (
          <button
            id="btn-main-disconnect"
            onClick={onDisconnect}
            disabled={isDisconnecting}
            className="w-full h-12 rounded-xl bg-[#17141E] hover:bg-[#201C2B] text-[#F4F3F7] border border-[#23202E] font-semibold text-sm tracking-wide transition-colors cursor-pointer flex items-center justify-center gap-2"
          >
            {isDisconnecting ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin text-[#8E899E]" />
                <span>Disconnecting…</span>
              </>
            ) : (
              <span>Disconnect</span>
            )}
          </button>
        )}
      </div>

      {/* Progress & Bootstrap Details (when connecting) */}
      {isConnecting && (
        <div className="w-full max-w-sm mb-6 p-3.5 rounded-xl bg-[#121018] border border-[#1B1824] space-y-2 text-xs">
          <div className="flex items-center justify-between text-[#8E899E]">
            <span>Tor Network Bootstrap</span>
            <span className="font-mono text-[#F4F3F7] font-semibold">
              {status.bootstrapPercent}%
            </span>
          </div>
          {/* Restrained progress bar */}
          <div className="w-full h-1.5 bg-[#17141E] rounded-full overflow-hidden">
            <div
              className="h-full bg-[#6C4DFF] transition-all duration-300 rounded-full"
              style={{ width: `${Math.max(5, status.bootstrapPercent)}%` }}
            />
          </div>
          <div className="text-[11px] text-[#8E899E] truncate">
            {status.stepDescription || 'Building 3-hop onion circuit...'}
          </div>
        </div>
      )}

      {/* Active Session & Exit Relay Card (when connected) */}
      {isConnected && (
        <div className="w-full max-w-sm mb-6 p-4 rounded-xl bg-[#121018] border border-[#1B1824] space-y-3 text-xs">
          <div className="flex items-center justify-between pb-2.5 border-b border-[#1B1824]">
            <div className="flex items-center gap-2">
              <Globe className="w-4 h-4 text-[#6C4DFF]" />
              <span className="text-[#8E899E]">Exit location</span>
            </div>
            <button
              onClick={onNavigateToExit}
              className="font-medium text-[#F4F3F7] hover:text-[#6C4DFF] flex items-center gap-1 transition-colors cursor-pointer"
            >
              <span>{getExitCountryLabel()}</span>
              <span className="text-[10px] text-[#8E899E]">Change</span>
            </button>
          </div>

          <div className="flex items-center justify-between pb-2.5 border-b border-[#1B1824]">
            <span className="text-[#8E899E]">Tor Circuit</span>
            <span className="text-emerald-400 font-medium flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              Tor circuit active
            </span>
          </div>

          <div className="flex items-center justify-between pb-2.5 border-b border-[#1B1824]">
            <span className="text-[#8E899E]">Exit IP</span>
            <div className="flex items-center gap-2">
              <span className="font-mono font-medium text-[#F4F3F7]">
                {status.publicIp || 'Tor Relay Verified'}
              </span>
              {status.publicIp && (
                <button
                  onClick={handleCopyIp}
                  className="p-1 text-[#8E899E] hover:text-[#F4F3F7] rounded transition-colors cursor-pointer"
                  title="Copy IP"
                >
                  {copiedIp ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                </button>
              )}
            </div>
          </div>

          <div className="flex items-center justify-between pb-2.5 border-b border-[#1B1824]">
            <span className="text-[#8E899E]">Protocol</span>
            <span className="text-[#F4F3F7] font-medium">
              Tor (TUN / SOCKS5)
            </span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-[#8E899E]">Session duration</span>
            <div className="flex items-center gap-3">
              <span className="font-mono text-[#F4F3F7]">{uptimeStr}</span>
              <button
                onClick={handleRefresh}
                disabled={isRefreshing}
                title="Request New Tor Circuit"
                className="text-[11px] text-[#6C4DFF] hover:text-[#8E75FF] font-medium flex items-center gap-1 cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin' : ''}`} />
                <span>New circuit</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Compact Privacy & Security Status (Section 13) */}
      <div className="w-full max-w-sm p-4 rounded-xl bg-[#121018] border border-[#1B1824] space-y-2.5 text-xs">
        <div className="text-[11px] font-semibold text-[#8E899E] uppercase tracking-wider mb-1">
          Privacy & Security
        </div>

        <div className="flex items-center justify-between">
          <span className="text-[#8E899E]">Tor connection</span>
          <span
            className={`font-medium ${
              isConnected ? 'text-emerald-400' : 'text-[#8E899E]'
            }`}
          >
            {isConnected ? 'Active' : 'Available'}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-[#8E899E]">Kill switch</span>
          <span
            className={`font-medium ${
              status.killSwitchActive || isConnected ? 'text-emerald-400' : 'text-[#8E899E]'
            }`}
          >
            {status.killSwitchActive || isConnected ? 'Active' : 'Ready'}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-[#8E899E]">DNS protection</span>
          <span
            className={`font-medium ${
              status.dnsProtected ? 'text-emerald-400' : 'text-[#8E899E]'
            }`}
          >
            {status.dnsProtected ? 'Active' : 'Ready'}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <span className="text-[#8E899E]">IPv6 protection</span>
          <span
            className={`font-medium ${
              status.ipv6Protected ? 'text-emerald-400' : 'text-[#8E899E]'
            }`}
          >
            {status.ipv6Protected ? 'Active' : 'Ready'}
          </span>
        </div>

        <div className="flex items-center justify-between pt-1 border-t border-[#1B1824]">
          <span className="text-[#8E899E]">Centium traffic logging</span>
          <span className="font-medium text-[#F4F3F7]">
            Disabled (Zero Proxy)
          </span>
        </div>
      </div>

      {/* Linux Physical Desktop Integration Card */}
      {onNavigateToPackages && (
        <div className="w-full max-w-sm mt-4 p-3 rounded-xl bg-[#121018] border border-[#1B1824] text-xs flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <Terminal className="w-4 h-4 text-[#6C4DFF] shrink-0" />
            <div className="text-[11px] text-[#8E899E] leading-tight">
              Run on your Linux system:{' '}
              <code className="text-[#F4F3F7] font-mono">sudo ./setup-linux.sh</code>
            </div>
          </div>
          <button
            onClick={onNavigateToPackages}
            className="text-[11px] text-[#6C4DFF] hover:text-[#8E75FF] font-medium shrink-0 cursor-pointer"
          >
            Guide →
          </button>
        </div>
      )}
    </div>
  );
};
