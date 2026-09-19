import React, { useState } from 'react';
import { Globe, Check, Info, ArrowLeft, RefreshCw } from 'lucide-react';
import { CentiumConfig } from '../types.ts';

interface ExitLocationViewProps {
  config: CentiumConfig;
  onSaveConfig: (newConfig: Partial<CentiumConfig>) => Promise<void>;
  onBack: () => void;
  isConnected: boolean;
  onRefreshCircuit: () => void;
}

interface CountryOption {
  code: string;
  name: string;
  region: string;
  flag: string;
}

export const ExitLocationView: React.FC<ExitLocationViewProps> = ({
  config,
  onSaveConfig,
  onBack,
  isConnected,
  onRefreshCircuit,
}) => {
  const [selected, setSelected] = useState(config.exitLocation || 'auto');
  const [saving, setSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const countries: CountryOption[] = [
    { code: 'auto', name: 'Automatic', region: 'Fastest available circuit', flag: '⚡' },
    { code: 'any', name: 'Any Country', region: 'Global Tor consensus', flag: '🌐' },
    { code: 'nl', name: 'Netherlands', region: 'Europe', flag: '🇳🇱' },
    { code: 'de', name: 'Germany', region: 'Europe', flag: '🇩🇪' },
    { code: 'ch', name: 'Switzerland', region: 'Europe', flag: '🇨🇭' },
    { code: 'se', name: 'Sweden', region: 'Europe', flag: '🇸🇪' },
    { code: 'us', name: 'United States', region: 'North America', flag: '🇺🇸' },
    { code: 'gb', name: 'United Kingdom', region: 'Europe', flag: '🇬🇧' },
    { code: 'ca', name: 'Canada', region: 'North America', flag: '🇨🇦' },
    { code: 'fr', name: 'France', region: 'Europe', flag: '🇫🇷' },
    { code: 'is', name: 'Iceland', region: 'Europe', flag: '🇮🇸' },
    { code: 'ro', name: 'Romania', region: 'Europe', flag: '🇷🇴' },
    { code: 'jp', name: 'Japan', region: 'Asia Pacific', flag: '🇯🇵' },
  ];

  const handleSelect = async (code: string) => {
    setSelected(code);
    setSaving(true);
    try {
      await onSaveConfig({ exitLocation: code });
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 2500);
      if (isConnected) {
        // Trigger circuit rebuild with new exit country
        await onRefreshCircuit();
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-xl mx-auto py-6 px-4">
      {/* Header with back button */}
      <div className="flex items-center justify-between pb-4 border-b border-zinc-800 mb-6">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-exit"
            onClick={onBack}
            className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <Globe className="w-4 h-4 text-emerald-400" />
              Exit Location
            </h2>
            <p className="text-xs text-zinc-400">
              Select preferred exit relay country for Tor circuit routing
            </p>
          </div>
        </div>

        {savedSuccess && (
          <span className="text-xs font-mono text-emerald-400 flex items-center gap-1">
            <Check className="w-3.5 h-3.5" /> Saved
          </span>
        )}
      </div>

      {/* Tor Notice Box (Section 14 requirement) */}
      <div className="mb-6 p-3.5 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 flex items-start gap-3">
        <Info className="w-4 h-4 shrink-0 text-zinc-500 mt-0.5" />
        <div className="space-y-1 leading-relaxed">
          <p className="text-zinc-300">
            Centium uses Tor's standard <code className="text-emerald-400 font-mono">ExitNodes</code> configuration.
          </p>
          <p className="text-zinc-500">
            Exit-location availability depends entirely on active exit relays in the Tor network consensus.
            Centium never runs private relays or alters Tor's path selection cryptography.
          </p>
        </div>
      </div>

      {/* Country List */}
      <div className="space-y-1.5">
        {countries.map((country) => {
          const isChosen = selected.toLowerCase() === country.code.toLowerCase();
          return (
            <button
              key={country.code}
              id={`exit-choice-${country.code}`}
              onClick={() => handleSelect(country.code)}
              disabled={saving}
              className={`w-full flex items-center justify-between p-3 rounded-lg border transition-all text-left cursor-pointer ${
                isChosen
                  ? 'bg-zinc-800/80 border-emerald-500/60 text-zinc-100 shadow-xs'
                  : 'bg-zinc-900/50 border-zinc-800/80 text-zinc-300 hover:bg-zinc-800/40 hover:border-zinc-700'
              }`}
            >
              <div className="flex items-center gap-3">
                <span className="text-base">{country.flag}</span>
                <div>
                  <div className="text-xs font-medium text-zinc-200">{country.name}</div>
                  <div className="text-[11px] text-zinc-500">{country.region}</div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {country.code !== 'auto' && country.code !== 'any' && (
                  <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-zinc-800 border border-zinc-700/50 text-zinc-400 uppercase">
                    {country.code}
                  </span>
                )}
                <div
                  className={`w-4 h-4 rounded-full border flex items-center justify-center transition-colors ${
                    isChosen
                      ? 'border-emerald-500 bg-emerald-500 text-zinc-950'
                      : 'border-zinc-700 bg-zinc-900'
                  }`}
                >
                  {isChosen && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                </div>
              </div>
            </button>
          );
        })}
      </div>

      {isConnected && (
        <div className="mt-6 text-center text-xs text-zinc-500 flex items-center justify-center gap-2">
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Active circuits renew automatically when location preferences change.</span>
        </div>
      )}
    </div>
  );
};
