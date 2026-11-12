"""Whity's animated reactions, outfits and treats for the app, made from one painted sticker
(whity_peek).

Eyes are found automatically; each frame turns the eyeballs (a gentle local warp), closes
the lids (the fur above slides down), and adds drawn extras (happy eyes, heart eyes, spirals,
blush, hearts, stars, Zzz, crumbs). Her head never moves inside the pictures: how her whole
face bounces, squashes or tilts is written out as a motion track that the app plays in step,
so the outfit she wears (drawn here too) moves with her. The treat she munches is drawn by the
app along the path written here, so it can be any of her treats.

    python scripts/whity_reactions.py <whity_peek.webp> <res/raw dir> <res/drawable-nodpi dir> <font.ttf> <WhityTracks.kt> [preview dir]

Writes:
- res/raw/whity_react_<name>.webp: one transparent animated WebP per reaction;
- drawable-nodpi/whity_react_<name>_still.webp: one still pose per reaction (shown before an
  animation starts, when animations are off and on Android 8);
- drawable-nodpi/whity_outfit_<name>.webp: each outfit on a see-through picture the size of a
  reaction, laid over it;
- drawable-nodpi/whity_treat_<name>.webp: each treat;
- WhityTracks.kt: the motion tracks and the treat's path;
- with a preview dir: a contact sheet, an outfit sheet and a GIF showreel on white to look at.
"""
import math
import pathlib
import random
import sys
from collections import namedtuple

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

src = pathlib.Path(sys.argv[1])
raw_dir = pathlib.Path(sys.argv[2])
still_dir = pathlib.Path(sys.argv[3])
font_path = pathlib.Path(sys.argv[4])
tracks_path = pathlib.Path(sys.argv[5])
preview_dir = pathlib.Path(sys.argv[6]) if len(sys.argv) > 6 else None
OUT_WIDTH = 330  # px; Whity shows at about 72-210 dp
TREAT_SIZE = 128  # px
random.seed(7)

S = 3  # working scale
base = Image.open(src).convert('RGBA')
W0, H0 = base.size
big = base.resize((W0 * S, H0 * S), Image.LANCZOS)
ARR = np.asarray(big).astype(np.float32)
H, W = ARR.shape[:2]
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)

# Room around the sticker for bouncing, hearts, Zzz and props.
PAD_X, PAD_TOP, PAD_BOTTOM = int(W * 0.12), int(H * 0.30), int(H * 0.05)
CW, CH = W + 2 * PAD_X, H + PAD_TOP + PAD_BOTTOM

INK = (62, 40, 30, 255)
PINK = (248, 120, 160, 255)
RED = (226, 61, 45, 255)
YELLOW = (255, 199, 44, 255)
WHITE = (255, 253, 248, 255)


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


def place(img):
    """The face on the padded canvas, where it always sits: the app moves it."""
    canvas = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    canvas.alpha_composite(img, (PAD_X, PAD_TOP))
    return canvas


def to_canvas(x, y):
    """Face coordinates to canvas coordinates (for extras drawn around the face)."""
    return x + PAD_X, y + PAD_TOP


# ------------------------------------------------------------------ the reactions
# A frame: the picture, how long it shows (ms), how the app moves the whole picture while it
# shows (scale x, scale y, move x, move y in canvas px, turn in degrees clockwise) and, in the
# treat reaction, where the app draws the treat (centre x, centre y, radius in canvas px).

Frame = namedtuple('Frame', 'img ms move treat')
STILL = (1.0, 1.0, 0.0, 0.0, 0.0)
TREAT_R = W * 0.07


def move(sx=1.0, sy=1.0, dx=0.0, dy=0.0, turn=0.0):
    return (sx, sy, dx, dy, turn)


def idle_watch():
    frames = []

    def add(img, ms):
        frames.append(Frame(place(img), ms, STILL, None))

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
    open_face, blink_face = face(), face((0, 0), 0.5)
    for i, (sx, sy, lift, eyes) in enumerate(keys):
        img = {'open': open_face, 'happy': happy_face, 'blinkopen': blink_face}[eyes]
        canvas = place(img)
        floating_hearts(canvas, i / 15, 4, 0.3, seed=3, start=0.1)
        frames.append(Frame(canvas, 70 if i < len(keys) - 1 else 500, move(sx, sy, dy=lift * H), None))
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
        canvas = place(img)
        floating_hearts(canvas, t * 2.4, 8, 0.35, seed=11)
        frames.append(Frame(canvas, 80, move(dy=-H * 0.015 * abs(math.sin(t * math.pi * 2)), turn=-6 * math.sin(t * math.pi * 2)), None))
    return frames


