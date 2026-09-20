import React, { useState, useEffect } from 'react';
import { Activity, ArrowLeft, RefreshCw, Check, X, ChevronDown, ChevronUp, Terminal } from 'lucide-react';
import { CentiumStatus, DiagnosticResult } from '../types.ts';
import { runDiagnosticsTest, fetchLogs } from '../api.ts';

interface DiagnosticsViewProps {
  status: CentiumStatus;
  onBack: () => void;
}

export const DiagnosticsView: React.FC<DiagnosticsViewProps> = ({
  status,
  onBack,
}) => {
  const [testing, setTesting] = useState(false);
  const [diagResult, setDiagResult] = useState<DiagnosticResult | null>(null);
  const [showLogs, setShowLogs] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);

  useEffect(() => {
    fetchLogs().then((res) => setLogs(res)).catch(() => {});
  }, [showLogs]);

  const handleRunTest = async () => {
    setTesting(true);
    try {
      const resp = await runDiagnosticsTest();
      if (resp.success) {
        setDiagResult(resp.results);
      }
      const updatedLogs = await fetchLogs();
      setLogs(updatedLogs);
    } catch {
      // ignore
    } finally {
      setTesting(false);
    }
  };

  const torRunning = status.torPid !== null || (diagResult ? diagResult.torRunning : false);
  const isBootstrapped = (diagResult ? diagResult.bootstrap : status.bootstrapPercent) >= 100;
  const isConnected = status.state === 'CONNECTED';
  const isDnsProtected = diagResult ? diagResult.dnsProtected : status.dnsProtected;
  const isIpv6Protected = diagResult ? diagResult.ipv6Protected : status.ipv6Protected;
  const isKillSwitchActive = diagResult ? diagResult.killSwitchActive : status.killSwitchActive;

  const items = [
    {
      label: 'Centium Core',
      statusText: 'Running',
      ok: true,
      detail: 'Local daemon operational',
    },
    {
      label: 'Tor',
      statusText: torRunning ? 'Running' : 'Stopped',
      ok: torRunning,
      detail: torRunning ? `PID ${status.torPid || 'Active'}` : 'Process idle',
    },
    {
      label: 'Tor Bootstrap',
      statusText: isBootstrapped ? 'Complete' : `${status.bootstrapPercent}%`,
      ok: isBootstrapped,
      detail: isBootstrapped ? 'Consensus synchronized' : 'Establishing directory circuit',
    },
    {
      label: 'Virtual Interface',
      statusText: status.virtualInterface || 'centium0',
      ok: isConnected,
      detail: isConnected ? 'Real TUN device' : 'Ready',
    },
    {
      label: 'TUN Bridge',
      statusText: isConnected ? 'hev-socks5-tunnel' : 'Standby',
      ok: isConnected,
      detail: 'centium0 ↔ Tor SOCKS5 :9050',
    },
    {
      label: 'DNS',
      statusText: isDnsProtected ? 'Protected' : 'Unprotected',
      ok: isDnsProtected,
      detail: 'Tunnel Mapped-DNS (198.18.0.2)',
    },
    {
      label: 'IPv6',
      statusText: isIpv6Protected ? 'Protected' : 'Unprotected',
      ok: isIpv6Protected,
      detail: 'nftables fail-closed drop',
    },
    {
      label: 'Kill Switch',
      statusText: isKillSwitchActive || isConnected ? 'Active' : 'Standby',
      ok: isKillSwitchActive || isConnected,
      detail: 'nftables inet centium (Fail-closed)',
    },
  ];

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4 space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-[#1B1824]">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-diagnostics"
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-[#17141E] text-[#8E899E] hover:text-[#F4F3F7] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-[#F4F3F7] flex items-center gap-2">
              <Activity className="w-4 h-4 text-[#6C4DFF]" />
              Diagnostics
            </h2>
            <p className="text-xs text-[#8E899E]">
              Technical utility verifying Tor, DNS, and fail-closed state
            </p>
          </div>
        </div>

        <button
          id="btn-run-diagnostics"
          onClick={handleRunTest}
          disabled={testing}
          className="px-3.5 py-1.5 rounded-lg bg-[#6C4DFF] hover:bg-[#5B3EE0] text-[#F4F3F7] font-medium text-xs transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${testing ? 'animate-spin' : ''}`} />
          <span>{testing ? 'Testing…' : 'Run diagnostics'}</span>
        </button>
      </div>

      {/* Diagnostics Table (Section 15 Specification) */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl divide-y divide-[#1B1824] text-xs">
        {items.map((item, idx) => (
          <div key={idx} className="p-3.5 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                className={`w-4 h-4 rounded-full flex items-center justify-center shrink-0 ${
                  item.ok
                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                    : 'bg-[#17141E] text-[#555064] border border-[#23202E]'
                }`}
              >
                {item.ok ? <Check className="w-2.5 h-2.5 stroke-[3]" /> : <X className="w-2.5 h-2.5 stroke-[3]" />}
              </div>
              <span className="font-medium text-[#F4F3F7]">{item.label}</span>
            </div>

            <div className="flex items-center gap-3">
              <span className="text-[#8E899E] text-[11px]">{item.detail}</span>
              <span
                className={`font-medium ${
                  item.ok ? 'text-emerald-400' : 'text-[#8E899E]'
                }`}
              >
                {item.statusText}
              </span>
            </div>
          </div>
        ))}
      </div>

      {/* Live Verification Output if run */}
      {diagResult && (
        <div className="p-4 rounded-xl bg-[#121018] border border-[#1B1824] space-y-2 text-xs">
          <div className="flex items-center justify-between text-[#8E899E]">
            <span className="text-[#F4F3F7] font-medium">Tor Network Verification Report</span>
            <span className="font-mono">{diagResult.latencyMs}ms</span>
          </div>
          <div className="text-[11px] space-y-1 text-[#8E899E]">
            {diagResult.testDetails.map((line, i) => (
              <div key={i}>{line}</div>
            ))}
          </div>
          {diagResult.verifiedExitIp && (
            <div className="pt-2 border-t border-[#1B1824] flex items-center justify-between text-[11px]">
              <span className="text-[#8E899E]">Exit Node IP:</span>
              <span className="font-mono font-medium text-[#F4F3F7]">
                {diagResult.verifiedExitIp} ({diagResult.isTorExit ? 'Confirmed Tor' : 'Non-Tor'})
              </span>
            </div>
          )}
        </div>
      )}

      {/* Expandable Technical Logs Section */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl overflow-hidden">
        <button
          onClick={() => setShowLogs(!showLogs)}
          className="w-full px-4 py-3 flex items-center justify-between text-xs font-medium text-[#8E899E] hover:text-[#F4F3F7] cursor-pointer transition-colors"
        >
          <span className="flex items-center gap-2">
            <Terminal className="w-3.5 h-3.5 text-[#6C4DFF]" />
            <span>Technical Daemon Logs</span>
          </span>
          {showLogs ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>

        {showLogs && (
          <div className="p-3 border-t border-[#1B1824] bg-[#0D0B12] max-h-56 overflow-y-auto font-mono text-[11px] text-[#8E899E] space-y-1">
            {logs.length === 0 ? (
              <div className="text-[#555064]">No log entries available.</div>
            ) : (
              logs.map((log, i) => (
                <div key={i} className="truncate">
                  {log}
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};
