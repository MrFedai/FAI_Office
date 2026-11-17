// Linux: process start time and RSS from /proc.
import 'dart:io';

double msSinceProcessStart() {
  final stat = File('/proc/self/stat').readAsStringSync();
  final fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ');
  final startTicks = int.parse(fields[19]);
  final uptime = double.parse(File('/proc/uptime').readAsStringSync().split(' ').first);
  return uptime * 1000 - startTicks * 10;
}

double rssMb() {
  final m = RegExp(r'VmRSS:\s+(\d+)').firstMatch(File('/proc/self/status').readAsStringSync());
  return int.parse(m!.group(1)!) / 1024;
}

bool exitRequested() => Platform.environment.containsKey('POC_EXIT');
void exitNow() => exit(0);
