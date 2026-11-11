"""Whity's animated reactions for the app, made from one painted sticker (whity_peek).

Eyes are found automatically; each frame turns the eyeballs (a gentle local warp), closes
the lids (the fur above slides down), and adds drawn extras (happy eyes, heart eyes, spirals,
blush, hearts, stars, Zzz, a cookie). The whole face can bounce, squash or tilt.

    python scripts/whity_reactions.py <whity_peek.webp> <res/raw dir> <res/drawable-nodpi dir> <font.ttf> [preview dir]

Writes one animated WebP per reaction to res/raw (whity_react_<name>.webp, transparent), one
still pose per reaction to drawable-nodpi (whity_react_<name>_still.webp, shown before an
animation starts, when animations are off and on Android 8) and, with a preview dir, a contact
sheet and a GIF showreel on white to look at.
"""
import math
import pathlib
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

src = pathlib.Path(sys.argv[1])
raw_dir = pathlib.Path(sys.argv[2])
still_dir = pathlib.Path(sys.argv[3])
font_path = pathlib.Path(sys.argv[4])
preview_dir = pathlib.Path(sys.argv[5]) if len(sys.argv) > 5 else None
OUT_WIDTH = 330  # px; Whity shows at about 72-150 dp
random.seed(7)

S = 3  # working scale
base = Image.open(src).convert('RGBA')
W0, H0 = base.size
big = base.resize((W0 * S, H0 * S), Image.LANCZOS)
ARR = np.asarray(big).astype(np.float32)
H, W = ARR.shape[:2]
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)

# Room around the sticker for bouncing, hearts and Zzz.
PAD_X, PAD_TOP, PAD_BOTTOM = int(W * 0.12), int(H * 0.30), int(H * 0.05)
CW, CH = W + 2 * PAD_X, H + PAD_TOP + PAD_BOTTOM

INK = (62, 40, 30, 255)
PINK = (248, 120, 160, 255)
RED = (226, 61, 45, 255)
YELLOW = (255, 199, 44, 255)


def find_eyes():
    lum = 0.299 * ARR[..., 0] + 0.587 * ARR[..., 1] + 0.114 * ARR[..., 2]
    dark = (lum < 70) & (ARR[..., 3] > 200)
    area = np.zeros_like(dark)
    area[int(H * 0.35):int(H * 0.80), int(W * 0.12):int(W * 0.88)] = True
    labels, n = ndimage.label(dark & area)
    sizes = ndimage.sum(np.ones_like(lum), labels, range(1, n + 1))
    found = []
    for idx in np.argsort(sizes)[::-1][:8]:
        ys, xs = np.nonzero(labels == idx + 1)
        if len(xs) < 40:
            continue
        cx, cy = xs.mean(), ys.mean()
        if abs(cx - W / 2) < W * 0.08 and cy > H * 0.6:
            continue  # the nose
        found.append((cx, cy, math.sqrt(len(xs) / math.pi)))
        if len(found) == 2:
            break
    assert len(found) == 2, found
    return sorted(found)


EYES = find_eyes()
NOSE = (W / 2, max(cy for _, cy, _ in EYES) + EYES[0][2] * 3.2)


def remap(arr, sy, sx):
    out = np.empty_like(arr)
    for c in range(4):
        out[..., c] = ndimage.map_coordinates(arr[..., c], [YY - sy, XX - sx], order=1, mode='nearest')
    return out


def face(look=(0.0, 0.0), lid=0.0):
    """The face with the eyeballs turned by [look] (in iris radii) and the lids [lid] closed."""
    sx = np.zeros((H, W), np.float32)
    sy = np.zeros((H, W), np.float32)
    for cx, cy, r in EYES:
        d = np.hypot(XX - cx, (YY - cy) * 1.15)
        w = np.clip((r * 1.55 - d) / (r * 0.7), 0, 1)
        w = w * w * (3 - 2 * w)
        sx += w * look[0] * r
        sy += w * look[1] * r
    arr = remap(ARR, sy, sx) if (look[0] or look[1]) else ARR.copy()
    if lid > 0:
        shift = np.zeros((H, W), np.float32)
        for cx, cy, r in EYES:
            rx, ry = r * 1.45, r * 1.2
            top = cy - ry
            dx = (XX - cx) / rx
            across = np.clip(1 - dx * dx, 0, 1)
            edge = top + 2 * ry * lid + ry * 0.28 * lid * across
            hw = np.clip((1.12 - np.abs(dx)) / 0.3, 0, 1)
            hw = hw * hw * (3 - 2 * hw)
            above = np.clip((YY - (top - ry * 1.1)) / (ry * 1.1), 0, 1)
            shift = np.maximum(shift, 2 * ry * lid * hw * above * (YY <= edge))
        arr = remap(arr, shift, np.zeros_like(shift))
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    if 0 < lid < 1:
        lash(img, lid)
    return img


