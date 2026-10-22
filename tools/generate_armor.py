"""Minecraft armor: axis-aligned cuboids, flat pixel palettes, exact UV packing.

No rounded lofts, bevels, slanted faces, smooth gradients or filtered textures.
The 32px inventory sprites are imported from art/armor_square.png when present.
"""
from pathlib import Path
import ast,json,math
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
SIZE=256
DENSITY=2
SLOTS=('head','chest','legs','feet')
STYLES=('mail','lamellar','brigandine','plate')

def metals():
    tree=ast.parse((ROOT/'tools/generate_content.py').read_text())
    return next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='METALS' for t in n.targets))

class Armor:
    def __init__(self,style):
        self.style=style;self.pieces={s:{} for s in SLOTS};self.faces=[];self.cubes=0;self.solids={}
    def box(self,slot,bone,x,y,z,w,h,d,material='metal',front=None):
        x1,y1,z1=[round(v*4)/4 for v in (x+w,y+h,z+d)]
        x,y,z=[round(v*4)/4 for v in (x,y,z)]
        # Leave a real centre seam between the two rigid leg shells. The padded
        # lining still covers the underlying player, including mixed equipment.
        if slot in ('legs','feet') and material!='lining':
            if bone=='right_leg':x1=min(x1,1.75)
            if bone=='left_leg':x=max(x,-1.75)
        w,h,d=x1-x,y1-y,z1-z
        assert min(w,h,d)>0
        self.cubes+=1
        self.solids.setdefault((slot,bone),[]).append(((x,y,z),(x1,y1,z1),material,front))
    def centered(self,slot,bone,y,w,h,d,material='metal',cx=0,cz=0,front=None):
        self.box(slot,bone,cx-w/2,y,cz-d/2,w,h,d,material,front)

    def resolve(self):
        """Union the cuboids, retaining only exterior rectangles.

        Later boxes own coincident surfaces (e.g. a metal cuff over padding).
        This removes both hidden faces and z-fighting without rounding geometry.
        """
        directions=[(2,-1),(2,1),(0,-1),(0,1),(1,-1),(1,1)]
        ids=[(1,0,3,2),(4,5,6,7),(0,4,7,3),(5,1,2,6),(0,1,5,4),(3,7,6,2)]
        def subtract(rect,cover):
            u0,v0,u1,v1=rect;c0,d0,c1,d1=cover
            l,r=max(u0,c0),min(u1,c1);t,b=max(v0,d0),min(v1,d1)
            if l>=r or t>=b:return [rect]
            return [q for q in [(u0,v0,l,v1),(r,v0,u1,v1),(l,v0,r,t),(l,b,r,v1)] if q[0]<q[2] and q[1]<q[3]]
        for (slot,bone),boxes in self.solids.items():
            for index,(lo,hi,material,front) in enumerate(boxes):
                x,y,z=lo;xx,yy,zz=hi
                vertices=[(x,y,z),(xx,y,z),(xx,yy,z),(x,yy,z),(x,y,zz),(xx,y,zz),(xx,yy,zz),(x,yy,zz)]
                for side,(axis,sign) in enumerate(directions):
                    plane=lo[axis] if sign<0 else hi[axis]
                    u,v=[i for i in range(3) if i!=axis]
                    rectangles=[(lo[u],lo[v],hi[u],hi[v])]
                    for other,(lower,upper,_,__) in enumerate(boxes):
                        if other==index:continue
                        outside=(lower[axis]<plane<=upper[axis]) if sign<0 else (lower[axis]<=plane<upper[axis])
                        coincident=(lower[axis]==plane) if sign<0 else (upper[axis]==plane)
                        if outside or coincident and other>index:
                            rectangles=[r for rect in rectangles for r in subtract(rect,(lower[u],lower[v],upper[u],upper[v]))]
                            if not rectangles:break
                    for rect in rectangles:
                        points=[]
                        for vertex in [vertices[i] for i in ids[side]]:
                            p=list(vertex);p[u]=rect[0] if vertex[u]==lo[u] else rect[2];p[v]=rect[1] if vertex[v]==lo[v] else rect[3];points.append(p)
                        width=math.dist(points[0],points[1]);height=math.dist(points[1],points[2])
                        face={'v':points,'material':front if side==0 and front else material,
                            'pixels':[max(1,math.ceil(width*DENSITY)),max(1,math.ceil(height*DENSITY))]}
                        self.pieces[slot].setdefault(bone,[]).append(face);self.faces.append(face)

