/** Register before the first paint: native transitions can be cancelled when
 * navigating rapidly, restoring history, or enabling reduced motion. */
(() => {
  function observe(event) {
    const transition = event.viewTransition;
    if (!transition) return;
    transition.ready.catch(() => {});
    transition.finished.catch(() => {});
    transition.updateCallbackDone.catch(() => {});
  }
  window.addEventListener("pageswap", observe);
  window.addEventListener("pagereveal", observe);
})();
