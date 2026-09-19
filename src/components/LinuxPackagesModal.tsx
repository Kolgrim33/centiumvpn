import React, { useState, useEffect } from 'react';
import { Package, ArrowLeft, Copy, Check, Terminal, FileText, Code2, Shield } from 'lucide-react';
import { fetchLinuxFiles } from '../api.ts';

interface LinuxPackagesModalProps {
  onBack: () => void;
}

export const LinuxPackagesModal: React.FC<LinuxPackagesModalProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<'arch' | 'debian' | 'service' | 'routing' | 'rust'>('arch');
  const [files, setFiles] = useState<{
    systemd: string;
    routingScript: string;
    archPkgbuild: string;
    debianControl: string;
    rustDaemon: string;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    fetchLinuxFiles().then((res) => {
      setFiles(res);
    });
  }, []);

  const getCurrentContent = () => {
    if (!files) return 'Loading Linux integration templates...';
    switch (activeTab) {
      case 'arch':
        return files.archPkgbuild;
      case 'debian':
        return files.debianControl;
      case 'service':
        return files.systemd;
      case 'routing':
        return files.routingScript;
      case 'rust':
        return files.rustDaemon;
    }
  };

  const handleCopy = () => {
    const text = getCurrentContent();
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="w-full max-w-2xl mx-auto py-6 px-4 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-zinc-800">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-packages"
            onClick={onBack}
            className="p-1.5 rounded-md hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
              <Package className="w-4 h-4 text-emerald-400" />
              Linux Packaging & Native Daemon
            </h2>
            <p className="text-xs text-zinc-400">
              Arch Linux (PKGBUILD), Debian/Ubuntu (.deb), Systemd service & Rust core
            </p>
          </div>
        </div>

        <button
          onClick={handleCopy}
          className="px-3 py-1.5 rounded-md bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-xs font-mono flex items-center gap-1.5 transition-colors cursor-pointer"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied' : 'Copy File'}</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-zinc-800 pb-2 overflow-x-auto">
        <button
          onClick={() => setActiveTab('arch')}
          className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer ${
            activeTab === 'arch'
              ? 'bg-zinc-800 text-zinc-100 border border-zinc-700'
              : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          Arch (PKGBUILD)
        </button>
        <button
          onClick={() => setActiveTab('debian')}
          className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer ${
            activeTab === 'debian'
              ? 'bg-zinc-800 text-zinc-100 border border-zinc-700'
              : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          Debian / Ubuntu (.deb)
        </button>
        <button
          onClick={() => setActiveTab('service')}
          className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer ${
            activeTab === 'service'
              ? 'bg-zinc-800 text-zinc-100 border border-zinc-700'
              : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          Systemd (centiumd.service)
        </button>
        <button
          onClick={() => setActiveTab('routing')}
          className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer ${
            activeTab === 'routing'
              ? 'bg-zinc-800 text-zinc-100 border border-zinc-700'
              : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          Routing & Kill Switch Script
        </button>
        <button
          onClick={() => setActiveTab('rust')}
          className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors cursor-pointer ${
            activeTab === 'rust'
              ? 'bg-zinc-800 text-zinc-100 border border-zinc-700'
              : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          Rust Core Daemon (main.rs)
        </button>
      </div>

      {/* Instructions Guide */}
      <div className="p-3.5 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300 space-y-1">
        {activeTab === 'arch' && (
          <div>
            <strong>Build on Arch Linux:</strong> <code className="text-emerald-400 font-mono">makepkg -si</code> creates and installs the complete package with systemd service and desktop file.
          </div>
        )}
        {activeTab === 'debian' && (
          <div>
            <strong>Build on Ubuntu/Debian:</strong> <code className="text-emerald-400 font-mono">dpkg-buildpackage -b -us -uc</code> generates the <code className="text-zinc-400 font-mono">centium_1.0.0_amd64.deb</code> package.
          </div>
        )}
        {activeTab === 'service' && (
          <div>
            <strong>Privileged Daemon:</strong> <code className="text-emerald-400 font-mono">sudo systemctl enable --now centiumd</code> provides rootless desktop UI communication via <code className="text-zinc-400 font-mono">/run/centium/centium.sock</code>.
          </div>
        )}
        {activeTab === 'routing' && (
          <div>
            <strong>Transparent Routing:</strong> Run <code className="text-emerald-400 font-mono">centium-routing.sh enable</code> to redirect TCP to TransPort 9040, DNS to 5353, and arm the fail-closed kill switch.
          </div>
        )}
        {activeTab === 'rust' && (
          <div>
            <strong>Native Rust Core:</strong> Standalone privileged daemon (<code className="text-emerald-400 font-mono">centiumd</code>) that handles network interface creation, IPC, and child process security.
          </div>
        )}
      </div>

      {/* Code Viewer */}
      <div className="bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden font-mono text-xs">
        <div className="bg-zinc-900 px-4 py-2 border-b border-zinc-800 flex items-center justify-between text-[11px] text-zinc-400">
          <span>Source File Preview</span>
          <span>UTF-8 Linux</span>
        </div>
        <pre className="p-4 overflow-x-auto text-[11px] text-zinc-300 leading-relaxed max-h-96">
          {getCurrentContent()}
        </pre>
      </div>
    </div>
  );
};