def helmet(a):
    s=a.style
    if s=='plate':
        a.box('head','head',-4.5,-8.75,-4.5,9,9,9)
        a.box('head','head',-4.75,-9,-4.75,9.5,.5,9.5)
        a.box('head','head',-4.25,-5.6,-4.75,8.5,.6,.25,'lining')
        a.box('head','head',-4.1,-5,-5.25,8.2,5,.75,'metal',front='visor')
        a.box('head','head',-4.6,-6,-4.85,9.2,.35,.35,'trim')
        for x in [-4.75,4.5]:a.box('head','head',x,-4.9,-2,.25,1,.75,'trim')
        a.box('head','head',-4.8,.15,-4.75,9.6,.5,9.5)
    else:
        mat='mail' if s=='mail' else 'metal'
        # A flat cap, straight cheek pieces and a rectangular neck guard.
        a.box('head','head',-4.5,-8.75,-4.5,9,2.25,9,mat)
        a.box('head','head',-4.5,-6.5,4,9,7.25,.5,mat)
        for x in [-4.5,4]:a.box('head','head',x,-6.5,-4.25,.5,7.25,8.25,mat if s!='lamellar' else 'lamellar')
        if s=='mail':
            a.box('head','head',-4.5,-6.5,-4.5,9,.4,.35,'padded')
            for x in [-4.5,4.1]:a.box('head','head',x,-6.1,-4.5,.4,6.9,.35,'padded')
            a.box('head','head',-5,.5,3.75,10,1,1,'mail')
            for x in [-5,4]:a.box('head','head',x,.5,-3.75,1,1,7.5,'mail')
        elif s=='lamellar':
            a.box('head','head',-4.75,-6.75,-4.75,9.5,.5,9.5,'trim')
            for y in [-5.75,-3.75,-1.75]:
                a.box('head','head',-4.75,y,4.25,9.5,1.75,.5,'lamellar')
                for x in [-4.75,4.25]:a.box('head','head',x,y,-4.5,.5,1.75,8.75,'lamellar')
        else:
            a.box('head','head',-4.75,-6.5,-5.75,9.5,.5,1.5)
            a.box('head','head',-4.75,.75,3.75,9.5,.75,1)
            for x in [-4.75,4.25]:a.box('head','head',x,-5,-1.25,.5,.5,.75,'trim')

def torso(a):
    s=a.style;mat={'mail':'mail','lamellar':'lining','brigandine':'cloth','plate':'metal'}[s]
    a.centered('chest','body',.25,8.75,11.5,4.75,mat)
    a.centered('chest','body',-.25,6.5,1,5.25,'metal' if s=='plate' else 'padded')
    if s=='plate':
        a.box('chest','body',-4.1,1,-3,8.2,7.75,.625)
        a.box('chest','body',-4.1,1,2.375,8.2,8,.375)
        a.centered('chest','body',10.25,9,1,5.25)
        a.centered('chest','body',11.25,9.25,1,5.5)
    elif s=='lamellar':
        for y in [1,3.5,6,8.5]:a.centered('chest','body',y,9,2.25,5.25,'lamellar')
    else:
        a.centered('chest','body',11.5,9,0.5,5,'padded')
    a.centered('chest','body',9.25,9.25,.65,5.55,'leather')
    a.box('chest','body',-.5,9.3,-2.95,1,.65,.3,'trim')
    if s!='plate':
        for x in [-3.5,3]:a.box('chest','body',x,.5,-2.75,.5,3,.3,'leather')
    for bone,cx in [('right_arm',-1),('left_arm',1)]:
        if s=='mail':
            a.centered('chest',bone,-1.75,4.75,7.25,4.75,'mail',cx)
            a.centered('chest',bone,5.5,4.85,.6,4.85,'padded',cx)
        else:
            a.centered('chest',bone,-1.75,4.5,10.75,4.5,'padded',cx)
            shoulder='metal' if s=='plate' else 'lamellar' if s=='lamellar' else 'cloth'
            for y,w,h,d in [(-2.25,5.5,3.25,5.5),(1,5.1,1.75,5.15),(2.75,4.85,1.75,4.9)]:
                a.centered('chest',bone,y,w,h,d,shoulder,cx)
            a.centered('chest',bone,4.5,4.85,.55,4.9,'trim' if s=='plate' else 'leather',cx)
            if s=='plate':a.centered('chest',bone,5.05,5,1.45,5,'metal',cx)
            else:a.centered('chest',bone,5.05,4.75,.8,4.75,'padded',cx)
            a.centered('chest',bone,6.5,4.6,2.75,4.6,'metal' if s!='lamellar' else 'leather',cx)
            a.centered('chest',bone,9.1,4.75,.4,4.75,'trim' if s=='plate' else 'leather',cx)

