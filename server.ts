import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import { torManager } from './server/torManager.ts';
import {
  CENTIUM_SYSTEMD_SERVICE,
  CENTIUM_ROUTING_SCRIPT,
  ARCH_PKGBUILD,
  DEBIAN_CONTROL,
  RUST_DAEMON_SOURCE,
} from './server/linuxIntegration.ts';

async function startServer() {
  const app = express();
  const PORT = 3000;

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
    res.json(torManager.config);
  });

  app.post('/api/config', (req, res) => {
    torManager.updateConfig(req.body);
    res.json({ success: true, config: torManager.config });
  });

  app.post('/api/diagnostics/test', async (req, res) => {
    try {
      const results = await torManager.runDiagnostics();
      res.json({ success: true, results });
    } catch (e: any) {
      res.status(500).json({ success: false, error: e.message });
    }
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
      routingScript: CENTIUM_ROUTING_SCRIPT,
      archPkgbuild: ARCH_PKGBUILD,
      debianControl: DEBIAN_CONTROL,
      rustDaemon: RUST_DAEMON_SOURCE,
    });
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
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

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[Centium] Daemon listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Fatal server startup failure:', err);
});
