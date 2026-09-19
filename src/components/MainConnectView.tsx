import React, { useState, useEffect } from 'react';
import { ShieldCheck, ShieldAlert, ShieldX, RefreshCw, Globe, Clock, ArrowRight, AlertTriangle, Terminal, ExternalLink } from 'lucide-react';
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

  // Connection timer update
  useEffect(() => {
    if (!status.connectedSince || status.state !== 'CONNECTED') {
      setUptimeStr('00:00:00');
      return;
    }

    const updateTimer = () => {
      const now = Date.now();
      const diffSec = Math.max(0, Math.floor((now - status.connectedSince!) / 1000));
      const hours = Math.floor(diffSec / 3600).toString().padStart(2, '0');
      const mins = Math.floor((diffSec % 3600) / 60).toString().padStart(2, '0');
      const secs = (diffSec % 60).toString().padStart(2, '0');
      setUptimeStr(`${hours}:${mins}:${secs}`);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [status.connectedSince, status.state]);

  const isTransitioning =
    status.state === 'STARTING' ||
    status.state === 'CONNECTING' ||
    status.state === 'DISCONNECTING' ||
    status.state === 'RECONNECTING';

  const isConnected = status.state === 'CONNECTED';

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await onRefreshCircuit();
    setIsRefreshing(false);
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
    <div className="w-full max-w-md mx-auto py-8 px-4 flex flex-col items-center">
      {/* Visual Status Indicator Node */}
      <div className="relative my-8 flex items-center justify-center">
        {/* Outer ambient glow */}
        <div
          className={`absolute w-32 h-32 rounded-full transition-all duration-700 pointer-events-none ${
            isConnected
              ? 'bg-emerald-500/10 blur-xl scale-110'
              : isTransitioning
              ? 'bg-amber-500/15 blur-xl animate-pulse'
              : status.state === 'ERROR'
              ? 'bg-rose-500/15 blur-xl'
              : 'bg-zinc-800/20 blur-md'
          }`}
        />

        {/* Outer Ring */}
        <div
          className={`w-28 h-28 rounded-full border flex items-center justify-center transition-all duration-500 ${
            isConnected
              ? 'border-emerald-500/30 bg-emerald-950/20'
              : isTransitioning
              ? 'border-amber-500/40 bg-amber-950/20 animate-spin-slow'
              : status.state === 'ERROR'
              ? 'border-rose-500/40 bg-rose-950/20'
              : 'border-zinc-800 bg-zinc-900/40'
          }`}
        >
          {/* Inner core circle */}
          <div
            className={`w-12 h-12 rounded-full flex items-center justify-center transition-all duration-500 ${
              isConnected
                ? 'bg-emerald-400 text-zinc-950 shadow-[0_0_20px_rgba(52,211,153,0.5)]'
                : isTransitioning
                ? 'bg-amber-400 text-zinc-950 animate-pulse'
                : status.state === 'ERROR'
                ? 'bg-rose-500 text-zinc-100'
                : 'bg-zinc-700 text-zinc-400'
            }`}
          >
            {isConnected ? (
              <ShieldCheck className="w-6 h-6" />
            ) : status.state === 'ERROR' ? (
              <ShieldAlert className="w-6 h-6" />
            ) : (
              <ShieldX className="w-6 h-6" />
            )}
          </div>
        </div>
      </div>

      {/* State Headline & Subtitle */}
      <div className="text-center space-y-1 mb-8">
        <h2
          className={`text-2xl font-bold tracking-wider uppercase font-mono ${
            isConnected
              ? 'text-emerald-400'
              : isTransitioning
              ? 'text-amber-400'
              : status.state === 'ERROR'
              ? 'text-rose-400'
              : 'text-zinc-200'
          }`}
        >
          {status.state}
        </h2>
        <p className="text-sm text-zinc-400 font-normal">
          {isConnected && 'Protected by Tor'}
          {status.state === 'DISCONNECTED' && 'Your connection is not protected'}
          {status.state === 'STARTING' && 'Starting Tor...'}
          {status.state === 'CONNECTING' &&
            (status.bootstrapPercent > 0
              ? `Building secure circuit... (${status.bootstrapPercent}%)`
              : 'Building secure circuit...')}
          {status.state === 'DISCONNECTING' && 'Disconnecting and restoring routing...'}
          {status.state === 'RECONNECTING' && 'Reconnecting to Tor network...'}
          {status.state === 'ERROR' && (status.errorMessage || 'Connection failed')}
        </p>
      </div>

      {/* Connection Progress Detail (During Transition) */}
      {isTransitioning && (
        <div className="w-full bg-zinc-900 border border-zinc-800 rounded-lg p-3.5 mb-6 space-y-2">
          <div className="flex items-center justify-between text-xs text-zinc-400">
            <span className="font-mono">Step {status.currentStep} of {status.totalSteps}</span>
            <span className="font-medium text-amber-400 font-mono">{status.bootstrapPercent}%</span>
          </div>
          {/* Progress bar */}
          <div className="w-full bg-zinc-800 h-1.5 rounded-full overflow-hidden">
            <div
              className="bg-amber-400 h-full rounded-full transition-all duration-300"
              style={{ width: `${Math.max(5, status.bootstrapPercent)}%` }}
            />
          </div>
          <p className="text-xs text-zinc-300 font-mono truncate">
            {status.stepDescription || 'Negotiating Tor rendezvous...'}
          </p>
        </div>
      )}

      {/* Error Alert Box */}
      {status.state === 'ERROR' && (
        <div className="w-full bg-rose-950/40 border border-rose-900/60 rounded-lg p-3.5 mb-6 text-xs text-rose-300 flex items-start gap-2.5">
          <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400 mt-0.5" />
          <div className="space-y-1">
            <div className="font-semibold text-rose-200">Connection Failed</div>
            <div>{status.errorMessage || 'Unable to establish onion circuit.'}</div>
            <div className="text-[11px] text-rose-400/80 pt-1">
              Kill switch prevented unprotected traffic leakage.
            </div>
          </div>
        </div>
      )}

      {/* Big Action Button: [ CONNECT ] or [ DISCONNECT ] */}
      <div className="w-full mb-8">
        {isConnected ? (
          <button
            id="btn-disconnect"
            onClick={onDisconnect}
            disabled={isTransitioning}
            className="w-full py-3.5 px-6 rounded-lg text-sm font-semibold tracking-wider uppercase font-mono bg-zinc-800 hover:bg-rose-950/60 hover:text-rose-300 text-zinc-200 border border-zinc-700 hover:border-rose-700 transition-all duration-200 cursor-pointer shadow-sm active:scale-[0.99] disabled:opacity-50 disabled:pointer-events-none"
          >
            {isTransitioning ? 'Disconnecting...' : 'DISCONNECT'}
          </button>
        ) : (
          <button
            id="btn-connect"
            onClick={onConnect}
            disabled={isTransitioning}
            className="w-full py-3.5 px-6 rounded-lg text-sm font-semibold tracking-wider uppercase font-mono bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-bold transition-all duration-200 cursor-pointer shadow-md hover:shadow-emerald-500/20 active:scale-[0.99] disabled:opacity-50 disabled:pointer-events-none"
          >
            {isTransitioning ? 'Connecting...' : 'CONNECT'}
          </button>
        )}
      </div>

      {/* Information Cards (Section 4 layout) */}
      <div className="w-full bg-zinc-900/90 border border-zinc-800/90 rounded-xl p-4 divide-y divide-zinc-800/60 text-xs">
        {/* Row 1: Exit location */}
        <div className="pb-3 flex items-center justify-between">
          <div className="flex items-center gap-2 text-zinc-400">
            <Globe className="w-4 h-4 text-zinc-500" />
            <span>Exit location</span>
          </div>
          <button
            id="btn-change-exit"
            onClick={onNavigateToExit}
            className="flex items-center gap-1.5 font-medium text-zinc-200 hover:text-emerald-400 transition-colors cursor-pointer group"
          >
            <span>{getExitCountryLabel()}</span>
            <ArrowRight className="w-3 h-3 text-zinc-500 group-hover:translate-x-0.5 transition-transform" />
          </button>
        </div>

        {/* Row 2: Status / Connection Duration */}
        {isConnected ? (
          <div className="py-3 flex items-center justify-between">
            <div className="flex items-center gap-2 text-zinc-400">
              <Clock className="w-4 h-4 text-zinc-500" />
              <span>Connection</span>
            </div>
            <div className="font-mono text-zinc-200 font-medium">{uptimeStr}</div>
          </div>
        ) : (
          <div className="py-3 flex items-center justify-between">
            <div className="flex items-center gap-2 text-zinc-400">
              <ShieldCheck className="w-4 h-4 text-zinc-500" />
              <span>Tor Network</span>
            </div>
            <div className="font-mono text-zinc-300 font-medium">Ready</div>
          </div>
        )}

        {/* Row 3: Public IP and Circuit Refresh (when connected) */}
        {isConnected && (
          <div className="pt-3 flex items-center justify-between">
            <div className="space-y-0.5">
              <div className="text-zinc-500 text-[11px]">Tor Exit IP</div>
              <div className="font-mono text-zinc-200 text-xs font-semibold">
                {status.publicIp || 'Protected Relay'}
              </div>
            </div>
            <button
              id="btn-refresh-circuit"
              onClick={handleRefresh}
              disabled={isRefreshing}
              title="Request New Tor Circuit (SIGNAL NEWNYM)"
              className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-300 text-[11px] font-mono transition-colors disabled:opacity-50 cursor-pointer"
            >
              <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>New Circuit</span>
            </button>
          </div>
        )}

        {/* Row 4: Network interface info */}
        <div className="pt-3 flex items-center justify-between text-[11px]">
          <span className="text-zinc-500">Routing Interface</span>
          <span className="font-mono text-zinc-400">{status.virtualInterface} (Transparent)</span>
        </div>
      </div>

      {/* Linux Host Integration Helper Banner */}
      {onNavigateToPackages && (
        <div className="w-full mt-4 p-3 rounded-lg bg-zinc-900/40 border border-zinc-800 text-xs text-zinc-400 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <Terminal className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
            <span className="text-[11px]">
              Deploy to your physical Linux desktop: <code className="text-zinc-200 font-mono">sudo ./setup-linux.sh</code>
            </span>
          </div>
          <button
            onClick={onNavigateToPackages}
            className="text-[11px] font-medium text-emerald-400 hover:text-emerald-300 whitespace-nowrap flex items-center gap-1 cursor-pointer"
          >
            <span>Guide</span>
            <ExternalLink className="w-3 h-3" />
          </button>
        </div>
      )}
    </div>
  );
};