def treat_munch():
    frames = []
    nose_x, nose_y = to_canvas(*NOSE)
    mouth = (nose_x + W * 0.035, nose_y + H * 0.035)
    start = (CW * 0.98, CH * 0.95)
    # 1. The treat flies in on an arc; her eyes follow it.
    for i in range(10):
        p = i / 9
        x = start[0] + (mouth[0] - start[0]) * p
        y = start[1] + (mouth[1] - start[1]) * p - math.sin(p * math.pi) * H * 0.35
        look = (0.45 * (1 - p) + 0.1, 0.25 + 0.1 * p)
        frames.append(Frame(place(face(look)), 60, STILL, (x, y, TREAT_R)))
    # 2. Nom nom: happy eyes, the head bobs, the treat gets smaller, crumbs fly.
    happy = face((0, 0), 1.0)
    closed_eyes(happy, happy=True)
    blush(happy, 0.7)
    rnd = random.Random(5)
    for i in range(12):
        bob = (-1) ** i * H * 0.02
        canvas = place(happy)
        g = ImageDraw.Draw(canvas)
        for _ in range(3):
            cx = mouth[0] + rnd.uniform(-0.14, 0.16) * W
            cy = mouth[1] + rnd.uniform(-0.04, 0.06) * H
            g.ellipse([cx - S * 2.5, cy - S * 2.5, cx + S * 2.5, cy + S * 2.5], fill=(226, 184, 128, 255))
        word = 'nom' if (i // 3) % 2 == 0 else 'nom!'
        g.text((CW * 0.60 + (i % 3) * S * 4, PAD_TOP * 0.35 - (i % 3) * S * 5), word, font=FONT, fill=INK, stroke_width=S * 3, stroke_fill=(255, 255, 255, 255))
        size = TREAT_R * max(0.0, 1 - i / 9)
        frames.append(Frame(canvas, 90, move(dy=bob), (mouth[0], mouth[1], size) if size > 2 else None))
    # 3. Love: heart eyes pulse, hearts rise.
    for i in range(10):
        img = face((0, 0), 1.0)
        heart_eyes(img, 1.0 + 0.15 * math.sin(i / 10 * math.pi * 4))
        blush(img, 0.8)
        canvas = place(img)
        floating_hearts(canvas, i / 10, 5, 0.3, seed=9)
        frames.append(Frame(canvas, 80, STILL, None))
    frames.append(Frame(place(face()), 600, STILL, None))
    return frames


def dizzy():
    frames = []
    n = 24
    for i in range(n):
        t = i / n
        img = face((0, 0), 1.0)
        spiral_eyes(img, angle=t * math.pi * 6)
        canvas = place(img)
        g = ImageDraw.Draw(canvas)
        for k in range(3):
            a = t * math.pi * 4 + k * math.pi * 2 / 3
            star(g, CW / 2 + math.cos(a) * W * 0.25, PAD_TOP + H * 0.08 + math.sin(a) * H * 0.07, W * 0.03)
        frames.append(Frame(canvas, 70, move(dx=W * 0.02 * math.sin(t * math.pi * 4), turn=-7 * math.sin(t * math.pi * 4)), None))
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
        canvas = place(img)
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
        frames.append(Frame(canvas, 90, move(breath, breath), None))
    return frames


def love_eyes():
    frames = []
    for i in range(20):
        t = i / 20
        img = face((0, 0), 1.0)
        heart_eyes(img, 1.0 + 0.18 * math.sin(t * math.pi * 4))
        blush(img, 0.9)
        canvas = place(img)
        floating_hearts(canvas, t * 1.8, 6, 0.35, seed=21)
        frames.append(Frame(canvas, 75, move(dy=-H * 0.01 * abs(math.sin(t * math.pi * 4))), None))
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
STILL_FRAME = {'watch': 0, 'tap': 4, 'pet': 10, 'treat': 12, 'dizzy': 3, 'sleepy': 14, 'love': 6}


# ------------------------------------------------------------------ drawing helpers for outfits and treats

def disk(r):
    r = max(1, int(round(r)))
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def inked(layer, width):
    """[layer] with a sticker-style ink outline [width] px wide around everything opaque in it."""
    solid = np.asarray(layer)[..., 3] > 110
    ring = ndimage.binary_dilation(solid, structure=disk(width)) & ~solid
    arr = np.zeros(solid.shape + (4,), np.uint8)
    arr[ring] = INK
    out = Image.fromarray(arr, 'RGBA').filter(ImageFilter.GaussianBlur(0.6))
    out.alpha_composite(layer)
    return out


def clip_to(layer, shape):
    """Keeps only the parts of [layer] that fall inside the opaque parts of [shape]."""
    a = np.asarray(layer).copy()
    keep = np.asarray(shape)[..., 3] > 110
    a[..., 3] = np.where(keep, a[..., 3], 0)
    return Image.fromarray(a, 'RGBA')


def bez(p0, p1, p2, p3, n=28):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return pts


def turned(points, centre, degrees):
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    return [(centre[0] + (x - centre[0]) * c - (y - centre[1]) * s, centre[1] + (x - centre[0]) * s + (y - centre[1]) * c) for x, y in points]


def fluffy(g, cx, cy, r, fill, bumps=9, seed=1):
    """A pompom or fur trim: a circle of overlapping little circles."""
    rnd = random.Random(seed)
    g.ellipse([cx - r * 0.8, cy - r * 0.8, cx + r * 0.8, cy + r * 0.8], fill=fill)
    for i in range(bumps):
        a = 2 * math.pi * i / bumps + rnd.uniform(-0.2, 0.2)
        rr = r * rnd.uniform(0.32, 0.42)
        x, y = cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62
        g.ellipse([x - rr, y - rr, x + rr, y + rr], fill=fill)


def flower(g, cx, cy, r, petal, centre=(255, 196, 60, 255), petals=5, turn=0.0):
    for i in range(petals):
        a = turn + 2 * math.pi * i / petals
        x, y = cx + math.cos(a) * r * 0.55, cy + math.sin(a) * r * 0.55
        rr = r * 0.48
        g.ellipse([x - rr, y - rr, x + rr, y + rr], fill=petal, outline=INK, width=max(2, int(r * 0.09)))
    rr = r * 0.3
    g.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=centre, outline=INK, width=max(2, int(r * 0.09)))


def leaf(g, cx, cy, length, angle, fill=(110, 184, 96, 255)):
    pts = []
    for i in range(24):
        t = i / 23
        pts.append((t * length, math.sin(t * math.pi) * length * 0.32))
    pts += [(x, -y) for x, y in reversed(pts)]
    pts = turned([(cx + x, cy + y) for x, y in pts], (cx, cy), angle)
    g.polygon(pts, fill=fill, outline=INK, width=max(2, int(length * 0.07)))


# ------------------------------------------------------------------ outfits
# Drawn on a see-through canvas the size of a reaction, in sticker pixels (whity_peek.webp is
# 329 x 226: her eyes are at about (111, 147) and (221, 146), the top of her head between the
# ears at about y 85, the ledge at y 192-205).

