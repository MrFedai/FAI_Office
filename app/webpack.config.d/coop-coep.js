// The office engine needs SharedArrayBuffer, which browsers give only to cross-origin isolated pages
// (PHASE1_PROPOSAL.md section 13). The development server sends the same headers as production hosting.
config.devServer = config.devServer || {};
config.devServer.headers = Object.assign({}, config.devServer.headers, {
  "Cross-Origin-Opener-Policy": "same-origin",
  "Cross-Origin-Embedder-Policy": "require-corp",
});
