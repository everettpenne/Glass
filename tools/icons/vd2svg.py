import sys,re,xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
AA='{http://schemas.android.com/aapt}'
def col(c,alpha=None):
    c=c.lstrip('#')
    if len(c)==8: a=int(c[:2],16)/255; c=c[2:]
    else: a=1
    if alpha is not None: a*=float(alpha)
    return '#'+c,a
def conv(path,idp):
    root=ET.parse(path).getroot()
    vw=float(root.get(A+'viewportWidth',108)); vh=float(root.get(A+'viewportHeight',108))
    defs=[];body=[];n=0
    def grad(g):
        nonlocal n; n+=1; gid=f'{idp}g{n}'
        stops=''.join(f'<stop offset="{i.get(A+"offset")}" stop-color="{col(i.get(A+"color"))[0]}" stop-opacity="{col(i.get(A+"color"))[1]}"/>' for i in g.findall('item'))
        if g.get(A+'type')=='radial':
            defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A+"centerX")}" cy="{g.get(A+"centerY")}" r="{g.get(A+"gradientRadius")}">{stops}</radialGradient>')
        else:
            x1,y1,x2,y2=[g.get(A+k,'0') for k in('startX','startY','endX','endY')]
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>')
        return f'url(#{gid})'
    def walk(el):
        for ch in el:
            if ch.tag=='group':
                tx=ch.get(A+'translateX','0'); ty=ch.get(A+'translateY','0'); sx=ch.get(A+'scaleX','1'); sy=ch.get(A+'scaleY','1')
                px=ch.get(A+'pivotX','0'); py=ch.get(A+'pivotY','0')
                body.append(f'<g transform="translate({tx} {ty}) translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})">'); walk(ch); body.append('</g>')
            elif ch.tag=='path': pth(ch)
    def pth(p):
        d=p.get(A+'pathData')
        fill='none';fo=1;stroke='none';so=1
        f=p.get(A+'fillColor')
        if f: fill,fo=col(f,p.get(A+'fillAlpha'))
        sw=p.get(A+'strokeWidth','0')
        s=p.get(A+'strokeColor')
        if s: stroke,so=col(s,p.get(A+'strokeAlpha'))
        for at in p.findall(AA+'attr'):
            g=at.find('gradient')
            if g is None: continue
            if at.get('name')=='android:fillColor': fill=grad(g)
            if at.get('name')=='android:strokeColor': stroke=grad(g)
        fr='evenodd' if p.get(A+'fillType')=='evenOdd' else 'nonzero'
        body.append(f'<path d="{d}" fill="{fill}" fill-opacity="{fo}" fill-rule="{fr}" stroke="{stroke}" stroke-opacity="{so}" stroke-width="{sw}" stroke-linecap="{p.get(A+"strokeLineCap","butt")}" stroke-linejoin="{p.get(A+"strokeLineJoin","miter")}"/>')
    walk(root)
    return vw,vh,''.join(defs),''.join(body)
if __name__=="__main__":
    bg,fg,out=sys.argv[1:4]
    vw,vh,d1,b1=conv(bg,'b') if bg!='-' else (108,108,'','')
    _,_,d2,b2=conv(fg,'f')
    # adaptive: 108 viewport, visible 72dp center -> mask squircle on 66..: render full 108 then crop 18..90
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="18 18 72 72"><defs>{d1}{d2}</defs>{b1}{b2}</svg>'
    open(out,'w').write(svg)
