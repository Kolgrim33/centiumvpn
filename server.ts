import express from 'express';
import fs from 'fs';
import path from 'path';
import { torManager } from './server/torManager.ts';

if (!process.env.NODE_ENV) {
  process.env.NODE_ENV = 'production';
}
import {
  CENTIUM_SYSTEMD_SERVICE,
  CENTIUM_HEV_SERVICE,
  CENTIUM_NETWORK_SCRIPT,
  CENTIUM_ROUTING_SCRIPT,
  ARCH_PKGBUILD,
  DEBIAN_CONTROL,
  RUST_DAEMON_SOURCE,
} from './server/linuxIntegration.ts';
import { exec, execFile } from 'child_process';

async function startServer() {
  const app = express();
  const PORT = 3000;

  // Block DNS rebinding attacks: strictly permit localhost / 127.0.0.1
  app.use((req, res, next) => {
    const host = req.headers.host;
    if (host && !/^(127\.0\.0\.1|localhost)(:\d+)?$/i.test(host)) {
      return res.status(403).json({ error: 'Access denied: invalid Host header' });
    }
    next();
  });

  app.use(express.json());

  // Centium IPC / REST endpoints
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', service: 'centiumd', timestamp: Date.now() });
  });

  app.get('/api/status', (req, res) => {
    res.json(torManager.getStatus());
  });

  app.post('/api/connect', async (req, res) => {
    try {
      if (torManager.isConnectInProgress()) {
        return res.status(409).json({ success: false, message: 'Connection already in progress' });
      }
      if (req.body && typeof req.body === 'object') {
        torManager.updateConfig(req.body);
      }
      // Trigger connection asynchronously so the UI gets instantaneous feedback and can stream progress
      torManager.connect().catch((err) => {
        console.error('Connection error:', err);
      });
      res.json({ success: true, message: 'Connection sequence started' });
    } catch (e: any) {
      res.status(500).json({ success: false, error: e.message });
    }
  });

  app.post('/api/disconnect', async (req, res) => {
    try {
      torManager.disconnect().catch((err) => {
        console.error('Disconnect error:', err);
      });
      res.json({ success: true, message: 'Disconnection sequence started' });
    } catch (e: any) {
      res.status(500).json({ success: false, error: e.message });
    }
  });

  app.get('/api/config', (req, res) => {
    res.json(torManager.getConfig());
  });

  app.post('/api/config', (req, res) => {
    torManager.updateConfig(req.body);
    res.json({ success: true, config: torManager.getConfig() });
  });

  app.post('/api/diagnostics/test', async (req, res) => {
    try {
      const results = await torManager.runDiagnostics();
      res.json({ success: true, results });
    } catch (e: any) {
      res.status(500).json({ success: false, error: e.message });
    }
  });

  app.get('/api/diagnostics/suite', (req, res) => {
    const diagScript = path.resolve(process.cwd(), 'linux/centium-diagnose.sh');
    execFile('bash', [diagScript], (err, stdout, stderr) => {
      res.json({
        success: !err,
        output: stdout || stderr,
        exitCode: err ? err.code : 0,
      });
    });
  });

  app.post('/api/newnym', async (req, res) => {
    try {
      const ok = await torManager.signalNewnym();
      res.json({ success: ok });
    } catch (e: any) {
      res.status(500).json({ success: false, error: e.message });
    }
  });

  app.get('/api/logs', (req, res) => {
    res.json({ logs: torManager.getLogs() });
  });

  app.get('/api/linux-integration', (req, res) => {
    res.json({
      systemd: CENTIUM_SYSTEMD_SERVICE,
      hevService: CENTIUM_HEV_SERVICE,
      networkScript: CENTIUM_NETWORK_SCRIPT,
      routingScript: CENTIUM_ROUTING_SCRIPT,
      archPkgbuild: ARCH_PKGBUILD,
      debianControl: DEBIAN_CONTROL,
      rustDaemon: RUST_DAEMON_SOURCE,
    });
  });

  // Serve built assets whenever dist/index.html is available or in production mode
  const distCandidates = [
    path.join(process.cwd(), 'dist'),
    '/opt/centium/dist',
  ];
  if (typeof __dirname !== 'undefined') {
    distCandidates.push(path.join(__dirname, '../dist'), path.join(__dirname, '.'));
  }
  const distPath = distCandidates.find((p) => {
    try {
      return fs.existsSync(path.join(p, 'index.html'));
    } catch {
      return false;
    }
  });

  const isProduction = process.env.NODE_ENV === 'production' || !!distPath;

  if (isProduction && distPath) {
    console.log(`[Centium] Serving production frontend from ${distPath}`);
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  } else {
    // Development mode fallback when dist is not yet built
    try {
      const { createServer: createViteServer } = await import('vite');
      const vite = await createViteServer({
        server: { middlewareMode: true },
        appType: 'spa',
      });
      app.use(vite.middlewares);
    } catch (viteErr: any) {
      console.warn('[Centium] Failed to start Vite middleware, serving static fallback:', viteErr.message);
      if (distPath) {
        app.use(express.static(distPath));
        app.get('*', (req, res) => {
          res.sendFile(path.join(distPath, 'index.html'));
        });
      }
    }
  }

  // Graceful termination
  let isShuttingDown = false;
  const handleShutdown = async (signal: string) => {
    if (isShuttingDown) return;
    isShuttingDown = true;
    console.log(`[Centium] Received ${signal}, starting graceful shutdown...`);
    try {
      await torManager.disconnect();
    } catch (err: any) {
      console.error(`[Centium] Error during graceful disconnect on ${signal}:`, err);
    } finally {
      process.exit(0);
    }
  };

  process.on('SIGTERM', () => {
    handleShutdown('SIGTERM');
  });

  process.on('SIGINT', () => {
    handleShutdown('SIGINT');
  });

  process.on('uncaughtException', (err) => {
    console.error('[Centium Fatal] Uncaught exception:', err);
  });

  process.on('unhandledRejection', (reason) => {
    console.error('[Centium Warning] Unhandled promise rejection:', reason);
  });

  app.listen(PORT, '127.0.0.1', () => {
    console.log(`[Centium] Daemon listening on http://127.0.0.1:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Fatal server startup failure:', err);
});
