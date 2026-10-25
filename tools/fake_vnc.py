#!/usr/bin/env python3
"""fake_vnc.py [PASSWORD] [PORT]: a small VNC server for testing the app without the Mac's Screen Sharing.
It serves a picture of the Mac's two monitors side by side, in points (BenQ 2560x1440 + MacBook 1512x982 = 4072x1440),
asks for the VNC password (security type 2), answers frames in ZRLE (solid and raw tiles) or Raw, and prints every
pointer and key event it receives, so a tap on the phone can be checked against where it landed."""
import socket, struct, sys, os, zlib, subprocess, threading
from PIL import Image, ImageDraw
from cryptography.hazmat.decrepit.ciphers.algorithms import TripleDES
from cryptography.hazmat.primitives.ciphers import Cipher, modes
PW = sys.argv[1] if len(sys.argv) > 1 else 'mantra'; PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 5901
W, H = 4072, 1440

def picture():
    img = Image.new('RGB', (W, H), (20, 20, 20))
    for i, (x, w, h) in enumerate([(0, 2560, 1440), (2560, 1512, 982)]):
        f = f'/tmp/claude-501/fake_vnc_{i}.png'
        subprocess.run(['screencapture', '-x', '-D', str(i + 1), f])
        if os.path.exists(f): img.paste(Image.open(f).convert('RGB').resize((w, h)), (x, 0))
        d = ImageDraw.Draw(img); d.rectangle([x + 4, 4, x + w - 5, h - 5], outline=(255, 176, 0), width=8)
        d.text((x + 40, 40), f'MONITOR {i + 1}', fill=(255, 176, 0))
    return img.tobytes()

def des_response(pw, ch):
    key = bytes(int(f'{b:08b}'[::-1], 2) for b in (pw.encode()[:8] + b'\0' * 8)[:8])
    e = Cipher(TripleDES(key * 3), modes.ECB()).encryptor(); return e.update(ch) + e.finalize()

def rx(c, n):
    b = b''
    while len(b) < n:
        k = c.recv(n - len(b))
        if not k: raise EOFError
        b += k
    return b

def serve(c):
    rgb = picture()
    c.sendall(b'RFB 003.008\n'); rx(c, 12)
    c.sendall(bytes([1, 2])); assert rx(c, 1)[0] == 2
    ch = os.urandom(16); c.sendall(ch)
    if rx(c, 16) != des_response(PW, ch):
        c.sendall(struct.pack('>I', 1) + struct.pack('>I', 14) + b'wrong password'); print('PASSWORD WRONG'); return
    c.sendall(struct.pack('>I', 0)); rx(c, 1)
    name = b'fake mantra-mac'
    c.sendall(struct.pack('>HH', W, H) + bytes([32, 24, 0, 1]) + struct.pack('>HHH', 255, 255, 255) + bytes([16, 8, 0, 0, 0, 0])
              + struct.pack('>I', len(name)) + name)
    print('CLIENT IN, password OK')
    z = zlib.compressobj(); encs = []; sent_full = set()
    while True:
        t = rx(c, 1)[0]
        if t == 0: rx(c, 19)
        elif t == 2:
            _, n = struct.unpack('>BH', rx(c, 3)); encs = list(struct.unpack('>%di' % n, rx(c, 4 * n))); print('encodings', encs)
        elif t == 3:
            inc, x, y, w, h = struct.unpack('>BHHHH', rx(c, 9))
            if inc and (x, y, w, h) in sent_full: continue   # nothing changes in a still picture: incremental = no answer
            sent_full.add((x, y, w, h)); print(f'FRAME REQUEST {"inc" if inc else "full"} x={x} y={y} w={w} h={h}')
            if 16 in encs:
                out = bytearray()
                for ty in range(y, y + h, 64):
                    for tx in range(x, x + w, 64):
                        tw, th = min(64, x + w - tx), min(64, y + h - ty)
                        px = b''.join(rgb[((ty + r) * W + tx) * 3:((ty + r) * W + tx + tw) * 3] for r in range(th))
                        bgr = bytes(px[i ^ 2 if i % 3 != 1 else i] for i in range(len(px)))  # RGB -> BGR per pixel
                        if bgr[:3] * (tw * th) == bgr: out += b'\x01' + bgr[:3]
                        else: out += b'\x00' + bgr
                data = z.compress(bytes(out)) + z.flush(zlib.Z_SYNC_FLUSH)
                c.sendall(struct.pack('>BBH', 0, 0, 1) + struct.pack('>HHHHi', x, y, w, h, 16) + struct.pack('>I', len(data)) + data)
                print(f'  sent ZRLE {len(data) / 1e3:.0f} kB')
            else:
                body = b''.join(rgb[((y + r) * W + x) * 3:((y + r) * W + x + w) * 3] for r in range(h))
                raw = bytearray(w * h * 4); raw[0::4] = body[2::3]; raw[1::4] = body[1::3]; raw[2::4] = body[0::3]
                c.sendall(struct.pack('>BBH', 0, 0, 1) + struct.pack('>HHHHi', x, y, w, h, 0) + raw)
        elif t == 4:
            down, _, k = struct.unpack('>BHI', rx(c, 7)); print(f'KEY {"down" if down else "up  "} 0x{k:x} {chr(k) if 32 <= k < 127 else ""}')
        elif t == 5:
            m, x, y = struct.unpack('>BHH', rx(c, 5)); print(f'POINTER buttons={m} x={x} y={y}', flush=True)
        elif t == 6: rx(c, 3); n = struct.unpack('>I', rx(c, 4))[0]; rx(c, n)

s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(('127.0.0.1', PORT)); s.listen(1)   # this Mac only; the emulator reaches it as 10.0.2.2
print(f'fake VNC on :{PORT}, password {PW!r}', flush=True)
while True:
    c, a = s.accept(); print('connection from', a, flush=True)
    threading.Thread(target=serve, args=(c,), daemon=True).start()
