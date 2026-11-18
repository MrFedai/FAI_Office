# FAI_Office

Ücretsiz, açık kaynak (MPL-2.0), çevrimdışı öncelikli ofis paketi: belge, tablo, sunum, PDF, görüntüleme, dönüştürme,
arşiv; hepsi tek uygulamada. Platform sırası: Android, Linux, Web, Windows, iOS, macOS.
Ortak kurallar (yanıt tarzı, çalışma, kesin kurallar, model / işçi, graphify): `~/.claude/CLAUDE.md` (`~/PROJECTS/claude-ortak`).
Burada yalnız bu projenin farkı. Kabuk: Compose Multiplatform (Faz 2, `PHASE2_PLAN.md`); `DESIGN.md` P2.4'te yazılır.

**Tek uygulama:** Writer, Sheets, Slides, PDF ayrı uygulama olmaz; ağır Office motoru ayrı modül, gerektiğinde yüklenir.
**Lisans:** AGPL, yalnız-GPL ve özgür olmayan bağımlılık eklenmez (`MASTER_SPEC.md` bölüm 11).
**Bulut yok:** hesap, reklam, abonelik, ücretli servis, sunucuda belge işleme yok.
**Faz 1 onaylandı (18.11.2026);** ürün kodu `app/`, `core/`, `androidApp/`; `poc/` yalnız referans, ürün yapısına girmez.
Plan ve kararlar: `MASTER_SPEC.md` (bölüm 0 = kilitli kararlar). Durum notu: `~/fai_notes/FAI_Office_DURUM.md`
(oturum başında oku, sonunda güncelle).

## Çalışma
- Geliştirme dalı `dev`; `main`'e commit ve `v*` etiketi yok (yayın kullanıcıda).
- Desteklenen biçim / özellik iddiası yalnız testi geçince uyumluluk tablosuna yazılır.

- Her kaynak dosyası MPL-2.0 başlığıyla başlar (`core/src/commonMain/.../AppInfo.kt`'deki iki satır).
- Sürümler yalnız `gradle/libs.versions.toml`'da; Android `minSdk 29`, yalnız `arm64-v8a`.

## Araçlar
- Depo temizliği: `clear.sh` (listeler, y/N sorar).
- CI: `ci/all.sh` (lint, test, licenses, android, desktop, web; ilerleme basar, özet verir); tek adım `ci/all.sh web`
  ya da `ci/web.sh`. Loglar `build/ci/`. GitHub Actions aynı betiği koşar (`.github/workflows/ci.yml`, `dev` push).
  Biçim düzeltme: `./gradlew ktlintFormat`.
- Çalıştır: masaüstü `./gradlew :app:run`, Web `./gradlew :app:wasmJsBrowserDevelopmentRun` (COOP/COEP başlıklı).
