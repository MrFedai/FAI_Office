# FAI_Office

Ücretsiz, açık kaynak (MPL-2.0), çevrimdışı öncelikli ofis paketi: belge, tablo, sunum, PDF, görüntüleme, dönüştürme,
arşiv; hepsi tek uygulamada. Platform sırası: Android, Linux, Web, Windows, iOS, macOS.
Ortak kurallar (yanıt tarzı, çalışma, kesin kurallar, model / işçi, graphify): `~/.claude/CLAUDE.md` (`~/PROJECTS/claude-ortak`).
Burada yalnız bu projenin farkı. Arayüz kodu henüz yok; `DESIGN.md` kabuk çatısı seçilince (Faz 2) yazılır.

**Tek uygulama:** Writer, Sheets, Slides, PDF ayrı uygulama olmaz; ağır Office motoru ayrı modül, gerektiğinde yüklenir.
**Lisans:** AGPL, yalnız-GPL ve özgür olmayan bağımlılık eklenmez (`MASTER_SPEC.md` bölüm 11).
**Bulut yok:** hesap, reklam, abonelik, ücretli servis, sunucuda belge işleme yok.
**Faz 1 onaylanmadan ürün kodu yazılmaz;** deneme kodu yalnız `poc/` altında.
Plan ve kararlar: `MASTER_SPEC.md` (bölüm 0 = kilitli kararlar). Durum notu: `~/fai_notes/FAI_Office_DURUM.md`
(oturum başında oku, sonunda güncelle).

## Çalışma
- Geliştirme dalı `dev`; `main`'e commit ve `v*` etiketi yok (yayın kullanıcıda).
- Desteklenen biçim / özellik iddiası yalnız testi geçince uyumluluk tablosuna yazılır.

## Araçlar
- Depo temizliği: `clear.sh` (listeler, y/N sorar).
