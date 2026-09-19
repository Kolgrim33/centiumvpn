import React, { useState } from 'react';
import { Settings, ShieldAlert, Check, ArrowLeft, Info, Cpu, Network } from 'lucide-react';
import { CentiumConfig } from '../types.ts';

interface SettingsViewProps {
  config: CentiumConfig;
  onSaveConfig: (newConfig: Partial<CentiumConfig>) => Promise<void>;
  onBack: () => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  config,
  onSaveConfig,
  onBack,
}) => {
  const [killSwitch, setKillSwitch] = useState(config.killSwitch ?? true);
  const [blockIpv6, setBlockIpv6] = useState(config.blockIpv6 ?? true);
  const [autoConnect, setAutoConnect] = useState(config.autoConnect ?? false);
  const [startWithSystem, setStartWithSystem] = useState(config.startWithSystem ?? false);
  const [dnsProtection, setDnsProtection] = useState(config.dnsProtection ?? true);
  const [socksPort, setSocksPort] = useState(config.socksPort ?? 9050);
  const [controlPort, setControlPort] = useState(config.controlPort ?? 9051);
  const [virtualInterface, setVirtualInterface] = useState(config.virtualInterface || 'centium0');
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      await onSaveConfig({
        killSwitch,
        blockIpv6,
        autoConnect,
        startWithSystem,
        dnsProtection,
        socksPort: Number(socksPort),
        controlPort: Number(controlPort),
        virtualInterface,
      });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 2500);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-settings"
            onClick={onBack}
            className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <Settings className="w-4 h-4 text-emerald-400" />
              Settings & Preferences
            </h2>
            <p className="text-xs text-zinc-400">
              Configure system daemon behavior, firewall rules, and Tor ports
            </p>
          </div>
        </div>

        {savedSuccess && (
          <span className="text-xs font-mono text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Saved
          </span>
        )}
      </div>

      {/* Main Settings Form (Section 24 specification) */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 space-y-5">
        <div className="text-xs font-semibold text-zinc-300 uppercase tracking-wider font-mono">
          Startup & Automation
        </div>

        <div className="space-y-4 text-xs">
          {/* Start with system */}
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              id="chk-start-with-system"
              checked={startWithSystem}
              onChange={(e) => setStartWithSystem(e.target.checked)}
              className="mt-0.5 accent-emerald-500 rounded"
            />
            <div>
              <div className="text-zinc-200 font-medium">Start Centium when system starts</div>
              <div className="text-zinc-400 text-[11px] mt-0.5">
                Launches the Centium UI and enables the systemd <code className="text-zinc-300 font-mono">centiumd.service</code> at boot.
              </div>
            </div>
          </label>

          {/* Auto Connect */}
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              id="chk-auto-connect"
              checked={autoConnect}
              onChange={(e) => setAutoConnect(e.target.checked)}
              className="mt-0.5 accent-emerald-500 rounded"
            />
            <div>
              <div className="text-zinc-200 font-medium">Automatically connect</div>
              <div className="text-zinc-400 text-[11px] mt-0.5">
                Immediately initiates Tor circuit negotiation and transparent routing when Centium launches.
              </div>
            </div>
          </label>

          {/* Kill Switch */}
          <div className="pt-2 border-t border-zinc-800/80">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                id="chk-kill-switch"
                checked={killSwitch}
                onChange={(e) => setKillSwitch(e.target.checked)}
                className="mt-0.5 accent-emerald-500 rounded"
              />
              <div>
                <div className="text-zinc-200 font-medium flex items-center gap-1.5">
                  <span>Kill switch</span>
                  <span className="text-[10px] px-1.5 py-0.2 bg-emerald-950 text-emerald-400 border border-emerald-800 rounded font-mono">
                    RECOMMENDED
                  </span>
                </div>
                <div className="text-zinc-400 text-[11px] mt-0.5">
                  Fail-closed network filter. Completely blocks all outbound internet traffic if Tor drops or crashes unexpectedly.
                </div>
              </div>
            </label>

            {!killSwitch && (
              <div className="mt-2.5 p-2.5 rounded bg-rose-950/40 border border-rose-900/60 text-[11px] text-rose-300 flex items-start gap-2">
                <ShieldAlert className="w-4 h-4 shrink-0 text-rose-400 mt-0.5" />
                <span>
                  <strong>Warning:</strong> Disabling the kill switch may expose your true ISP IP address if the Tor connection drops.
                </span>
              </div>
            )}
          </div>

          {/* Block IPv6 */}
          <div className="pt-2 border-t border-zinc-800/80">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                id="chk-block-ipv6"
                checked={blockIpv6}
                onChange={(e) => setBlockIpv6(e.target.checked)}
                className="mt-0.5 accent-emerald-500 rounded"
              />
              <div>
                <div className="text-zinc-200 font-medium">Block IPv6 when disconnected from Tor</div>
                <div className="text-zinc-400 text-[11px] mt-0.5">
                  Prevents operating system IPv6 stack from bypassing Tor routing layer.
                </div>
              </div>
            </label>
          </div>

          {/* DNS Protection */}
          <div className="pt-2 border-t border-zinc-800/80">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                id="chk-dns-protection"
                checked={dnsProtection}
                onChange={(e) => setDnsProtection(e.target.checked)}
                className="mt-0.5 accent-emerald-500 rounded"
              />
              <div>
                <div className="text-zinc-200 font-medium">Enforce DNS leak protection</div>
                <div className="text-zinc-400 text-[11px] mt-0.5">
                  Locks system resolver to 127.0.0.1:5353 (Tor DNSPort) so no plaintext DNS lookups reach your ISP.
                </div>
              </div>
            </label>
          </div>
        </div>
      </div>

      {/* Advanced Network & Ports Card */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 space-y-4">
        <div className="text-xs font-semibold text-zinc-300 uppercase tracking-wider font-mono flex items-center gap-2">
          <Network className="w-3.5 h-3.5 text-emerald-400" />
          Virtual Interface & Tor Ports
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div>
            <label htmlFor="input-vif" className="text-zinc-400 block mb-1">
              Virtual Interface Name:
            </label>
            <input
              type="text"
              id="input-vif"
              value={virtualInterface}
              onChange={(e) => setVirtualInterface(e.target.value)}
              className="w-full bg-zinc-950 border border-zinc-700 rounded p-2 text-xs font-mono text-zinc-200"
            />
          </div>

          <div>
            <label htmlFor="input-socks-port" className="text-zinc-400 block mb-1">
              Tor SOCKS5 Port:
            </label>
            <input
              type="number"
              id="input-socks-port"
              value={socksPort}
              onChange={(e) => setSocksPort(Number(e.target.value))}
              className="w-full bg-zinc-950 border border-zinc-700 rounded p-2 text-xs font-mono text-zinc-200"
            />
          </div>

          <div>
            <label htmlFor="input-control-port" className="text-zinc-400 block mb-1">
              Tor Control Port:
            </label>
            <input
              type="number"
              id="input-control-port"
              value={controlPort}
              onChange={(e) => setControlPort(Number(e.target.value))}
              className="w-full bg-zinc-950 border border-zinc-700 rounded p-2 text-xs font-mono text-zinc-200"
            />
          </div>

          <div>
            <label htmlFor="input-ipc-socket" className="text-zinc-400 block mb-1">
              Local IPC Daemon Socket:
            </label>
            <input
              type="text"
              id="input-ipc-socket"
              readOnly
              value="/run/centium/centium.sock"
              className="w-full bg-zinc-950/70 border border-zinc-800 rounded p-2 text-xs font-mono text-zinc-400 cursor-not-allowed"
            />
          </div>
        </div>
      </div>

      {/* UDP Limitation Notice (Section 13) */}
      <div className="p-4 rounded-xl border border-zinc-800 bg-zinc-950 text-xs text-zinc-400 flex items-start gap-3">
        <Info className="w-4 h-4 shrink-0 text-amber-400 mt-0.5" />
        <div className="space-y-1">
          <div className="font-semibold text-zinc-300">UDP Protocol Notice</div>
          <div className="text-[11px] leading-relaxed">
            Tor is fundamentally TCP-oriented. Centium safely handles all standard TCP traffic (web, email, messaging)
            and redirects DNS through Tor DNSPort. Arbitrary non-DNS UDP traffic is safely blocked by the Centium
            routing filter rather than silently leaking outside the tunnel.
          </div>
        </div>
      </div>

      {/* Save Button */}
      <div className="flex justify-end">
        <button
          id="btn-save-settings"
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2 rounded-md bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold text-xs transition-colors cursor-pointer disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'SAVE SETTINGS'}
        </button>
      </div>
    </div>
  );
};
