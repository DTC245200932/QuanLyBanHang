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

function settlingEnvironment({ reduced = false, previous = true, sidebar = true, loading = false } = {}) {
  const handlers = new Map();
  const classes = new Set();
  const timers = new Map();
  let nextTimer = 0;
  const window = {
    addEventListener: (name, callback) => handlers.set(name, callback),
    matchMedia: () => ({ matches: reduced }),
    navigation: { activation: { from: { getState: () => previous ? { saleStockNavigation: { owner: '7', top: 312 } } : {} } } },
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../frontend/js/app-transition.js'), 'utf8'), {
    window,
    document: { readyState: loading ? 'loading' : 'complete', getElementById: () => sidebar ? {} : null,
      documentElement: { classList: { add: name => classes.add(name), remove: name => classes.delete(name) } } },
    setTimeout: (callback, delay) => { const id = ++nextTimer; timers.set(id, { callback, delay }); return id; },
    clearTimeout: id => timers.delete(id),
  });
  function transition() {
    let finish;
    const finished = new Promise(resolve => { finish = resolve; });
    return { ready: Promise.resolve(), updateCallbackDone: Promise.resolve(), finished, finish };
  }
  return { handlers, classes, timers, window, transition };
}

test('outgoing menu stays until the permission-aware navigation is ready', () => {
  const app = settlingEnvironment();
  app.handlers.get('pagereveal')({ viewTransition: app.transition() });
  assert.equal(app.classes.has('app-shell-settling'), true);
  assert.equal([...app.timers.values()][0].delay, 1000);
  app.window.AppTransition.navigationReady();
  assert.equal(app.classes.size, 0);
  assert.equal(app.timers.size, 0);
});

test('watchdog releases visual hold even when page initialization never finishes', () => {
  const app = settlingEnvironment();
  app.handlers.get('pagereveal')({ viewTransition: app.transition() });
  [...app.timers.values()][0].callback();
  assert.equal(app.classes.size, 0);
  assert.equal(app.timers.size, 0);
});

test('direct visits, reduced motion, standalone pages and ready menus never pause', () => {
  for (const options of [{ reduced: true }, { previous: false }, { sidebar: false }, { ready: true }]) {
    const app = settlingEnvironment(options);
    if (options.ready) app.window.AppTransition.navigationReady();
    app.handlers.get('pagereveal')({ viewTransition: app.transition() });
    assert.equal(app.classes.size, 0);
    assert.equal(app.timers.size, 0);
  }
});

test('completion of an earlier cancelled transition cannot release a newer hold', async () => {
  const app = settlingEnvironment();
  const old = app.transition();
  app.handlers.get('pagereveal')({ viewTransition: old });
  app.handlers.get('pageswap')({ viewTransition: null });
  app.handlers.get('pagereveal')({ viewTransition: app.transition() });
  old.finish();
  await new Promise(setImmediate);
  assert.equal(app.classes.has('app-shell-settling'), true);
  assert.equal(app.timers.size, 1);
  app.window.AppTransition.navigationReady();
});

test('a cold document can hold the menu before the parser reaches the sidebar', () => {
  const app = settlingEnvironment({ loading: true, sidebar: false });
  app.handlers.get('pagereveal')({ viewTransition: app.transition() });
  assert.equal(app.classes.has('app-shell-settling'), true);
  app.window.AppTransition.navigationReady();
  assert.equal(app.timers.size, 0);
});