def C(x, y):
    """Sticker pixels to canvas pixels."""
    return PAD_X + x * S, PAD_TOP + y * S


def Cs(points):
    return [C(x, y) for x, y in points]


def blank():
    return Image.new('RGBA', (CW, CH), (0, 0, 0, 0))


def party_hat():
    shape = blank()
    g = ImageDraw.Draw(shape)
    centre = (168, 90)
    cone = turned([(132, 92), (204, 92), (166, 10)], centre, -8)
    g.polygon(Cs(cone), fill=(255, 214, 90, 255))
    stripes = blank()
    gs = ImageDraw.Draw(stripes)
    for k in range(-6, 8):
        y0 = 92 - k * 16
        band = turned([(100, y0), (240, y0 - 30), (240, y0 - 22), (100, y0 + 8)], centre, -8)
        gs.polygon(Cs(band), fill=(248, 120, 160, 255))
    shape.alpha_composite(clip_to(stripes, shape))
    g = ImageDraw.Draw(shape)
    rnd = random.Random(4)
    for _ in range(9):
        x, y = rnd.uniform(146, 186), rnd.uniform(30, 84)
        px, py = turned([(x, y)], centre, -8)[0]
        if shape.getpixel(tuple(int(v) for v in C(px, py)))[3] > 0:
            rr = 2.6 * S
            cx, cy = C(px, py)
            g.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=WHITE)
    tip = turned([(166, 10)], centre, -8)[0]
    fluffy(g, *C(tip[0], tip[1] - 3), 11 * S, (120, 200, 255, 255), seed=3)
    out = inked(shape, 2.4 * S)
    # A zigzag trim along the bottom.
    trim = blank()
    gt = ImageDraw.Draw(trim)
    pts = []
    for i in range(13):
        x = 132 + i * 6
        pts.append((x, 92 - (5 if i % 2 else 0)))
    gt.line(Cs(turned(pts, centre, -8)), fill=(120, 200, 255, 255), width=int(4 * S), joint='curve')
    out.alpha_composite(trim)
    return out


def santa_hat():
    shape = blank()
    g = ImageDraw.Draw(shape)
    body = [(112, 96)] + bez((112, 96), (112, 44), (150, 22), (196, 24)) + bez((196, 24), (236, 26), (256, 44), (258, 66)) \
        + bez((258, 66), (244, 56), (228, 54), (218, 62)) + bez((218, 62), (220, 74), (226, 86), (228, 96))
    g.polygon(Cs(body), fill=(222, 52, 58, 255))
    shade = blank()
    ImageDraw.Draw(shade).polygon(Cs(bez((150, 96), (196, 70), (214, 46), (246, 44)) + [(260, 70), (240, 100)]), fill=(184, 34, 44, 255))
    shape.alpha_composite(clip_to(shade, shape))
    g = ImageDraw.Draw(shape)
    for i in range(10):
        t = i / 9
        x = 108 + t * 124
        y = 98 - math.sin(t * math.pi) * 9
        rr = (9.5 + (i % 2) * 1.5) * S
        cx, cy = C(x, y)
        g.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=WHITE)
    fluffy(g, *C(258, 72), 13 * S, WHITE, seed=8)
    return inked(shape, 2.4 * S)


def flower_crown():
    out = blank()
    g = ImageDraw.Draw(out)
    path = bez((86, 104), (116, 76), (220, 74), (250, 102), n=40)
    g.line(Cs(path), fill=(98, 150, 80, 255), width=int(3.6 * S), joint='curve')
    for i, t in enumerate([0.1, 0.3, 0.5, 0.7, 0.9]):
        x, y = path[int(t * 40)]
        leaf(g, *C(x, y - 2), 19 * S, angle=-120 + i * 40)
    petals = [(248, 140, 170, 255), (255, 214, 90, 255), (196, 168, 244, 255), (255, 250, 245, 255),
              (255, 168, 120, 255), (248, 140, 170, 255)]
    for i, t in enumerate([0.0, 0.2, 0.4, 0.6, 0.8, 1.0]):
        x, y = path[int(t * 40)]
        r = (17.5 if i in (2, 3) else 15) * S
        flower(g, *C(x, y - 1), r, petals[i], turn=i * 0.6)
    return out


def cool_shades():
    shape = blank()
    g = ImageDraw.Draw(shape)
    centre = (167, 100)

    def lens(cx):
        pts = bez((cx - 26, 88), (cx - 10, 86), (cx + 10, 86), (cx + 26, 88), n=10) \
            + bez((cx + 26, 88), (cx + 27, 104), (cx + 14, 113), (cx, 113), n=12) \
            + bez((cx, 113), (cx - 16, 113), (cx - 27, 104), (cx - 26, 88), n=12)
        return turned(pts, centre, -4)

    for cx in (137, 197):
        g.polygon(Cs(lens(cx)), fill=(34, 30, 44, 255))
    g.line(Cs(turned([(160, 91), (167, 88), (174, 91)], centre, -4)), fill=(34, 30, 44, 255), width=int(4.5 * S), joint='curve')
    g.line(Cs(turned([(111, 90), (98, 92)], centre, -4)), fill=(34, 30, 44, 255), width=int(4 * S))
    g.line(Cs(turned([(223, 90), (236, 92)], centre, -4)), fill=(34, 30, 44, 255), width=int(4 * S))
    out = inked(shape, 1.6 * S)
    shine = blank()
    gs = ImageDraw.Draw(shine)
    for cx in (137, 197):
        gs.line(Cs(turned([(cx - 16, 105), (cx - 4, 92)], centre, -4)), fill=(255, 255, 255, 200), width=int(3.4 * S))
        gs.line(Cs(turned([(cx - 6, 108), (cx + 2, 99)], centre, -4)), fill=(255, 255, 255, 150), width=int(2 * S))
    out.alpha_composite(clip_to(shine, shape))
    return out


