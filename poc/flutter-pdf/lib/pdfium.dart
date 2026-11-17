// PoC WP5: PDFium through dart:ffi, owned by one background isolate. Throwaway (MASTER_SPEC section 15).
//
// The UI isolate never calls PDFium. It sends requests to the worker and gets RGBA pixels back as
// TransferableTypedData, so rendering never blocks a frame.
import 'dart:async';
import 'dart:ffi';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

typedef _VoidC = Void Function();
typedef _VoidD = void Function();
typedef _LoadDocC = Pointer<Void> Function(Pointer<Utf8>, Pointer<Utf8>);
typedef _PtrIntC = Int32 Function(Pointer<Void>);
typedef _PtrIntD = int Function(Pointer<Void>);
typedef _LoadPageC = Pointer<Void> Function(Pointer<Void>, Int32);
typedef _LoadPageD = Pointer<Void> Function(Pointer<Void>, int);
typedef _SizeC = Int32 Function(Pointer<Void>, Int32, Pointer<Double>, Pointer<Double>);
typedef _SizeD = int Function(Pointer<Void>, int, Pointer<Double>, Pointer<Double>);
typedef _BmpCreateC = Pointer<Void> Function(Int32, Int32, Int32, Pointer<Uint8>, Int32);
typedef _BmpCreateD = Pointer<Void> Function(int, int, int, Pointer<Uint8>, int);
typedef _FillC = Int32 Function(Pointer<Void>, Int32, Int32, Int32, Int32, UnsignedLong);
typedef _FillD = int Function(Pointer<Void>, int, int, int, int, int);
typedef _RenderC = Void Function(Pointer<Void>, Pointer<Void>, Int32, Int32, Int32, Int32, Int32, Int32);
typedef _RenderD = void Function(Pointer<Void>, Pointer<Void>, int, int, int, int, int, int);
typedef _PtrVoidC = Void Function(Pointer<Void>);
typedef _PtrVoidD = void Function(Pointer<Void>);
typedef _ErrC = UnsignedLong Function();
typedef _ErrD = int Function();

const _bgra = 4; // FPDFBitmap_BGRA
const _flags = 0x01 | 0x10; // FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER (gives RGBA)

class _Pdfium {
  _Pdfium(DynamicLibrary l)
      : init = l.lookupFunction<_VoidC, _VoidD>('FPDF_InitLibrary'),
        loadDocument = l.lookupFunction<_LoadDocC, _LoadDocC>('FPDF_LoadDocument'),
        pageCount = l.lookupFunction<_PtrIntC, _PtrIntD>('FPDF_GetPageCount'),
        pageSize = l.lookupFunction<_SizeC, _SizeD>('FPDF_GetPageSizeByIndex'),
        loadPage = l.lookupFunction<_LoadPageC, _LoadPageD>('FPDF_LoadPage'),
        bitmapCreate = l.lookupFunction<_BmpCreateC, _BmpCreateD>('FPDFBitmap_CreateEx'),
        fill = l.lookupFunction<_FillC, _FillD>('FPDFBitmap_FillRect'),
        render = l.lookupFunction<_RenderC, _RenderD>('FPDF_RenderPageBitmap'),
        bitmapDestroy = l.lookupFunction<_PtrVoidC, _PtrVoidD>('FPDFBitmap_Destroy'),
        closePage = l.lookupFunction<_PtrVoidC, _PtrVoidD>('FPDF_ClosePage'),
        lastError = l.lookupFunction<_ErrC, _ErrD>('FPDF_GetLastError');

  final _VoidD init;
  final _LoadDocC loadDocument;
  final _PtrIntD pageCount;
  final _SizeD pageSize;
  final _LoadPageD loadPage;
  final _BmpCreateD bitmapCreate;
  final _FillD fill;
  final _RenderD render;
  final _PtrVoidD bitmapDestroy;
  final _PtrVoidD closePage;
  final _ErrD lastError;
}

