import React, { useState } from 'react';
import { Key, ShieldCheck, Check, AlertCircle, ArrowLeft } from 'lucide-react';
import { CentiumConfig } from '../types.ts';

interface BridgesViewProps {
  config: CentiumConfig;
  onSaveConfig: (newConfig: Partial<CentiumConfig>) => Promise<void>;
  onBack: () => void;
}

export const BridgesView: React.FC<BridgesViewProps> = ({
  config,
  onSaveConfig,
  onBack,
}) => {
  const [mode, setMode] = useState<'auto' | 'builtin' | 'custom'>(config.bridgeMode || 'auto');
  const [builtinType, setBuiltinType] = useState<'obfs4' | 'snowflake' | 'meek'>(
    config.bridgeType && config.bridgeType !== 'none' ? config.bridgeType : 'obfs4'
  );
  const [customBridge, setCustomBridge] = useState(config.customBridge || '');
  const [saving, setSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);
  const [customError, setCustomError] = useState<string | null>(null);

  const handleSave = async () => {
    setCustomError(null);
    if (mode === 'custom' && !customBridge.trim()) {
      setCustomError('Please enter at least one valid Tor bridge line or select Automatic.');
      return;
    }

    setSaving(true);
    try {
      await onSaveConfig({
        bridgeMode: mode,
        bridgeType: mode === 'builtin' ? builtinType : 'none',
        customBridge: mode === 'custom' ? customBridge.trim() : '',
      });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 3000);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-zinc-800 mb-6">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-bridges"
            onClick={onBack}
            className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <Key className="w-4 h-4 text-emerald-400" />
              Tor Bridges
            </h2>
            <p className="text-xs text-zinc-400">
              Circumvent ISP or local network blocking of direct Tor relays
            </p>
          </div>
        </div>

        {savedSuccess && (
          <span className="text-xs font-mono text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Saved
          </span>
        )}
      </div>

      {/* Bridges Explanation Box */}
      <div className="mb-6 p-3.5 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 space-y-1.5">
        <div className="flex items-center gap-2 text-zinc-200 font-medium">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>When should you use a bridge?</span>
        </div>
        <p className="text-zinc-400 leading-relaxed">
          Public Tor relay IP addresses are listed in the Tor consensus directory. If your ISP,
          university, or workplace blocks known Tor IP addresses, bridges act as unlisted entry points
          to bypass deep packet inspection (DPI) and censorship.
        </p>
      </div>

      {/* Bridge Modes Form */}
      <div className="space-y-4">
        {/* Option 1: Automatic */}
        <label
          htmlFor="bridge-mode-auto"
          className={`flex items-start gap-3 p-3.5 rounded-lg border cursor-pointer transition-all ${
            mode === 'auto'
              ? 'bg-zinc-800/80 border-emerald-500/60 text-zinc-100'
              : 'bg-zinc-900/40 border-zinc-800 text-zinc-300 hover:bg-zinc-800/30'
          }`}
        >
          <input
            type="radio"
            id="bridge-mode-auto"
            name="bridgeMode"
            checked={mode === 'auto'}
            onChange={() => setMode('auto')}
            className="mt-1 accent-emerald-500"
          />
          <div>
            <div className="text-xs font-medium text-zinc-200">Automatic (Direct Connection)</div>
            <div className="text-[11px] text-zinc-400 mt-0.5">
              Connect directly to the Tor network guard relays without intermediary bridges. Recommended for open networks.
            </div>
          </div>
        </label>

        {/* Option 2: Built-in bridge */}
        <div
          className={`p-3.5 rounded-lg border transition-all ${
            mode === 'builtin'
              ? 'bg-zinc-800/80 border-emerald-500/60 text-zinc-100'
              : 'bg-zinc-900/40 border-zinc-800 text-zinc-300'
          }`}
        >
          <label htmlFor="bridge-mode-builtin" className="flex items-start gap-3 cursor-pointer">
            <input
              type="radio"
              id="bridge-mode-builtin"
              name="bridgeMode"
              checked={mode === 'builtin'}
              onChange={() => setMode('builtin')}
              className="mt-1 accent-emerald-500"
            />
            <div>
              <div className="text-xs font-medium text-zinc-200">Built-in Bridge (Pluggable Transport)</div>
              <div className="text-[11px] text-zinc-400 mt-0.5">
                Use built-in obfuscation to disguise Tor traffic as benign HTTPS or WebRTC traffic.
              </div>
            </div>
          </label>

          {mode === 'builtin' && (
            <div className="mt-3.5 pt-3 border-t border-zinc-700/60 pl-6 space-y-2">
              <div className="text-[11px] font-medium text-zinc-300">Select Pluggable Transport:</div>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  id="btn-transport-obfs4"
                  onClick={() => setBuiltinType('obfs4')}
                  className={`px-3 py-2 rounded text-xs text-left border transition-colors cursor-pointer ${
                    builtinType === 'obfs4'
                      ? 'bg-zinc-700 border-emerald-500 text-zinc-100 font-medium'
                      : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-zinc-200'
                  }`}
                >
                  <div className="font-mono font-semibold">obfs4</div>
                  <div className="text-[10px] text-zinc-400">Obfuscated TCP streams</div>
                </button>
                <button
                  type="button"
                  id="btn-transport-snowflake"
                  onClick={() => setBuiltinType('snowflake')}
                  className={`px-3 py-2 rounded text-xs text-left border transition-colors cursor-pointer ${
                    builtinType === 'snowflake'
                      ? 'bg-zinc-700 border-emerald-500 text-zinc-100 font-medium'
                      : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-zinc-200'
                  }`}
                >
                  <div className="font-mono font-semibold">snowflake</div>
                  <div className="text-[10px] text-zinc-400">WebRTC peer proxies</div>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Option 3: Custom bridge */}
        <div
          className={`p-3.5 rounded-lg border transition-all ${
            mode === 'custom'
              ? 'bg-zinc-800/80 border-emerald-500/60 text-zinc-100'
              : 'bg-zinc-900/40 border-zinc-800 text-zinc-300'
          }`}
        >
          <label htmlFor="bridge-mode-custom" className="flex items-start gap-3 cursor-pointer">
            <input
              type="radio"
              id="bridge-mode-custom"
              name="bridgeMode"
              checked={mode === 'custom'}
              onChange={() => setMode('custom')}
              className="mt-1 accent-emerald-500"
            />
            <div>
              <div className="text-xs font-medium text-zinc-200">Custom Bridge</div>
              <div className="text-[11px] text-zinc-400 mt-0.5">
                Provide private bridge lines obtained from bridges.torproject.org or BridgeDB.
              </div>
            </div>
          </label>

          {mode === 'custom' && (
            <div className="mt-3.5 pt-3 border-t border-zinc-700/60 pl-6 space-y-2">
              <label htmlFor="input-custom-bridge" className="text-[11px] text-zinc-300 block">
                Bridge address line:
              </label>
              <textarea
                id="input-custom-bridge"
                value={customBridge}
                onChange={(e) => setCustomBridge(e.target.value)}
                placeholder="obfs4 192.0.2.1:443 14787FC8E743F245DF78CF2EE7C7475CF4F2EC25 cert=a82... iat-mode=0"
                rows={3}
                className="w-full bg-zinc-950 border border-zinc-700 rounded p-2 text-xs font-mono text-zinc-200 placeholder-zinc-600 focus:outline-hidden focus:border-emerald-500"
              />
              {customError && (
                <div className="flex items-center gap-1 text-[11px] text-rose-400">
                  <AlertCircle className="w-3.5 h-3.5" />
                  <span>{customError}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Save Button */}
      <div className="mt-6 flex justify-end">
        <button
          id="btn-save-bridges"
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2 rounded-md bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold text-xs transition-colors cursor-pointer disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'SAVE BRIDGE SETTINGS'}
        </button>
      </div>
    </div>
  );
};