def beanie():
    shape = blank()
    g = ImageDraw.Draw(shape)
    dome = [(116, 92)] + bez((116, 92), (114, 46), (146, 32), (168, 32)) + bez((168, 32), (190, 32), (222, 46), (220, 90))
    g.polygon(Cs(dome), fill=(176, 160, 238, 255))
    stripe = blank()
    ImageDraw.Draw(stripe).polygon(Cs([(100, 58), (240, 56), (240, 68), (100, 70)]), fill=(248, 140, 170, 255))
    shape.alpha_composite(clip_to(stripe, shape))
    g = ImageDraw.Draw(shape)
    top = bez((108, 86), (140, 78), (196, 76), (228, 84), n=20)
    bottom = bez((230, 102), (198, 94), (138, 96), (106, 104), n=20)
    g.polygon(Cs(top + bottom), fill=(150, 132, 222, 255))
    fluffy(g, *C(168, 28), 14 * S, (255, 236, 244, 255), seed=5)
    out = inked(shape, 2.4 * S)
    g = ImageDraw.Draw(out)
    for i in range(1, 12):
        t = i / 12
        a, b = top[int(t * 20)], bottom[int((1 - t) * 20)]
        g.line(Cs([(a[0], a[1] + 3), (b[0], b[1] - 3)]), fill=(118, 98, 200, 255), width=int(1.6 * S))
    return out


def bow():
    shape = blank()
    g = ImageDraw.Draw(shape)
    centre = (106, 76)
    left = bez((106, 76), (90, 56), (72, 66), (76, 80), n=14) + bez((76, 80), (78, 94), (94, 92), (106, 76), n=14)
    right = bez((106, 76), (116, 56), (140, 60), (136, 76), n=14) + bez((136, 76), (134, 92), (118, 94), (106, 76), n=14)
    tails = [(104, 78), (94, 102), (102, 100), (106, 82), (112, 100), (120, 104), (110, 78)]
    for part in (tails, left, right):
        g.polygon(Cs(turned(part, centre, -18)), fill=(248, 120, 160, 255))
    dots = blank()
    gd = ImageDraw.Draw(dots)
    for x, y in [(84, 70), (90, 84), (126, 66), (128, 82), (98, 64), (118, 88)]:
        px, py = turned([(x, y)], centre, -18)[0]
        cx, cy = C(px, py)
        gd.ellipse([cx - 2.6 * S, cy - 2.6 * S, cx + 2.6 * S, cy + 2.6 * S], fill=WHITE)
    shape.alpha_composite(clip_to(dots, shape))
    g = ImageDraw.Draw(shape)
    cx, cy = C(*centre)
    g.ellipse([cx - 7 * S, cy - 7 * S, cx + 7 * S, cy + 7 * S], fill=(226, 88, 136, 255))
    return inked(shape, 2.2 * S)


def holi_colours():
    """Gulal on her fur: soft powder colour on the head, ears and cheeks, never over her eyes."""
    fur = blank()
    fur.alpha_composite(base.resize((W, H), Image.LANCZOS), (PAD_X, PAD_TOP))
    a = np.asarray(fur).astype(np.float32)
    whiteish = (a[..., 0] > 236) & (a[..., 1] > 236) & (a[..., 2] > 228)
    keep = (a[..., 3] > 200) & ~whiteish
    yy, xx = np.mgrid[0:CH, 0:CW]
    for cx, cy, r in EYES:
        ex, ey = cx + PAD_X, cy + PAD_TOP
        keep &= np.hypot(xx - ex, yy - ey) > r * 2.2
    nx, ny = to_canvas(*NOSE)
    keep &= np.hypot(xx - nx, yy - ny) > W * 0.09
    keep &= yy < C(0, 186)[1]
    colours = [((236, 48, 146), (150, 104), 15), ((255, 200, 40), (196, 112), 13), ((248, 98, 160), (74, 150), 17),
               ((60, 186, 100), (262, 150), 16), ((70, 130, 236), (78, 70), 13), ((255, 128, 40), (268, 58), 12),
               ((150, 80, 220), (226, 96), 9), ((255, 200, 40), (60, 118), 8), ((60, 186, 100), (122, 96), 8)]
    tint = np.zeros((CH, CW, 3), np.float32)  # colour times coverage
    cover = np.zeros((CH, CW), np.float32)
    rnd = random.Random(12)
    for colour, (x, y), r in colours:
        blob = Image.new('L', (CW, CH), 0)
        gb = ImageDraw.Draw(blob)
        for _ in range(9):
            ox, oy = rnd.uniform(-0.8, 0.8) * r, rnd.uniform(-0.8, 0.8) * r
            rr = rnd.uniform(0.4, 0.75) * r * S
            px, py = C(x + ox, y + oy)
            gb.ellipse([px - rr, py - rr, px + rr, py + rr], fill=150)
        for _ in range(26):
            ox, oy = rnd.gauss(0, 1.0) * r, rnd.gauss(0, 1.0) * r
            rr = rnd.uniform(0.8, 2.0) * S
            px, py = C(x + ox, y + oy)
            gb.ellipse([px - rr, py - rr, px + rr, py + rr], fill=255)
        m = np.asarray(blob.filter(ImageFilter.GaussianBlur(4.0 * S))).astype(np.float32) / 255 * 0.85
        speck = np.asarray(blob.filter(ImageFilter.GaussianBlur(0.8 * S))).astype(np.float32) / 255
        m = np.maximum(m, (speck > 0.9) * 0.55)
        for c in range(3):
            tint[..., c] = tint[..., c] * (1 - m) + colour[c] * m
        cover = cover * (1 - m) + m
    out = np.zeros((CH, CW, 4), np.float32)
    out[..., :3] = tint / np.maximum(cover, 1e-4)[..., None]
    out[..., 3] = cover * keep * 255
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def diya(g, glow, cx, bottom, facing=1):
    """A clay lamp sitting at [bottom], centred on [cx] (sticker pixels), its spout and flame
    on the right ([facing] 1) or left (-1), with a warm glow."""
    def X(dx):
        return cx + dx * facing

    bowl = [(X(-20), bottom - 13)] + bez((X(-20), bottom - 13), (X(-18), bottom + 1), (X(18), bottom + 1), (X(20), bottom - 13), n=20) \
        + [(X(27), bottom - 17), (X(20), bottom - 15)] + bez((X(20), bottom - 15), (X(8), bottom - 19), (X(-8), bottom - 19), (X(-20), bottom - 15), n=12)
    g.polygon(Cs(bowl), fill=(210, 104, 48, 255))
    for i in range(5):
        px, py = C(X(-13 + i * 6.5), bottom - 8 + (1.5 if i % 2 else 0))
        g.ellipse([px - 1.8 * S, py - 1.8 * S, px + 1.8 * S, py + 1.8 * S], fill=(255, 214, 90, 255))
    fx, fy = X(18), bottom - 18
    f = facing
    flame = bez((fx, fy), (fx - 9 * f, fy - 6), (fx - 2 * f, fy - 16), (fx + 1 * f, fy - 24), n=12) \
        + bez((fx + 1 * f, fy - 24), (fx + 6 * f, fy - 14), (fx + 9 * f, fy - 6), (fx, fy), n=12)
    g.polygon(Cs(flame), fill=(255, 150, 30, 255))
    inner = bez((fx, fy - 2), (fx - 4 * f, fy - 6), (fx - 1 * f, fy - 11), (fx + 1 * f, fy - 15), n=8) \
        + bez((fx + 1 * f, fy - 15), (fx + 4 * f, fy - 9), (fx + 4 * f, fy - 5), (fx, fy - 2), n=8)
    g.polygon(Cs(inner), fill=(255, 238, 120, 255))
    gx, gy = C(fx, fy - 10)
    glow.ellipse([gx - 13 * S, gy - 13 * S, gx + 13 * S, gy + 13 * S], fill=(255, 210, 90, 130))


