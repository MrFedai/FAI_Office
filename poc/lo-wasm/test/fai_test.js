// PoC WP7: runs inside LibreOffice's main thread (a web worker) once UNO is up. Loads the corpus documents
// (index.html writes them to /tmp) with loadComponentFromURL and reports timings to the page over a
// BroadcastChannel. Throwaway (MASTER_SPEC 15).
(function () {
  const ch = new BroadcastChannel('fai');
  const say = (k, v) => ch.postMessage({ k, v });
  const faiSeen = new Set();
  say('worker_script_loaded_at', Date.now());
  Module.uno_init.then(async function () {
    say('uno_ready_at', Date.now());
    try {
      const css = Module.uno.com.sun.star;
      const desktop = css.frame.Desktop.create(Module.getUnoComponentContext());
      const loader = css.frame.XComponentLoader.query(desktop);
      const hidden = () => new Module.uno_Sequence_com$sun$star$beans$PropertyValue([{
        Name: 'Hidden', Handle: 0, Value: new Module.uno_Any(Module.uno_Type.Boolean(), true),
        State: css.beans.PropertyState.DIRECT_VALUE }]);
      const open = (url) => { const a = hidden(); try { return loader.loadComponentFromURL(url, '_blank', 0, a); } finally { a.delete(); } };
      try {
        const t = Date.now();
        css.util.XCloseable.query(open('private:factory/swriter')).close(true);
        say('new_writer_doc', (Date.now() - t) + ' ms');
      } catch (e) { say('new_writer_doc', 'error ' + String(e).split('\n')[0]); }
      try {
        const t = Date.now();
        css.util.XCloseable.query(open('file:///android/default-document/example.odt')).close(true);
        say('load_example.odt', (Date.now() - t) + ' ms');
      } catch (e) { say('load_example.odt', 'error ' + String(e).split('\n')[0]); }
      for (const name of ['typical-20p.docx', 'typical.xlsx', 'typical.pptx', 'typical-20p.docx']) {
        const key = (k) => faiSeen.has(k) ? k + '_warm' : (faiSeen.add(k), k);
        const k = key('load_' + name);
        try {
          const t = Date.now();
          const model = open('file:///tmp/' + name);
          const loadMs = Date.now() - t;
          let pages = '';
          let layoutMs = '';
          if (css.text.XTextDocument.query(model)) {
            // Writer: the view's PageCount property needs the full layout, so reading it forces one.
            const t2 = Date.now();
            const ctl = css.beans.XPropertySet.query(css.frame.XModel.query(model).getCurrentController());
            const n = ctl.getPropertyValue('PageCount');
            pages = 'pages ' + n.get();
            n.delete();
            layoutMs = ', full layout +' + (Date.now() - t2) + ' ms';
          } else if (css.sheet.XSpreadsheetDocument.query(model)) {
            pages = 'sheets ' + css.sheet.XSpreadsheetDocument.query(model).getSheets().getElementNames().size();
          } else if (css.drawing.XDrawPagesSupplier.query(model)) {
            pages = 'slides ' + css.drawing.XDrawPagesSupplier.query(model).getDrawPages().getCount();
          }
          say(k, loadMs + ' ms, ' + pages + layoutMs);
          css.util.XCloseable.query(model).close(true);
        } catch (e) {
          say(k, 'error ' + String(e).split('\n')[0]);
        }
      }
      say('done', Date.now());
    } catch (e) {
      say('error', String(e && (e.stack || e.message) || e));
    }
  });
})();
