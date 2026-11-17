#!/usr/bin/env python3
"""Seed corpus generator for Phase 1 (throwaway, MASTER_SPEC section 15).

Writes flat ODF sources (FODT / FODS / FODP) with deterministic content, then converts them with a headless
LibreOffice into the formats the budgets and proofs of concept need. Every file is generated here, so the
corpus is freely redistributable.

Usage: gen.py            (writes poc/corpus/out/, needs LibreOffice; path from $SOFFICE or the local cache)
"""
import os
import random
import struct
import subprocess
import sys
import zlib
import base64
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
SOFFICE = os.environ.get(
    "SOFFICE",
    str(next(Path.home().glob("dev/cache/fai-office/lo-*/root/opt/libreoffice*/program/soffice"), "soffice")),
)

WORDS = (
    "office document page table format engine render layout memory budget measure archive viewer shell module "
    "file convert compatible offline open source license font text image chart sheet slide editor print save "
    "çalışma belge sayfa tablo biçim ölçüm yazı tipi görüntü dönüşüm arşiv ağır hafif şekil öğe ılık güneş"
).split()


def sentence(rng, n=None):
    n = n or rng.randint(8, 20)
    s = " ".join(rng.choice(WORDS) for _ in range(n))
    return s[0].upper() + s[1:] + "."