def diwali_diyas():
    """Two lamps on the ground either side of her, flames turned towards her."""
    shape = blank()
    glow = blank()
    g, gg = ImageDraw.Draw(shape), ImageDraw.Draw(glow)
    diya(g, gg, -8, 205, facing=1)
    diya(g, gg, 337, 205, facing=-1)
    out = glow.filter(ImageFilter.GaussianBlur(5 * S))
    out.alpha_composite(inked(shape, 2.0 * S))
    return out


def kite():
    out = blank()
    g = ImageDraw.Draw(out)
    cx, cy = 330, -30
    string = bez((cx - 2, cy + 10), (330, 40), (300, 90), (292, 168), n=40)
    g.line(Cs(string), fill=(90, 70, 60, 255), width=int(1.4 * S), joint='curve')
    shape = blank()
    gs = ImageDraw.Draw(shape)
    top, right, bottom, left = (cx + 6, cy - 36), (cx + 30, cy - 4), (cx - 4, cy + 38), (cx - 26, cy - 10)
    mid = (cx + 2, cy - 4)
    for (a, b), colour in zip([(top, right), (right, bottom), (bottom, left), (left, top)],
                              [(248, 120, 160, 255), (255, 214, 90, 255), (120, 200, 255, 255), (110, 200, 120, 255)]):
        gs.polygon(Cs([mid, a, b]), fill=colour)
    tail = bez(bottom, (cx + 14, cy + 52), (cx - 10, cy + 62), (cx + 6, cy + 80), n=20)
    gs.line(Cs(tail), fill=(248, 120, 160, 255), width=int(2.2 * S), joint='curve')
    for t in (0.35, 0.7):
        x, y = tail[int(t * 20)]
        gs.polygon(Cs([(x, y), (x - 7, y - 5), (x - 7, y + 5)]), fill=(255, 214, 90, 255))
        gs.polygon(Cs([(x, y), (x + 7, y - 5), (x + 7, y + 5)]), fill=(255, 214, 90, 255))
    shape = inked(shape, 1.8 * S)
    gk = ImageDraw.Draw(shape)
    gk.line(Cs([top, bottom]), fill=INK, width=int(1.6 * S))
    gk.line(Cs([left, right]), fill=INK, width=int(1.6 * S))
    out.alpha_composite(shape)
    return out


OUTFITS = [
    ('bow', 'Bow', bow),
    ('party_hat', 'Party hat', party_hat),
    ('flower_crown', 'Flower crown', flower_crown),
    ('cool_shades', 'Cool shades', cool_shades),
    ('beanie', 'Cosy beanie', beanie),
    ('santa_hat', 'Santa hat', santa_hat),
    ('diyas', 'Diwali diyas', diwali_diyas),
    ('holi', 'Holi colours', holi_colours),
    ('kite', 'Kite', kite),
]


# ------------------------------------------------------------------ treats
# Drawn in a box from -1 to 1 (the treat fills about the unit circle) on a big canvas, then made small.

TB = 512


def T(x, y):
    return TB / 2 + x * TB / 2.3, TB / 2 + y * TB / 2.3


def Ts(points):
    return [T(x, y) for x, y in points]


def treat_canvas():
    return Image.new('RGBA', (TB, TB), (0, 0, 0, 0))


def treat_inked(layer, width=13):
    solid = np.asarray(layer)[..., 3] > 110
    ring = ndimage.binary_dilation(solid, structure=disk(width)) & ~solid
    arr = np.zeros(solid.shape + (4,), np.uint8)
    arr[ring] = INK
    out = Image.fromarray(arr, 'RGBA')
    out.alpha_composite(layer)
    return out


def treat_cookie():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    cookie(g, TB / 2, TB / 2, TB / 2.3 * 0.92)
    return im


