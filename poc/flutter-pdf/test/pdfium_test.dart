// Host check of the PDFium FFI binding and worker protocol, with the Linux build of the same PDFium release.
// Run: LD_LIBRARY_PATH=~/dev/cache/fai-office/pdfium-8111/linux-x64/lib flutter test
import 'package:flutter_test/flutter_test.dart';
import 'package:pdf_poc/pdfium.dart';

void main() {
  test('opens the 100-page corpus PDF and renders page 1 and 101', () async {
    final w = await PdfWorker.start();
    final info = await w.open('../corpus/out/long-100p.pdf');
    expect(info.sizes.length, 101);
    expect(info.sizes.first.$1, closeTo(595.3, 0.5)); // A4 width in points
    for (final page in [0, 100]) {
      final b = await w.render(page, 1080);
      expect(b.width, 1080);
      expect(b.height, closeTo(1080 * 841.9 / 595.3, 1));
      expect(b.pixels.length, b.width * b.height * 4);
      // Not blank: some pixels are darker than white (text).
      var dark = 0;
      for (var i = 0; i < b.pixels.length; i += 4) {
        if (b.pixels[i] < 128) dark++;
      }
      expect(dark, greaterThan(1000), reason: 'page $page looks blank');
      expect(b.pixels[3], 255, reason: 'alpha must be opaque (RGBA order)');
    }
  });
}
