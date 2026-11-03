# docs/icon.svg for the README: the launcher coin on its round icon background, from the vector drawable.
import os, re
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, '..', 'app', 'src', 'main', 'res', 'drawable', 'ic_launcher_foreground.xml'), encoding='utf-8').read()

def colour(argb):
    h = argb.lstrip('#')
    a, rgb = (int(h[:2], 16) / 255, h[2:]) if len(h) == 8 else (1.0, h)
    return '#' + rgb, a

defs, shapes = [], []
for n, m in enumerate(re.finditer(r'<path (.*?)(/>|</path>)', src, re.S)):
    body = m.group(0)
    d = re.search(r'pathData="([^"]+)"', body).group(1)
    g = re.search(r'startX="([\d.]+)" android:startY="([\d.]+)" android:endX="([\d.]+)" android:endY="([\d.]+)" android:startColor="([^"]+)" android:endColor="([^"]+)"', body)
    if g:
        (c1, a1), (c2, a2) = colour(g.group(5)), colour(g.group(6))
        defs.append(f'<linearGradient id="g{n}" gradientUnits="userSpaceOnUse" x1="{g.group(1)}" y1="{g.group(2)}" x2="{g.group(3)}" y2="{g.group(4)}">'
                    f'<stop offset="0" stop-color="{c1}" stop-opacity="{a1:.2f}"/><stop offset="1" stop-color="{c2}" stop-opacity="{a2:.2f}"/></linearGradient>')
        shapes.append(f'<path d="{d}" fill="url(#g{n})"/>')
    else:
        c, a = colour(re.search(r'fillColor="([^"]+)"', body).group(1))
        shapes.append(f'<path d="{d}" fill="{c}" fill-opacity="{a:.2f}"/>')
svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="216" height="216" viewBox="0 0 108 108"><defs>'
       '<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath>' + ''.join(defs) + '</defs>'
       '<g clip-path="url(#m)"><rect width="108" height="108" fill="#F4F4F5"/>' + ''.join(shapes) + '</g></svg>\n')
open(os.path.join(HERE, '..', 'docs', 'icon.svg'), 'w', encoding='utf-8', newline='\n').write(svg)