def legs(a):
    s=a.style
    a.centered('legs','body',10.2,8.3,1.65,4.35,'lining')
    for bone in ['right_leg','left_leg']:
        a.centered('legs',bone,0,4.3,7.5,4.4,'lining')
        if s=='plate':
            a.centered('legs',bone,.2,4.5,5.2,4.7)
            for y,z in [(.2,-2.9),(1.85,-3.1),(3.5,-3.3)]:a.box('legs',bone,-2.15,y,z,4.3,1.35,.55)
            a.centered('legs',bone,5.4,4.75,1.65,5.05)
            a.centered('legs',bone,7.05,4.4,.45,4.6,'trim')
        elif s=='lamellar':
            for y in [.1,2.6,5.1]:a.centered('legs',bone,y,4.5,2.4,4.9,'lamellar')
        else:
            a.centered('legs',bone,.1,4.5,7.3,4.7,'mail' if s=='mail' else 'cloth')
            a.centered('legs',bone,5.1,4.6,.45,4.8,'leather')
        surface='mail' if s=='mail' else 'lamellar' if s=='lamellar' else 'metal'
        a.centered('feet',bone,7.5,5,4.5,5,'lining')
        a.centered('feet',bone,7.5,4.65,4.35,4.85,surface)
        if s=='lamellar':
            for y in [7.5,9]:a.centered('feet',bone,y,4.8,1.25,5,'lamellar')
        a.centered('feet',bone,7.5,4.8,.5,5,'cloth' if s=='brigandine' else 'leather' if s=='mail' else 'trim')
        a.centered('feet',bone,9.8,4.85,.4,5.1,'leather')
        a.box('feet',bone,-.5,9.8,-2.8,1,.55,.3,'trim')
        a.box('feet',bone,-2.4,10.7,-4.1,4.8,1.4,6.6,surface)
        a.box('feet',bone,-2.3,11.3,-4.6,4.6,.8,.5,surface)
        a.box('feet',bone,-2.5,12.05,-4.65,5,.3,7.3,'leather')

def pack(a):
    x=y=row=0
    for f in sorted(a.faces,key=lambda f:-f['pixels'][1]):
        w,h=f['pixels'];pw,ph=w+4,h+4
        if x+pw>SIZE:x=0;y+=row;row=0
        if y+ph>SIZE:raise ValueError(f'{a.style}: atlas overflow')
        f['uv']=[x+2,y+2,x+2+w,y+2+h];x+=pw;row=max(row,ph)
    return y+row

def shade(col,k):return tuple(max(0,min(255,round(c*k))) for c in col)

