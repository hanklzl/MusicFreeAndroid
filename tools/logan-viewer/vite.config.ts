import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 部署到 GitHub Pages 时 base 会被设置为子路径；本地 dev / preview 默认 '/'。
const repoBase = process.env.VITE_PAGES_BASE ?? '/';

export default defineConfig({
  base: repoBase,
  plugins: [react()],
  worker: {
    format: 'es',
  },
  build: {
    target: 'es2022',
    sourcemap: true,
  },
});