def treat_momo():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    body = bez((-0.95, 0.5), (-1.0, -0.2), (-0.35, -0.55), (0.0, -0.78)) + bez((0.0, -0.78), (0.35, -0.55), (1.0, -0.2), (0.95, 0.5)) \
        + bez((0.95, 0.5), (0.6, 0.78), (-0.6, 0.78), (-0.95, 0.5))
    g.polygon(Ts(body), fill=(252, 246, 234, 255))
    g.polygon(Ts(bez((-0.9, 0.42), (-0.4, 0.66), (0.4, 0.66), (0.9, 0.42)) + bez((0.95, 0.5), (0.6, 0.78), (-0.6, 0.78), (-0.95, 0.5))),
              fill=(234, 218, 196, 255))
    im = treat_inked(im)
    g = ImageDraw.Draw(im)
    for x in (-0.55, -0.2, 0.2, 0.55):
        g.line(Ts(bez((0.0, -0.74), (x * 0.4, -0.5), (x * 0.9, -0.25), (x, 0.15), n=16)), fill=INK, width=9, joint='curve')
    return im


def treat_ice_cream():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    cone = [(-0.5, -0.02), (0.5, -0.02), (0.0, 1.0)]
    g.polygon(Ts(cone), fill=(236, 182, 104, 255))
    waffle = treat_canvas()
    gw = ImageDraw.Draw(waffle)
    for k in range(-5, 6):
        gw.line(Ts([(k * 0.2 - 0.6, -0.1), (k * 0.2 + 0.6, 1.1)]), fill=(196, 128, 62, 255), width=8)
        gw.line(Ts([(k * 0.2 + 0.6, -0.1), (k * 0.2 - 0.6, 1.1)]), fill=(196, 128, 62, 255), width=8)
    im.alpha_composite(clip_to(waffle, im))
    g = ImageDraw.Draw(im)
    g.ellipse([*T(-0.62, -0.92), *T(0.62, 0.22)], fill=(250, 162, 194, 255))
    for x in (-0.5, -0.2, 0.1, 0.4):
        g.ellipse([*T(x - 0.13, -0.08), *T(x + 0.13, 0.2)], fill=(250, 162, 194, 255))
    g.ellipse([*T(0.02, -1.12), *T(0.3, -0.84)], fill=(226, 40, 60, 255))
    im = treat_inked(im)
    g = ImageDraw.Draw(im)
    rnd = random.Random(2)
    for _ in range(9):
        x, y = rnd.uniform(-0.4, 0.4), rnd.uniform(-0.6, -0.05)
        a = rnd.uniform(0, math.pi)
        colour = rnd.choice([(255, 214, 90, 255), (120, 200, 255, 255), (110, 200, 120, 255), WHITE])
        g.line(Ts([(x - 0.06 * math.cos(a), y - 0.06 * math.sin(a)), (x + 0.06 * math.cos(a), y + 0.06 * math.sin(a))]), fill=colour, width=11)
    return im


def treat_bone():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    pts = Ts(turned([(-0.6, -0.17), (0.6, -0.17), (0.6, 0.17), (-0.6, 0.17)], (0, 0), -28))
    g.polygon(pts, fill=(252, 246, 236, 255))
    for ex in (-0.66, 0.66):
        for ey in (-0.2, 0.2):
            x, y = turned([(ex, ey)], (0, 0), -28)[0]
            g.ellipse([*T(x - 0.27, y - 0.27), *T(x + 0.27, y + 0.27)], fill=(252, 246, 236, 255))
    return treat_inked(im)


def treat_laddoo():
    """A boondi laddoo: a ball of tiny golden pearls (a bumpy edge), with slivers of pistachio."""
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    g.ellipse([*T(-0.8, -0.8), *T(0.8, 0.8)], fill=(250, 176, 44, 255))
    for i in range(22):
        a = 2 * math.pi * i / 22
        x, y = math.cos(a) * 0.8, math.sin(a) * 0.8
        g.ellipse([*T(x - 0.12, y - 0.12), *T(x + 0.12, y + 0.12)], fill=(250, 176, 44, 255))
    im = treat_inked(im, 11)
    g = ImageDraw.Draw(im)
    rnd = random.Random(6)
    for _ in range(90):
        a, rr = rnd.uniform(0, 2 * math.pi), math.sqrt(rnd.random()) * 0.82
        x, y = math.cos(a) * rr, math.sin(a) * rr
        d = 0.075
        g.ellipse([*T(x - d, y - d), *T(x + d, y + d)], fill=rnd.choice([(236, 140, 24, 255), (255, 206, 92, 255), (246, 160, 36, 255)]),
                  outline=(214, 120, 20, 255), width=3)
    g.ellipse([*T(-0.52, -0.6), *T(-0.18, -0.36)], fill=(255, 236, 170, 210))
    for x, y, a in [(-0.1, -0.15, 30), (0.3, 0.1, -40), (0.0, 0.35, 70)]:
        pts = turned([(x - 0.13, y), (x, y - 0.05), (x + 0.13, y), (x, y + 0.05)], (x, y), a)
        g.polygon(Ts(pts), fill=(150, 200, 100, 255), outline=(80, 120, 60, 255))
    return im


def treat_gujiya():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    g.pieslice([*T(-0.95, -0.7), *T(0.95, 1.1)], 180, 360, fill=(240, 194, 112, 255))
    for i in range(11):
        a = math.pi + math.pi * i / 10
        x, y = math.cos(a) * 0.9, 0.2 + math.sin(a) * 0.86
        g.ellipse([*T(x - 0.14, y - 0.14), *T(x + 0.14, y + 0.14)], fill=(226, 160, 80, 255))
    im = treat_inked(im)
    g = ImageDraw.Draw(im)
    for i in range(11):
        a = math.pi + math.pi * i / 10
        x, y = math.cos(a) * 0.9, 0.2 + math.sin(a) * 0.86
        g.arc([*T(x - 0.14, y - 0.14), *T(x + 0.14, y + 0.14)], 200, 340, fill=INK, width=7)
    rnd = random.Random(3)
    for _ in range(8):
        x, y = rnd.uniform(-0.5, 0.5), rnd.uniform(-0.2, 0.1)
        g.ellipse([*T(x - 0.035, y - 0.035), *T(x + 0.035, y + 0.035)], fill=WHITE)
    return im


