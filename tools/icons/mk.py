import os,sys
HDR='<?xml version="1.0" encoding="utf-8"?>\n'
def vec(body,comment=''):
    c=f'<!-- {comment} -->\n' if comment else ''
    return HDR+c+'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'+body+'</vector>\n'
def lin(x1,y1,x2,y2,stops):
    it=''.join(f'<item android:offset="{o}" android:color="#{c}"/>' for o,c in stops)
    return f'<gradient android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}" android:type="linear">{it}</gradient>'
def rad(cx,cy,r,stops):
    it=''.join(f'<item android:offset="{o}" android:color="#{c}"/>' for o,c in stops)
    return f'<gradient android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}" android:type="radial">{it}</gradient>'
def P(d,fill=None,grad=None,fa=None,stroke=None,sgrad=None,sw=None,sa=None,cap='round',join='round',even=False):
    a=f'<path android:pathData="{d}"'
    if even: a+=' android:fillType="evenOdd"'
    if fill and not grad: a+=f' android:fillColor="#{fill}"'
    if fa is not None: a+=f' android:fillAlpha="{fa}"'
    if stroke and not sgrad: a+=f' android:strokeColor="#{stroke}"'
    if sw: a+=f' android:strokeWidth="{sw}" android:strokeLineCap="{cap}" android:strokeLineJoin="{join}"'
    if sa is not None: a+=f' android:strokeAlpha="{sa}"'
    if sw and not stroke and not sgrad: a+=' android:fillColor="#00000000"'
    a+='>'
    if grad: a+=f'<aapt:attr name="android:fillColor">{grad}</aapt:attr>'
    if sgrad: a+=f'<aapt:attr name="android:strokeColor">{sgrad}</aapt:attr>'
    return a+'</path>\n'
def G(inner,ty=0,tx=0,sc=None,pivot=(54,54)):
    s=f'<group android:translateX="{tx}" android:translateY="{ty}"'
    if sc: s+=f' android:scaleX="{sc}" android:scaleY="{sc}" android:pivotX="{pivot[0]}" android:pivotY="{pivot[1]}"'
    return s+'>\n'+inner+'</group>\n'
def background(top,bottom,glow=0.26):
    b=P('M0,0h108v108h-108z',grad=lin(30,0,78,108,[(0,top),(1,bottom)]))
    # a soft light from the upper left, as iOS icons have
    b+=P('M0,0h108v108h-108z',grad=rad(32,14,92,[(0,f'{int(glow*255):02X}FFFFFF'),(0.55,'00FFFFFF'),(1,'00FFFFFF')]))
    # a faint darkening toward the bottom edge
    b+=P('M0,0h108v108h-108z',grad=lin(0,60,0,108,[(0,'00000000'),(1,'33000000')]))
    return vec(b)
def shadowed(paths_fn,depth=1.0):
    """paths_fn(color_kwargs) -> xml for the glyph shape; drawn as three soft shadow copies then the glyph itself."""
    out=''
    for ty,al in ((3.2*depth,.10),(2.2*depth,.14),(1.2*depth,.18)):
        out+=G(paths_fn('shadow',al),ty=ty)
    return out
def write(app,name,xml,root):
    p=os.path.join(root,name); os.makedirs(os.path.dirname(p),exist_ok=True); open(p,'w').write(xml)