def lash(img, lid):
    g = ImageDraw.Draw(img)
    for cx, cy, r in EYES:
        rx, ry = r * 1.45, r * 1.2
        top = cy - ry
        pts = [(cx + t * rx, top + 2 * ry * lid + ry * 0.28 * lid * (1 - t * t)) for t in np.linspace(-0.85, 0.85, 20)]
        g.line(pts, fill=INK, width=int(S * (1.6 + 1.2 * lid)), joint='curve')


def closed_eyes(img, happy):
    """Shut eyes: a sleepy curve (happy=False) or a smiling arc (happy=True), with tiny lashes."""
    g = ImageDraw.Draw(img)
    for cx, cy, r in EYES:
        rx = r * 1.25
        base_y = cy + r * 0.35
        pts = []
        for t in np.linspace(-1, 1, 24):
            bend = r * 0.55 * (1 - t * t)
            pts.append((cx + t * rx, base_y - bend if happy else base_y + bend * 0.6 - r * 0.3))
        g.line(pts, fill=INK, width=int(S * 3.2), joint='curve')
        if not happy:
            for t in (-0.8, 0.8):
                x, y = cx + t * rx, base_y - r * 0.3 + r * 0.55 * (1 - t * t) * 0.6
                g.line([(x, y), (x + t * r * 0.35, y + r * 0.35)], fill=INK, width=int(S * 1.8))


def heart_path(cx, cy, size):
    pts = []
    for i in range(48):
        t = math.pi * 2 * i / 48
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * size / 16, cy - y * size / 16))
    return pts


def heart(g, cx, cy, size, fill=PINK, alpha=255):
    f = fill[:3] + (alpha,)
    g.polygon(heart_path(cx, cy, size), fill=f, outline=INK[:3] + (alpha,), width=max(2, int(size * 0.14)))


def heart_eyes(img, pulse):
    g = ImageDraw.Draw(img)
    for cx, cy, r in EYES:
        heart(g, cx, cy, r * 1.25 * pulse, fill=RED)


def spiral_eyes(img, angle):
    g = ImageDraw.Draw(img)
    for cx, cy, r in EYES:
        g.ellipse([cx - r * 1.2, cy - r * 1.05, cx + r * 1.2, cy + r * 1.05], fill=(255, 250, 240, 255), outline=INK, width=int(S * 2))
        pts = []
        for i in range(90):
            t = i / 89
            a = angle + t * math.pi * 5
            rr = r * 0.95 * t
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a) * 0.9))
        g.line(pts, fill=INK, width=int(S * 2.2), joint='curve')


def blush(img, alpha):
    if alpha <= 0:
        return
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(layer)
    for i, (cx, cy, r) in enumerate(EYES):
        ox = -r * 0.9 if i == 0 else r * 0.9
        x, y = cx + ox, cy + r * 1.9
        g.ellipse([x - r * 1.0, y - r * 0.55, x + r * 1.0, y + r * 0.55], fill=(255, 120, 150, int(140 * alpha)))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(S * 3)))


def star(g, cx, cy, size, alpha=255):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = size if i % 2 == 0 else size * 0.45
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    g.polygon(pts, fill=YELLOW[:3] + (alpha,), outline=INK[:3] + (alpha,), width=max(2, int(size * 0.15)))


