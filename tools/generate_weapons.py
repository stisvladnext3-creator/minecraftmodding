"""Detailed Minecraft-native weapon meshes, pixel UVs and grip-centred poses.

Half-unit solid voxels are unioned before exporting only exterior rectangles.
Shared shape parents keep all ten materials identical in silhouette and grip.
Inventory models render the same three-dimensional geometry as held items.
"""
from pathlib import Path
from collections import defaultdict
import ast, json, math
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
MODELS=ASSETS/'models/item'
TEX=ASSETS/'textures'
SIZE=256
KINDS=('arming_sword','longsword','mace','spear','katana','dagger','morningstar','battleaxe',
       'halberd','war_hammer','rapier','sabre','gladius','kukri','katar','naginata','pike','flail')
POLES={'spear','halberd','naginata','pike'}
# Total held length in model units (16 units = one Minecraft block).
# A dagger is 0.5 blocks, an arming sword 1.25, a longsword 1.875,
# and a pike 2.75. All materials of a shape retain the same dimensions.
HELD_LENGTHS={'dagger':8,'katar':9,'kukri':11,'gladius':14,'mace':16,
              'morningstar':18,'war_hammer':19,'arming_sword':20,'sabre':21,
              'flail':22,'rapier':23,'katana':24,'battleaxe':24,'longsword':30,
              'spear':34,'naginata':35,'halberd':36,'pike':44}

def metals():
    tree=ast.parse((ROOT/'tools/generate_content.py').read_text())
    return next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign)
                and any(isinstance(t,ast.Name) and t.id=='METALS' for t in n.targets))

def put(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,indent=2)+'\n')

