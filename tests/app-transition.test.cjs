const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

test('cancelled native transitions are handled before page scripts initialize', async () => {
  const handlers = new Map();
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../frontend/js/app-transition.js'), 'utf8'), {
    window: { addEventListener: (name, callback) => handlers.set(name, callback) },
  });
  for (const name of ['pageswap', 'pagereveal']) {
    const transition = {
      ready: Promise.reject(new Error('cancelled')),
      finished: Promise.reject(new Error('cancelled')),
      updateCallbackDone: Promise.reject(new Error('cancelled')),
    };
    handlers.get(name)({ viewTransition: transition });
    handlers.get(name)({ viewTransition: null });
  }
  await new Promise(setImmediate);
  assert.equal(handlers.size, 2);
});

test('every page using shared navigation installs cancellation handlers in its head', () => {
  const root = path.join(__dirname, '../frontend');
  function inspect(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) inspect(file);
      else if (file.endsWith('.html')) {
        const html = fs.readFileSync(file, 'utf8');
        if (!html.includes('/css/app-navigation.css')) continue;
        const script = html.indexOf('<script src="/js/app-transition.js"></script>');
        assert.ok(script > html.indexOf('<head>') && script < html.indexOf('</head>'), file);
      }
    }
  }
  inspect(root);
});