def cookie(g, cx, cy, size):
    g.ellipse([cx - size, cy - size, cx + size, cy + size], fill=(214, 160, 96, 255), outline=INK, width=max(2, int(size * 0.14)))
    for dx, dy in [(-0.35, -0.3), (0.3, -0.15), (-0.05, 0.35), (0.4, 0.35), (-0.45, 0.2)]:
        rr = size * 0.15
        g.ellipse([cx + dx * size - rr, cy + dy * size - rr, cx + dx * size + rr, cy + dy * size + rr], fill=(96, 56, 36, 255))


FONT = ImageFont.truetype(str(font_path), int(S * 34))
try:
    FONT.set_variation_by_name('Bold')
except Exception:
    pass


def place(img, dx=0.0, dy=0.0, angle=0.0, sx=1.0, sy=1.0):
    """The face on the padded canvas, moved / squashed / tilted around its bottom centre."""
    canvas = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    face_img = img
    if sx != 1.0 or sy != 1.0:
        face_img = face_img.resize((max(1, int(W * sx)), max(1, int(H * sy))), Image.BICUBIC)
    if angle:
        face_img = face_img.rotate(angle, resample=Image.BICUBIC, expand=True)
    x = PAD_X + (W - face_img.width) / 2 + dx
    y = PAD_TOP + (H - face_img.height) + dy
    canvas.alpha_composite(face_img, (int(round(x)), int(round(y))))
    return canvas


def to_canvas(x, y):
    """Face coordinates to canvas coordinates (for extras drawn around the face)."""
    return x + PAD_X, y + PAD_TOP


# ------------------------------------------------------------------ the reactions

def idle_watch():
    frames = []

    def add(img, ms):
        frames.append((place(img), ms))

    def glide(a, b, steps):
        for i in range(1, steps + 1):
            t = 0.5 - 0.5 * math.cos(math.pi * i / steps)
            add(face((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)), 50)

    c, l, r, u = (0, 0), (-0.42, 0.03), (0.42, 0.03), (0, -0.25)
    add(face(c), 700)
    glide(c, l, 4)
    add(face(l), 550)
    glide(l, r, 6)
    add(face(r), 550)
    glide(r, c, 4)
    add(face(c), 300)
    for a in [0.3, 0.65, 1.0, 1.0, 0.7, 0.35]:
        add(face(c, a), 40)
    add(face(c), 500)
    glide(c, u, 3)
    add(face(u), 450)
    glide(u, c, 3)
    return frames


def floating_hearts(canvas, t, count, spread, seed, start=0.0):
    rnd = random.Random(seed)
    g = ImageDraw.Draw(canvas)
    for i in range(count):
        t0 = start + i * 0.12
        p = (t - t0) / 0.9
        if not 0 <= p <= 1:
            continue
        x0 = CW / 2 + rnd.uniform(-spread, spread) * W
        x = x0 + math.sin(p * math.pi * 2 + i) * W * 0.03
        y = PAD_TOP + H * 0.25 - p * (PAD_TOP + H * 0.15)
        size = W * (0.035 + 0.02 * rnd.random()) * (0.6 + 0.6 * min(1, p * 3))
        heart(g, x, y, size, alpha=int(255 * (1 - max(0, p - 0.6) / 0.4)))


def tap_happy():
    frames = []
    # (squash x, squash y, lift, eyes) through a bounce.
    keys = [(1.0, 1.0, 0, 'open'), (1.08, 0.9, 0, 'happy'), (1.04, 0.95, 0, 'happy'), (0.96, 1.06, -0.10, 'happy'),
            (0.98, 1.03, -0.16, 'happy'), (1.0, 1.0, -0.12, 'happy'), (1.02, 0.98, -0.04, 'happy'), (1.07, 0.92, 0, 'happy'),
            (0.98, 1.02, 0, 'happy'), (1.0, 1.0, 0, 'happy'), (1.0, 1.0, 0, 'happy'), (1.0, 1.0, 0, 'happy'),
            (1.0, 1.0, 0, 'blinkopen'), (1.0, 1.0, 0, 'open')]
    happy_face = face((0, 0), 1.0)
    closed_eyes(happy_face, happy=True)
    blush(happy_face, 0.8)
    for i, (sx, sy, lift, eyes) in enumerate(keys):
        img = {'open': face(), 'happy': happy_face, 'blinkopen': face((0, 0), 0.5)}[eyes]
        canvas = place(img, dy=lift * H, sx=sx, sy=sy)
        floating_hearts(canvas, i / 15, 4, 0.3, seed=3, start=0.1)
        frames.append((canvas, 70 if i < len(keys) - 1 else 500))
    return frames


