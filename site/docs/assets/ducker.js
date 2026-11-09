// The theme switch as the app does it: the new theme spreads from the button as a circle, through a
// view transition. Without view transitions, or with reduced motion, the theme changes at once.
(() => {
  if (!document.startViewTransition || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  document.addEventListener("click", (event) => {
    const label = event.target.closest('label[for^="__palette_"]');
    const input = label && document.getElementById(label.htmlFor);
    if (!input) return;
    event.preventDefault();
    const box = label.getBoundingClientRect();
    const root = document.documentElement.style;
    root.setProperty("--dk-wave-x", `${box.left + box.width / 2}px`);
    root.setProperty("--dk-wave-y", `${box.top + box.height / 2}px`);
    document.startViewTransition(() => input.click());
  }, true);
})();
