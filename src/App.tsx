import React, { useState, useEffect, useCallback } from 'react';
import { Header } from './components/Header.tsx';
import { MainConnectView } from './components/MainConnectView.tsx';
import { ExitLocationView } from './components/ExitLocationView.tsx';
import { BridgesView } from './components/BridgesView.tsx';
import { PrivacyDashboardView } from './components/PrivacyDashboardView.tsx';
import { DiagnosticsView } from './components/DiagnosticsView.tsx';
import { NetworkInfoView } from './components/NetworkInfoView.tsx';
import { SettingsView } from './components/SettingsView.tsx';
import { LinuxPackagesModal } from './components/LinuxPackagesModal.tsx';
import { SystemTrayPreview } from './components/SystemTrayPreview.tsx';
import {
  fetchStatus,
  fetchConfig,
  connectVPN,
  disconnectVPN,
  saveConfig,
  signalNewCircuit,
} from './api.ts';
import { CentiumStatus, CentiumConfig } from './types.ts';

const DEFAULT_STATUS: CentiumStatus = {
  state: 'DISCONNECTED',
  statusMessage: 'Ready to connect',
  stepDescription: '',
  currentStep: 0,
  totalSteps: 11,
  bootstrapPercent: 0,
  connectedSince: null,
  publicIp: null,
  exitCountry: null,
  exitCountryCode: null,
  isTor: false,
  circuit: [],
  virtualInterface: 'centium0',
  killSwitchActive: false,
  dnsProtected: true,
  ipv6Protected: true,
  torPid: null,
  bytesReceived: 0,
  bytesSent: 0,
  errorMessage: null,
  lastUpdated: Date.now(),
};

const DEFAULT_CONFIG: CentiumConfig = {
  exitLocation: 'auto',
  bridgeMode: 'auto',
  bridgeType: 'none',
  customBridge: '',
  killSwitch: true,
  blockIpv6: true,
  autoConnect: false,
  startWithSystem: false,
  dnsProtection: true,
  virtualInterface: 'centium0',
  socksPort: 9050,
  controlPort: 9051,
  dnsPort: 5353,
  transportPort: 9040,
};

export default function App() {
  const [status, setStatus] = useState<CentiumStatus>(DEFAULT_STATUS);
  const [config, setConfig] = useState<CentiumConfig>(DEFAULT_CONFIG);
  const [currentTab, setCurrentTab] = useState<string>('connect');
  const [showTray, setShowTray] = useState<boolean>(false);

  // Load initial config and status
  const refreshStatus = useCallback(async () => {
    try {
      const data = await fetchStatus();
      setStatus(data);
    } catch {
      // ignore transient poll error
    }
  }, []);

  useEffect(() => {
    fetchConfig()
      .then((cfg) => setConfig(cfg))
      .catch(() => {});
    refreshStatus();
  }, [refreshStatus]);

  // Dynamic status poll interval based on connection state
  useEffect(() => {
    const isBusy =
      status.state === 'STARTING' ||
      status.state === 'CONNECTING' ||
      status.state === 'DISCONNECTING' ||
      status.state === 'RECONNECTING';

    const intervalMs = isBusy ? 800 : status.state === 'CONNECTED' ? 2000 : 4000;
    const timer = setInterval(refreshStatus, intervalMs);
    return () => clearInterval(timer);
  }, [status.state, refreshStatus]);

  // Connect handler
  const handleConnect = async () => {
    try {
      setStatus((prev) => ({
        ...prev,
        state: 'STARTING',
        statusMessage: 'Starting Tor...',
        currentStep: 1,
        stepDescription: 'Verifying Tor installation and environment',
      }));
      await connectVPN();
      await refreshStatus();
    } catch (e: any) {
      setStatus((prev) => ({
        ...prev,
        state: 'ERROR',
        errorMessage: e.message || 'Failed to start connection',
      }));
    }
  };

  // Disconnect handler
  const handleDisconnect = async () => {
    try {
      setStatus((prev) => ({
        ...prev,
        state: 'DISCONNECTING',
        statusMessage: 'Disconnecting and restoring routing...',
      }));
      await disconnectVPN();
      await refreshStatus();
    } catch (e: any) {
      console.error(e);
    }
  };

  // Save config handler
  const handleSaveConfig = async (newConfig: Partial<CentiumConfig>) => {
    try {
      const res = await saveConfig(newConfig);
      if (res.success) {
        setConfig(res.config);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // Refresh circuit handler
  const handleRefreshCircuit = async () => {
    try {
      await signalNewCircuit();
      await refreshStatus();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col selection:bg-emerald-500/30 selection:text-emerald-300">
      {/* Top Application Bar */}
      <Header
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        connectionState={status.state}
        showTray={showTray}
        onToggleTray={() => setShowTray(!showTray)}
      />

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col justify-center items-center px-4 py-4 w-full">
        {currentTab === 'connect' && (
          <MainConnectView
            status={status}
            config={config}
            onConnect={handleConnect}
            onDisconnect={handleDisconnect}
            onRefreshCircuit={handleRefreshCircuit}
            onNavigateToExit={() => setCurrentTab('exit')}
            onNavigateToPackages={() => setCurrentTab('packages')}
          />
        )}

        {currentTab === 'exit' && (
          <ExitLocationView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('connect')}
            isConnected={status.state === 'CONNECTED'}
            onRefreshCircuit={handleRefreshCircuit}
          />
        )}

        {currentTab === 'bridges' && (
          <BridgesView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('connect')}
          />
        )}

        {currentTab === 'privacy' && (
          <PrivacyDashboardView
            status={status}
            onBack={() => setCurrentTab('connect')}
          />
        )}

        {currentTab === 'diagnostics' && (
          <DiagnosticsView
            status={status}
            onBack={() => setCurrentTab('connect')}
          />
        )}

        {currentTab === 'network' && (
          <NetworkInfoView
            status={status}
            onBack={() => setCurrentTab('connect')}
            onRefreshCircuit={handleRefreshCircuit}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('connect')}
          />
        )}

        {currentTab === 'packages' && (
          <LinuxPackagesModal onBack={() => setCurrentTab('connect')} />
        )}
      </main>

      {/* Interactive System Tray Simulation */}
      {showTray && (
        <SystemTrayPreview
          status={status}
          onConnect={handleConnect}
          onDisconnect={handleDisconnect}
          onOpenSettings={() => {
            setCurrentTab('settings');
            setShowTray(false);
          }}
          onOpenDiagnostics={() => {
            setCurrentTab('diagnostics');
            setShowTray(false);
          }}
          onClose={() => setShowTray(false)}
        />
      )}

      {/* Footer bar */}
      <footer className="border-t border-zinc-900 bg-zinc-950 py-2.5 px-4 text-center text-[11px] font-mono text-zinc-500">
        <div className="max-w-5xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-1">
          <div>Centium VPN v1.0.0 — Arch & Debian Linux Target</div>
          <div className="flex items-center gap-3">
            <span>Interface: {status.virtualInterface}</span>
            <span>•</span>
            <span>Kill Switch: {status.killSwitchActive ? 'ARMED' : 'STANDBY'}</span>
            <span>•</span>
            <span>Zero Backend Proxies</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