class Weapon:
    def __init__(self,kind):self.kind=kind;self.vox={};self.parts=0;self.faces=[]
    def box(self,x0,y0,z0,x1,y1,z1,role='metal'):
        lo=[round(v*2) for v in (x0,y0,z0)];hi=[round(v*2) for v in (x1,y1,z1)]
        assert all(a<b for a,b in zip(lo,hi)),(self.kind,lo,hi)
        for x in range(lo[0],hi[0]):
            for y in range(lo[1],hi[1]):
                for z in range(lo[2],hi[2]):self.vox[x,y,z]=role
        self.parts+=1
    def profile(self,points,z0,z1,role='metal',bevel=False):
        # Rasterize the physical profile at 0.5 units, with no interpolation.
        im=Image.new('1',(96,96));d=ImageDraw.Draw(im)
        d.polygon([(round((x+16)*2),round((y+16)*2)) for x,y in points],fill=1)
        mask={(x,y) for y in range(96) for x in range(96) if im.getpixel((x,y))}
        for px,py in mask:
            edge=any((px+dx,py+dy) not in mask for dx in (-1,0,1) for dy in (-1,0,1))
            a,b=(z0+.5,z1-.5) if edge and bevel and z1-z0>=1.5 else (z0,z1)
            self.box(px/2-16,py/2-16,a,(px+1)/2-16,(py+1)/2-16,b,'edge' if edge else role)
    def ring(self,cx,cy,cz,w,h,d,plane='xy',role='metal'):
        if plane=='xy':
            self.box(cx-w/2,cy-h/2,cz-d/2,cx-w/2+.5,cy+h/2,cz+d/2,role)
            self.box(cx+w/2-.5,cy-h/2,cz-d/2,cx+w/2,cy+h/2,cz+d/2,role)
            self.box(cx-w/2,cy-h/2,cz-d/2,cx+w/2,cy-h/2+.5,cz+d/2,role)
            self.box(cx-w/2,cy+h/2-.5,cz-d/2,cx+w/2,cy+h/2,cz+d/2,role)
        else:
            self.box(cx-d/2,cy-h/2,cz-w/2,cx+d/2,cy+h/2,cz-w/2+.5,role)
            self.box(cx-d/2,cy-h/2,cz+w/2-.5,cx+d/2,cy+h/2,cz+w/2,role)
            self.box(cx-d/2,cy-h/2,cz-w/2,cx+d/2,cy-h/2+.5,cz+w/2,role)
            self.box(cx-d/2,cy+h/2-.5,cz-w/2,cx+d/2,cy+h/2,cz+w/2,role)
    def grip(self,top=11,bottom=4,wood=False):
        self.box(7,bottom,7,9,top,9,'wood' if wood else 'leather')
        self.box(6.5,bottom-.5,6.5,9.5,bottom+.5,9.5,'trim')
        self.box(6.5,top-.5,6.5,9.5,top+.5,9.5,'trim')
    def pommel(self,y=3):
        self.box(6.5,y-1,7,9.5,y+1,9,'metal')
        self.box(7,y-1.5,7,9,y+1.5,9,'trim')
        self.box(7,y-.5,6.5,9,y+.5,9.5,'trim')
    def guard(self,y=11,w=8):
        self.box(8-w/2,y-.5,7,8+w/2,y+.5,9,'trim')
        self.box(8-w/2-.5,y-1,7,8-w/2+.5,y+.5,9,'metal')
        self.box(8+w/2-.5,y-1,7,8+w/2+.5,y+.5,9,'metal')
    def blade(self,start=12,end=29,width=3,curve=0,leaf=False,single=False):
        points=[];n=round((end-start)*2)
        for side in (-1,1):
            seq=range(n+1) if side<0 else range(n,-1,-1)
            for i in seq:
                t=i/max(1,n);cx=8+round(curve*t*t*2)/2
                w=width*(1-.18*t) if t<.78 else width*(1-t)/.22
                if leaf:w*=.7+.45*math.sin(math.pi*t)
                xx=cx+(side*w/2 if not single else (-.5 if side<0 else w))
                points.append((round(xx*2)/2,start+i/2))
        self.profile(points,7.5,8,'metal')
        # Raised central ridge, clipped inside the real blade profile.
        for y in range(round(start*2),round(end*2)-3):
            t=(y/2-start)/(end-start);cx=8+round(curve*t*t*2)/2
            for zz in (16,):
                x=round(cx*2)
                for xx in (x-1,x):
                    if (xx,y,15) in self.vox:self.vox[xx,y,zz]='spine'
        if not single:
            for key,role in list(self.vox.items()):
                x,y,z=key
                if role in ('metal','spine') and z in (15,16) and start+2<y/2<end-3 and x==15:self.vox[key]='fuller'
    def curved_blade(self,start,end,curve=3,width=2):
        # Few monotone control points keep the cutting edge continuous. The
        # blade is a single flat extrusion, with one raised spine along its
        # back, rather than a succession of small crosswise bevels.
        ts=(0,.3,.65,.88,1)
        back=[(round((7.5+curve*t*t)*2)/2,round((start+(end-start)*t)*2)/2) for t in ts]
        front=[(round((8+curve*t*t+width)*2)/2,round((start+(end-start)*t)*2)/2) for t in ts[:-1]]
        self.profile(back+list(reversed(front)),7.5,8,'metal')
        strip=[(x+1,y) for x,y in back[:-1]]
        self.profile(back+list(reversed(strip)),7.5,8.5,'spine')
    def spike(self,cx,cy,cz,axis,length=3):
        for i in range(round(length*2)):
            w=2 if i<2 else 1 if i<4 else .5
            c=[cx,cy,cz];c[abs(axis)-1]+=i*.5*(1 if axis>0 else -1)
            lo=[q-w/2 for q in c];hi=[q+w/2 for q in c]
            a=abs(axis)-1;lo[a]=c[a]-.5;hi[a]=c[a]+.5
            # Quantize every detail to the same half-unit grid.
            lo=[math.floor(v*2)/2 for v in lo];hi=[math.ceil(v*2)/2 for v in hi]
            self.box(*lo,*hi,'edge')
    def resolve(self):
        # A curved half-pixel step may otherwise join two solids at an edge
        # with zero surface area. Fill one half-unit bridge at such junctions
        # before meshing, giving a closed manifold exterior instead of four
        # different faces meeting along the same edge.
        self.bridges=0
        for _ in range(32):
            added=0
            for k in tuple(self.vox):
                for aa,bb in ((0,1),(0,2),(1,2)):
                    for sign in (-1,1):
                        u=list(k);v=list(k);q=list(k)
                        u[aa]+=1;v[bb]+=sign;q[aa]+=1;q[bb]+=sign
                        u,v,q=tuple(u),tuple(v),tuple(q)
                        if q not in self.vox or u in self.vox or v in self.vox:continue
                        def score(p):
                            return sum(tuple(p[j]+(d if j==axis else 0) for j in range(3)) in self.vox
                                       for axis in range(3) for d in (-1,1))
                        p=u if score(u)>=score(v) else v
                        self.vox[p]=self.vox[k];added+=1
            self.bridges+=added
            if not added:break
        else:raise AssertionError((self.kind,'unresolved edge junctions'))
        faces=defaultdict(set)
        ds={'west':(-1,0,0),'east':(1,0,0),'down':(0,-1,0),'up':(0,1,0),'north':(0,0,-1),'south':(0,0,1)}
        for (x,y,z),role in self.vox.items():
            for side,(dx,dy,dz) in ds.items():
                if (x+dx,y+dy,z+dz) in self.vox:continue
                if dx:plane=x+(dx>0);p=(z,y)
                elif dy:plane=y+(dy>0);p=(x,z)
                else:plane=z+(dz>0);p=(x,y)
                faces[side,plane,role].add(p)
        for (side,plane,role),cells in sorted(faces.items()):
            while cells:
                u,v=min(cells,key=lambda p:(p[1],p[0]));w=h=1
                while (u+w,v) in cells:w+=1
                while all((i,v+h) in cells for i in range(u,u+w)):h+=1
                cells.difference_update((i,j) for i in range(u,u+w) for j in range(v,v+h))
                if side in ('north','south'):
                    z=plane/2;lo=[u/2,v/2,z if side=='north' else z-.5];hi=[(u+w)/2,(v+h)/2,z+.5 if side=='north' else z]
                elif side in ('west','east'):
                    x=plane/2;lo=[x if side=='west' else x-.5,v/2,u/2];hi=[x+.5 if side=='west' else x,(v+h)/2,(u+w)/2]
                else:
                    y=plane/2;lo=[u/2,y if side=='down' else y-.5,v/2];hi=[(u+w)/2,y+.5 if side=='down' else y,(v+h)/2]
                self.faces.append({'side':side,'from':lo,'to':hi,'role':role,'pixels':[w,h],'origin':[u,v]})
        self.low=[min(k[a] for k in self.vox)/2 for a in range(3)]
        self.high=[(max(k[a] for k in self.vox)+1)/2 for a in range(3)]
        assert min(self.low)>=-16 and max(self.high)<=32,(self.kind,self.low,self.high)

