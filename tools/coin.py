import math, sys
C = 54.0
R = 26.0                       # decagon circumradius, well inside the adaptive icon's safe zone
# Flat top edge, like the reference coin.
V = [(C + R*math.cos(math.radians(-90 + 18 + 36*k)), C + R*math.sin(math.radians(-90 + 18 + 36*k))) for k in range(10)]
# The raised, tilted "table" in the middle, with uneven heights so the facets catch light differently.
Q = [(C-11, C-14, 11.0), (C+11, C-9.5, 8.0), (C+8, C+11, 4.5), (C-13, C+7, 9.0)]
def ang(p): return math.atan2(p[1]-C, p[0]-C)
faces = []   # (points3d)
assign = []
for i in range(10):
    a, b = V[i], V[(i+1) % 10]
    mid = ((a[0]+b[0])/2, (a[1]+b[1])/2)
    q = min(range(4), key=lambda j: (Q[j][0]-mid[0])**2 + (Q[j][1]-mid[1])**2)
    assign.append(q)
# Fans from these table corners are cut into triangles (the bottom-right one, by the owner's eye).
SPLIT = {2}

# One facet per run of rim edges that lean on the same table corner, so the cut stays simple:
# the table, four fans from its corners to the rim, and a triangle where two fans meet.
start = next(i for i in range(10) if assign[i] != assign[i-1])
i = start
while True:
    q = assign[i]
    run = [i]
    while assign[(run[-1]+1) % 10] == q and (run[-1]+1) % 10 != start:
        run.append((run[-1]+1) % 10)
    edge = [V[k] for k in run] + [V[(run[-1]+1) % 10]]
    if q in SPLIT:
        # Cut this fan into one triangle per rim edge, so the corner catches the light in steps.
        for a, b in zip(edge, edge[1:]):
            faces.append([Q[q], (a[0], a[1], 0.0), (b[0], b[1], 0.0)])
    else:
        faces.append([Q[q]] + [(x, y, 0.0) for x, y in edge])
    nxt = (run[-1]+1) % 10
    faces.append([(edge[-1][0], edge[-1][1], 0.0), Q[assign[nxt]], Q[q]])
    i = nxt
    if i == start:
        break
faces.append(list(Q))
print('faces', len(faces), 'assign', assign, file=sys.stderr)

L = (-0.42, -0.5, 0.76)
n = math.sqrt(sum(x*x for x in L)); L = tuple(x/n for x in L)
H = (L[0], L[1], L[2] + 1); n = math.sqrt(sum(x*x for x in H)); H = tuple(x/n for x in H)
def normal(f):
    # Newell's method: a fair normal for a polygon whose corners are not quite in one plane.
    nn = [0.0, 0.0, 0.0]
    for k in range(len(f)):
        p, q = f[k], f[(k+1) % len(f)]
        nn[0] += (p[1]-q[1])*(p[2]+q[2]); nn[1] += (p[2]-q[2])*(p[0]+q[0]); nn[2] += (p[0]-q[0])*(p[1]+q[1])
    if nn[2] < 0: nn = [-x for x in nn]
    m = math.sqrt(sum(x*x for x in nn)); return [x/m for x in nn]
def shade(f):
    N = normal(f)
    lam = max(0, sum(N[i]*L[i] for i in range(3)))
    spec = max(0, sum(N[i]*H[i] for i in range(3))) ** 24
    return min(1.0, max(0.0, -0.1 + 1.15*lam**1.8 + 0.6*spec)), N
RAMP = [(0.0, (0x3A, 0x22, 0x02)), (0.3, (0x8E, 0x5E, 0x10)), (0.55, (0xD9, 0xA9, 0x3E)), (0.8, (0xF6, 0xD8, 0x83)), (1.0, (0xFF, 0xF8, 0xE1))]
def gold(t):
    t = max(0, min(1, t))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0)
            return '#%02X%02X%02X' % tuple(round(c0[i] + (c1[i]-c0[i])*k) for i in range(3))
SOFT = 0.6   # 1 is the hard cut; lower is softer

def path(f): return 'M' + ' L'.join(f'{p[0]:.2f},{p[1]:.2f}' for p in f) + ' Z'
fg, mono, svg = [], [], []
for f in faces:
    b, N = shade(f)
    # Soft cut: pull every facet towards the middle gold, so neighbours differ by less.
    b = 0.55 + (b - 0.55) * SOFT
    xs = [p[0] for p in f]; ys = [p[1] for p in f]
    cx, cy = sum(xs)/len(xs), sum(ys)/len(ys)
    # Sheen across the facet along the light's sweep: brighter towards the light.
    ext = max(max(xs)-min(xs), max(ys)-min(ys)) / 2
    sx, sy = cx + L[0]*ext, cy + L[1]*ext
    ex, ey = cx - L[0]*ext, cy - L[1]*ext
    # Only facets turned to the light shine across; those turned away stay an even gold, so the
    # shaded side of the coin reads as one piece rather than glowing at its seams.
    sheen = 0.14 if b > 0.6 else 0.03
    c1, c2 = gold(b + sheen), gold(b - sheen)
    d = path(f)
    fg.append(f'''    <path android:pathData="{d}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{sx:.2f}" android:startY="{sy:.2f}" android:endX="{ex:.2f}" android:endY="{ey:.2f}" android:startColor="#FF{c1[1:]}" android:endColor="#FF{c2[1:]}" />
        </aapt:attr>
    </path>''')
    gid = f'g{len(svg)}'
    svg.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{sx:.2f}" y1="{sy:.2f}" x2="{ex:.2f}" y2="{ey:.2f}"><stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient><path d="{d}" fill="url(#{gid})"/>')
    mono.append(f'    <path android:fillColor="#FFFFFFFF" android:fillAlpha="{0.45 + 0.55*b:.2f}" android:pathData="{d}" />')
outline = path([(x, y, 0) for x, y in V])
# A plain gold coin under the facets, so their anti-aliased seams blend into gold instead of showing
# the background as thin light lines.
base = f'    <path android:fillColor="{gold(0.5)}" android:pathData="{outline}" />'
head = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Golda's coin: a faceted ten-sided gold coin, lit from the top left. Generated by coin.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''
open('ic_launcher_foreground.xml', 'w', newline='\n').write(head + base + '\n' + '\n'.join(fg) + '\n</vector>\n')
open('ic_launcher_monochrome.xml', 'w', newline='\n').write(head.replace('xmlns:aapt="http://schemas.android.com/aapt"\n    ', '').replace("lit from the top left. Generated", "in one colour, facets by opacity. Generated") + '\n'.join(mono) + '\n</vector>\n')
def page(bg, scale):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{108*scale}" height="{108*scale}" viewBox="0 0 108 108"><defs><clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath></defs><g clip-path="url(#m)"><rect width="108" height="108" fill="{bg}"/><path d="{outline}" fill="{gold(0.5)}"/>{"".join(svg)}</g></svg>'