def treat_cupcake():
    im = treat_canvas()
    g = ImageDraw.Draw(im)
    g.polygon(Ts([(-0.66, 0.1), (0.66, 0.1), (0.5, 0.95), (-0.5, 0.95)]), fill=(248, 140, 170, 255))
    stripes = treat_canvas()
    gs = ImageDraw.Draw(stripes)
    for x in (-0.4, -0.13, 0.13, 0.4):
        gs.line(Ts([(x * 1.2, 0.1), (x, 0.95)]), fill=(226, 96, 138, 255), width=12)
    im.alpha_composite(clip_to(stripes, im))
    g = ImageDraw.Draw(im)
    for (x, y, r) in [(-0.45, 0.02, 0.3), (0.45, 0.02, 0.3), (0.0, 0.0, 0.36), (-0.22, -0.3, 0.3), (0.22, -0.3, 0.3), (0.0, -0.58, 0.26)]:
        g.ellipse([*T(x - r, y - r), *T(x + r, y + r)], fill=(255, 246, 236, 255))
    g.ellipse([*T(-0.12, -1.02), *T(0.16, -0.74)], fill=(226, 40, 60, 255))
    im = treat_inked(im)
    g = ImageDraw.Draw(im)
    g.line(Ts(bez((0.02, -0.88), (0.04, -1.0), (0.12, -1.06), (0.2, -1.08), n=8)), fill=INK, width=8)
    rnd = random.Random(9)
    for _ in range(8):
        x, y = rnd.uniform(-0.45, 0.45), rnd.uniform(-0.5, 0.05)
        a = rnd.uniform(0, math.pi)
        colour = rnd.choice([(255, 214, 90, 255), (120, 200, 255, 255), (248, 120, 160, 255)])
        g.line(Ts([(x - 0.05 * math.cos(a), y - 0.05 * math.sin(a)), (x + 0.05 * math.cos(a), y + 0.05 * math.sin(a))]), fill=colour, width=11)
    return im


TREATS = [
    ('cookie', treat_cookie),
    ('momo', treat_momo),
    ('ice_cream', treat_ice_cream),
    ('bone', treat_bone),
    ('laddoo', treat_laddoo),
    ('gujiya', treat_gujiya),
    ('cupcake', treat_cupcake),
]


# ------------------------------------------------------------------ writing it all out

OW = OUT_WIDTH
OH = round(CH * OW / CW)
PIVOT = (CW / 2, PAD_TOP + H)


def kt_float(v):
    if abs(v) < 5e-5:
        return '0f'
    s = f'{v:.4f}'.rstrip('0')
    if s.endswith('.'):
        s += '0'
    return s + 'f'


def moved(img, m):
    """[img] moved the way the app moves it (for the previews): scale and turn around the chin, then shift."""
    sx, sy, dx, dy, turn = m
    if m == STILL:
        return img
    a = math.radians(turn)
    c, s = math.cos(a), math.sin(a)
    px, py = PIVOT[0] * img.width / CW, PIVOT[1] * img.height / CH
    k = img.width / CW
    # Output point q = P + R(S(p - P)) + d; the inverse maps q back to p.
    qx0, qy0 = -px - dx * k, -py - dy * k
    ia = [c / sx, s / sx, 0, -s / sy, c / sy, 0]
    coeffs = (ia[0], ia[1], ia[0] * qx0 + ia[1] * qy0 + px, ia[3], ia[4], ia[3] * qx0 + ia[4] * qy0 + py)
    return img.transform(img.size, Image.AFFINE, coeffs, resample=Image.BICUBIC)


def with_treat(img, t, treat_img):
    if t is None:
        return img
    x, y, r = t
    k = img.width / CW
    side = max(1, int(round(2.3 * r * k)))
    small = treat_img.resize((side, side), Image.LANCZOS)
    out = img.copy()
    out.alpha_composite(small, (int(round(x * k - side / 2)), int(round(y * k - side / 2))))
    return out


raw_dir.mkdir(parents=True, exist_ok=True)
still_dir.mkdir(parents=True, exist_ok=True)

outfit_imgs = {}
for key, label, make in OUTFITS:
    small = make().resize((OW, OH), Image.LANCZOS)
    small.save(still_dir / f'whity_outfit_{key}.webp', quality=88, method=6)
    outfit_imgs[key] = small
    print('outfit', key, (still_dir / f'whity_outfit_{key}.webp').stat().st_size, 'bytes')

treat_imgs = {}
for key, make in TREATS:
    small = make().resize((TREAT_SIZE, TREAT_SIZE), Image.LANCZOS)
    small.save(still_dir / f'whity_treat_{key}.webp', quality=90, method=6)
    treat_imgs[key] = small
    print('treat', key, (still_dir / f'whity_treat_{key}.webp').stat().st_size, 'bytes')

