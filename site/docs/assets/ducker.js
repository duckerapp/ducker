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

// The language switch keeps the page. The theme's own mapping gives up when the current address starts with the
// target language's address, which is always the case going from Russian (/ducker/ru/…) to English (/ducker/…),
// and sends the reader to the English home page. Both languages have the same pages, so the path carries over.
(() => {
  document.addEventListener("click", (event) => {
    const link = event.target.closest("a.md-select__link[hreflang]");
    if (!link || event.metaKey || event.ctrlKey || event.shiftKey) return;
    const bases = [...document.querySelectorAll('link[rel="alternate"][hreflang]')]
      .map((alt) => new URL(alt.href).pathname)
      .sort((a, b) => b.length - a.length);
    const here = location.pathname;
    const current = bases.find((base) => here.startsWith(base));
    const target = new URL(link.href).pathname;
    if (!current || current === target) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    location.href = target + here.slice(current.length) + location.hash;
  }, true);
})();
