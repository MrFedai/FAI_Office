// Shell size PoC: the same minimal "home screen" in every shell candidate (app bar, button, 100-row list), to
// compare Linux and Web build size, start time and memory. Throwaway (MASTER_SPEC section 15).
// On Linux it prints "first_frame_ms_from_process_start" and "rss_mb", and exits after 3 s if POC_EXIT is set.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'proc_stub.dart' if (dart.library.io) 'proc_io.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const HomeApp());
  if (!kIsWeb) {
    WidgetsBinding.instance.waitUntilFirstFrameRasterized.then((_) {
      debugPrint('first_frame_ms_from_process_start ${msSinceProcessStart().toStringAsFixed(0)}');
      Timer(const Duration(seconds: 2), () {
        debugPrint('rss_mb ${rssMb().toStringAsFixed(1)}');
        if (exitRequested()) exitNow();
      });
    });
  }
}

class HomeApp extends StatelessWidget {
  const HomeApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'FAI Office PoC',
        theme: ThemeData(useMaterial3: true),
        home: Scaffold(
          appBar: AppBar(title: const Text('FAI Office PoC')),
          body: Column(children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: FilledButton(onPressed: () {}, child: const Text('Open document')),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: 100,
                itemBuilder: (_, i) => ListTile(title: Text('Recent file ${i + 1}.docx'), subtitle: const Text('Today')),
              ),
            ),
          ]),
        ),
      );
}
