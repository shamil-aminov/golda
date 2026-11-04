import sys
from PIL import Image, ImageDraw, ImageFont
src, out = sys.argv[1], sys.argv[2]
prefix = sys.argv[3] if len(sys.argv) > 3 else 'grid'
names=[('marathon','Марафон'),('ultramarine','Ультрамарин'),('signal','Сигнал'),('pixel','Пиксель'),
       ('hologram','Голограмма'),('terminal','Терминал'),('violet','Фиолет'),('plate','Плашка')]
try: font=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',30)
except: font=ImageFont.load_default()
for screen in ('home','insights'):
    W=300; gap=12; pairgap=36; head=50
    im0=Image.open(f'{src}/marathon_light_{screen}.png'); H=round(W*im0.height/im0.width)
    cols=4; pw=2*W+gap
    grid=Image.new('RGB',(cols*pw+(cols+1)*pairgap, 2*(H+head)+3*pairgap),(32,32,36))
    d=ImageDraw.Draw(grid)
    for i,(k,label) in enumerate(names):
        r,c=divmod(i,cols); x=pairgap+c*(pw+pairgap); y=pairgap+r*(H+head+pairgap)
        d.text((x,y+5),f'{i+1}. {label}',fill=(240,240,240),font=font)
        for j,side in enumerate(('light','dark')):
            grid.paste(Image.open(f'{src}/{k}_{side}_{screen}.png').convert('RGB').resize((W,H),Image.LANCZOS),(x+j*(W+gap),y+head))
    grid.save(f'{out}/{prefix}-{screen}.png',optimize=True)
    print(grid.size)

# OLED: the dark half as it is beside the same on a page of pure black.
oled=[('marathon','Марафон'),('ultramarine','Ультрамарин'),('pixel','Пиксель')]
small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',24) if font!=ImageFont.load_default() else font
for screen in ('home','insights'):
    W=300; gap=12; pairgap=36; head=80
    im0=Image.open(f'{src}/marathon_dark_{screen}.png'); H=round(W*im0.height/im0.width); pw=2*W+gap
    grid=Image.new('RGB',(3*pw+4*pairgap, H+head+2*pairgap),(128,128,132))
    d=ImageDraw.Draw(grid)
    for i,(k,label) in enumerate(oled):
        x=pairgap+i*(pw+pairgap); y=pairgap
        d.text((x,y),label,fill=(255,255,255),font=font)
        for j,(side,cap) in enumerate((('dark','сейчас #121212'),('oled','OLED #000000'))):
            d.text((x+j*(W+gap),y+42),cap,fill=(240,240,240),font=small)
            grid.paste(Image.open(f'{src}/{k}_{side}_{screen}.png').convert('RGB').resize((W,H),Image.LANCZOS),(x+j*(W+gap),y+head))
    grid.save(f'{out}/oled-{screen}.png',optimize=True)

# The Theme page's switch: off and on.
if __import__('os').path.exists(f'{src}/theme_oled_off.png'):
    W=420; gap=24; head=60
    im0=Image.open(f'{src}/theme_oled_off.png'); H=round(W*im0.height/im0.width)
    grid=Image.new('RGB',(2*W+3*gap, H+head+2*gap),(128,128,132)); d=ImageDraw.Draw(grid)
    for j,(side,cap) in enumerate((('off','Выкл: #121212'),('on','Вкл: #000000'))):
        d.text((gap+j*(W+gap),gap),cap,fill=(255,255,255),font=font)
        grid.paste(Image.open(f'{src}/theme_oled_{side}.png').convert('RGB').resize((W,H),Image.LANCZOS),(gap+j*(W+gap),gap+head))
    grid.save(f'{out}/oled-toggle.png',optimize=True)
