import { CentiumStatus, CentiumConfig, DiagnosticResult } from './types.ts';

export async function fetchStatus(): Promise<CentiumStatus> {
  const res = await fetch('/api/status');
  if (!res.ok) throw new Error('Failed to fetch Centium status');
  return res.json();
}

export async function connectVPN(configOverrides?: Partial<CentiumConfig>): Promise<{ success: boolean }> {
  const res = await fetch('/api/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(configOverrides || {}),
  });
  if (!res.ok) throw new Error('Failed to initiate connection');
  return res.json();
}

export async function disconnectVPN(): Promise<{ success: boolean }> {
  const res = await fetch('/api/disconnect', {
    method: 'POST',
  });
  if (!res.ok) throw new Error('Failed to disconnect');
  return res.json();
}

export async function fetchConfig(): Promise<CentiumConfig> {
  const res = await fetch('/api/config');
  if (!res.ok) throw new Error('Failed to fetch config');
  return res.json();
}

export async function saveConfig(config: Partial<CentiumConfig>): Promise<{ success: boolean; config: CentiumConfig }> {
  const res = await fetch('/api/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  });
  if (!res.ok) throw new Error('Failed to save config');
  return res.json();
}

export async function runDiagnosticsTest(): Promise<{ success: boolean; results: DiagnosticResult }> {
  const res = await fetch('/api/diagnostics/test', {
    method: 'POST',
  });
  if (!res.ok) throw new Error('Failed to run diagnostics');
  return res.json();
}

export async function signalNewCircuit(): Promise<{ success: boolean }> {
  const res = await fetch('/api/newnym', {
    method: 'POST',
  });
  if (!res.ok) throw new Error('Failed to signal new circuit');
  return res.json();
}

export async function fetchLogs(): Promise<string[]> {
  const res = await fetch('/api/logs');
  if (!res.ok) throw new Error('Failed to fetch logs');
  const data = await res.json();
  return data.logs || [];
}

export async function fetchLinuxFiles(): Promise<{
  systemd: string;
  routingScript: string;
  archPkgbuild: string;
  debianControl: string;
  rustDaemon: string;
}> {
  const res = await fetch('/api/linux-integration');
  if (!res.ok) throw new Error('Failed to fetch Linux integration files');
  return res.json();
}
