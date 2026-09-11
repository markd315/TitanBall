import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const sotResDir = path.resolve(__dirname, '../../res');
const publicResDir = path.resolve(__dirname, '../public/res');
const distResDir = path.resolve(__dirname, '../dist/res');

// SOT is exclusively game.cfg - user/client-specific files like masteries.json and config.json are not copied
const CONFIG_FILES = ['game.cfg'];

function syncFile(filename) {
  const src = path.join(sotResDir, filename);
  if (!fs.existsSync(src)) {
    console.warn(`[sync-sot] Warning: Source file not found: ${src}`);
    return;
  }

  // Ensure public/res exists
  if (!fs.existsSync(publicResDir)) {
    fs.mkdirSync(publicResDir, { recursive: true });
  }

  const destPublic = path.join(publicResDir, filename);
  fs.copyFileSync(src, destPublic);
  console.log(`[sync-sot] Copied ${filename} -> client-web/public/res/${filename}`);

  // If dist/res exists (e.g. from existing or previous build), update it as well
  if (fs.existsSync(distResDir)) {
    const destDist = path.join(distResDir, filename);
    fs.copyFileSync(src, destDist);
    console.log(`[sync-sot] Copied ${filename} -> client-web/dist/res/${filename}`);
  }
}

console.log('[sync-sot] Syncing SOT config files from server root (res/)...');
for (const file of CONFIG_FILES) {
  syncFile(file);
}
console.log('[sync-sot] SOT sync complete.');