tracks = {}
treat_path = None
sheet_rows = []
reel = []
label_font = ImageFont.truetype(str(font_path), 18)
show_outfit = outfit_imgs['party_hat']
for key, label, make in REACTIONS:
    frames = make()
    small = [Frame(f.img.resize((OW, OH), Image.LANCZOS), f.ms, f.move, f.treat) for f in frames]
    webp = raw_dir / f'whity_react_{key}.webp'
    small[0].img.save(webp, save_all=True, append_images=[f.img for f in small[1:]], duration=[f.ms for f in small], loop=0, quality=80, method=6)
    small[STILL_FRAME[key]].img.save(still_dir / f'whity_react_{key}_still.webp', quality=86, method=6)
    print(key, len(frames), 'frames', webp.stat().st_size, 'bytes')
    if any(f.move != STILL for f in frames):
        tracks[key] = [(f.ms, f.move[0], f.move[1], f.move[2] / CW, f.move[3] / CH, f.move[4]) for f in frames]
    if key == 'treat':
        treat_path = [(f.ms,) + ((f.treat[0] / CW, f.treat[1] / CH, f.treat[2] / CW) if f.treat else (0.0, 0.0, 0.0)) for f in frames]
    if preview_dir is None:
        continue

    def shown(f):
        img = f.img.copy()
        img.alpha_composite(show_outfit)
        img = with_treat(img, f.treat, treat_imgs['laddoo'])
        return moved(img, f.move)

    picks = [small[int(i * (len(small) - 1) / 4)] for i in range(5)]
    row = Image.new('RGB', (OW * 5 + 220, OH), 'white')
    ImageDraw.Draw(row).text((10, OH // 2 - 12), label, font=label_font, fill=(35, 27, 30))
    for i, f in enumerate(picks):
        row.paste(Image.alpha_composite(Image.new('RGBA', (OW, OH), (255, 255, 255, 255)), shown(f)).convert('RGB'), (220 + OW * i, 0))
    sheet_rows.append(row)
    for f in small + [small[-1]._replace(ms=300)]:
        frame = Image.new('RGB', (OW, OH + 34), 'white')
        frame.paste(Image.alpha_composite(Image.new('RGBA', (OW, OH), (255, 255, 255, 255)), shown(f)).convert('RGB'), (0, 34))
        ImageDraw.Draw(frame).text((10, 6), label, font=label_font, fill=(216, 57, 43))
        reel.append((frame, f.ms))


def kt_rows(rows, indent='        '):
    return '\n'.join(indent + ', '.join(kt_float(v) if i else f'{v}f' for i, v in enumerate(r)) + ',' for r in rows)


names = {'tap': 'TAP', 'pet': 'PET', 'treat': 'TREAT', 'dizzy': 'DIZZY', 'sleepy': 'SLEEPY', 'love': 'LOVE'}
kt = ['// Made by scripts/whity_reactions.py from whity_peek.webp: run it again instead of editing this file.',
      'package com.meyouww.core.designsystem.pet',
      '',
      '/**',
      " * How Whity's whole face moves during each reaction, frame by frame, in step with its pictures",
      ' * (the pictures keep her head still, so the outfit she wears moves with her), and the path her',
      ' * treat takes.',
      ' */',
      'internal object WhityTracks {',
      '    /** The point she squashes and turns around (the middle of her chin on the ledge), as fractions of the picture. */',
      '    const val PIVOT_X = 0.5f',
      f'    const val PIVOT_Y = {kt_float(PIVOT[1] / CH)}',
      '',
      '    /** Numbers per frame in a motion track. */',
      '    const val MOVE_STRIDE = 6',
      '']
for key in ['tap', 'pet', 'treat', 'dizzy', 'sleepy', 'love']:
    if key not in tracks:
        continue
    kt += ['    /**',
           f'     * The {key} reaction, per frame: how long it shows (ms), width and height scale, how far it moves',
           "     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).",
           '     */',
           f'    val {names[key]} = floatArrayOf(',
           kt_rows(tracks[key]),
           '    )',
           '']
kt += ['    /** Numbers per frame in the treat\'s path. */',
       '    const val TREAT_STRIDE = 4',
       '',
       '    /**',
       "     * Per frame of the treat reaction: how long it shows (ms), then the treat's centre across and down",
       "     * and its radius (fractions of the picture's width, height and width); radius 0 hides it.",
       '     */',
       '    val TREAT_PATH = floatArrayOf(',
       kt_rows(treat_path),
       '    )',
       '',
       '    /** The frame the treat reaction\'s still pose shows. */',
       f'    const val TREAT_STILL_FRAME = {STILL_FRAME["treat"]}',
       '}',
       '']
tracks_path.write_text('\n'.join(kt), encoding='utf-8')
print('tracks', tracks_path)

if preview_dir is not None:
    preview_dir.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGB', (sheet_rows[0].width, sum(r.height for r in sheet_rows)), 'white')
    y = 0
    for r in sheet_rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(preview_dir / 'whity_reactions_sheet.png')
    reel[0][0].save(preview_dir / 'whity_reactions_reel.gif', save_all=True, append_images=[f for f, _ in reel[1:]], duration=[ms for _, ms in reel], loop=0, optimize=True)
    watch = Image.open(still_dir / 'whity_react_watch_still.webp').convert('RGBA')
    cols = 3
    rows_n = math.ceil(len(OUTFITS) / cols)
    osheet = Image.new('RGB', (OW * cols, (OH + 30) * rows_n), 'white')
    for i, (key, label, _) in enumerate(OUTFITS):
        img = Image.alpha_composite(Image.new('RGBA', (OW, OH), (226, 240, 252, 255)), watch)
        img.alpha_composite(outfit_imgs[key])
        x, y = (i % cols) * OW, (i // cols) * (OH + 30)
        osheet.paste(img.convert('RGB'), (x, y + 30))
        ImageDraw.Draw(osheet).text((x + 8, y + 5), label, font=label_font, fill=(35, 27, 30))
    osheet.save(preview_dir / 'whity_outfits_sheet.png')
    tsheet = Image.new('RGB', (TREAT_SIZE * len(TREATS), TREAT_SIZE), 'white')
    for i, (key, _) in enumerate(TREATS):
        tsheet.paste(Image.alpha_composite(Image.new('RGBA', (TREAT_SIZE, TREAT_SIZE), (255, 255, 255, 255)), treat_imgs[key]).convert('RGB'), (i * TREAT_SIZE, 0))
    tsheet.save(preview_dir / 'whity_treats_sheet.png')