/// Result of opening a document: page sizes in PDF points.
class PdfInfo {
  PdfInfo(this.sizes, this.openMs);
  final List<(double, double)> sizes;
  final double openMs;
}

/// One rendered page: RGBA pixels and how long PDFium took.
class PageBitmap {
  PageBitmap(this.page, this.width, this.height, this.pixels, this.renderMs);
  final int page, width, height;
  final Uint8List pixels;
  final double renderMs;
}

/// Client side of the render worker.
class PdfWorker {
  PdfWorker._(this._send, this._replies);

  final SendPort _send;
  final Stream<dynamic> _replies;
  int _next = 0;

  static Future<PdfWorker> start() async {
    final port = ReceivePort();
    await Isolate.spawn(_main, port.sendPort, debugName: 'pdfium');
    final replies = port.asBroadcastStream();
    final send = await replies.first as SendPort;
    return PdfWorker._(send, replies);
  }

  Future<Map> _call(Map req) {
    final id = _next++;
    _send.send({...req, 'id': id});
    return _replies.firstWhere((r) => r is Map && r['id'] == id).then((r) => r as Map);
  }

  Future<PdfInfo> open(String path) async {
    final r = await _call({'op': 'open', 'path': path});
    if (r['error'] != null) throw StateError(r['error'] as String);
    final s = (r['sizes'] as List).cast<double>();
    return PdfInfo([for (var i = 0; i < s.length; i += 2) (s[i], s[i + 1])], r['ms'] as double);
  }

  Future<PageBitmap> render(int page, int width) async {
    final r = await _call({'op': 'render', 'page': page, 'width': width});
    final pixels = (r['pixels'] as TransferableTypedData).materialize().asUint8List();
    return PageBitmap(page, r['w'] as int, r['h'] as int, pixels, r['ms'] as double);
  }
}

void _main(SendPort out) {
  final p = _Pdfium(DynamicLibrary.open('libpdfium.so'));
  p.init();
  Pointer<Void> doc = nullptr;
  final inbox = ReceivePort();
  out.send(inbox.sendPort);
  inbox.listen((dynamic m) {
    final req = m as Map;
    final sw = Stopwatch()..start();
    if (req['op'] == 'open') {
      final path = (req['path'] as String).toNativeUtf8();
      doc = p.loadDocument(path, nullptr);
      malloc.free(path);
      if (doc == nullptr) {
        out.send({'id': req['id'], 'error': 'load failed, PDFium error ${p.lastError()}'});
        return;
      }
      final n = p.pageCount(doc);
      final w = calloc<Double>(), h = calloc<Double>();
      final sizes = <double>[];
      for (var i = 0; i < n; i++) {
        p.pageSize(doc, i, w, h);
        sizes..add(w.value)..add(h.value);
      }
      calloc..free(w)..free(h);
      out.send({'id': req['id'], 'sizes': sizes, 'ms': sw.elapsedMicroseconds / 1000});
    } else if (req['op'] == 'render') {
      final index = req['page'] as int, width = req['width'] as int;
      final page = p.loadPage(doc, index);
      final w = calloc<Double>(), h = calloc<Double>();
      p.pageSize(doc, index, w, h);
      final height = (width * h.value / w.value).round();
      calloc..free(w)..free(h);
      final buf = malloc<Uint8>(width * height * 4);
      final bmp = p.bitmapCreate(width, height, _bgra, buf, width * 4);
      p.fill(bmp, 0, 0, width, height, 0xFFFFFFFF);
      p.render(bmp, page, 0, 0, width, height, 0, _flags);
      final pixels = TransferableTypedData.fromList([buf.asTypedList(width * height * 4)]);
      p.bitmapDestroy(bmp);
      p.closePage(page);
      malloc.free(buf);
      out.send({
        'id': req['id'], 'w': width, 'h': height, 'pixels': pixels, 'ms': sw.elapsedMicroseconds / 1000,
      });
    }
  });
}
