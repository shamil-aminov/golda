import sys
from PIL import Image, ImageDraw, ImageFont
# python docs/tasks/category-colours/grid.py app/src/test/screenshots/bold docs/tasks/category-colours build
src, out = sys.argv[1], sys.argv[2]
themes = [('golda', 'Golda'), ('marathon', 'Марафон'), ('ultramarine', 'Ультрамарин'), ('plate', 'Плашка')]
prefix = sys.argv[3] if len(sys.argv) > 3 else 'circles'
font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 30)
small = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 24)
W, gap, group, head = 300, 12, 40, 90
for half in ('light', 'dark'):
    # Each theme: Home before, Home after, and Аналитика for the mosaic's colours to compare with.
    h_home = round(W * Image.open(f'{src}/circles_marathon_{half}_before.png').height / Image.open(f'{src}/circles_marathon_{half}_before.png').width)
    gw = 3 * W + 2 * gap
    grid = Image.new('RGB', (len(themes) * gw + (len(themes) + 1) * group, h_home + head + 2 * group), (128, 128, 132))
    d = ImageDraw.Draw(grid)
    for i, (k, label) in enumerate(themes):
        x = group + i * (gw + group); y = group
        d.text((x, y), label, fill=(255, 255, 255), font=font)
        shots = [(f'circles_{k}_{half}_before', 'выкл'), (f'circles_{k}_{half}_after', 'Цветные траты'), (f'circles_{k}_{half}_insights', 'аналитика')]
        for j, (name, cap) in enumerate(shots):
            d.text((x + j * (W + gap), y + 44), cap, fill=(240, 240, 240), font=small)
            im = Image.open(f'{src}/{name}.png').convert('RGB')
            grid.paste(im.resize((W, round(W * im.height / im.width)), Image.LANCZOS), (x + j * (W + gap), y + head))
    grid.save(f'{out}/{prefix}-{half}.png', optimize=True)