def paragraph(rng, n=None):
    return " ".join(sentence(rng) for _ in range(n or rng.randint(3, 7)))


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def png(width, height, seed):
    """A gradient PNG with some noise, built with zlib only."""
    rng = random.Random(seed)
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            row += bytes(((x * 255 // width) ^ rng.randint(0, 40), (y * 255 // height), (seed * 40) % 256))
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


NS = (
    'xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" '
    'xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" '
    'xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" '
    'xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" '
    'xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" '
    'xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" '
    'xmlns:xlink="http://www.w3.org/1999/xlink" '
    'xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" '
    'xmlns:chart="urn:oasis:names:tc:opendocument:xmlns:chart:1.0" '
    'xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0" '
    'xmlns:of="urn:oasis:names:tc:opendocument:xmlns:of:1.2" '
    'xmlns:loext="urn:org:documentfoundation:names:experimental:office:xmlns:loext:1.0" '
)


def image_frame(name, seed, w_cm, h_cm, anchor="paragraph"):
    data = base64.b64encode(png(320, 200, seed)).decode()
    return (f'<draw:frame draw:name="{name}" text:anchor-type="{anchor}" svg:width="{w_cm}cm" svg:height="{h_cm}cm">'
            f'<draw:image><office:binary-data>{data}</office:binary-data></draw:image></draw:frame>')


def fodt(pages, seed, title):
    """A text document of roughly `pages` A4 pages: headings, paragraphs, tables, images, header, footer."""
    rng = random.Random(seed)
    body = [f'<text:h text:outline-level="1">{esc(title)}</text:h>']
    for s in range(round(pages * 3.45)):  # about 3.45 sections per A4 page, measured
        body.append(f'<text:h text:outline-level="2">{s + 1}. {esc(sentence(rng, 4))}</text:h>')
        for _ in range(2):
            body.append(f'<text:p text:style-name="Body">{esc(paragraph(rng))}</text:p>')
        if s % 5 == 2:
            rows = "".join(
                "<table:table-row>" + "".join(
                    f'<table:table-cell office:value-type="string"><text:p>{esc(rng.choice(WORDS))} {rng.randint(1, 999)}</text:p></table:table-cell>'
                    for _ in range(4)) + "</table:table-row>"
                for _ in range(6))
            body.append(f'<table:table table:name="T{s}"><table:table-column table:number-columns-repeated="4"/>{rows}</table:table>')
        if s % 7 == 4:
            body.append(f'<text:p text:style-name="Body">{image_frame(f"img{s}", s, 12, 7.5)}</text:p>')
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document {NS} office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.text">
<office:font-face-decls>
 <style:font-face style:name="Liberation Serif" svg:font-family="'Liberation Serif'"/>
 <style:font-face style:name="Liberation Sans" svg:font-family="'Liberation Sans'"/>
</office:font-face-decls>
<office:styles>
 <style:style style:name="Standard" style:family="paragraph"><style:text-properties style:font-name="Liberation Serif" fo:font-size="11pt"/></style:style>
 <style:style style:name="Body" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:margin-bottom="0.2cm" fo:text-align="justify"/></style:style>
 <style:style style:name="Heading_20_1" style:display-name="Heading 1" style:family="paragraph" style:default-outline-level="1"><style:text-properties style:font-name="Liberation Sans" fo:font-size="20pt" fo:font-weight="bold"/></style:style>
 <style:style style:name="Heading_20_2" style:display-name="Heading 2" style:family="paragraph" style:default-outline-level="2"><style:paragraph-properties fo:margin-top="0.4cm"/><style:text-properties style:font-name="Liberation Sans" fo:font-size="14pt" fo:font-weight="bold"/></style:style>
</office:styles>
<office:automatic-styles>
 <style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="21cm" fo:page-height="29.7cm" fo:margin="2cm"/>
  <style:header-style/><style:footer-style/></style:page-layout>
</office:automatic-styles>
<office:master-styles>
 <style:master-page style:name="Standard" style:page-layout-name="pm1">
  <style:header><text:p>{esc(title)}</text:p></style:header>
  <style:footer><text:p>Page <text:page-number text:select-page="current"/> / <text:page-count/></text:p></style:footer>
 </style:master-page>
</office:master-styles>
<office:body><office:text>
{chr(10).join(body)}
</office:text></office:body></office:document>
'''


def fods(rows, seed):
    """A spreadsheet: a data sheet with numbers and formulas, and a summary sheet with SUMIF / AVERAGE."""
    rng = random.Random(seed)
    regions = ["North", "South", "East", "West", "Kuzey", "Güney"]
    head = "".join(f'<table:table-cell office:value-type="string"><text:p>{h}</text:p></table:table-cell>'
                   for h in ["Region", "Units", "Price", "Revenue", "Cumulative"])
    data = [f"<table:table-row>{head}</table:table-row>"]
    for r in range(2, rows + 2):
        u, p = rng.randint(1, 500), round(rng.uniform(1, 99), 2)
        cum = f"of:=[.E{r - 1}]+[.D{r}]" if r > 2 else f"of:=[.D{r}]"
        data.append(
            "<table:table-row>"
            f'<table:table-cell office:value-type="string"><text:p>{rng.choice(regions)}</text:p></table:table-cell>'
            f'<table:table-cell office:value-type="float" office:value="{u}"/>'
            f'<table:table-cell office:value-type="float" office:value="{p}"/>'
            f'<table:table-cell table:formula="of:=[.B{r}]*[.C{r}]" office:value-type="float"/>'
            f'<table:table-cell table:formula="{cum}" office:value-type="float"/>'
            "</table:table-row>")
    summary = []
    for i, reg in enumerate(regions):
        summary.append(
            "<table:table-row>"
            f'<table:table-cell office:value-type="string"><text:p>{reg}</text:p></table:table-cell>'
            f'<table:table-cell table:formula="of:=SUMIF([Data.A2:.A{rows + 1}];[.A{i + 1}];[Data.D2:.D{rows + 1}])" office:value-type="float"/>'
            f'<table:table-cell table:formula="of:=AVERAGEIF([Data.A2:.A{rows + 1}];[.A{i + 1}];[Data.C2:.C{rows + 1}])" office:value-type="float"/>'
            "</table:table-row>")
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document {NS} office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.spreadsheet">
<office:body><office:spreadsheet>
<table:table table:name="Data">{"".join(data)}</table:table>
<table:table table:name="Summary">{"".join(summary)}</table:table>
</office:spreadsheet></office:body></office:document>
'''


def fodp(slides, seed):
    """A presentation: title slide, bullet slides, image slides, shape slides."""
    rng = random.Random(seed)
    pages = []
    for i in range(slides):
        items = []
        items.append(f'<draw:frame draw:style-name="F" svg:x="1.5cm" svg:y="1cm" svg:width="25cm" svg:height="2.5cm"><draw:text-box>'
                     f'<text:p text:style-name="T">{esc(sentence(rng, 5))}</text:p></draw:text-box></draw:frame>')
        if i % 3 == 1:
            items.append(f'<draw:frame draw:style-name="F" svg:x="5cm" svg:y="4.5cm" svg:width="18cm" svg:height="11cm"><draw:image>'
                         f'<office:binary-data>{base64.b64encode(png(480, 300, i)).decode()}</office:binary-data></draw:image></draw:frame>')
        elif i % 3 == 2:
            for k in range(4):
                items.append(f'<draw:custom-shape draw:style-name="S" svg:x="{2 + k * 6}cm" svg:y="6cm" svg:width="5cm" svg:height="5cm">'
                             f'<text:p>{k + 1}</text:p><draw:enhanced-geometry draw:type="{["ellipse", "rectangle", "diamond", "star5"][k]}"/></draw:custom-shape>')
        else:
            bullets = "".join(f"<text:list-item><text:p>{esc(sentence(rng, 7))}</text:p></text:list-item>" for _ in range(5))
            items.append(f'<draw:frame draw:style-name="F" svg:x="1.5cm" svg:y="4.5cm" svg:width="25cm" svg:height="12cm"><draw:text-box>'
                         f'<text:list>{bullets}</text:list></draw:text-box></draw:frame>')
        pages.append(f'<draw:page draw:name="s{i + 1}" draw:master-page-name="M">{"".join(items)}</draw:page>')
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<office:document {NS} office:version="1.3" office:mimetype="application/vnd.oasis.opendocument.presentation">
<office:styles>
 <style:style style:name="S" style:family="graphic"><style:graphic-properties draw:fill="solid" draw:fill-color="#3465a4"/></style:style>
 <style:style style:name="F" style:family="graphic"><style:graphic-properties draw:fill="none" draw:stroke="none"/></style:style>
</office:styles>
<office:automatic-styles>
 <style:page-layout style:name="PL"><style:page-layout-properties fo:page-width="28cm" fo:page-height="15.75cm" fo:margin="0cm"/></style:page-layout>
 <style:style style:name="T" style:family="paragraph"><style:text-properties fo:font-size="28pt" fo:font-weight="bold"/></style:style>
</office:automatic-styles>
<office:master-styles><style:master-page style:name="M" style:page-layout-name="PL"/></office:master-styles>
<office:body><office:presentation>{"".join(pages)}</office:presentation></office:body></office:document>
'''


def convert(src, fmt, outdir):
    subprocess.run([SOFFICE, "--headless", "--norestore", "--convert-to", fmt, "--outdir", str(outdir), str(src)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    OUT.mkdir(exist_ok=True)
    src = OUT / "src"
    src.mkdir(exist_ok=True)
    jobs = [
        ("typical-20p.fodt", fodt(20, 1, "Typical document"), ["docx", "odt", "pdf"]),
        ("long-100p.fodt", fodt(100, 2, "Long document"), ["pdf"]),
        ("typical.fods", fods(2000, 3), ["xlsx", "ods"]),
        ("typical.fodp", fodp(12, 4), ["pptx", "odp"]),
    ]
    for i, (name, xml, fmts) in enumerate(jobs, 1):
        path = src / name
        path.write_text(xml, encoding="utf-8")
        for fmt in fmts:
            print(f"[{i}/{len(jobs)}] {name} -> {fmt}", flush=True)
            convert(path, fmt, OUT)
    for f in sorted(OUT.iterdir()):
        if f.is_file():
            print(f"{f.stat().st_size // 1024:>6} KB  {f.name}")


if __name__ == "__main__":
    sys.exit(main())
