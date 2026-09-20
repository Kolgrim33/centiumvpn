import React, { useState, useEffect, useCallback } from 'react';
import { Sidebar } from './components/Sidebar.tsx';
import { MobileNav } from './components/MobileNav.tsx';
import { MainConnectView } from './components/MainConnectView.tsx';
import { ExitLocationView } from './components/ExitLocationView.tsx';
import { BridgesView } from './components/BridgesView.tsx';
import { DiagnosticsView } from './components/DiagnosticsView.tsx';
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
};

export default function App() {
  const [status, setStatus] = useState<CentiumStatus>(DEFAULT_STATUS);
  const [config, setConfig] = useState<CentiumConfig>(DEFAULT_CONFIG);
  const [currentTab, setCurrentTab] = useState<string>('dashboard');
  const [showTray, setShowTray] = useState<boolean>(false);

  // Status poller
  const refreshStatus = useCallback(async () => {
    try {
      const data = await fetchStatus();
      setStatus(data);
    } catch {
      // transient network error during reload
    }
  }, []);

  useEffect(() => {
    fetchConfig()
      .then((cfg) => setConfig(cfg))
      .catch(() => {});
    refreshStatus();
  }, [refreshStatus]);

  // Dynamic status poll interval
  useEffect(() => {
    const isBusy =
      status.state === 'STARTING_TOR' ||
      status.state === 'WAITING_FOR_BOOTSTRAP' ||
      status.state === 'STARTING_TUN' ||
      status.state === 'STARTING_BRIDGE' ||
      status.state === 'INSTALLING_ROUTING' ||
      status.state === 'INSTALLING_KILLSWITCH' ||
      status.state === 'VERIFYING' ||
      status.state === 'STARTING' ||
      status.state === 'CONNECTING' ||
      status.state === 'DISCONNECTING' ||
      status.state === 'RECONNECTING';

    const intervalMs = isBusy ? 600 : status.state === 'CONNECTED' ? 2000 : 4000;
    const timer = setInterval(refreshStatus, intervalMs);
    return () => clearInterval(timer);
  }, [status.state, refreshStatus]);

  // Connect handler
  const handleConnect = async () => {
    try {
      setStatus((prev) => ({
        ...prev,
        state: 'STARTING_TOR',
        statusMessage: 'Initializing Tor engine...',
        currentStep: 1,
        totalSteps: 8,
        stepDescription: 'Validating environment and generating configuration',
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
    <div className="min-h-screen bg-[#0D0B12] text-[#F4F3F7] flex flex-col md:flex-row antialiased selection:bg-[#6C4DFF]/30 selection:text-[#F4F3F7]">
      {/* Desktop Sidebar (Section 7) */}
      <div className="hidden md:flex">
        <Sidebar
          currentTab={currentTab}
          onSelectTab={setCurrentTab}
          connectionState={status.state}
          showTray={showTray}
          onToggleTray={() => setShowTray(!showTray)}
        />
      </div>

      {/* Mobile Top Header and Bottom Navigation (Section 10) */}
      <MobileNav
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        connectionState={status.state}
      />

      {/* Main Content Pane (Section 7, 8, 17) */}
      <main className="flex-1 flex flex-col justify-center items-center px-4 py-8 pb-24 md:pb-8 w-full max-w-4xl mx-auto">
        {currentTab === 'dashboard' && (
          <MainConnectView
            status={status}
            config={config}
            onConnect={handleConnect}
            onDisconnect={handleDisconnect}
            onRefreshCircuit={handleRefreshCircuit}
            onNavigateToExit={() => setCurrentTab('locations')}
            onNavigateToPackages={() => setCurrentTab('about')}
          />
        )}

        {currentTab === 'locations' && (
          <ExitLocationView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('dashboard')}
            isConnected={status.state === 'CONNECTED'}
            onRefreshCircuit={handleRefreshCircuit}
          />
        )}

        {currentTab === 'bridges' && (
          <BridgesView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('dashboard')}
          />
        )}

        {currentTab === 'diagnostics' && (
          <DiagnosticsView
            status={status}
            onBack={() => setCurrentTab('dashboard')}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView
            config={config}
            onSaveConfig={handleSaveConfig}
            onBack={() => setCurrentTab('dashboard')}
          />
        )}

        {currentTab === 'about' && (
          <LinuxPackagesModal onBack={() => setCurrentTab('dashboard')} />
        )}
      </main>

      {/* Desktop System Tray Context Menu Simulation (Section 3, 23) */}
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
    </div>
  );
}
