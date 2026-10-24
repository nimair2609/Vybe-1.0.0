import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';

const buildVersion = process.env.COMMIT_REF?.slice(0, 8) || `1.0.0-${Date.now()}`;
const builtAt = new Date().toISOString();

function vybeVersionManifestPlugin(): Plugin {
  return {
    name: 'vybe-version-manifest',
    writeBundle(options) {
      const outDir = options.dir || path.resolve(__dirname, 'dist');
      const versionPayload = JSON.stringify(
        {
          name: 'VYBE',
          version: buildVersion,
          builtAt
        },
        null,
        2
      );
      fs.writeFileSync(path.join(outDir, 'version.json'), versionPayload, 'utf-8');

      // Stamp buildVersion into dist/sw.js so Service Worker cache versioning tracks exact deployments
      const swPath = path.join(outDir, 'sw.js');
      if (fs.existsSync(swPath)) {
        const swRaw = fs.readFileSync(swPath, 'utf-8');
        fs.writeFileSync(swPath, swRaw.replace(/__VYBE_BUILD_VERSION__/g, buildVersion), 'utf-8');
      }
    }
  };
}

export default defineConfig({
  plugins: [react(), vybeVersionManifestPlugin()],
  envDir: '.',
  envPrefix: ['VITE_', 'SUPABASE_'],
  define: {
    __APP_VERSION__: JSON.stringify(buildVersion),
    __APP_BUILT_AT__: JSON.stringify(builtAt)
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    target: 'es2020'
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
});
