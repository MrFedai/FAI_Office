/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was
 * not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
@file:OptIn(ExperimentalLayoutApi::class)

package org.faioffice.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.faioffice.app.theme.FaiAlpha
import org.faioffice.app.theme.FaiBreakpoint
import org.faioffice.app.theme.FaiColors
import org.faioffice.app.theme.FaiRadius
import org.faioffice.app.theme.FaiSize
import org.faioffice.app.theme.FaiSpace
import org.faioffice.app.theme.FaiTheme

/**
 * Token preview: palette, file-type colours, type scale, controls and surfaces with real-length TR / EN text.
 * Phone layout below [FaiBreakpoint.compact] (bottom bar), desktop layout above (top bar and side navigation).
 * Development screen for screenshots only; P2.7 extends it into the screen package.
 */
@Composable
fun TokenPreview(themeLabel: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(FaiTheme.colors.bg)) {
        if (maxWidth >= FaiBreakpoint.compact) DesktopPreview(themeLabel) else PhonePreview(themeLabel)
    }
}

@Composable
private fun DesktopPreview(themeLabel: String) {
    val c = FaiTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("FAI Office", themeLabel, FaiSize.desktopTopBar)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SideNav()
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = FaiSpace.pagePadX, vertical = FaiSpace.pagePadY),
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.gap),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Card("Renkler · Colours", Modifier.weight(1f)) { Palette(withHex = true) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(FaiSpace.gap)) {
                    Card("Dosya türleri · File types") { FileTypes(columns = 2) }
                    Card("Yazı ölçeği · Type scale", Modifier.weight(1f)) { TypeScale() }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(FaiSpace.gap)) {
                    Card("Belge görünümü · Document view") { Controls(); Surfaces() }
                    Card("Son dosyalar · Recent files", Modifier.weight(1f)) { RecentFiles(recent) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(FaiSize.border).background(c.border))
    }
}

@Composable
private fun PhonePreview(themeLabel: String) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Ana sayfa", themeLabel, FaiSize.desktopTopBar)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(FaiSpace.mobilePad),
            verticalArrangement = Arrangement.spacedBy(FaiSpace.mobileGap),
        ) {
            Card("Belge görünümü · Document view", compact = true) { Controls() }
            Card("Dosya türleri · File types", compact = true) { FileTypes(columns = 2) }
            Card("Son dosyalar · Recent files", compact = true) { RecentFiles(recent.take(2)) }
            Card("Yazı ölçeği · Type scale", compact = true) { TypeScale(short = true) }
            Card("Renkler · Colours", Modifier.weight(1f), compact = true) { Palette(withHex = false) }
        }
        BottomBar()
    }
}

@Composable
private fun TopBar(
    title: String,
    themeLabel: String,
    height: Dp,
) {
    val c = FaiTheme.colors
    Row(
        Modifier.fillMaxWidth().height(height).background(c.surface).padding(horizontal = FaiSpace.pagePadX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s10),
    ) {
        Box(Modifier.size(FaiSpace.s12).clip(RoundedCornerShape(FaiRadius.chip)).background(c.brand))
        BasicText(title, style = FaiTheme.type.pageTitle.copy(color = c.text))
        Spacer(Modifier.weight(1f))
        Pill(themeLabel, c.primaryInk, c.primarySoft)
    }
    Box(Modifier.fillMaxWidth().height(FaiSize.border).background(c.border))
}

@Composable
private fun SideNav() {
    val c = FaiTheme.colors
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(FaiSize.sideNav).fillMaxHeight().background(c.surface).padding(FaiSpace.s8),
            verticalArrangement = Arrangement.spacedBy(FaiSpace.s2),
        ) {
            destinations.forEachIndexed { i, name ->
                NavItem(name, selected = i == 0, Modifier.fillMaxWidth().height(FaiSize.control))
            }
        }
        Box(Modifier.width(FaiSize.border).fillMaxHeight().background(c.border))
    }
}

