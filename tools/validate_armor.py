"""Audit shipped armor topology, slot ownership, UV packing and all 80 textures."""
from pathlib import Path
import json,math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
ALLOWED={'head':{'head'},'chest':{'body','right_arm','left_arm'},'legs':{'body','right_leg','left_leg'},'feet':{'right_leg','left_leg'}}
METALS=('iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite')
def overlaps(a,b):
    """Separating-axis test for the interiors of two convex polygons."""
    for polygon in (a,b):
        for i,p in enumerate(polygon):
            q=polygon[(i+1)%len(polygon)];axis=(-(q[1]-p[1]),q[0]-p[0])
            if abs(axis[0])+abs(axis[1])<1e-9:continue
            aa=[x*axis[0]+y*axis[1] for x,y in a]
            bb=[x*axis[0]+y*axis[1] for x,y in b]
            if min(max(aa),max(bb))<=max(min(aa),min(bb))+1e-7:return False
    return True
total=0
for style in ('mail','lamellar','brigandine','plate'):
    data=json.loads((ASSETS/'armor_geometry'/f'{style}.json').read_text())
    size=data['texture_size'];assert data['format']==1 and size==512
    assert data['geometry']=='faceted_fantasy_mesh'
    occupied=Image.new('1',(size,size));pixels=occupied.load()
    assert set(data['pieces'])==set(ALLOWED)
    for slot,bones in data['pieces'].items():
        assert set(bones)==ALLOWED[slot],(style,slot,set(bones))
        for bone,faces in bones.items():
            assert faces
            planes={}
            for f in faces:
                v=f['v'];assert len(v)==4 and all(len(p)==3 and all(math.isfinite(x) for x in p) for p in v)
                a=[v[1][i]-v[0][i] for i in range(3)];b=[v[2][i]-v[0][i] for i in range(3)]
                n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                assert sum(x*x for x in n)>1e-8
                # Faceted armor intentionally uses bevels. Every exported quad
                # must still be planar, finite and have nonzero area.
                assert abs(sum(n[i]*(v[3][i]-v[0][i]) for i in range(3)))<1e-5,(style,'nonplanar quad',v)
                assert all(-16<=c<=32 for p in v for c in p),(style,'out of bounds',v)
                axis=max(range(3),key=lambda i:abs(n[i]))
                u,w=[i for i in range(3) if i!=axis]
                poly=[(p[u],p[w]) for p in v]
                # Opposite-facing caps at solid joints are internal. For
                # coplanar visible caps, audit the real polygons, not their
                # overlapping bounding boxes (beveled caps use quad fans).
                group=planes.setdefault((axis,round(v[0][axis],6),n[axis]>0),[]) if sum(abs(x)>1e-7 for x in n)==1 else []
                for other in group:
                    assert not overlaps(poly,other),(style,bone,'coplanar overlap',poly,other)
                group.append(poly)
                x0,y0,x1,y1=f['uv'];assert 2<=x0<x1<=size-2 and 2<=y0<y1<=size-2
                for y in range(y0-2,y1+2):
                    for x in range(x0-2,x1+2):
                        assert not pixels[x,y],(style,'UV overlap',f['uv'])
                        pixels[x,y]=1
                if slot=='feet':assert min(p[1] for p in v)>=7.5
                if slot=='legs' and bone.endswith('_leg'):assert max(p[1] for p in v)<=7.5
                total+=1
    for metal in METALS:
        layers=[]
        for layer in (1,2):
            p=ASSETS/'textures/models/armor'/f'{metal}_{style}_layer_{layer}.png'
            im=Image.open(p);assert im.size==(size,size) and im.mode=='RGBA'
            alpha=im.getchannel('A')
            for bones in data['pieces'].values():
                for faces in bones.values():
                    for f in faces:
                        assert alpha.crop(f['uv']).getextrema()==(255,255),(p,f['uv'])
            layers.append(p.read_bytes())
        assert layers[0]==layers[1]
        metadata=json.loads(p.with_suffix('.png.mcmeta').read_text())
        assert metadata['texture']['blur'] is False
print(json.dumps({'status':'passed','styles':4,'slot_models':16,'equipment':160,'armor_textures':80,'valid_faces':total,'uv_overlap':False,'slot_ownership':'passed'}))
