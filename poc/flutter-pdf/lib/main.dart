// PoC WP5: Flutter shell showing PDFium-rendered pages on Android, with measurements.
// Throwaway (MASTER_SPEC section 15): a measuring harness, not product UI.
//
// Auto mode (used by run.sh): if <files>/auto exists, the app opens <files>/<pdf>, waits, scrolls through the
// whole document at a fixed speed, then writes <files>/results.txt and prints "POC_DONE".
import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter/scheduler.dart';

import 'pdfium.dart';

const pkg = 'org.faioffice.poc.pdf_poc';
const filesDir = '/storage/emulated/0/Android/data/$pkg/files';

final results = <String, String>{};
void report(String k, Object v) {
  results[k] = '$v';
  debugPrint('POC_RESULT $k $v');
}

double rssMb() {
  final m = RegExp(r'VmRSS:\s+(\d+)').firstMatch(File('/proc/self/status').readAsStringSync());
  return int.parse(m!.group(1)!) / 1024;
}

String pct(List<double> xs, double p) {
  if (xs.isEmpty) return '-';
  final s = [...xs]..sort();
  return s[((s.length - 1) * p).round()].toStringAsFixed(1);
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const PocApp()); // first-frame time is logged by MainActivity (apps cannot read /proc/uptime)
}

class PocApp extends StatelessWidget {
  const PocApp({super.key});

  @override
  Widget build(BuildContext context) =>
      MaterialApp(title: 'PDFium PoC', theme: ThemeData(useMaterial3: true), home: const Home());
}

class Home extends StatefulWidget {
  const Home({super.key});

  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  String pdf = 'long-100p.pdf';
  String status = '';

  @override
  void initState() {
    super.initState();
    final auto = File('$filesDir/auto');
    if (auto.existsSync()) {
      final name = auto.readAsStringSync().trim();
      if (name.isNotEmpty) pdf = name;
      Future.delayed(const Duration(milliseconds: 1500), () {
        report('rss_home_mb', rssMb().toStringAsFixed(1));
        _open(auto: true);
      });
    }
  }

  void _open({bool auto = false}) {
    final path = '$filesDir/$pdf';
    if (!File(path).existsSync()) {
      setState(() => status = 'Missing $path (push it with run.sh)');
      return;
    }
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => Viewer(path: path, auto: auto)));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('PDFium PoC')),
        body: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            FilledButton(onPressed: _open, child: Text('Open $pdf')),
            const SizedBox(height: 16),
            Text(status),
          ]),
        ),
      );
}

class Viewer extends StatefulWidget {
  const Viewer({super.key, required this.path, required this.auto});
  final String path;
  final bool auto;

  @override
  State<Viewer> createState() => _ViewerState();
}

class _ViewerState extends State<Viewer> {
  static const gap = 8.0;
  static const cacheLimit = 12;

  final clock = Stopwatch()..start();
  final scroll = ScrollController();
  PdfWorker? worker;
  PdfInfo? info;
  final images = <int, ui.Image>{}; // insertion order = LRU order
  final pending = <int>{};
  List<double> offsets = [];
  double width = 0;
  int pixelWidth = 0;