def pet_bliss():
    frames = []
    shut = face((0, 0), 1.0)
    closed_eyes(shut, happy=True)
    n = 30
    for i in range(n):
        t = i / n
        img = shut.copy()
        blush(img, min(1.0, 0.3 + t * 2))
        canvas = place(img, angle=6 * math.sin(t * math.pi * 2), dy=-H * 0.015 * abs(math.sin(t * math.pi * 2)))
        floating_hearts(canvas, t * 2.4, 8, 0.35, seed=11)
        frames.append((canvas, 80))
    return frames


def treat_munch():
    frames = []
    nose_x, nose_y = to_canvas(*NOSE)
    start = (CW * 0.98, CH * 0.95)
    # 1. The cookie flies in on an arc; her eyes follow it.
    for i in range(10):
        p = i / 9
        x = start[0] + (nose_x + W * 0.05 - start[0]) * p
        y = start[1] + (nose_y - H * 0.02 - start[1]) * p - math.sin(p * math.pi) * H * 0.35
        look = (0.45 * (1 - p) + 0.1, 0.25 + 0.1 * p)
        canvas = place(face(look))
        cookie(ImageDraw.Draw(canvas), x, y, W * 0.055)
        frames.append((canvas, 60))
    # 2. Nom nom: happy eyes, the head bobs, the cookie gets smaller, crumbs fly.
    happy = face((0, 0), 1.0)
    closed_eyes(happy, happy=True)
    blush(happy, 0.7)
    rnd = random.Random(5)
    for i in range(12):
        bob = (-1) ** i * H * 0.02
        canvas = place(happy, dy=bob)
        g = ImageDraw.Draw(canvas)
        size = W * 0.055 * max(0.0, 1 - i / 9)
        if size > 2:
            cookie(g, nose_x + W * 0.05, nose_y - H * 0.02 + bob, size)
        for _ in range(3):
            cx = nose_x + rnd.uniform(-0.12, 0.18) * W
            cy = nose_y + rnd.uniform(-0.05, 0.08) * H
            g.ellipse([cx - S * 2.5, cy - S * 2.5, cx + S * 2.5, cy + S * 2.5], fill=(214, 160, 96, 255))
        word = 'nom' if (i // 3) % 2 == 0 else 'nom!'
        g.text((CW * 0.60 + (i % 3) * S * 4, PAD_TOP * 0.35 - (i % 3) * S * 5), word, font=FONT, fill=INK, stroke_width=S * 3, stroke_fill=(255, 255, 255, 255))
        frames.append((canvas, 90))
    # 3. Love: heart eyes pulse, hearts rise.
    for i in range(10):
        img = face((0, 0), 1.0)
        heart_eyes(img, 1.0 + 0.15 * math.sin(i / 10 * math.pi * 4))
        blush(img, 0.8)
        canvas = place(img)
        floating_hearts(canvas, i / 10, 5, 0.3, seed=9)
        frames.append((canvas, 80))
    frames.append((place(face()), 600))
    return frames


def dizzy():
    frames = []
    n = 24
    for i in range(n):
        t = i / n
        img = face((0, 0), 1.0)
        spiral_eyes(img, angle=t * math.pi * 6)
        canvas = place(img, angle=7 * math.sin(t * math.pi * 4), dx=W * 0.02 * math.sin(t * math.pi * 4))
        g = ImageDraw.Draw(canvas)
        for k in range(3):
            a = t * math.pi * 4 + k * math.pi * 2 / 3
            star(g, CW / 2 + math.cos(a) * W * 0.25, PAD_TOP + H * 0.08 + math.sin(a) * H * 0.07, W * 0.03)
        frames.append((canvas, 70))
    return frames


def sleepy():
    frames = []
    n = 32
    shut = face((0, 0), 1.0)
    closed_eyes(shut, happy=False)
    for i in range(n):
        t = i / n
        if i < 6:
            img = face((0, 0.15), 0.45 + i * 0.09)
        else:
            img = shut
        breath = 1 + 0.02 * math.sin(t * math.pi * 4)
        canvas = place(img, sx=breath, sy=breath)
        g = ImageDraw.Draw(canvas)
        for k in range(3):
            p = (t * 1.5 - k * 0.3) % 1.0
            if i >= 6 or k == 0:
                size = int(S * (20 + 18 * p))
                f = ImageFont.truetype(str(font_path), size)
                x = CW * 0.62 + p * W * 0.14 + k * W * 0.05
                y = PAD_TOP + H * 0.1 - p * PAD_TOP * 0.9
                fade = int(255 * min(1.0, (1 - p) * 1.6))
                g.text((x, y), 'Z', font=f, fill=(58, 80, 190, fade), stroke_width=S * 2, stroke_fill=(255, 255, 255, fade))
        frames.append((canvas, 90))
    return frames


def love_eyes():
    frames = []
    for i in range(20):
        t = i / 20
        img = face((0, 0), 1.0)
        heart_eyes(img, 1.0 + 0.18 * math.sin(t * math.pi * 4))
        blush(img, 0.9)
        canvas = place(img, dy=-H * 0.01 * abs(math.sin(t * math.pi * 4)))
        floating_hearts(canvas, t * 1.8, 6, 0.35, seed=21)
        frames.append((canvas, 75))
    return frames


REACTIONS = [
    ('watch', 'Watching you', idle_watch),
    ('tap', 'Tap: happy bounce', tap_happy),
    ('pet', 'Petting: bliss', pet_bliss),
    ('treat', 'Treat: nom nom', treat_munch),
    ('dizzy', 'Too many taps: dizzy', dizzy),
    ('sleepy', 'Night: sleepy', sleepy),
    ('love', 'New message: love', love_eyes),
]

# The frame that best shows each reaction as a single picture.
STILL_FRAME = {'watch': 0, 'tap': 4, 'pet': 10, 'treat': 15, 'dizzy': 3, 'sleepy': 14, 'love': 6}
OW = OUT_WIDTH
OH = round(CH * OW / CW)
raw_dir.mkdir(parents=True, exist_ok=True)
still_dir.mkdir(parents=True, exist_ok=True)
sheet_rows = []
reel = []
label_font = ImageFont.truetype(str(font_path), 18)
for key, label, make in REACTIONS:
    frames = make()
    small = [(f.resize((OW, OH), Image.LANCZOS), ms) for f, ms in frames]
    webp = raw_dir / f'whity_react_{key}.webp'
    small[0][0].save(webp, save_all=True, append_images=[f for f, _ in small[1:]], duration=[ms for _, ms in small], loop=0, quality=80, method=6)
    small[STILL_FRAME[key]][0].save(still_dir / f'whity_react_{key}_still.webp', quality=86, method=6)
    print(key, len(frames), 'frames', webp.stat().st_size, 'bytes')
    if preview_dir is None:
        continue
    picks = [small[int(i * (len(small) - 1) / 4)][0] for i in range(5)]
    row = Image.new('RGB', (OW * 5 + 220, OH), 'white')
    ImageDraw.Draw(row).text((10, OH // 2 - 12), label, font=label_font, fill=(35, 27, 30))
    for i, f in enumerate(picks):
        row.paste(Image.alpha_composite(Image.new('RGBA', f.size, (255, 255, 255, 255)), f).convert('RGB'), (220 + OW * i, 0))
    sheet_rows.append(row)
    for f, ms in small + [(small[-1][0], 300)]:
        frame = Image.new('RGB', (OW, OH + 34), 'white')
        frame.paste(Image.alpha_composite(Image.new('RGBA', f.size, (255, 255, 255, 255)), f).convert('RGB'), (0, 34))
        ImageDraw.Draw(frame).text((10, 6), label, font=label_font, fill=(216, 57, 43))
        reel.append((frame, ms))

if preview_dir is not None:
    preview_dir.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGB', (sheet_rows[0].width, sum(r.height for r in sheet_rows)), 'white')
    y = 0
    for r in sheet_rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(preview_dir / 'whity_reactions_sheet.png')
    reel[0][0].save(preview_dir / 'whity_reactions_reel.gif', save_all=True, append_images=[f for f, _ in reel[1:]], duration=[ms for _, ms in reel], loop=0, optimize=True)
