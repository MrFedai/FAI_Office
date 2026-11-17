// PoC WP8: minimal JNI bridge from Kotlin to PDFium. One document at a time; callers serialize access.
#include <android/bitmap.h>
#include <fpdfview.h>
#include <jni.h>
#include <stdlib.h>

static FPDF_DOCUMENT doc;

JNIEXPORT jint JNICALL Java_org_faioffice_poc_compose_Pdfium_open(JNIEnv *env, jclass cls, jstring path) {
  static int init;
  if (!init) FPDF_InitLibrary(), init = 1;
  if (doc) FPDF_CloseDocument(doc);
  const char *p = (*env)->GetStringUTFChars(env, path, NULL);
  doc = FPDF_LoadDocument(p, NULL);
  (*env)->ReleaseStringUTFChars(env, path, p);
  return doc ? FPDF_GetPageCount(doc) : -(jint)FPDF_GetLastError();
}

// Page sizes in points: [w0, h0, w1, h1, ...]
JNIEXPORT jdoubleArray JNICALL Java_org_faioffice_poc_compose_Pdfium_pageSizes(JNIEnv *env, jclass cls) {
  int n = FPDF_GetPageCount(doc);
  jdoubleArray out = (*env)->NewDoubleArray(env, n * 2);
  double *buf = malloc(sizeof(double) * n * 2);
  for (int i = 0; i < n; i++) FPDF_GetPageSizeByIndex(doc, i, &buf[2 * i], &buf[2 * i + 1]);
  (*env)->SetDoubleArrayRegion(env, out, 0, n * 2, buf);
  free(buf);
  return out;
}

// Renders a page into an ARGB_8888 Bitmap (RGBA bytes in memory) of the size the caller chose.
JNIEXPORT jboolean JNICALL Java_org_faioffice_poc_compose_Pdfium_render(JNIEnv *env, jclass cls, jint index,
                                                                         jobject bitmap) {
  AndroidBitmapInfo info;
  void *pixels;
  if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0)
    return JNI_FALSE;
  FPDF_PAGE page = FPDF_LoadPage(doc, index);
  FPDF_BITMAP bmp = FPDFBitmap_CreateEx(info.width, info.height, FPDFBitmap_BGRA, pixels, info.stride);
  FPDFBitmap_FillRect(bmp, 0, 0, info.width, info.height, 0xFFFFFFFF);
  FPDF_RenderPageBitmap(bmp, page, 0, 0, info.width, info.height, 0, FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER);
  FPDFBitmap_Destroy(bmp);
  FPDF_ClosePage(page);
  AndroidBitmap_unlockPixels(env, bitmap);
  return JNI_TRUE;
}