@Composable
private fun BottomBar() {
    val c = FaiTheme.colors
    Box(Modifier.fillMaxWidth().height(FaiSize.border).background(c.border))
    Row(
        Modifier.fillMaxWidth().height(FaiSize.mobileTabBar).background(c.surface).padding(FaiSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s2),
    ) {
        phoneTabs.forEachIndexed { i, name ->
            val selected = i == 0
            val ink = if (selected) c.primaryInk else c.muted
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(FaiRadius.control))
                    .background(if (selected) c.primarySoft else c.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FaiSpace.s4, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(FaiSize.icon).clip(RoundedCornerShape(FaiRadius.control)).background(ink))
                BasicText(name, style = FaiTheme.type.label.copy(color = ink), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavItem(
    name: String,
    selected: Boolean,
    modifier: Modifier,
) {
    val c = FaiTheme.colors
    val ink = if (selected) c.primaryInk else c.text2
    Row(
        modifier.clip(RoundedCornerShape(FaiRadius.control))
            .background(if (selected) c.primarySoft else c.surface)
            .padding(horizontal = FaiSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s10),
    ) {
        Box(Modifier.size(FaiSize.iconSmall).clip(RoundedCornerShape(FaiSpace.s4)).background(ink))
        BasicText(name, style = FaiTheme.type.body.copy(color = ink), maxLines = 1)
    }
}

@Composable
private fun Card(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FaiTheme.colors
    val shape = RoundedCornerShape(FaiRadius.card)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(FaiSize.border, c.border, shape)
            .padding(if (compact) FaiSpace.mobilePad else FaiSpace.cardPad),
        verticalArrangement = Arrangement.spacedBy(if (compact) FaiSpace.s8 else FaiSpace.s12),
    ) {
        BasicText(title, style = FaiTheme.type.cardTitle.copy(color = c.text))
        content()
    }
}

@Composable
private fun Pill(
    text: String,
    ink: Color,
    fill: Color,
) {
    BasicText(
        text,
        Modifier.clip(RoundedCornerShape(FaiRadius.chip)).background(fill)
            .padding(horizontal = FaiSpace.s10, vertical = FaiSpace.s4),
        style = FaiTheme.type.label.copy(color = ink),
    )
}

@Composable
private fun Palette(withHex: Boolean) {
    val c = FaiTheme.colors
    val all = paletteOf(c)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s6),
        maxItemsInEachRow = if (withHex) 2 else 3,
    ) {
        all.forEach { (name, color) ->
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s6),
            ) {
                val swatch = if (withHex) FaiSize.icon else FaiSize.iconSmall
                Box(
                    Modifier.size(swatch).clip(RoundedCornerShape(FaiSpace.s4)).background(color)
                        .border(FaiSize.border, c.border2, RoundedCornerShape(FaiSpace.s4)),
                )
                BasicText(name, Modifier.weight(1f), style = FaiTheme.type.label.copy(color = c.text2), maxLines = 1)
                if (withHex) {
                    BasicText(hex(color), style = FaiTheme.type.numberSmall.copy(color = c.muted))
                }
            }
        }
    }
}

