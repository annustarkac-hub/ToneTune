# Regenerates the launcher, monochrome, splash and notification icons.
# Usage: python3 tools/generate_icons.py app/src/main/res
import sys
res = sys.argv[1]
# Five faders; knob heights trace the Tono Pro Signature curve (+3 -2 +1 +4 +3).
xs = [37, 54, 71]
ys = [47, 60, 43]
TOP, BOT = 35, 73
pts = list(zip(xs, ys))

def curve(pts):
    """Smooth wave: horizontal tangent at every knob (like a real EQ response)."""
    d = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        h = (x2 - x1) * 0.55
        d += f" C{x1 + h:.2f},{y1:.2f} {x2 - h:.2f},{y2:.2f} {x2:.2f},{y2:.2f}"
    return d

def curve_cr(pts):
    d = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
    for i in range(len(pts) - 1):
        p0 = pts[i - 1] if i > 0 else pts[i]
        p1, p2 = pts[i], pts[i + 1]
        p3 = pts[i + 2] if i + 2 < len(pts) else p2
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {p2[0]:.2f},{p2[1]:.2f}"
    return d

CURVE = curve(pts)
AREA = CURVE + f" L82.5,{BOT} L25.5,{BOT} Z"
def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r},{r} 0,1 1,{2*r},0a{r},{r} 0,1 1,{-2*r},0z"
def track(x):
    return f"M{x},{TOP}L{x},{BOT}"

def grad_linear(x1, y1, x2, y2, stops, attr="android:fillColor"):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return (f'<aapt:attr name="{attr}"><gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
            f'android:endX="{x2}" android:endY="{y2}">{items}</gradient></aapt:attr>')

HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
        'android:viewportWidth="108" android:viewportHeight="108">\n')
ACCENT = [(0, "#FF3DD6F5"), (1, "#FF8B7CF6")]

def foreground(mono=False):
    out = HEAD
    tcol = "#FFFFFFFF" if mono else "#2EFFFFFF"
    for x in xs:
        out += f'  <path android:pathData="{track(x)}" android:strokeColor="{tcol}" android:strokeWidth="{3.2 if mono else 3}" android:strokeLineCap="round"/>\n'
    if not mono:
        out += f'  <path android:pathData="{CURVE}" android:strokeWidth="8" android:strokeAlpha="0.18" android:strokeLineCap="round" android:strokeLineJoin="round">{grad_linear(37, 0, 71, 0, ACCENT, "android:strokeColor")}</path>\n'
        out += f'  <path android:pathData="{CURVE}" android:strokeWidth="3.6" android:strokeLineCap="round" android:strokeLineJoin="round">{grad_linear(37, 0, 71, 0, ACCENT, "android:strokeColor")}</path>\n'
    else:
        out += f'  <path android:pathData="{CURVE}" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.6" android:strokeLineCap="round" android:strokeLineJoin="round"/>\n'
    for x, y in zip(xs, ys):
        if mono:
            out += f'  <path android:pathData="{circle(x, y, 5.0)}" android:fillColor="#FFFFFFFF"/>\n'
        else:
            out += f'  <path android:pathData="{circle(x, y, 5.9)}" android:fillColor="#FF0F1119"/>\n'
            out += f'  <path android:pathData="{circle(x, y, 4.8)}" android:fillColor="#FFF2F4F8"/>\n'
            out += f'  <path android:pathData="{circle(x, y, 2.1)}">{grad_linear(37, 0, 71, 0, ACCENT)}</path>\n'
    return out + "</vector>\n"

background = HEAD + f'''  <path android:pathData="M0,0h108v108h-108z">{grad_linear(0, 0, 108, 108, [(0, "#FF161A2B"), (1, "#FF07080D")])}</path>
  <path android:pathData="{circle(54, 54, 46)}"><aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="54" android:centerY="50" android:gradientRadius="46"><item android:offset="0" android:color="#333DD6F5"/><item android:offset="0.6" android:color="#108B7CF6"/><item android:offset="1" android:color="#008B7CF6"/></gradient></aapt:attr></path>
</vector>
'''

# Notification icon (24dp, white only): 3 faders + curve.
sx = [6, 12, 18]; sy = [10, 15, 8]
nd = curve(list(zip(sx, sy)))
stat = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n')
for x in sx:
    stat += f'  <path android:pathData="M{x},4L{x},20" android:strokeColor="#80FFFFFF" android:strokeWidth="1.6" android:strokeLineCap="round"/>\n'
stat += f'  <path android:pathData="{nd}" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round"/>\n'
for x, y in zip(sx, sy):
    stat += f'  <path android:pathData="{circle(x, y, 2.6)}" android:fillColor="#FFFFFFFF"/>\n'
stat += "</vector>\n"

open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(foreground())
open(f"{res}/drawable/ic_splash.xml", "w").write(foreground())
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(foreground(mono=True))
open(f"{res}/drawable/ic_launcher_background.xml", "w").write(background)
open(f"{res}/drawable/ic_stat_tono.xml", "w").write(stat)
print("ok")
