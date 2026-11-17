// Linked into soffice.js with EMSCRIPTEN_EXTRA_SOFFICE_PRE_JS (see ../run.sh). Registers the PoC's UNO script
// and exposes the Emscripten file system to it (FS is not an exported runtime method in this build).
Module.uno_scripts = ['./fai_test.js'];
Module.faiFS = function () { return FS; };