  final renderMs = <double>[], decodeMs = <double>[];
  final frames = <FrameTiming>[];
  bool recording = false;
  int blankSamples = 0, samples = 0;
  double rssPeak = 0;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    worker = await PdfWorker.start();
    final w = worker!;
    report('worker_start_ms', clock.elapsedMilliseconds);
    info = await w.open(widget.path);
    report('pages', info!.sizes.length);
    report('document_open_ms', info!.openMs.toStringAsFixed(1));
    if (mounted) setState(() {});
  }

  void _layout(double w) {
    if (w == width || info == null) return;
    width = w;
    var y = 0.0;
    offsets = [for (final (pw, ph) in info!.sizes) (y += w * ph / pw + gap) - (w * ph / pw + gap)];
  }

  double _extent(int i) => width * info!.sizes[i].$2 / info!.sizes[i].$1 + gap;

  void _request(int i) {
    if (images.containsKey(i) || pending.contains(i)) return;
    pending.add(i);
    worker!.render(i, pixelWidth).then((b) {
      renderMs.add(b.renderMs);
      final sw = Stopwatch()..start();
      ui.decodeImageFromPixels(b.pixels, b.width, b.height, ui.PixelFormat.rgba8888, (img) {
        decodeMs.add(sw.elapsedMicroseconds / 1000);
        pending.remove(i);
        if (!mounted) return img.dispose();
        images[i] = img;
        while (images.length > cacheLimit) {
          final far = images.keys.reduce((a, b) => (a - _centerPage()).abs() > (b - _centerPage()).abs() ? a : b);
          images.remove(far)!.dispose();
        }
        setState(() {});
        if (i == 0 && !results.containsKey('first_page_visible_ms')) {
          SchedulerBinding.instance.addPostFrameCallback((_) => _firstPageShown());
        }
      });
    });
  }

  int _centerPage() {
    if (!scroll.hasClients || offsets.isEmpty) return 0;
    final y = scroll.offset + scroll.position.viewportDimension / 2;
    var i = 0;
    while (i + 1 < offsets.length && offsets[i + 1] <= y) {
      i++;
    }
    return i;
  }

  void _firstPageShown() {
    report('first_page_visible_ms', clock.elapsedMilliseconds);
    report('first_page_render_ms', renderMs.first.toStringAsFixed(1));
    report('rss_first_page_mb', rssMb().toStringAsFixed(1));
    if (widget.auto) Future.delayed(const Duration(seconds: 1), _autoScroll);
  }

  Future<void> _autoScroll() async {
    final view = View.of(context);
    final hz = view.display.refreshRate;
    final pages = info!.sizes.length;
    const msPerPage = 300; // ~3.3 pages per second, a fast reading scroll
    SchedulerBinding.instance.addTimingsCallback(_onTimings);
    recording = true;
    final sampler = Timer.periodic(const Duration(milliseconds: 50), (_) => _sample());
    await scroll.animateTo(scroll.position.maxScrollExtent,
        duration: Duration(milliseconds: pages * msPerPage), curve: Curves.linear);
    recording = false;
    sampler.cancel();
    SchedulerBinding.instance.removeTimingsCallback(_onTimings);
    await Future.delayed(const Duration(seconds: 2));

    final budget = 1000 / hz;
    final total = [for (final f in frames) f.totalSpan.inMicroseconds / 1000];
    final build = [for (final f in frames) f.buildDuration.inMicroseconds / 1000];
    final raster = [for (final f in frames) f.rasterDuration.inMicroseconds / 1000];
    report('display_hz', hz.toStringAsFixed(0));
    report('scroll_ms_per_page', msPerPage);
    report('scroll_frames', frames.length);
    report('scroll_janky_frames', '${total.where((t) => t > budget).length} (budget ${budget.toStringAsFixed(1)} ms)');
    report('scroll_frame_total_p50_p90_p99_ms', '${pct(total, .5)} / ${pct(total, .9)} / ${pct(total, .99)}');
    report('scroll_build_p90_ms', pct(build, .9));
    report('scroll_raster_p90_ms', pct(raster, .9));
    report('scroll_blank_visible_page_pct', (100 * blankSamples / samples).toStringAsFixed(1));
    report('render_ms_avg_p90_max',
        '${(renderMs.reduce((a, b) => a + b) / renderMs.length).toStringAsFixed(1)} / ${pct(renderMs, .9)} / ${pct(renderMs, 1)}');
    report('decode_ms_p90', pct(decodeMs, .9));
    report('render_width_px', pixelWidth);
    report('rss_scroll_peak_mb', rssPeak.toStringAsFixed(1));
    report('rss_after_scroll_mb', rssMb().toStringAsFixed(1));
    File('$filesDir/results.txt').writeAsStringSync(results.entries.map((e) => '${e.key} ${e.value}').join('\n'));
    debugPrint('POC_DONE');
  }

  void _onTimings(List<FrameTiming> t) {
    if (recording) frames.addAll(t);
  }

  void _sample() {
    final r = rssMb();
    if (r > rssPeak) rssPeak = r;
    final top = scroll.offset, bottom = top + scroll.position.viewportDimension;
    for (var i = 0; i < offsets.length; i++) {
      if (offsets[i] > bottom) break;
      if (offsets[i] + _extent(i) < top) continue;
      samples++;
      if (!images.containsKey(i)) blankSamples++;
    }
  }

  @override
  void dispose() {
    for (final i in images.values) {
      i.dispose();
    }
    scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final i = info;
    return Scaffold(
      appBar: AppBar(title: Text(widget.path.split('/').last)),
      backgroundColor: const Color(0xFFE0E0E0),
      body: i == null
          ? const Center(child: CircularProgressIndicator())
          : LayoutBuilder(builder: (context, c) {
              _layout(c.maxWidth);
              pixelWidth = (c.maxWidth * MediaQuery.devicePixelRatioOf(context)).round();
              return ListView.builder(
                controller: scroll,
                itemCount: i.sizes.length,
                itemExtentBuilder: (index, _) => _extent(index),
                scrollCacheExtent: const ScrollCacheExtent.viewport(1), // prefetch one screen ahead and behind
                itemBuilder: (context, index) {
                  _request(index);
                  final img = images[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: gap),
                    child: ColoredBox(
                      color: Colors.white,
                      child: img == null ? null : RawImage(image: img, fit: BoxFit.fill),
                    ),
                  );
                },
              );
            }),
    );
  }
}
