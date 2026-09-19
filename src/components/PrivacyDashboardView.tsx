import React from 'react';
import { Lock, ShieldCheck, Check, ArrowDown, Server, Shield, ArrowLeft } from 'lucide-react';
import { CentiumStatus } from '../types.ts';

interface PrivacyDashboardViewProps {
  status: CentiumStatus;
  onBack: () => void;
}

export const PrivacyDashboardView: React.FC<PrivacyDashboardViewProps> = ({
  status,
  onBack,
}) => {
  const isConnected = status.state === 'CONNECTED';

  const privacyChecklist = [
    {
      title: 'Tor connected',
      verified: isConnected,
      description: isConnected
        ? `System traffic is routed through 3-hop onion circuit ending at ${status.exitCountry || 'Tor Exit Relay'}.`
        : 'Connect Centium to encrypt and onion-route your internet packets.',
    },
    {
      title: 'Kill switch active',
      verified: status.killSwitchActive || isConnected,
      description:
        'Fail-closed firewall filter rules prevent unprotected traffic leaks if the Tor circuit drops unexpectedly.',
    },
    {
      title: 'DNS protected',
      verified: status.dnsProtected,
      description:
        'System DNS queries are strictly handled by Tor DNSPort (127.0.0.1:5353) to eliminate ISP DNS hijacking and leaks.',
    },
    {
      title: 'IPv6 protected',
      verified: status.ipv6Protected,
      description:
        'IPv6 traffic is blocked at the firewall level to ensure non-Tor IPv6 paths cannot bypass the privacy tunnel.',
    },
    {
      title: 'No Centium traffic logging',
      verified: true,
      description:
        'Centium daemon operates locally on your device. Centium never hosts VPN proxies or inspects user browsing payloads.',
    },
  ];

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4">
      {/* Header */}
      <div className="flex items-center gap-3 pb-4 border-b border-zinc-800 mb-6">
        <button
          id="btn-back-from-privacy"
          onClick={onBack}
          className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
            <Lock className="w-4 h-4 text-emerald-400" />
            Privacy Architecture
          </h2>
          <p className="text-xs text-zinc-400">
            Technical audit and data minimization guarantees
          </p>
        </div>
      </div>

      {/* Main Checklist (Section 16) */}
      <div className="bg-zinc-900/80 border border-zinc-800 rounded-xl p-4 mb-6 space-y-4">
        <div className="text-xs font-semibold text-zinc-300 uppercase tracking-wider font-mono">
          Security Protections
        </div>

        <div className="space-y-3">
          {privacyChecklist.map((item, i) => (
            <div key={i} className="flex items-start gap-3">
              <div
                className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${
                  item.verified
                    ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                    : 'bg-zinc-800 text-zinc-600 border border-zinc-700'
                }`}
              >
                <Check className="w-3 h-3 stroke-[2.5]" />
              </div>
              <div className="space-y-0.5">
                <div
                  className={`text-xs font-medium ${
                    item.verified ? 'text-zinc-200' : 'text-zinc-400'
                  }`}
                >
                  {item.title}
                </div>
                <div className="text-[11px] text-zinc-400 leading-relaxed">
                  {item.description}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Section 16 explicit quote */}
        <div className="pt-3 border-t border-zinc-800/80 text-xs text-zinc-300 font-medium leading-relaxed">
          Centium does not need to inspect your browsing traffic to provide the service.
        </div>
      </div>

      {/* Real Traffic Path Diagram (Section 1 & 19) */}
      <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 mb-6 space-y-3">
        <div className="text-xs font-semibold text-zinc-300 uppercase tracking-wider font-mono flex items-center gap-2">
          <Shield className="w-3.5 h-3.5 text-emerald-400" />
          Traffic Routing Flow
        </div>

        <div className="flex flex-col items-center space-y-1.5 py-2 font-mono text-[11px]">
          <div className="w-full text-center py-1 px-3 rounded bg-zinc-800/80 text-zinc-200 border border-zinc-700/60">
            User Applications (Browser, CLI, Clients)
          </div>
          <ArrowDown className="w-3 h-3 text-zinc-500" />
          <div className="w-full text-center py-1 px-3 rounded bg-zinc-800/80 text-zinc-300 border border-zinc-700/60">
            Centium Virtual Interface ({status.virtualInterface})
          </div>
          <ArrowDown className="w-3 h-3 text-zinc-500" />
          <div className="w-full text-center py-1 px-3 rounded bg-emerald-950/40 text-emerald-300 border border-emerald-800/40">
            Centium Network Daemon (TransPort & DNSPort)
          </div>
          <ArrowDown className="w-3 h-3 text-zinc-500" />
          <div className="w-full text-center py-1 px-3 rounded bg-zinc-800/90 text-zinc-300 border border-zinc-700">
            Local Tor Process (Layered Encryption)
          </div>
          <ArrowDown className="w-3 h-3 text-zinc-500" />
          <div className="w-full grid grid-cols-3 gap-1.5 text-[10px] text-center">
            <div className="py-1 px-1 rounded bg-zinc-800 border border-zinc-700 text-zinc-300">
              1. Guard Relay
            </div>
            <div className="py-1 px-1 rounded bg-zinc-800 border border-zinc-700 text-zinc-300">
              2. Middle Relay
            </div>
            <div className="py-1 px-1 rounded bg-zinc-800 border border-zinc-700 text-zinc-300">
              3. Exit Relay
            </div>
          </div>
          <ArrowDown className="w-3 h-3 text-zinc-500" />
          <div className="w-full text-center py-1 px-3 rounded bg-emerald-500/10 text-emerald-300 border border-emerald-500/30">
            Destination Internet Server
          </div>
        </div>

        <div className="p-2.5 rounded bg-zinc-950 border border-zinc-800 text-[11px] text-zinc-400 flex items-start gap-2">
          <Server className="w-4 h-4 shrink-0 text-amber-400 mt-0.5" />
          <span>
            <strong className="text-zinc-200">Zero Proxying:</strong> Centium backend servers are NEVER in the traffic path.
            Unlike conventional VPN providers that can inspect and log your unencrypted packets at their centralized data centers, Centium routes directly to Tor onion relays.
          </span>
        </div>
      </div>

      {/* Data Minimization Pledge */}
      <div className="p-4 rounded-xl border border-zinc-800 bg-zinc-950 text-xs text-zinc-400 space-y-2">
        <div className="text-zinc-200 font-semibold flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          Strict Data Minimization Guarantee
        </div>
        <p className="text-[11px] text-zinc-400 leading-relaxed">
          Centium does not collect, record, or transmit browsing history, destination URLs, DNS lookups,
          session payloads, or personal credentials. No email, password, payment, or phone number is ever required.
        </p>
      </div>
    </div>
  );
};