def pixel_patch(w,h,kind,metal,col):
    im=Image.new('RGB',(w,h));p=im.load()
    for y in range(h):
        for x in range(w):
            if kind in ('metal','visor'):
                # Discrete, deliberately flat colour clusters, never gradients.
                k=1.12 if y==0 or x==0 else .72 if y==h-1 or x==w-1 else 1
                if metal=='damascus' and (y+(x//3)%2)%4==1:k*=.84
                color=shade(col,k)
                if kind=='visor' and y>=3 and x%4==2 and y%4 in (1,2):color=(30,34,42)
            elif kind=='mail':
                xx=(x+(y//3%2))%3;yy=y%3
                color=shade(col,1.13 if yy==0 and xx==1 else .86) if xx==1 or yy==1 else (37,41,48)
            elif kind=='lamellar':
                color=shade(col,.65 if x%4==0 or y%4==3 else 1.1 if y%4==0 else .94)
                if x%4==2 and y%4==2:color=(172,126,62)
            elif kind=='cloth':
                color=(113,34,40) if (x//2+y//2)%2==0 else (99,28,35)
                if x%4==2 and y%4==2:color=shade(col,1.1)
                if x==0 or x==w-1:color=(70,24,30)
            elif kind=='leather':color=(92,55,32) if (x//2+y//2)%3 else (112,69,39)
            elif kind=='padded':color=(204,186,143) if (x+y)%4 else (166,144,103)
            elif kind=='trim':color=(185,145,65) if x and y else (224,188,102)
            else:color=(35,39,48)
            p[x,y]=color
    return im

def render(a,metal,col):
    image=Image.new('RGBA',(SIZE,SIZE))
    for f in a.faces:
        w,h=f['pixels'];x,y=f['uv'][:2]
        patch=pixel_patch(w,h,f['material'],metal,col)
        # Gutters are nearest-neighbour copies; texture filtering is disabled.
        image.paste(patch.resize((w+4,h+4),Image.Resampling.NEAREST),(x-2,y-2))
        image.paste(patch,(x,y))
    return image

def icons(palette):
    source=ROOT/'art/armor_square.png'
    if not source.exists():return
    source=Image.open(source).convert('RGBA')
    for row,style in enumerate(STYLES):
        for column,piece in enumerate(('helmet','chestplate','leggings','boots')):
            cell=source.crop((round(column*source.width/4),round(row*source.height/4),round((column+1)*source.width/4),round((row+1)*source.height/4)))
            bbox=cell.getchannel('A').point(lambda a:255 if a>128 else 0).getbbox()
            assert bbox
            cell=cell.crop(bbox);cell.thumbnail((28,28),Image.Resampling.NEAREST)
            sprite=Image.new('RGBA',(32,32));sprite.alpha_composite(cell,((32-cell.width)//2,(32-cell.height)//2))
            for metal,(_,col) in palette.items():
                final=sprite.copy();p=final.load()
                for y in range(32):
                    for x in range(32):
                        r,g,b,alpha=p[x,y]
                        if alpha<128:p[x,y]=(0,0,0,0);continue
                        if max(r,g,b)-min(r,g,b)<44 and max(r,g,b)>44:
                            k=round(((r+g+b)/3)/45)*.25
                            p[x,y]=shade(col,k)+(255,)
                        else:p[x,y]=(r//16*16,g//16*16,b//16*16,255)
                final.save(ASSETS/'textures/item'/f'{metal}_{style}_{piece}.png')

def main():
    palette=metals();report={}
    out=ASSETS/'armor_geometry';out.mkdir(parents=True,exist_ok=True)
    textures=ASSETS/'textures/models/armor';textures.mkdir(parents=True,exist_ok=True)
    for old in textures.glob('*.tmp'):old.unlink()
    for style in STYLES:
        a=Armor(style);helmet(a);torso(a);legs(a);a.resolve();height=pack(a)
        data={'format':1,'geometry':'axis_aligned_cuboids','texture_size':SIZE,'pixel_density':DENSITY,
            'pieces':{s:{b:[{'v':f['v'],'uv':f['uv']} for f in fs] for b,fs in bones.items()} for s,bones in a.pieces.items()}}
        (out/(style+'.json')).write_text(json.dumps(data,separators=(',',':'))+'\n')
        for metal,(_,col) in palette.items():
            image=render(a,metal,col)
            for layer in (1,2):
                target=textures/f'{metal}_{style}_layer_{layer}.png';temp=target.with_suffix('.png.tmp')
                image.save(temp,format='PNG',optimize=True);temp.replace(target)
                target.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":false}}\n')
        report[style]={'cuboids':a.cubes,'faces':len(a.faces),'used_atlas_height':height}
    icons(palette)
    (ROOT/'tools/armor-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))

if __name__=='__main__':main()
