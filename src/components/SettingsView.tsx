import React, { useState } from 'react';
import { Settings, ArrowLeft, Check, ShieldAlert } from 'lucide-react';
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
  const [autoConnect, setAutoConnect] = useState(config.autoConnect ?? false);
  const [startWithSystem, setStartWithSystem] = useState(config.startWithSystem ?? false);
  const [reconnectAuto, setReconnectAuto] = useState(true);
  const [killSwitch, setKillSwitch] = useState(config.killSwitch ?? true);
  const [dnsProtection, setDnsProtection] = useState(config.dnsProtection ?? true);
  const [blockIpv6, setBlockIpv6] = useState(config.blockIpv6 ?? true);
  const [startMinimized, setStartMinimized] = useState(false);
  const [systemTray, setSystemTray] = useState(true);
  const [notifications, setNotifications] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      await onSaveConfig({
        autoConnect,
        startWithSystem,
        killSwitch,
        dnsProtection,
        blockIpv6,
      });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 2000);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-[#1B1824]">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-settings"
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-[#17141E] text-[#8E899E] hover:text-[#F4F3F7] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-[#F4F3F7] flex items-center gap-2">
              <Settings className="w-4 h-4 text-[#6C4DFF]" />
              Settings
            </h2>
            <p className="text-xs text-[#8E899E]">
              Preferences, network security, and application behavior
            </p>
          </div>
        </div>

        {savedSuccess && (
          <span className="text-xs font-medium text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Saved
          </span>
        )}
      </div>

      {/* Category 1: Connection (Section 14) */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl p-4 space-y-3">
        <h3 className="text-xs font-semibold text-[#F4F3F7] uppercase tracking-wider">
          Connection
        </h3>
        <div className="space-y-3 text-xs divide-y divide-[#1B1824]">
          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">Automatically connect</div>
              <div className="text-[11px] text-[#8E899E]">Connect to Tor immediately on launch</div>
            </div>
            <input
              type="checkbox"
              checked={autoConnect}
              onChange={(e) => setAutoConnect(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>

          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">Start Centium with system</div>
              <div className="text-[11px] text-[#8E899E]">Launch at system startup via systemd</div>
            </div>
            <input
              type="checkbox"
              checked={startWithSystem}
              onChange={(e) => setStartWithSystem(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>

          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">Reconnect automatically</div>
              <div className="text-[11px] text-[#8E899E]">Restore connection if Tor circuit drops</div>
            </div>
            <input
              type="checkbox"
              checked={reconnectAuto}
              onChange={(e) => setReconnectAuto(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>
        </div>
      </div>

      {/* Category 2: Network (Section 14) */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl p-4 space-y-3">
        <h3 className="text-xs font-semibold text-[#F4F3F7] uppercase tracking-wider">
          Network Protection
        </h3>
        <div className="space-y-3 text-xs divide-y divide-[#1B1824]">
          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">Kill switch</div>
              <div className="text-[11px] text-[#8E899E]">Fail-closed iptables rules block unencrypted traffic leaks</div>
            </div>
            <input
              type="checkbox"
              checked={killSwitch}
              onChange={(e) => setKillSwitch(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>

          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">DNS protection</div>
              <div className="text-[11px] text-[#8E899E]">Lock resolver to Tor DNSPort (127.0.0.1:5353)</div>
            </div>
            <input
              type="checkbox"
              checked={dnsProtection}
              onChange={(e) => setDnsProtection(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>

          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">IPv6 protection</div>
              <div className="text-[11px] text-[#8E899E]">Block un-tunneled IPv6 egress to prevent dual-stack leaks</div>
            </div>
            <input
              type="checkbox"
              checked={blockIpv6}
              onChange={(e) => setBlockIpv6(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>
        </div>
      </div>

      {/* Category 3: Application (Section 14) */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl p-4 space-y-3">
        <h3 className="text-xs font-semibold text-[#F4F3F7] uppercase tracking-wider">
          Application
        </h3>
        <div className="space-y-3 text-xs divide-y divide-[#1B1824]">
          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">System tray</div>
              <div className="text-[11px] text-[#8E899E]">Show status icon and quick menu in desktop tray</div>
            </div>
            <input
              type="checkbox"
              checked={systemTray}
              onChange={(e) => setSystemTray(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>

          <label className="flex items-center justify-between pt-2 cursor-pointer">
            <div>
              <div className="font-medium text-[#F4F3F7]">Desktop notifications</div>
              <div className="text-[11px] text-[#8E899E]">Notify when connection state changes</div>
            </div>
            <input
              type="checkbox"
              checked={notifications}
              onChange={(e) => setNotifications(e.target.checked)}
              className="accent-[#6C4DFF] w-4 h-4 cursor-pointer"
            />
          </label>
        </div>
      </div>

      {/* Save Button */}
      <div className="flex justify-end">
        <button
          id="btn-save-settings"
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2 rounded-xl bg-[#6C4DFF] hover:bg-[#5B3EE0] text-[#F4F3F7] font-semibold text-xs transition-colors cursor-pointer disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
};
