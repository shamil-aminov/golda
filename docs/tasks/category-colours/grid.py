import sys
from PIL import Image, ImageDraw, ImageFont
# python docs/tasks/category-colours/grid.py app/src/test/screenshots/bold docs/tasks/category-colours
src, out = sys.argv[1], sys.argv[2]
themes = [('marathon', 'Марафон'), ('ultramarine', 'Ультрамарин'), ('plate', 'Плашка')]
font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 30)
small = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 24)
W, gap, group, head = 300, 12, 40, 90
for half in ('light', 'dark'):
    # Each theme: Home before, Home after, and Аналитика for the mosaic's colours to compare with.
    h_home = round(W * Image.open(f'{src}/circles_marathon_{half}_before.png').height / Image.open(f'{src}/circles_marathon_{half}_before.png').width)
    gw = 3 * W + 2 * gap
    grid = Image.new('RGB', (3 * gw + 4 * group, h_home + head + 2 * group), (128, 128, 132))
    d = ImageDraw.Draw(grid)
    for i, (k, label) in enumerate(themes):
        x = group + i * (gw + group); y = group
        d.text((x, y), label, fill=(255, 255, 255), font=font)
        shots = [(f'circles_{k}_{half}_before', 'сейчас'), (f'circles_{k}_{half}_after', 'цвет категории'), (f'{k}_{half}_insights', 'аналитика')]
        for j, (name, cap) in enumerate(shots):
            d.text((x + j * (W + gap), y + 44), cap, fill=(240, 240, 240), font=small)
            im = Image.open(f'{src}/{name}.png').convert('RGB')
            grid.paste(im.resize((W, round(W * im.height / im.width)), Image.LANCZOS), (x + j * (W + gap), y + head))
    grid.save(f'{out}/circles-{half}.png', optimize=True)
