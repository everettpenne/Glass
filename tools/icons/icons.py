import sys, os; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from mk import *
OUT=os.environ.get('ICON_OUT', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen'))
WHITE=lin(54,24,54,86,[(0,'FFFFFFFF'),(1,'FFD9D4F2')])

def glyph(mode,al,fills,strokes,shade='10083F'):
    """fills: list of (d, gradient_or_None, even); strokes: list of (d, width, alpha). mode shadow|body."""
    o=''
    for d,g,ev in fills:
        if mode=='shadow': o+=P(d,fill=shade,fa=al,even=ev)
        else: o+=P(d,grad=g or WHITE,fill='FFFFFF',even=ev)
    for d,w,a in strokes:
        if mode=='shadow': o+=P(d,stroke=shade,sw=w,sa=al*a)
        else: o+=P(d,sgrad=lin(54,24,54,86,[(0,'FFFFFFFF'),(1,'FFDCD6FF')]),sw=w,sa=a)
    return o
def full(fills,strokes,shade,depth=1):
    o=''
    for ty,al in ((3.4*depth,.10),(2.3*depth,.14),(1.3*depth,.20)):
        o+=G(glyph('shadow',al,fills,strokes,shade),ty=ty)
    o+=glyph('body',1,fills,strokes,shade)
    return o
def edge(fills):
    # a light line along the top of each solid shape
    o=''
    for d,g,ev in fills:
        o+=P(d,sgrad=lin(54,24,54,86,[(0,'CCFFFFFF'),(0.35,'00FFFFFF')]),sw=0.8,sa=1,even=ev) if False else ''
    return o
def mono(fills,strokes):
    o=''
    for d,g,ev in fills: o+=P(d,fill='000000',even=ev)
    for d,w,a in strokes: o+=P(d,stroke='000000',sw=w,sa=max(a,.6))
    return vec(o)

# ---- Thumper
th_fills=[('M50.2,42.5H57.8L56.8,64.5L54,75L51.2,64.5Z',None,False),
          ('M48.5,38.2h11a1.6,1.6 0 0 1 1.6,1.6v1.4h-14.2v-1.4a1.6,1.6 0 0 1 1.6,-1.6z',None,False),
          ('M45.5,28.5h17a4.2,4.2 0 0 1 4,4v0a4,4 0 0 1 -4,4h-15a4,4 0 0 1 -4,-4v0a4,4 0 0 1 4,-4z',None,False)]
th_strokes=[('M44,75C44,72.9 48.5,71.2 54,71.2C59.5,71.2 64,72.9 64,75C64,77.1 59.5,78.8 54,78.8C48.5,78.8 44,77.1 44,75Z',3.4,1.0),
            ('M36,75C36,71.2 44.1,68.1 54,68.1C63.9,68.1 72,71.2 72,75C72,78.8 63.9,81.9 54,81.9C44.1,81.9 36,78.8 36,75Z',3.0,.72),
            ('M28,75C28,69.5 39.6,65.1 54,65.1C68.4,65.1 80,69.5 80,75C80,80.5 68.4,84.9 54,84.9C39.6,84.9 28,80.5 28,75Z',2.6,.46)]
write('thumper','drawable/ic_launcher_background.xml',background('9C86FF','4B33D6'),OUT+'/thumper')
write('thumper','drawable/ic_launcher_foreground.xml',vec(G(full(th_fills,th_strokes,'1A0E66'),sc=.9,ty=-3)),OUT+'/thumper')
write('thumper','drawable/ic_launcher_monochrome.xml',mono(th_fills,th_strokes),OUT+'/thumper')

# ---- Spice
eye='M26.67,61.46C26.19,49.41 36.45,37.94 50.76,37.80C65.34,37.66 79.65,46.58 81.67,58.99C81.94,62.91 80.73,66.15 78.30,68.96C70.51,68.51 64.95,62.40 56.47,63.73C46.84,65.25 38.48,56.53 26.67,61.46ZM44.01,51.84C44.01,44.98 48.48,39.42 54.00,39.42C59.52,39.42 63.99,44.98 63.99,51.84C63.99,58.70 59.52,64.26 54.00,64.26C48.48,64.26 44.01,58.70 44.01,51.84Z'
dune='M26.67,61.46C38.48,56.53 46.84,65.25 56.46,63.73C66.09,62.21 71.96,70.31 81.59,68.79'
sp_fills=[(eye,None,True)]
o=''
for ty,al in ((3.4,.10),(2.3,.14),(1.3,.20)): o+=G(P(eye,fill='1A0B55',fa=al,even=True)+P(dune,stroke='1A0B55',sw=3.2,sa=al),ty=ty)
o+=P(eye,grad=lin(54,36,54,70,[(0,'FFFFFFFF'),(1,'FFD9ECFF')]),fill='FFFFFF',even=True)
o+=P(dune,sgrad=lin(26,60,82,70,[(0,'FFFFB347'),(0.5,'FFFF7A33'),(1,'FFFFB347')]),sw=3.2)
write('spice','drawable/ic_launcher_background.xml',background('19B4F2','7A45C4'),OUT+'/spice')
write('spice','drawable/ic_launcher_foreground.xml',vec(G(o,sc=1.04,ty=-1)),OUT+'/spice')
write('spice','drawable/ic_launcher_monochrome.xml',vec(P(eye,fill='000000',even=True)+P(dune,stroke='000000',sw=3.2)),OUT+'/spice')

# ---- Sietch
folder='M21,50C21,43 26,37 33,37H46L51,43H85C90,43 94,47 94,52V80C94,86 90,90 84,90H33C26,90 21,86 21,80Z'
arch='M48,90L48,73C48,60 66,60 66,73L66,90Z'
m1='M74,31a5.4,5.4 0 1 0 0,-10.8a5.4,5.4 0 1 0 0,10.8z'; m2='M84,25a3.4,3.4 0 1 0 0,-6.8a3.4,3.4 0 1 0 0,6.8z'
sc=.86; pv=(54,58)
inner=''
for ty,al in ((3.4,.10),(2.3,.14),(1.3,.20)): inner+=G(P(folder,fill='04364D',fa=al),ty=ty)
inner+=P(folder,grad=lin(54,37,54,90,[(0,'FFFFFFFF'),(1,'FFCDEFF7')]),fill='FFFFFF')
inner+=P(folder,sgrad=lin(54,37,54,90,[(0,'CCFFFFFF'),(0.4,'00FFFFFF')]),sw=0.9)
inner+=P(arch,grad=lin(57,60,57,90,[(0,'FF0F6C8C'),(1,'FF07475E')]),fill='0F6C8C')
inner+=P(m1,fill='FFFFFF',fa=.95)+P(m2,fill='E4F8FD',fa=.9)
write('sietch','drawable/ic_launcher_background.xml',background('41D9EE','0A78A8'),OUT+'/sietch')
write('sietch','drawable/ic_launcher_foreground.xml',vec(G(inner,sc=.76,pivot=pv,ty=-1)),OUT+'/sietch')
write('sietch','drawable/ic_launcher_monochrome.xml',vec(G(P(folder,fill='000000')+P(m1,fill='000000')+P(m2,fill='000000'),sc=.76,pivot=pv,ty=-1)),OUT+'/sietch')

# ---- Baliset
arm1='M40,28 C33,45 33,61 42,76'; arm2='M68,28 C75,45 75,61 66,76'; bar='M38,28 L70,28'; base='M42,76 C47,82 61,82 66,76'
strs=['M46,31 L46,75','M51,31 L51,77','M56,31 L56,77','M61,31 L61,75']
o=''
for ty,al in ((3.4,.10),(2.3,.14),(1.3,.22)):
    s=''.join(P(d,stroke='3A1604',sw=5,sa=al) for d in (arm1,arm2,bar,base))+''.join(P(d,stroke='3A1604',sw=1.6,sa=al) for d in strs)
    o+=G(s,ty=ty)
cream=lin(54,26,54,84,[(0,'FFFFF6E4'),(1,'FFF6D49A')])
o+=''.join(P(d,sgrad=cream,sw=5) for d in (arm1,arm2,bar,base))+''.join(P(d,stroke='FFFFFFFF',sw=1.6,sa=.85) for d in strs)
write('baliset','drawable/ic_launcher_background.xml',background('FFC470','9A4A1C'),OUT+'/baliset')
write('baliset','drawable/ic_launcher_foreground.xml',vec(G(o,sc=.92)),OUT+'/baliset')
write('baliset','drawable/ic_launcher_monochrome.xml',vec(''.join(P(d,stroke='000000',sw=5) for d in (arm1,arm2,bar,base))+''.join(P(d,stroke='000000',sw=1.8) for d in strs)),OUT+'/baliset')

# ---- Uno: the island over a grid of icons
pill='M43.5,27h21a4.5,4.5 0 0 1 0,9h-21a4.5,4.5 0 0 1 0,-9z'
tiles=[f'M{x+4.4},{y}h{9.2}a4.4,4.4 0 0 1 4.4,4.4v{9.2}a4.4,4.4 0 0 1 -4.4,4.4h-{9.2}a4.4,4.4 0 0 1 -4.4,-4.4v-{9.2}a4.4,4.4 0 0 1 4.4,-4.4z' for x,y in ((31,44),(55,44),(31,67),(55,67))]
tiles=[f'M{x+4.6},{y}h{12.8}a4.6,4.6 0 0 1 4.6,4.6v{12.8}a4.6,4.6 0 0 1 -4.6,4.6h-{12.8}a4.6,4.6 0 0 1 -4.6,-4.6v-{12.8}a4.6,4.6 0 0 1 4.6,-4.6z' for x,y in ((32,43),(56,43),(32,66),(56,66))]
o=''
for ty,al in ((3.4,.10),(2.3,.14),(1.3,.20)):
    o+=G(''.join(P(t,fill='06222F',fa=al) for t in tiles)+P(pill,fill='06222F',fa=al),ty=ty)
for i,t in enumerate(tiles):
    o+=P(t,grad=lin(54,43,54,89,[(0,'FFFFFFFF'),(1,'FFD5E6EE')]),fill='FFFFFF',fa=1 if i==0 else .92)
o+=P(pill,fill='0A141A')
o+=P(pill,sgrad=lin(54,27,54,36,[(0,'55FFFFFF'),(1,'00FFFFFF')]),sw=.8)
write('uno','drawable/ic_launcher_background.xml',background('5FB4D6','1C5272'),OUT+'/uno')
write('uno','drawable/ic_launcher_foreground.xml',vec(G(o,sc=.86,ty=-2)),OUT+'/uno')
write('uno','drawable/ic_launcher_monochrome.xml',vec(G(''.join(P(t,fill='000000') for t in tiles)+P(pill,fill='000000'),sc=.86,ty=-2)),OUT+'/uno')
print('written')

# ---- Seekur (the new default; the original sandworm stays as the "Original" icon choice)
import re
src=open(os.path.expanduser('~/Code/DuneDdit')+'/app/src/main/res/drawable/ic_launcher_foreground.xml').read()
paths=re.findall(r'<path android:fillColor="#(\w+)"(?: android:fillType="(\w+)")? android:pathData="([^"]+)"',src)
worm=[(c,ev,d) for c,ev,d in paths if c not in('FF4A3214','FF6B4A1C')]
sh=''
for ty,al in ((3.4,.10),(2.3,.14),(1.3,.20)):
    sh+=G(''.join(P(d,fill='4A1500',fa=al,even=bool(ev)) for c,ev,d in worm),ty=ty)
cm={'FFFF9F0A':'FFFFFFFF','FFE88A00':'FFFFD9A0','FFFFC266':'FF5A1C00','FFFFF1DC':'FFFFFFFF'}
body=''
for c,ev,d in worm:
    cc='FF'+c[-6:] if len(c)==6 else c
    key={'FF9F0A':'FFFF9F0A','E88A00':'FFE88A00','FFC266':'FFFFC266','FFF1DC':'FFFFF1DC'}.get(c,c)
    body+=P(d,fill=cm.get(key,key)[2:],even=bool(ev))
moon1='M58,60a6,6 0 1 1,0 12a6,6 0 1 1,0 -12z'; moon2='M69,71a3,3 0 1 1,0 6a3,3 0 1 1,0 -6z'
body+=P(moon1,fill='FFF3D6',fa=.95)+P(moon2,fill='FFE0A8',fa=.95)
dune='M0,83C20,75 38,81 56,78C76,75 92,81 108,76L108,108L0,108Z'
body+=P(dune,grad=lin(54,76,54,108,[(0,'FFD9560C'),(1,'FFA83A06')]),fill='D9560C')
body+=P(dune,sgrad=lin(54,74,54,90,[(0,'88FFFFFF'),(1,'00FFFFFF')]),sw=0.9)
write('seekur','drawable/ic_launcher_background.xml',background('FFB347','E0560C'),OUT+'/seekur')
write('seekur','drawable/ic_launcher_foreground.xml',vec(G(sh,sc=.98,ty=-2)+G(body,sc=.98,ty=-2)),OUT+'/seekur')
write('seekur','drawable/ic_launcher_monochrome.xml',open(os.path.expanduser('~/Code/DuneDdit')+'/app/src/main/res/drawable/ic_launcher_monochrome.xml').read(),OUT+'/seekur')