def make(kind):
    a=Weapon(kind)
    if kind in POLES:
        bottom={'spear':0,'pike':-4,'halberd':-2,'naginata':-2}[kind]
        top={'spear':22,'pike':26,'halberd':27,'naginata':22}[kind]
        radius=.5 if kind=='pike' else 1
        a.box(8-radius,bottom,8-radius,8+radius,top,8+radius,'wood')
        a.grip(top=11,bottom=5,wood=True)
        a.box(8-radius-.5,bottom-.5,8-radius-.5,8+radius+.5,bottom+1,8+radius+.5,'metal')
        a.box(6.5,top-2,6.5,9.5,top+.5,9.5,'trim')
        if kind in ('spear','pike'):
            a.blade(start=top,end=31,width=4 if kind=='spear' else 2.5,leaf=True)
        elif kind=='naginata':
            a.curved_blade(21,31,curve=3,width=2.5)
            a.box(6,20,7,10,21,9,'trim')
        else:
            a.profile([(8,22),(11,21),(13,20),(16,21),(16,27),(14,29),(12,27),(8,26)],7.5,8)
            a.blade(start=26,end=31,width=2)
            a.profile([(8,25),(5,25),(3,22),(4,21),(6,23),(8,23)],7,9)
            for y in (20,24):a.box(7,y,6.5,9,y+1,9.5,'trim')
    elif kind in ('arming_sword','longsword','katana','dagger','rapier','sabre','gladius','kukri','katar'):
        if kind=='katar':
            a.box(4.5,4,7,5.5,14,9,'metal');a.box(10.5,4,7,11.5,14,9,'metal')
            a.box(5,7,7,11,9,9,'leather');a.box(5,11,7,11,12,9,'trim')
            a.profile([(4.5,14),(11,14),(8,24)],7,9)
            a.box(7.5,14,6.5,8.5,21,9.5,'spine')
        else:
            top=12 if kind in ('longsword','katana','sabre') else 11
            bottom=2 if kind in ('longsword','katana') else 4
            a.grip(top,bottom);a.pommel(bottom-1)
            if kind=='katana':
                a.box(5.5,11.5,6,10.5,12.5,10,'metal');a.box(7,12.5,6.5,9.5,14,9.5,'trim')
                a.curved_blade(14,31,curve=2.5,width=2)
            elif kind=='sabre':
                a.guard(12,6);a.ring(11,8,8,5,9,1,'xy','trim')
                a.curved_blade(13,29,curve=4,width=2)
            elif kind=='rapier':
                a.guard(11,7)
                a.ring(8,10,8,6,4,1,'xy','trim');a.ring(8,10,8,5,4,1,'zy','metal')
                a.ring(10,7.5,8,4,7,1,'xy','trim')
                a.blade(12,30,1.5)
            elif kind=='kukri':
                a.guard(11,3)
                a.profile([(7,12),(9,12),(11,16),(14,18),(15,22),(14,24),(12,23),(9,20),(8,17)],7.5,8)
                a.box(7.5,11,6.5,8.5,14,9.5,'spine')
            else:
                a.guard(top,10 if kind=='longsword' else 7 if kind=='arming_sword' else 5)
                end={'arming_sword':29,'longsword':31,'dagger':22,'gladius':25}[kind]
                a.blade(top+1,end,4 if kind=='gladius' else 3,leaf=kind=='gladius')
                if kind=='longsword':
                    a.box(7,13,6.5,9,16,9.5,'fuller')
                    a.box(5.5,16,7,10.5,16.5,9,'metal')
    else:
        a.box(7,4,7,9,22,9,'wood');a.grip(12,4);a.pommel(3)
        if kind=='mace':
            a.box(6.5,18,6.5,9.5,25,9.5,'metal')
            for z0,z1 in ((7.5,8.5),(6,7),(9,10)):
                a.box(4.5 if z0==7.5 else 5.5,19,z0,11.5 if z0==7.5 else 10.5,24,z1,'metal')
            a.box(7.5,19,4.5,8.5,24,11.5,'metal')
            a.box(6,19,5.5,7,24,10.5,'metal');a.box(9,19,5.5,10,24,10.5,'metal')
            for y in (17.5,24.5):a.box(6, y,6,10,y+1,10,'trim')
            a.box(7,25.5,7,9,27,9,'edge')
        elif kind in ('morningstar','flail'):
            cx,cy=(8,22) if kind=='morningstar' else (16,18)
            if kind=='flail':
                a.box(6.5,19,6.5,9.5,21,9.5,'trim')
                a.ring(8,22,8,2,4,1,'xy')
                a.ring(9.5,22,8,4,3,1,'zy')
                a.ring(11,21,8,4,3,1,'xy')
                a.ring(12.5,20,8,4,3,1,'zy')
                a.ring(14,19,8,3,3,1,'xy')
            a.box(cx-2,cy-2,6,cx+2,cy+2,10,'metal')
            a.box(cx-2.5,cy-1.5,6.5,cx+2.5,cy+1.5,9.5,'metal')
            a.box(cx-1.5,cy-2.5,6.5,cx+1.5,cy+2.5,9.5,'metal')
            for axis in (-3,-2,-1,1,2,3):
                c=[cx,cy,8];c[abs(axis)-1]+=2*(1 if axis>0 else -1)
                a.spike(*c,axis,length=2.5 if kind=='flail' else 3)
            a.box(7,15.5,6.5,9,17,9.5,'trim')
        elif kind=='battleaxe':
            a.box(6.5,20,6,10,25,10,'metal')
            a.profile([(9,21),(12,20),(15,17),(17,18),(17,27),(15,29),(13,27),(11,24),(9,24)],7.5,8)
            a.profile([(7,23),(4,25),(3,25),(3.5,22),(7,21)],7,9)
            a.box(7,20,5.5,9,21,10.5,'trim');a.box(7,24,5.5,9,25,10.5,'trim')
        elif kind=='war_hammer':
            a.box(3,20,6,12,24,10,'metal');a.box(2,19.5,5.5,4,24.5,10.5,'edge')
            a.box(4,19.5,5.5,5,24.5,10.5,'trim')
            a.profile([(11,20),(14,20),(17,18),(16,21),(13,23),(11,23)],7,9)
            a.box(7,18,6.5,9,20,9.5,'trim')
    a.resolve();return a

