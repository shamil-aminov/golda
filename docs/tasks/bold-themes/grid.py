import sys
from PIL import Image, ImageDraw, ImageFont
src, out = sys.argv[1], sys.argv[2]
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
    grid.save(f'{out}/grid-{screen}.png',optimize=True)
    print(grid.size)
