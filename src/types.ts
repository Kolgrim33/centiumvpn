export type ConnectionState =
  | 'DISCONNECTED'
  | 'STARTING_TOR'
  | 'WAITING_FOR_BOOTSTRAP'
  | 'STARTING_TUN'
  | 'STARTING_BRIDGE'
  | 'INSTALLING_ROUTING'
  | 'INSTALLING_KILLSWITCH'
  | 'VERIFYING'
  | 'CONNECTED'
  | 'RECONNECTING'
  | 'DISCONNECTING'
  | 'ERROR'
  | 'STARTING'
  | 'CONNECTING';

export interface CircuitNode {
  role: 'Guard' | 'Middle' | 'Exit';
  ip?: string;
  fingerprint: string;
  nickname: string;
  country: string;
  countryCode: string;
}

export interface CentiumStatus {
  state: ConnectionState;
  statusMessage: string;
  stepDescription: string;
  currentStep: number;
  totalSteps: number;
  bootstrapPercent: number;
  connectedSince: number | null;
  publicIp: string | null;
  exitCountry: string | null;
  exitCountryCode: string | null;
  isTor: boolean;
  circuit: CircuitNode[];
  virtualInterface: string;
  killSwitchActive: boolean;
  dnsProtected: boolean;
  ipv6Protected: boolean;
  torPid: number | null;
  bridgePid?: number | null;
  bridgeRunning?: boolean;
  bytesReceived: number;
  bytesSent: number;
  errorMessage: string | null;
  lastUpdated: number;
}

export interface CentiumConfig {
  exitLocation: string;
  bridgeMode: 'auto' | 'builtin' | 'custom';
  bridgeType: 'obfs4' | 'snowflake' | 'meek' | 'none';
  customBridge: string;
  killSwitch: boolean;
  blockIpv6: boolean;
  autoConnect: boolean;
  startWithSystem: boolean;
  dnsProtection: boolean;
  virtualInterface: string;
  socksPort: number;
  controlPort: number;
}

export interface DiagnosticResult {
  torRunning: boolean;
  bootstrap: number;
  networkConnected: boolean;
  dnsProtected: boolean;
  ipv6Protected: boolean;
  killSwitchActive: boolean;
  trafficRouted: boolean;
  verifiedExitIp: string | null;
  isTorExit: boolean;
  latencyMs: number;
  testDetails: string[];
}
