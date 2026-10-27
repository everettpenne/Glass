import os
import sys,io,subprocess
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
def mask(n):
    N=n*4; m=Image.new('L',(N,N),0); px=m.load()
    for y in range(N):
        for x in range(N):
            u=abs((x+.5)/N*2-1); v=abs((y+.5)/N*2-1)
            if u**5+v**5<=1: px[x,y]=255
    return m.resize((n,n),Image.LANCZOS)
svg,out=sys.argv[1:3]; n=512
png=subprocess.run(['rsvg-convert','-w',str(n),'-h',str(n),svg],capture_output=True).stdout
im=Image.open(io.BytesIO(png)).convert('RGBA'); o=Image.new('RGBA',(n,n),(0,0,0,0)); o.paste(im,(0,0),mask(n)); o.save(out)
