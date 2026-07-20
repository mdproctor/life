import { defineConfig } from 'vite';
import { resolve } from 'path';

const blocksUi = resolve(__dirname, '../../blocks-ui');
const pages = resolve(__dirname, '../../pages/packages');

export default defineConfig({
  root: __dirname,
  resolve: {
    alias: [
      { find: '@casehubio/blocks-ui-core', replacement: resolve(blocksUi, 'packages/blocks-ui-core/src') },
      { find: '@casehubio/blocks-ui-work-item-workbench', replacement: resolve(blocksUi, 'components/work-item-workbench/src') },
      { find: '@casehubio/blocks-ui-work-item-inbox', replacement: resolve(blocksUi, 'components/work-item-inbox/src') },
      { find: '@casehubio/blocks-ui-work-item-detail', replacement: resolve(blocksUi, 'components/work-item-detail/src') },
      { find: '@casehubio/blocks-ui-work-item-row', replacement: resolve(blocksUi, 'components/work-item-row/src') },
      { find: '@casehubio/blocks-ui-kpi-metric-row', replacement: resolve(blocksUi, 'components/kpi-metric-row/src') },
      { find: '@casehubio/blocks-ui-sla-indicator', replacement: resolve(blocksUi, 'components/sla-indicator/src') },
      { find: '@casehubio/blocks-ui-sla-breach-policy', replacement: resolve(blocksUi, 'components/sla-breach-policy/src') },
      { find: '@casehubio/blocks-ui-grouped-data-view', replacement: resolve(blocksUi, 'components/grouped-data-view/src') },
      { find: '@casehubio/blocks-ui-notification-inbox', replacement: resolve(blocksUi, 'components/notification-inbox/src') },
      { find: '@casehubio/blocks-ui-split-workbench', replacement: resolve(blocksUi, 'components/split-workbench/src') },
      { find: '@casehubio/blocks-ui-list-pane', replacement: resolve(blocksUi, 'components/list-pane/src') },
      { find: '@casehubio/blocks-ui-detail-pane', replacement: resolve(blocksUi, 'components/detail-pane/src') },
      { find: '@casehubio/pages-ui-tokens', replacement: resolve(pages, 'pages-ui-tokens/src') },
      { find: /^@casehubio\/pages-component\/dist\/(.*)/, replacement: resolve(pages, 'pages-component/src/$1') },
      { find: '@casehubio/pages-component', replacement: resolve(pages, 'pages-component/src') },
      { find: /^@casehubio\/pages-data\/dist\/(.*)/, replacement: resolve(pages, 'pages-data/src/$1') },
      { find: '@casehubio/pages-data', replacement: resolve(pages, 'pages-data/src') },
      { find: '@casehubio/pages-primitives', replacement: resolve(pages, 'pages-primitives/src') },
      { find: '@casehubio/pages-table', replacement: resolve(pages, 'pages-table/src') },
      { find: 'lit', replacement: resolve(blocksUi, 'node_modules/lit') },
      { find: 'lit/decorators.js', replacement: resolve(blocksUi, 'node_modules/lit/decorators.js') },
      { find: '@lit/reactive-element', replacement: resolve(blocksUi, 'node_modules/@lit/reactive-element') },
    ],
  },
  esbuild: {
    target: 'es2022',
    tsconfigRaw: {
      compilerOptions: {
        experimentalDecorators: true,
        useDefineForClassFields: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: resolve(__dirname, 'index.html'),
    },
  },
  server: {
    proxy: {
      '/life-cases': 'http://localhost:8080',
      '/life-tasks': 'http://localhost:8080',
      '/pending-actions': 'http://localhost:8080',
      '/external-actors': 'http://localhost:8080',
      '/analytics': 'http://localhost:8080',
      '/events': 'http://localhost:8080',
    },
    fs: {
      allow: ['..', '../../blocks-ui', '../../pages'],
    },
  },
});