def pack(a):
    x=y=row=0
    for f in sorted(a.faces,key=lambda f:-f['pixels'][1]):
        w,h=f['pixels']
        if x+w+2>SIZE:x=0;y+=row;row=0
        assert y+h+2<=SIZE,(a.kind,'UV atlas overflow')
        f['uv']=[x+1,y+1,x+1+w,y+1+h];x+=w+2;row=max(row,h+2)
    return y+row

def color(role,col,metal,x,y,side):
    if role=='wood':base=(134,87,46);k=.73 if x%4==0 else 1.12 if x%4==1 else 1
    elif role=='leather':base=(83,47,31);k=1.35 if (x+y//2)%4==0 else .8 if (x+y//2)%4==3 else 1
    elif role=='trim':base=(190,144,63);k=1.15 if y%4==0 else .82 if y%4==3 else 1
    else:
        base=col;k={'edge':1.22,'metal':1,'spine':1.09,'fuller':.63}.get(role,1)
        if metal=='damascus' and (y+(x//3)%3)%5 in (0,1):k*=.76
    return tuple(max(0,min(255,round(c*k))) for c in base)+(255,)

def texture(a,metal,col):
    im=Image.new('RGBA',(SIZE,SIZE));p=im.load()
    for f in a.faces:
        x,y,x1,y1=f['uv'];w,h=f['pixels'];ox,oy=f['origin']
        for yy in range(-1,h+1):
            for xx in range(-1,w+1):
                sx=max(0,min(w-1,xx));sy=max(0,min(h-1,yy))
                p[x+xx,y+yy]=color(f['role'],col,metal,ox+sx,oy+sy,f['side'])
    return im

def rotate(point,angles):
    x,y,z=point;ax,ay,az=map(math.radians,angles)
    x,y=x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az)
    x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
    y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
    return x,y,z

def fit(a,angles,limit):
    corners=[rotate((x-8,y-8,z-8),angles) for x in (a.low[0],a.high[0]) for y in (a.low[1],a.high[1]) for z in (a.low[2],a.high[2])]
    lo=[min(p[i] for p in corners) for i in range(3)];hi=[max(p[i] for p in corners) for i in range(3)]
    scale=limit/max(hi[0]-lo[0],hi[1]-lo[1])
    return {'rotation':angles,'translation':[-round((lo[i]+hi[i])/2*scale,4) for i in range(3)],'scale':[round(scale,4)]*3}

def display(a):
    kind=a.kind
    held_scale=HELD_LENGTHS[kind]/(a.high[1]-a.low[1])
    first_scale=held_scale*.72
    result={}
    for hand,sign in (('right',1),('left',-1)):
        # Minecraft 1.21.1 item/handheld uses (0,-90,55) in third person
        # and (0,-90,25) in first person, mirrored in the left-hand entry.
        # Its sword sprite runs diagonally along X+Y; our meshes run along Y.
        # Subtract 45 degrees of Z rotation to match the actual blade axis.
        # Keep the mesh's grip pivot at (8,8,8), rather than copying the
        # translation that compensates for a vanilla sprite's off-centre grip.
        result[f'thirdperson_{hand}hand']={'rotation':[0,-90*sign,10*sign],'translation':[0,0,0],'scale':[held_scale]*3}
        result[f'firstperson_{hand}hand']={'rotation':[0,-90*sign,-20*sign],'translation':[-2,-1,-1],'scale':[first_scale]*3}
    # Inventory icons also convey the difference without making small blades
    # unreadable; do not auto-fit every weapon to the same icon dimensions.
    result['gui']=fit(a,[15,-35,-35],13.2*math.sqrt(HELD_LENGTHS[kind]/44))
    result['fixed']=fit(a,[0,0,-35],14)
    result['ground']=fit(a,[0,0,0],10)
    result['ground']['translation'][1]=round(2+(a.high[1]-a.low[1])*result['ground']['scale'][1]/2,4)
    return result

def icon(a,col,metal):
    # Exact technical projection of the same mesh for consumers of item PNGs.
    im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
    t=fit(a,[15,-35,-35],26*math.sqrt(HELD_LENGTHS[a.kind]/44));scale=t['scale'][0]*2;ang=t['rotation'];centre=t['translation']
    polys=[]
    normals={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
    for f in a.faces:
        n=rotate(normals[f['side']],ang)
        if n[2]>=-.01:continue
        lo,hi=f['from'],f['to'];side=f['side'];axis=0 if side in ('west','east') else 1 if side in ('up','down') else 2
        other=[i for i in range(3) if i!=axis];plane=lo[axis] if side in ('west','down','north') else hi[axis]
        pts=[]
        for u,v in ((0,0),(1,0),(1,1),(0,1)):
            p=[0,0,0];p[axis]=plane;p[other[0]]=hi[other[0]] if u else lo[other[0]];p[other[1]]=hi[other[1]] if v else lo[other[1]]
            q=rotate(tuple(p[i]-8 for i in range(3)),ang);pts.append(q)
        shade=.68+.32*max(0,-n[2]);c=color(f['role'],col,metal,2,2,side);c=tuple(round(v*shade) for v in c[:3])+(255,)
        polys.append((sum(p[2] for p in pts)/4,[(round(32+p[0]*scale+centre[0]*2),round(32-p[1]*scale-centre[1]*2)) for p in pts],c))
    for _,pts,c in sorted(polys,reverse=True):d.polygon(pts,fill=c)
    return im

def main():
    palette=metals();report={};directory=TEX/'item/weapons';directory.mkdir(parents=True,exist_ok=True)
    for kind in KINDS:
        a=make(kind);used=pack(a);dsp=display(a)
        elements=[{'from':f['from'],'to':f['to'],'faces':{f['side']:{'texture':'#surface','uv':[v/16 for v in f['uv']]}}} for f in a.faces]
        put(MODELS/'weapons'/f'{kind}.json',{'ambientocclusion':False,'elements':elements,'display':dsp})
        report[kind]={'parts':a.parts,'exterior_faces':len(a.faces),'bounds':[a.low,a.high],'grip':[8,8,8],'atlas_height':used,'texture_size':SIZE,'closed_edge_junctions':a.bridges,'held_length':HELD_LENGTHS[kind]}
        for metal,(_,col) in palette.items():
            key=f'{metal}_{kind}';target=directory/f'{key}.png';temp=target.with_suffix('.png.tmp')
            texture(a,metal,col).save(temp,format='PNG',optimize=True);temp.replace(target)
            target.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":false}}\n')
            icon(a,col,metal).save(TEX/'item'/f'{key}.png')
            (TEX/'item'/f'{key}.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":false}}\n')
            tx={'surface':f'epocharmory:item/weapons/{key}','particle':f'epocharmory:block/{metal}_metal'}
            put(MODELS/f'{key}_3d.json',{'parent':f'epocharmory:item/weapons/{kind}','textures':tx})
            put(MODELS/f'{key}_icon.json',{'parent':f'epocharmory:item/{key}_3d'})
            put(MODELS/f'{key}.json',{'loader':'neoforge:separate_transforms','textures':{'particle':tx['particle']},
                'base':{'parent':f'epocharmory:item/{key}_3d'},'perspectives':{'gui':{'parent':f'epocharmory:item/{key}_icon'}}})
    (ROOT/'tools/weapon-geometry.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'weapons':180,'shapes':18,'texture_size':SIZE,'status':'generated'}))

if __name__=='__main__':main()
