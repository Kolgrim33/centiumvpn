import React, { useState } from 'react';
import { Globe, ArrowLeft, Check, Info } from 'lucide-react';
import { CentiumConfig } from '../types.ts';

interface ExitLocationViewProps {
  config: CentiumConfig;
  onSaveConfig: (newConfig: Partial<CentiumConfig>) => Promise<void>;
  onBack: () => void;
  isConnected: boolean;
  onRefreshCircuit: () => Promise<void>;
}

interface LocationOption {
  code: string;
  name: string;
  flag: string;
  description: string;
}

const LOCATIONS: LocationOption[] = [
  { code: 'auto', name: 'Automatic', flag: '🌐', description: 'Fastest available circuit based on Tor consensus' },
  { code: 'nl', name: 'Netherlands', flag: '🇳🇱', description: 'High-bandwidth European Tor relay cluster' },
  { code: 'de', name: 'Germany', flag: '🇩🇪', description: 'Extensive Tor node infrastructure' },
  { code: 'us', name: 'United States', flag: '🇺🇸', description: 'Large distribution of high-speed exit relays' },
  { code: 'gb', name: 'United Kingdom', flag: '🇬🇧', description: 'Western European routing path' },
  { code: 'ch', name: 'Switzerland', flag: '🇨🇭', description: 'Central European privacy-centric relays' },
  { code: 'se', name: 'Sweden', flag: '🇸🇪', description: 'Nordic region exit nodes' },
  { code: 'ca', name: 'Canada', flag: '🇨🇦', description: 'North American routing nodes' },
  { code: 'fr', name: 'France', flag: '🇫🇷', description: 'Western European Tor network exit' },
  { code: 'is', name: 'Iceland', flag: '🇮🇸', description: 'Strong local privacy laws and independent relays' },
  { code: 'jp', name: 'Japan', flag: '🇯🇵', description: 'East Asia region exit nodes' },
];

export const ExitLocationView: React.FC<ExitLocationViewProps> = ({
  config,
  onSaveConfig,
  onBack,
  isConnected,
  onRefreshCircuit,
}) => {
  const [selectedCode, setSelectedCode] = useState<string>(config.exitLocation || 'auto');
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const handleSelect = async (code: string) => {
    setSelectedCode(code);
    setSaving(true);
    try {
      await onSaveConfig({ exitLocation: code });
      setSaveSuccess(true);
      if (isConnected) {
        await onRefreshCircuit();
      }
      setTimeout(() => setSaveSuccess(false), 2000);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-[#1B1824] mb-5">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-exit"
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-[#17141E] text-[#8E899E] hover:text-[#F4F3F7] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-[#F4F3F7] flex items-center gap-2">
              <Globe className="w-4 h-4 text-[#6C4DFF]" />
              Tor Exit Locations
            </h2>
            <p className="text-xs text-[#8E899E]">
              Select preferred exit relay jurisdiction
            </p>
          </div>
        </div>

        {saveSuccess && (
          <span className="text-xs font-medium text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Updated
          </span>
        )}
      </div>

      {/* Honest Technical Explanation Notice (Section 12) */}
      <div className="mb-5 p-3.5 rounded-xl bg-[#121018] border border-[#1B1824] text-xs text-[#8E899E] flex items-start gap-2.5">
        <Info className="w-4 h-4 text-[#6C4DFF] shrink-0 mt-0.5" />
        <div className="leading-relaxed">
          <strong className="text-[#F4F3F7] font-medium">Tor Network Routing: </strong>
          Tor determines the cryptographic 3-hop relay path. Centium configures your preferences
          via standard Tor <code className="text-[#F4F3F7] font-mono">ExitNodes</code> directives.
          Centium does not operate a centralized VPN server fleet.
        </div>
      </div>

      {/* Locations List */}
      <div className="space-y-1.5">
        {LOCATIONS.map((loc) => {
          const isSelected = selectedCode === loc.code;
          return (
            <button
              key={loc.code}
              id={`exit-loc-${loc.code}`}
              onClick={() => handleSelect(loc.code)}
              disabled={saving}
              className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-colors cursor-pointer ${
                isSelected
                  ? 'bg-[#17141E] border-[#6C4DFF]/40 text-[#F4F3F7]'
                  : 'bg-[#121018] border-[#1B1824] text-[#8E899E] hover:text-[#F4F3F7] hover:bg-[#15121D]'
              }`}
            >
              <div className="flex items-center gap-3">
                <span className="text-base select-none">{loc.flag}</span>
                <div>
                  <div className="text-xs font-medium text-[#F4F3F7]">
                    {loc.name}
                  </div>
                  <div className="text-[11px] text-[#8E899E]">
                    {loc.description}
                  </div>
                </div>
              </div>

              {isSelected && (
                <div className="w-5 h-5 rounded-full bg-[#6C4DFF]/20 border border-[#6C4DFF] flex items-center justify-center text-[#6C4DFF] shrink-0">
                  <Check className="w-3 h-3 stroke-[2.5]" />
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
};
