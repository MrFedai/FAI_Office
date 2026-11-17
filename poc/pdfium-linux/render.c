// PoC-1: PDFium on Linux. Measures time to first visible page, per-page render time and peak RSS.
// Usage: render <file.pdf> [width_px=1080] [out.ppm]
// Throwaway code (MASTER_SPEC section 15).
#define _GNU_SOURCE
#include <fpdfview.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <time.h>

static double now_ms(void) {
  struct timespec t;
  clock_gettime(CLOCK_MONOTONIC, &t);
  return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

static long peak_rss_kb(void) {
  struct rusage r;
  getrusage(RUSAGE_SELF, &r);
  return r.ru_maxrss;
}

static long current_rss_kb(void) {
  FILE *f = fopen("/proc/self/status", "r");
  char line[256];
  long kb = -1;
  while (f && fgets(line, sizeof line, f))
    if (sscanf(line, "VmRSS: %ld", &kb) == 1) break;
  if (f) fclose(f);
  return kb;
}

// Renders one page to a BGRx bitmap `width` pixels wide. Returns the bitmap (caller destroys) or NULL.
static FPDF_BITMAP render_page(FPDF_DOCUMENT doc, int index, int width) {
  FPDF_PAGE page = FPDF_LoadPage(doc, index);
  if (!page) return NULL;
  double pw = FPDF_GetPageWidthF(page), ph = FPDF_GetPageHeightF(page);
  int height = (int)(width * ph / pw + 0.5);
  FPDF_BITMAP bmp = FPDFBitmap_Create(width, height, 0);
  FPDFBitmap_FillRect(bmp, 0, 0, width, height, 0xFFFFFFFF);
  FPDF_RenderPageBitmap(bmp, page, 0, 0, width, height, 0, FPDF_ANNOT | FPDF_LCD_TEXT);
  FPDF_ClosePage(page);
  return bmp;
}

static void write_ppm(FPDF_BITMAP bmp, const char *path) {
  int w = FPDFBitmap_GetWidth(bmp), h = FPDFBitmap_GetHeight(bmp), stride = FPDFBitmap_GetStride(bmp);
  const unsigned char *buf = FPDFBitmap_GetBuffer(bmp);
  FILE *f = fopen(path, "wb");
  fprintf(f, "P6\n%d %d\n255\n", w, h);
  for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++) {
      const unsigned char *p = buf + y * stride + x * 4;  // BGRx
      unsigned char rgb[3] = {p[2], p[1], p[0]};
      fwrite(rgb, 1, 3, f);
    }
  fclose(f);
}

int main(int argc, char **argv) {
  if (argc < 2) {
    fprintf(stderr, "usage: %s file.pdf [width_px=1080] [out.ppm]\n", argv[0]);
    return 2;
  }
  int width = argc > 2 ? atoi(argv[2]) : 1080;
  long rss0 = current_rss_kb();
  double t0 = now_ms();

  FPDF_LIBRARY_CONFIG cfg = {.version = 2};
  FPDF_InitLibraryWithConfig(&cfg);
  double t_init = now_ms();

  FPDF_DOCUMENT doc = FPDF_LoadDocument(argv[1], NULL);
  if (!doc) {
    fprintf(stderr, "load failed, error %lu\n", FPDF_GetLastError());
    return 1;
  }
  int pages = FPDF_GetPageCount(doc);
  double t_load = now_ms();

  FPDF_BITMAP first = render_page(doc, 0, width);
  double t_first = now_ms();
  if (argc > 3) write_ppm(first, argv[3]);
  FPDFBitmap_Destroy(first);
  long rss_first = current_rss_kb();

  double worst = 0;
  for (int i = 1; i < pages; i++) {
    double a = now_ms();
    FPDF_BITMAP b = render_page(doc, i, width);
    double d = now_ms() - a;
    if (d > worst) worst = d;
    if (b) FPDFBitmap_Destroy(b);
  }
  double t_all = now_ms();

  FPDF_CloseDocument(doc);
  FPDF_DestroyLibrary();

  printf("file              %s\n", argv[1]);
  printf("pages             %d\n", pages);
  printf("width_px          %d\n", width);
  printf("init_ms           %.1f\n", t_init - t0);
  printf("load_ms           %.1f\n", t_load - t_init);
  printf("first_page_ms     %.1f   (from process start: %.1f)\n", t_first - t_load, t_first - t0);
  printf("other_pages_avg   %.1f ms, worst %.1f ms\n", pages > 1 ? (t_all - t_first) / (pages - 1) : 0, worst);
  printf("rss_start_mb      %.1f\n", rss0 / 1024.0);
  printf("rss_first_page_mb %.1f\n", rss_first / 1024.0);
  printf("rss_peak_mb       %.1f\n", peak_rss_kb() / 1024.0);
  return 0;
}
