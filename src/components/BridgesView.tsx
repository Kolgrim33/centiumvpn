import React, { useState } from 'react';
import { Key, ArrowLeft, Check, ShieldCheck, AlertCircle } from 'lucide-react';
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
            id="btn-back-from-bridges"
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-[#17141E] text-[#8E899E] hover:text-[#F4F3F7] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-[#F4F3F7] flex items-center gap-2">
              <Key className="w-4 h-4 text-[#6C4DFF]" />
              Tor Bridges
            </h2>
            <p className="text-xs text-[#8E899E]">
              Circumvent ISP or local network blocking of direct Tor relays
            </p>
          </div>
        </div>

        {savedSuccess && (
          <span className="text-xs font-medium text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Saved
          </span>
        )}
      </div>

      {/* Bridges Explanation Box */}
      <div className="p-3.5 rounded-xl bg-[#121018] border border-[#1B1824] text-xs text-[#8E899E] space-y-1.5 leading-relaxed">
        <div className="flex items-center gap-2 text-[#F4F3F7] font-medium">
          <ShieldCheck className="w-4 h-4 text-[#6C4DFF]" />
          <span>Bridges & Censorship Circumvention</span>
        </div>
        <p>
          Bridges are unlisted Tor relays that protect your connection from deep packet inspection
          (DPI) in restricted environments where public Tor guard IP addresses are blocked.
        </p>
      </div>

      {/* Options */}
      <div className="space-y-3">
        {/* Option 1: Automatic */}
        <label
          className={`flex items-start gap-3 p-3.5 rounded-xl border cursor-pointer transition-colors ${
            mode === 'auto'
              ? 'bg-[#17141E] border-[#6C4DFF]/40 text-[#F4F3F7]'
              : 'bg-[#121018] border-[#1B1824] text-[#8E899E] hover:text-[#F4F3F7]'
          }`}
        >
          <input
            type="radio"
            name="bridgeMode"
            checked={mode === 'auto'}
            onChange={() => setMode('auto')}
            className="mt-0.5 accent-[#6C4DFF]"
          />
          <div>
            <div className="text-xs font-medium text-[#F4F3F7]">Automatic (Direct Connection)</div>
            <div className="text-[11px] text-[#8E899E] mt-0.5">
              Connect directly to standard Tor relays. Recommended for open networks.
            </div>
          </div>
        </label>

        {/* Option 2: Built-in */}
        <div
          className={`p-3.5 rounded-xl border transition-colors ${
            mode === 'builtin'
              ? 'bg-[#17141E] border-[#6C4DFF]/40 text-[#F4F3F7]'
              : 'bg-[#121018] border-[#1B1824] text-[#8E899E]'
          }`}
        >
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="radio"
              name="bridgeMode"
              checked={mode === 'builtin'}
              onChange={() => setMode('builtin')}
              className="mt-0.5 accent-[#6C4DFF]"
            />
            <div>
              <div className="text-xs font-medium text-[#F4F3F7]">Built-in Pluggable Transport</div>
              <div className="text-[11px] text-[#8E899E] mt-0.5">
                Obfuscates Tor packets as benign HTTPS or WebRTC streams.
              </div>
            </div>
          </label>

          {mode === 'builtin' && (
            <div className="mt-3 pt-3 border-t border-[#1B1824] pl-6 flex gap-2">
              <button
                type="button"
                onClick={() => setBuiltinType('obfs4')}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium border cursor-pointer ${
                  builtinType === 'obfs4'
                    ? 'bg-[#6C4DFF] text-[#F4F3F7] border-[#6C4DFF]'
                    : 'bg-[#121018] text-[#8E899E] border-[#23202E] hover:text-[#F4F3F7]'
                }`}
              >
                obfs4
              </button>
              <button
                type="button"
                onClick={() => setBuiltinType('snowflake')}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium border cursor-pointer ${
                  builtinType === 'snowflake'
                    ? 'bg-[#6C4DFF] text-[#F4F3F7] border-[#6C4DFF]'
                    : 'bg-[#121018] text-[#8E899E] border-[#23202E] hover:text-[#F4F3F7]'
                }`}
              >
                snowflake
              </button>
            </div>
          )}
        </div>

        {/* Option 3: Custom */}
        <div
          className={`p-3.5 rounded-xl border transition-colors ${
            mode === 'custom'
              ? 'bg-[#17141E] border-[#6C4DFF]/40 text-[#F4F3F7]'
              : 'bg-[#121018] border-[#1B1824] text-[#8E899E]'
          }`}
        >
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="radio"
              name="bridgeMode"
              checked={mode === 'custom'}
              onChange={() => setMode('custom')}
              className="mt-0.5 accent-[#6C4DFF]"
            />
            <div>
              <div className="text-xs font-medium text-[#F4F3F7]">Custom Bridge</div>
              <div className="text-[11px] text-[#8E899E] mt-0.5">
                Provide private bridge lines from BridgeDB.
              </div>
            </div>
          </label>

          {mode === 'custom' && (
            <div className="mt-3 pt-3 border-t border-[#1B1824] pl-6 space-y-2">
              <textarea
                value={customBridge}
                onChange={(e) => setCustomBridge(e.target.value)}
                placeholder="obfs4 192.0.2.1:443 14787FC8E743F245DF78CF2EE7C7475CF4F2EC25 cert=a82... iat-mode=0"
                rows={3}
                className="w-full bg-[#0D0B12] border border-[#23202E] rounded-lg p-2.5 text-xs font-mono text-[#F4F3F7] focus:outline-hidden focus:border-[#6C4DFF]"
              />
              {customError && (
                <div className="text-[11px] text-rose-400 flex items-center gap-1">
                  <AlertCircle className="w-3.5 h-3.5" />
                  <span>{customError}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Save Button */}
      <div className="flex justify-end">
        <button
          id="btn-save-bridges"
          onClick={handleSave}
          disabled={saving}
          className="px-5 py-2 rounded-xl bg-[#6C4DFF] hover:bg-[#5B3EE0] text-[#F4F3F7] font-semibold text-xs transition-colors cursor-pointer disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save Bridges'}
        </button>
      </div>
    </div>
  );
};
