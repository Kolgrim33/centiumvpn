import React, { useState, useEffect } from 'react';
import { Terminal, Shield, RefreshCw, ArrowLeft, ArrowRight, Activity, Download, Upload, Copy, Check } from 'lucide-react';
import { CentiumStatus } from '../types.ts';
import { fetchLogs } from '../api.ts';

interface NetworkInfoViewProps {
  status: CentiumStatus;
  onBack: () => void;
  onRefreshCircuit: () => Promise<void>;
}

export const NetworkInfoView: React.FC<NetworkInfoViewProps> = ({
  status,
  onBack,
  onRefreshCircuit,
}) => {
  const [logs, setLogs] = useState<string[]>([]);
  const [refreshingCircuit, setRefreshingCircuit] = useState(false);
  const [copiedIp, setCopiedIp] = useState(false);
  const [sessionUptime, setSessionUptime] = useState('00:00:00');

  useEffect(() => {
    let mounted = true;
    const loadLogs = async () => {
      try {
        const lines = await fetchLogs();
        if (mounted) setLogs(lines);
      } catch {
        // ignore
      }
    };
    loadLogs();
    const interval = setInterval(loadLogs, 3000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (!status.connectedSince || status.state !== 'CONNECTED') {
      setSessionUptime('00:00:00');
      return;
    }
    const update = () => {
      const diff = Math.max(0, Math.floor((Date.now() - status.connectedSince!) / 1000));
      const h = Math.floor(diff / 3600).toString().padStart(2, '0');
      const m = Math.floor((diff % 3600) / 60).toString().padStart(2, '0');
      const s = (diff % 60).toString().padStart(2, '0');
      setSessionUptime(`${h}:${m}:${s}`);
    };
    update();
    const timer = setInterval(update, 1000);
    return () => clearInterval(timer);
  }, [status.connectedSince, status.state]);

  const handleRefresh = async () => {
    setRefreshingCircuit(true);
    await onRefreshCircuit();
    setRefreshingCircuit(false);
  };

  const handleCopyIp = () => {
    if (status.publicIp) {
      navigator.clipboard.writeText(status.publicIp);
      setCopiedIp(true);
      setTimeout(() => setCopiedIp(false), 2000);
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-network"
            onClick={onBack}
            className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <Terminal className="w-4 h-4 text-emerald-400" />
              Network Information & Circuit
            </h2>
            <p className="text-xs text-zinc-400">
              Live onion circuit node telemetry and daemon logs
            </p>
          </div>
        </div>

        {status.state === 'CONNECTED' && (
          <button
            id="btn-circuit-newnym"
            onClick={handleRefresh}
            disabled={refreshingCircuit}
            className="px-3 py-1.5 rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-mono flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-3 h-3 ${refreshingCircuit ? 'animate-spin' : ''}`} />
            <span>New Circuit</span>
          </button>
        )}
      </div>

      {/* Connection Info Block (Section 17 specification) */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 space-y-3 font-mono text-xs">
        <div className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">
          Connection Overview
        </div>

        <div className="divide-y divide-zinc-800/80">
          <div className="py-2 flex items-center justify-between">
            <span className="text-zinc-400">Status</span>
            <span
              className={`font-semibold ${
                status.state === 'CONNECTED' ? 'text-emerald-400' : 'text-zinc-400'
              }`}
            >
              {status.state}
            </span>
          </div>

          <div className="py-2 flex items-center justify-between">
            <span className="text-zinc-400">Network</span>
            <span className="text-zinc-200">Tor (Onion Routing)</span>
          </div>

          <div className="py-2 flex items-center justify-between">
            <span className="text-zinc-400">Exit country</span>
            <span className="text-zinc-200">
              {status.exitCountry || 'Automatic'}
              {status.exitCountryCode ? ` (${status.exitCountryCode})` : ''}
            </span>
          </div>

          <div className="py-2 flex items-center justify-between">
            <span className="text-zinc-400">VPN interface</span>
            <span className="text-zinc-200">{status.virtualInterface}</span>
          </div>

          <div className="py-2 flex items-center justify-between">
            <span className="text-zinc-400">Session</span>
            <span className="text-zinc-200">{sessionUptime}</span>
          </div>

          {status.publicIp && (
            <div className="py-2 flex items-center justify-between">
              <span className="text-zinc-400">Current public IP</span>
              <div className="flex items-center gap-2">
                <span className="text-emerald-400 font-bold">{status.publicIp}</span>
                <button
                  onClick={handleCopyIp}
                  className="p-1 text-zinc-400 hover:text-zinc-200 rounded hover:bg-zinc-800 transition-colors"
                  title="Copy IP"
                >
                  {copiedIp ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Traffic Statistics */}
        {status.state === 'CONNECTED' && (
          <div className="pt-2 border-t border-zinc-800 grid grid-cols-2 gap-3 text-[11px]">
            <div className="flex items-center gap-2 p-2 rounded bg-zinc-950 border border-zinc-800/80">
              <Download className="w-3.5 h-3.5 text-emerald-400" />
              <div>
                <div className="text-zinc-500">Downloaded</div>
                <div className="text-zinc-200 font-semibold">{formatBytes(status.bytesReceived)}</div>
              </div>
            </div>
            <div className="flex items-center gap-2 p-2 rounded bg-zinc-950 border border-zinc-800/80">
              <Upload className="w-3.5 h-3.5 text-blue-400" />
              <div>
                <div className="text-zinc-500">Uploaded</div>
                <div className="text-zinc-200 font-semibold">{formatBytes(status.bytesSent)}</div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 3-Hop Tor Circuit Display */}
      {status.circuit && status.circuit.length > 0 && (
        <div className="bg-zinc-900/90 border border-zinc-800 rounded-xl p-4 space-y-3 font-mono text-xs">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wider flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-emerald-400" />
              Active Tor 3-Hop Circuit
            </span>
            <span className="text-[10px] text-zinc-500">128-bit layered AES</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {status.circuit.map((node, index) => (
              <div
                key={index}
                className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800 space-y-1"
              >
                <div className="flex items-center justify-between text-[10px]">
                  <span className="text-emerald-400 font-bold uppercase">{node.role}</span>
                  <span className="text-zinc-500">{node.countryCode}</span>
                </div>
                <div className="text-zinc-200 font-semibold truncate text-[11px]">
                  {node.nickname}
                </div>
                <div className="text-[10px] text-zinc-500 truncate">{node.country}</div>
                <div className="text-[9px] text-zinc-600 truncate font-mono">{node.fingerprint}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Live Tor Daemon Logs Terminal */}
      <div className="bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden font-mono text-xs">
        <div className="bg-zinc-900/90 px-4 py-2.5 border-b border-zinc-800 flex items-center justify-between">
          <span className="text-[11px] text-zinc-300 font-semibold flex items-center gap-2">
            <Activity className="w-3.5 h-3.5 text-emerald-400" />
            Centium Daemon & Tor Process Logs
          </span>
          <span className="text-[10px] text-zinc-500">Auto-refresh (3s)</span>
        </div>

        <div className="p-3 h-48 overflow-y-auto space-y-1 text-[11px] text-zinc-400 leading-relaxed font-mono">
          {logs.length === 0 ? (
            <div className="text-zinc-600">Waiting for daemon event stream...</div>
          ) : (
            logs.map((line, idx) => (
              <div
                key={idx}
                className={`truncate ${
                  line.includes('[Error]') || line.includes('[ALERT]')
                    ? 'text-rose-400'
                    : line.includes('[Tor Bootstrap 100%]') || line.includes('[Step 11/11]')
                    ? 'text-emerald-400 font-semibold'
                    : line.includes('[Step')
                    ? 'text-amber-400'
                    : 'text-zinc-400'
                }`}
              >
                {line}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
