// The converter on the ducker-* headers page: text → `base64:<its UTF-8 bytes in base64>`, the
// form the app decodes in every provider header (ProviderInfo.decode). Runs in the browser only.
(() => {
  const init = () => {
    const input = document.getElementById("b64-in");
    if (!input) return;
    const output = document.getElementById("b64-out");
    const copy = document.getElementById("b64-copy");
    const label = copy.textContent;

    const update = () => {
      let binary = "";
      for (const byte of new TextEncoder().encode(input.value)) binary += String.fromCharCode(byte);
      output.value = input.value ? "base64:" + btoa(binary) : "";
      copy.textContent = label;
    };

    input.addEventListener("input", update);
    copy.addEventListener("click", () => {
      output.select();
      navigator.clipboard.writeText(output.value).then(
        () => { copy.textContent = copy.dataset.done; },
        () => document.execCommand("copy"),
      );
    });
    update();
  };

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
