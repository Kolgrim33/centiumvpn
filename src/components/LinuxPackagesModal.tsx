import React, { useState, useEffect } from 'react';
import { Package, ArrowLeft, Copy, Check, Terminal, Code2 } from 'lucide-react';
import { fetchLinuxFiles } from '../api.ts';

interface LinuxPackagesModalProps {
  onBack: () => void;
}

export const LinuxPackagesModal: React.FC<LinuxPackagesModalProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<'setup' | 'arch' | 'debian' | 'service' | 'hev' | 'network' | 'rust'>('setup');
  const [files, setFiles] = useState<{
    systemd: string;
    hevService?: string;
    networkScript?: string;
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

  const getActiveContent = () => {
    if (!files) return 'Loading Linux package configurations...';
    switch (activeTab) {
      case 'setup':
        return `#!/usr/bin/env bash
# Centium VPN - Native Linux Desktop Installer (TUN + Tor SOCKS5 Architecture)
# Run on your Arch Linux, Ubuntu, Debian, or Fedora desktop:

sudo ./setup-linux.sh

# Starts Tor, builds pinned hev-socks5-tunnel, and arms nftables fail-closed protection
sudo systemctl enable --now centiumd
npm start`;
      case 'arch':
        return files.archPkgbuild;
      case 'debian':
        return files.debianControl;
      case 'service':
        return files.systemd;
      case 'hev':
        return files.hevService || '';
      case 'network':
        return files.networkScript || files.routingScript;
      case 'rust':
        return files.rustDaemon;
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(getActiveContent());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="w-full max-w-2xl mx-auto py-6 px-4 space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-[#1B1824]">
        <div className="flex items-center gap-3">
          <button
            id="btn-back-from-packages"
            onClick={onBack}
            className="p-1.5 rounded-lg hover:bg-[#17141E] text-[#8E899E] hover:text-[#F4F3F7] transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-base font-semibold text-[#F4F3F7] flex items-center gap-2">
              <Terminal className="w-4 h-4 text-[#6C4DFF]" />
              Linux Desktop Integration
            </h2>
            <p className="text-xs text-[#8E899E]">
              Arch Linux (PKGBUILD), Debian/Ubuntu (.deb), and Systemd daemon setup
            </p>
          </div>
        </div>

        <button
          onClick={handleCopy}
          className="px-3 py-1.5 rounded-lg bg-[#17141E] hover:bg-[#201C2B] text-[#F4F3F7] border border-[#23202E] text-xs font-mono flex items-center gap-1.5 transition-colors cursor-pointer"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-[#1B1824] pb-2 overflow-x-auto text-xs">
        {[
          { id: 'setup', label: 'Quick Setup' },
          { id: 'network', label: 'Network Engine (centium-network.sh)' },
          { id: 'hev', label: 'Bridge Service (hev-socks5)' },
          { id: 'service', label: 'Systemd Daemon' },
          { id: 'arch', label: 'Arch (PKGBUILD)' },
          { id: 'debian', label: 'Debian / Ubuntu' },
          { id: 'rust', label: 'Rust Core (centiumd)' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer ${
              activeTab === tab.id
                ? 'bg-[#17141E] text-[#F4F3F7] border border-[#6C4DFF]/40'
                : 'text-[#8E899E] hover:text-[#F4F3F7]'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Code / Text viewer */}
      <div className="bg-[#121018] border border-[#1B1824] rounded-xl overflow-hidden font-mono text-xs">
        <div className="bg-[#0D0B12] px-4 py-2 border-b border-[#1B1824] flex items-center justify-between text-[11px] text-[#8E899E]">
          <span>Source Specification</span>
          <span>Linux AMD64 / ARM64</span>
        </div>
        <pre className="p-4 overflow-x-auto text-[11px] text-[#F4F3F7] leading-relaxed max-h-96">
          {getActiveContent()}
        </pre>
      </div>
    </div>
  );
};
