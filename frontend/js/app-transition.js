/** Native document navigation: keep the outgoing menu still while the next
 * document restores scroll and checks permissions. Never transplant page DOM
 * or replay page scripts; each page retains its own form/event lifecycle. */
(() => {
  let navigationReady = false;
  let releasePending = () => {};

  function observe(event) {
    const transition = event.viewTransition;
    if (!transition) return;
    transition.ready.catch(() => {});
    transition.finished.catch(() => {});
    transition.updateCallbackDone.catch(() => {});
  }

  function reveal(event) {
    observe(event);
    releasePending();
    const transition = event.viewTransition;
    if (!transition || navigationReady || typeof document === 'undefined'
        || (document.readyState !== 'loading' && !document.getElementById('sidebar'))
        || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;

    // No old menu exists on a direct visit or when arriving from login/portal.
    let previousMenu;
    try { previousMenu = window.navigation?.activation?.from?.getState?.()?.saleStockNavigation; }
    catch { return; }
    if (!previousMenu) return;

    const root = document.documentElement;
    root.classList.add('app-shell-settling');
    // A failed script or stalled network must never leave a frozen screenshot.
    let released = false;
    const timeout = setTimeout(release, 1000);
    function release() {
      if (released) return;
      released = true;
      clearTimeout(timeout);
      root.classList.remove('app-shell-settling');
      if (releasePending === release) releasePending = () => {};
    }
    releasePending = release;
    transition.ready.catch(release);
    transition.finished.then(release, release);
  }

  window.AppTransition = {
    navigationReady() {
      navigationReady = true;
      releasePending();
    },
  };
  if (typeof document !== 'undefined') {
    document.addEventListener?.('DOMContentLoaded', () => {
      // On a cold load pagereveal can precede parsing the sidebar. Only decide
      // that a destination is standalone once its DOM has actually been parsed.
      if (!document.getElementById('sidebar')) window.AppTransition.navigationReady();
    }, { once: true });
  }
  window.addEventListener('pageswap', event => { releasePending(); observe(event); });
  window.addEventListener('pagereveal', reveal);
})();
