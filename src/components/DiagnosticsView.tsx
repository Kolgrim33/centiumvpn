import React, { useState } from 'react';
import { Activity, Check, X, ArrowLeft, RefreshCw, AlertTriangle, ShieldCheck, Zap } from 'lucide-react';
import { CentiumStatus, DiagnosticResult } from '../types.ts';
import { runDiagnosticsTest } from '../api.ts';

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
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleRunTest = async () => {
    setTesting(true);
    setErrorMsg(null);
    try {
      const resp = await runDiagnosticsTest();
      if (resp.success) {
        setDiagResult(resp.results);
      }
    } catch (e: any) {
      setErrorMsg(e.message || 'Diagnostic execution failed');
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
  const isTrafficRouted = diagResult ? diagResult.trafficRouted : isConnected;

  const items = [
    {
      label: 'Tor:',
      statusText: torRunning ? 'Running' : 'Stopped',
      ok: torRunning,
      detail: torRunning ? `PID ${status.torPid || 'active'}` : 'Process not started',
    },
    {
      label: 'Bootstrap:',
      statusText: `${diagResult ? diagResult.bootstrap : status.bootstrapPercent}%`,
      ok: isBootstrapped,
      detail: isBootstrapped ? 'Network consensus synchronized' : 'Establishing directory consensus',
    },
    {
      label: 'Network:',
      statusText: isConnected ? 'Connected' : 'Disconnected',
      ok: isConnected,
      detail: isConnected ? `Interface ${status.virtualInterface}` : 'Normal internet route',
    },
    {
      label: 'DNS:',
      statusText: isDnsProtected ? 'Protected' : 'Unprotected',
      ok: isDnsProtected,
      detail: isDnsProtected ? 'Redirected to Tor DNSPort (5353)' : 'Using ISP nameserver',
    },
    {
      label: 'IPv6:',
      statusText: isIpv6Protected ? 'Protected' : 'Unprotected',
      ok: isIpv6Protected,
      detail: isIpv6Protected ? 'Non-Tor IPv6 egress blocked' : 'IPv6 bypass risk',
    },
    {
      label: 'Kill switch:',
      statusText: isKillSwitchActive ? 'Active' : 'Disabled',
      ok: isKillSwitchActive,
      detail: isKillSwitchActive ? 'Fail-closed iptables filter rules' : 'Unarmed',
    },
    {
      label: 'Traffic:',
      statusText: isTrafficRouted ? 'Routed through Tor' : 'Not routed',
      ok: isTrafficRouted,
      detail: isTrafficRouted
        ? `Verified Tor exit IP (${status.publicIp || 'Relay verified'})`
        : 'Direct internet route',
    },
  ];

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4">
      {/* Header */}
      <div className="flex items-center gap-3 pb-4 border-b border-zinc-800 mb-6">
        <button
          id="btn-back-from-diagnostics"
          onClick={onBack}
          className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
            <Activity className="w-4 h-4 text-emerald-400" />
            Diagnostics & Health
          </h2>
          <p className="text-xs text-zinc-400">
            Real-time verification of Tor routing, DNS integrity, and fail-closed state
          </p>
        </div>
      </div>

      {/* Main Checklist Card (Section 25 layout) */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 mb-6 space-y-4">
        <div className="text-xs font-semibold text-zinc-300 uppercase tracking-wider font-mono">
          System Verification Status
        </div>

        <div className="divide-y divide-zinc-800/70">
          {items.map((item, idx) => (
            <div key={idx} className="py-2.5 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div
                  className={`w-4 h-4 rounded-full flex items-center justify-center shrink-0 ${
                    item.ok
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                      : 'bg-zinc-800 text-zinc-500 border border-zinc-700'
                  }`}
                >
                  {item.ok ? <Check className="w-3 h-3 stroke-[3]" /> : <X className="w-3 h-3 stroke-[3]" />}
                </div>
                <span className="text-xs font-medium text-zinc-300 font-mono">{item.label}</span>
                <span
                  className={`text-xs font-medium ${
                    item.ok ? 'text-emerald-400' : 'text-zinc-400'
                  }`}
                >
                  {item.statusText}
                </span>
              </div>
              <span className="text-[11px] text-zinc-500 font-mono">{item.detail}</span>
            </div>
          ))}
        </div>

        {/* Live Test Run Button */}
        <div className="pt-3 border-t border-zinc-800 flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="text-[11px] text-zinc-400">
            Verifies actual packet egress through Tor Project directory consensus.
          </div>
          <button
            id="btn-run-connection-test"
            onClick={handleRunTest}
            disabled={testing}
            className="w-full sm:w-auto px-4 py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold text-xs font-mono tracking-wider uppercase transition-colors cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2"
          >
            {testing ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                <span>TESTING TUNNEL...</span>
              </>
            ) : (
              <>
                <Zap className="w-3.5 h-3.5" />
                <span>RUN CONNECTION TEST</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Test Error Message */}
      {errorMsg && (
        <div className="p-3.5 rounded-lg bg-rose-950/40 border border-rose-900/60 text-xs text-rose-300 flex items-center gap-2 mb-6">
          <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Test Results Output */}
      {diagResult && (
        <div className="bg-zinc-950 border border-zinc-800 rounded-xl p-4 space-y-3 font-mono text-xs">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
            <span className="text-emerald-400 font-semibold flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4" /> Live Verification Report
            </span>
            <span className="text-zinc-500 text-[11px]">Latency: {diagResult.latencyMs}ms</span>
          </div>

          <div className="space-y-1.5 text-[11px]">
            {diagResult.testDetails.map((line, i) => (
              <div key={i} className="text-zinc-300">
                {line}
              </div>
            ))}
          </div>

          {diagResult.verifiedExitIp && (
            <div className="pt-2 border-t border-zinc-800/80 flex items-center justify-between text-[11px]">
              <span className="text-zinc-400">check.torproject.org:</span>
              <span className="text-emerald-400 font-bold">
                {diagResult.verifiedExitIp} ({diagResult.isTorExit ? 'Confirmed Tor Exit' : 'Non-Tor'})
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
