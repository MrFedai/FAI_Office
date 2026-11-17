#!/usr/bin/env python3
"""Converts PoC PPM output to PNG for viewing, halving the size (2x2 box filter). Usage: ppm2png.py <file.ppm>... (writes .png)"""
import struct, sys, zlib
for path in sys.argv[1:]:
    d = open(path, 'rb').read()
    _, dims, _, px = d.split(b'\n', 3)
    w, h = map(int, dims.split())
    rows = []
    for y in range(0, h - 1, 2):
        a, b = px[y * w * 3:(y + 1) * w * 3], px[(y + 1) * w * 3:(y + 2) * w * 3]
        rows.append(b'\0' + bytes((a[x * 3 + k] + a[x * 3 + 3 + k] + b[x * 3 + k] + b[x * 3 + 3 + k]) // 4
                                   for x in range(0, w - 1, 2) for k in range(3)))
    c = lambda t, x: struct.pack('>I', len(x)) + t + x + struct.pack('>I', zlib.crc32(t + x) & 0xffffffff)
    ihdr = struct.pack('>IIBBBBB', w // 2, len(rows), 8, 2, 0, 0, 0)
    out = path[:-4] + '.png'
    open(out, 'wb').write(b'\x89PNG\r\n\x1a\n' + c(b'IHDR', ihdr) + c(b'IDAT', zlib.compress(b''.join(rows))) + c(b'IEND', b''))
    print(out)
