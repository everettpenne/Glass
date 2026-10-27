import os
import subprocess,sys,io,math
from PIL import Image,ImageDraw,ImageFont
def mask(n):
    m=Image.new('L',(n*4,n*4),0); px=m.load(); N=n*4
    for y in range(N):
        for x in range(N):
            u=abs((x+.5)/N*2-1); v=abs((y+.5)/N*2-1)
            if u**5+v**5<=1: px[x,y]=255
    return m.resize((n,n),Image.LANCZOS)
def render(svg,n=256):
    png=subprocess.run(['rsvg-convert','-w',str(n),'-h',str(n),svg],capture_output=True).stdout
    im=Image.open(io.BytesIO(png)).convert('RGBA'); out=Image.new('RGBA',(n,n),(0,0,0,0)); out.paste(im,(0,0),mask(n)); return out
names=sys.argv[2:]; n=256
sheet=Image.new('RGB',(len(names)*(n+20)+20,n+60),(235,235,240))
d=ImageDraw.Draw(sheet)
for i,nm in enumerate(names):
    im=render(nm,n); sheet.paste(im,(20+i*(n+20),20),im); d.text((20+i*(n+20),n+30),nm.split('/')[-1][:-4],fill=(0,0,0))
sheet.save(sys.argv[1])