@Composable
private fun FileTypes(columns: Int) {
    val c = FaiTheme.colors
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        maxItemsInEachRow = columns,
    ) {
        fileKinds.forEach { kind ->
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
            ) {
                FileBadge(kind)
                Column {
                    BasicText(kind.tr, style = FaiTheme.type.body.copy(color = c.text), maxLines = 1)
                    BasicText(kind.en, style = FaiTheme.type.small.copy(color = c.muted), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FileBadge(kind: FileKind) {
    val ink = FaiTheme.colors.fileTypes.getValue(kind.key)
    Box(
        Modifier.size(FaiSize.fileBadge).clip(RoundedCornerShape(FaiRadius.control))
            .background(ink.copy(alpha = FaiAlpha.FILE_TINT)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(kind.ext, style = FaiTheme.type.label.copy(color = ink), maxLines = 1)
    }
}

@Composable
private fun TypeScale(short: Boolean = false) {
    val c = FaiTheme.colors
    val styles = FaiTheme.type.all.entries.toList()
    Column(verticalArrangement = Arrangement.spacedBy(if (short) FaiSpace.s2 else FaiSpace.s6)) {
        styles.forEach { (name, style) ->
            val mono = name == "hero" || name.startsWith("number")
            val sample =
                when {
                    name == "hero" -> "1.248 / 3.096"
                    mono -> "12:45 · 3,8 MB · %87,5 · 1,234.56"
                    short -> "Çevrimdışı ofis · Offline office"
                    else -> "Çevrimdışı ofis paketi, tek uygulama · Offline-first office suite, one app"
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!short) {
                    BasicText(
                        name,
                        Modifier.width(FaiSize.sideNav / 2),
                        style = FaiTheme.type.label.copy(color = c.muted),
                    )
                }
                BasicText(
                    if (short) "$name · $sample" else sample,
                    Modifier.weight(1f),
                    style = style.copy(color = c.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Controls() {
    val c = FaiTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
        verticalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        Button("Düzenle · Edit", c.onPrimary, c.primary, null)
        Button("Paylaş · Share", c.text, c.surface, c.border2)
        Button("Kaydet · Save", c.text, c.surface2, c.border, Modifier.alpha(FaiAlpha.DISABLED))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8)) {
        Pill("Görüntüleme kipi · View mode", c.primaryInk, c.primarySoft)
        Pill("Dönüştür · Convert", c.violet, c.violetSoft)
    }
    Status("✓ Kaydedildi · Saved", c.pos)
    Status("✕ Kaydedilemedi: disk dolu · Save failed: disk full", c.neg)
    Status("! Salt okunur, düzenlemek için kopyala · Read-only, copy to edit", c.warn)
}

@Composable
private fun Button(
    text: String,
    ink: Color,
    fill: Color,
    border: Color?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FaiRadius.control)
    val outlined = if (border != null) Modifier.border(FaiSize.border, border, shape) else Modifier
    Box(
        modifier.heightIn(min = FaiSize.touchMin).clip(shape).background(fill).then(outlined)
            .padding(horizontal = FaiSpace.s14),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = FaiTheme.type.button.copy(color = ink), maxLines = 1)
    }
}

@Composable
private fun Status(
    text: String,
    ink: Color,
) {
    BasicText(text, style = FaiTheme.type.small.copy(color = ink), maxLines = 2)
}

@Composable
private fun Surfaces() {
    val c = FaiTheme.colors
    val shape = RoundedCornerShape(FaiRadius.control)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.bg).padding(FaiSpace.s8),
        horizontalArrangement = Arrangement.spacedBy(FaiSpace.s8),
    ) {
        listOf("bg" to c.bg, "surface" to c.surface, "surface2" to c.surface2, "input" to c.input).forEach { (n, s) ->
            Column(
                Modifier.weight(1f).clip(shape).background(s).border(FaiSize.border, c.border, shape)
                    .padding(FaiSpace.s8),
            ) {
                BasicText(n, style = FaiTheme.type.label.copy(color = c.muted))
                BasicText("Metin · Text", style = FaiTheme.type.small.copy(color = c.text), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecentFiles(files: List<RecentFile>) {
    val c = FaiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(FaiSpace.s4)) {
        files.forEachIndexed { i, file ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(FaiSize.border).background(c.border))
            Row(
                Modifier.fillMaxWidth().heightIn(min = FaiSize.touchMin),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FaiSpace.s10),
            ) {
                FileBadge(fileKinds.first { it.key == file.kind })
                Column(Modifier.weight(1f)) {
                    BasicText(file.name, style = FaiTheme.type.body.copy(color = c.text), maxLines = 1, overflow = ell)
                    BasicText(file.where, style = FaiTheme.type.small.copy(color = c.muted), maxLines = 1, overflow = ell)
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(file.size, style = FaiTheme.type.number.copy(color = c.text2))
                    BasicText(file.date, style = FaiTheme.type.numberSmall.copy(color = c.muted))
                }
            }
        }
    }
}

private val ell = TextOverflow.Ellipsis

private fun hex(color: Color): String {
    val argb = color.toArgb()
    return if (argb ushr 24 == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
}

private fun paletteOf(c: FaiColors): List<Pair<String, Color>> =
    listOf(
        "bg" to c.bg,
        "surface" to c.surface,
        "surface2" to c.surface2,
        "input" to c.input,
        "border" to c.border,
        "border2" to c.border2,
        "text" to c.text,
        "text2" to c.text2,
        "muted" to c.muted,
        "primary" to c.primary,
        "primaryInk" to c.primaryInk,
        "primarySoft" to c.primarySoft,
        "onPrimary" to c.onPrimary,
        "pos" to c.pos,
        "neg" to c.neg,
        "warn" to c.warn,
        "violet" to c.violet,
        "violetSoft" to c.violetSoft,
        "brand" to c.brand,
    ) + c.fileTypes.map { (k, v) -> "file·$k" to v }

private class FileKind(
    val key: String,
    val ext: String,
    val tr: String,
    val en: String,
)

private class RecentFile(
    val kind: String,
    val name: String,
    val where: String,
    val size: String,
    val date: String,
)

private val fileKinds =
    listOf(
        FileKind("document", "DOCX", "Belgeler", "Documents"),
        FileKind("spreadsheet", "XLSX", "Tablolar", "Spreadsheets"),
        FileKind("presentation", "PPTX", "Sunumlar", "Presentations"),
        FileKind("pdf", "PDF", "PDF", "PDF"),
        FileKind("archive", "ZIP", "Arşiv", "Archive"),
        FileKind("converter", "↔", "Dosya dönüştürücü", "File Converter"),
        FileKind("image", "PNG", "Görseller", "Images"),
    )

private val destinations =
    listOf(
        "Ana sayfa",
        "Son dosyalar",
        "Belgeler",
        "Sunumlar",
        "Tablolar",
        "PDF",
        "Dosya dönüştürücü",
        "Arşiv",
        "Ayarlar",
    )

private val phoneTabs = listOf("Ana sayfa", "Son", "Belgeler", "PDF", "Dönüştür")

private val recent =
    listOf(
        RecentFile(
            "spreadsheet",
            "2026 Yılı Bütçe Taslağı — Şirket İçi Gözden Geçirme (son sürüm).xlsx",
            "Belgeler › Muhasebe › 2026",
            "1,2 MB",
            "18.11.2026 14:05",
        ),
        RecentFile(
            "presentation",
            "Quarterly Board Presentation – Q3 Results and Outlook FINAL v7.pptx",
            "Downloads",
            "18,4 MB",
            "17.11.2026 09:12",
        ),
        RecentFile(
            "pdf",
            "Kira Sözleşmesi_İstanbul_Şişli_imzalı_taranmış.pdf",
            "İndirilenler",
            "3,8 MB",
            "16.11.2026 18:47",
        ),
        RecentFile(
            "document",
            "Proje Raporu – Çevrimdışı Ofis Paketi Mimari Kararları.docx",
            "Belgeler › FAI Office",
            "412 KB",
            "15.11.2026 11:30",
        ),
        RecentFile("archive", "Fotoğraflar_Ağustos_Tatil_Ölüdeniz.zip", "Arşiv", "246 MB", "02.09.2026 21:16"),
    )
