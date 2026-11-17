// PoC-4: LibreOfficeKit on Linux. Loads the engine once, then opens each document given and paints its first
// screen. The first document measures "engine not yet loaded", the following ones "engine already loaded".
// Usage: render <lo-program-dir> <outdir> <file>...
// Throwaway code (MASTER_SPEC section 15).
#define LOK_USE_UNSTABLE_API
#include <LibreOfficeKit/LibreOfficeKitInit.h>
#include <LibreOfficeKit/LibreOfficeKit.h>
#include <LibreOfficeKit/LibreOfficeKitEnums.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <time.h>
#include <unistd.h>

static double now_ms(void) {
  struct timespec t;
  clock_gettime(CLOCK_MONOTONIC, &t);
  return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

static double rss_mb(void) {
  FILE *f = fopen("/proc/self/status", "r");
  char line[256];
  long kb = -1;
  while (f && fgets(line, sizeof line, f))
    if (sscanf(line, "VmRSS: %ld", &kb) == 1) break;
  if (f) fclose(f);
  return kb / 1024.0;
}

static double peak_mb(void) {
  struct rusage r;
  getrusage(RUSAGE_SELF, &r);
  return r.ru_maxrss / 1024.0;
}

static void write_ppm(const unsigned char *buf, int w, int h, int bgra, const char *path) {
  FILE *f = fopen(path, "wb");
  if (!f) return;
  fprintf(f, "P6\n%d %d\n255\n", w, h);
  for (int i = 0; i < w * h; i++) {
    const unsigned char *p = buf + i * 4;
    unsigned char rgb[3] = {bgra ? p[2] : p[0], p[1], bgra ? p[0] : p[2]};
    fwrite(rgb, 1, 3, f);
  }
  fclose(f);
}

// Paints the first screen of the document: a 1080 px wide canvas. Writer and Calc get a phone-shaped
// 1080x1920 view at the document's top-left; Impress gets the whole first slide.
static void first_screen(LibreOfficeKitDocument *doc, const char *ppm, int *cw, int *ch) {
  int type = doc->pClass->getDocumentType(doc);
  long dw = 0, dh = 0;
  doc->pClass->getDocumentSize(doc, &dw, &dh);  // twips
  const int W = 1080;
  long tw, th;
  int H;
  if (type == LOK_DOCTYPE_PRESENTATION || type == LOK_DOCTYPE_DRAWING) {
    tw = dw, th = dh, H = (int)(W * dh / dw);
  } else if (type == LOK_DOCTYPE_SPREADSHEET) {
    tw = W * 15, th = 1920 * 15, H = 1920;  // 100 % zoom: 15 twips per px at 96 dpi
  } else {
    tw = dw, th = dw * 1920 / W, H = 1920;  // full page width, phone-height window from the top
  }
  unsigned char *buf = (unsigned char *)malloc((size_t)W * H * 4);
  memset(buf, 0xff, (size_t)W * H * 4);
  doc->pClass->paintPartTile(doc, buf, 0, 0, W, H, 0, 0, (int)tw, (int)th);
  int bgra = doc->pClass->getTileMode(doc) == LOK_TILEMODE_BGRA;
  if (ppm) write_ppm(buf, W, H, bgra, ppm);
  free(buf);
  *cw = W, *ch = H;
}

int main(int argc, char **argv) {
  if (argc < 4) {
    fprintf(stderr, "usage: %s <lo-program-dir> <outdir> <file>...\n", argv[0]);
    return 2;
  }
  setvbuf(stdout, NULL, _IOLBF, 0);
  char profile[PATH_MAX + 16];
  snprintf(profile, sizeof profile, "file://%s/profile", realpath(argv[2], NULL));

  double t0 = now_ms(), rss0 = rss_mb();
  LibreOfficeKit *lo = lok_init_2(argv[1], profile);
  if (!lo) {
    fprintf(stderr, "lok_init failed\n");
    return 1;
  }
  double t_init = now_ms();
  printf("engine_init_ms     %.0f   rss %.0f MB -> %.0f MB\n", t_init - t0, rss0, rss_mb());

  for (int i = 3; i < argc; i++) {
    char url[PATH_MAX + 8], ppm[PATH_MAX];
    char *abs = realpath(argv[i], NULL);
    snprintf(url, sizeof url, "file://%s", abs);
    const char *base = strrchr(abs, '/') + 1;
    snprintf(ppm, sizeof ppm, "%s/%s.ppm", argv[2], base);

    double a = now_ms();
    LibreOfficeKitDocument *doc = lo->pClass->documentLoadWithOptions(lo, url, "Language=en-US");
    if (!doc) {
      printf("%s: load failed: %s\n", base, lo->pClass->getError(lo));
      continue;
    }
    double b = now_ms();
    doc->pClass->initializeForRendering(doc, "{\".uno:SpellOnline\":{\"type\":\"boolean\",\"value\":false}}");  // viewer: no spell marks
    int parts = doc->pClass->getParts(doc);
    int w, h;
    first_screen(doc, ppm, &w, &h);
    double c = now_ms();
    printf("%-20s load %5.0f ms  first_screen %4.0f ms  total %5.0f ms%s  parts %3d  rss %4.0f MB\n", base, b - a,
           c - b, c - a, i == 3 ? " (+engine init)" : "", parts, rss_mb());
    doc->pClass->destroy(doc);
    printf("%-20s after close rss %4.0f MB\n", "", rss_mb());
    free(abs);
  }
  printf("peak_rss_mb        %.0f\n", peak_mb());
  printf("from_start_ms      %.0f\n", now_ms() - t0);
  // lok destroy() blocks in this build (observed hang); a viewer process exits instead of tearing down.
  fflush(stdout);
  _exit(0);
}
