// PoC WP7: runs inside LibreOffice's main thread (a web worker) once UNO is up. Loads the corpus documents with
// loadComponentFromURL and reports timings to the page over a BroadcastChannel. Throwaway (MASTER_SPEC 15).
(function () {
  const ch = new BroadcastChannel('fai');
  const say = (k, v) => ch.postMessage({ k, v });
  say('worker_script_loaded_at', Date.now());
  Module.uno_init.then(async function () {
    say('uno_ready_at', Date.now());
    try {
      const css = Module.uno.com.sun.star;
      const desktop = css.frame.Desktop.create(Module.getUnoComponentContext());
      const loader = css.frame.XComponentLoader.query(desktop);
      for (const name of ['typical-20p.docx', 'typical.xlsx', 'typical.pptx', 'typical-20p.docx']) {
        const bytes = new Uint8Array(await (await fetch('./docs/' + name)).arrayBuffer());
        Module.faiFS().writeFile('/tmp/' + name, bytes);
        const args = new Module.uno_Sequence_com$sun$star$beans$PropertyValue(0, Module.uno_Sequence.FromSize);
        const t = Date.now();
        const model = loader.loadComponentFromURL('file:///tmp/' + name, '_blank', 0, args);
        const loadMs = Date.now() - t;
        args.delete();
        let pages = '';
        const xPages = css.drawing.XDrawPagesSupplier.query(model);
        if (xPages) pages = 'slides ' + xPages.getDrawPages().getCount();
        const xSheets = css.sheet.XSpreadsheetDocument.query(model);
        if (xSheets) pages = 'sheets ' + xSheets.getSheets().getElementNames().length;
        say('load_' + name, loadMs + ' ms ' + pages);
        css.util.XCloseable.query(model).close(true);
      }
      say('done', Date.now());
    } catch (e) {
      say('error', String(e && (e.stack || e.message) || e));
    }
  });
})();
