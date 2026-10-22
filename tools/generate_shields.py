"""Pixel-native shields with a closed voxel shell and a shared, centred grip.

The silhouette is authored on a one-model-unit grid. Only exterior faces are
exported, so there are no layered scanline edges or transparent slice walls.
All transforms refer to the grip at (8, 8, 8), including the mirrored left hand.
"""
from pathlib import Path
from collections import defaultdict
import ast, json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/epocharmory'
MODELS = ASSETS/'models/item'
TEX = ASSETS/'textures'
KINDS = ('buckler','round','kite','heater','scutum','aspis','pavise','kalkan','targe','rotella')
SIZES = {'buckler':(10,10),'round':(14,14),'kite':(14,22),'heater':(14,18),
         'scutum':(14,22),'aspis':(16,16),'pavise':(16,24),'kalkan':(14,14),
         'targe':(14,20),'rotella':(14,16)}

def palette():
    tree=ast.parse((ROOT/'tools/generate_content.py').read_text())
    return next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign)
                and any(isinstance(t,ast.Name) and t.id=='METALS' for t in n.targets))

def put(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n')

def shade(c,k): return tuple(max(0,min(255,round(x*k))) for x in c)+(255,)

def silhouette(kind):
    w,h=SIZES[kind];x=(32-w)//2;y=(32-h)//2
    im=Image.new('1',(32,32));d=ImageDraw.Draw(im)
    if kind in ('buckler','round','aspis','kalkan','rotella'):
        # Octagonal stair steps, deliberately no high-resolution circles.
        n=2 if w<=10 else 3
        points=[(x+n,y),(x+w-n-1,y),(x+w-1,y+n),(x+w-1,y+h-n-1),
                (x+w-n-1,y+h-1),(x+n,y+h-1),(x,y+h-n-1),(x,y+n)]
    elif kind=='kite':
        points=[(x+3,y),(x+w-4,y),(x+w-1,y+3),(x+w-1,y+8),
                (x+w-3,y+13),(x+w//2,y+h-1),(x+w//2-1,y+h-1),(x+2,y+13),(x,y+8),(x,y+3)]
    elif kind=='heater':
        points=[(x,y),(x+w-1,y),(x+w-1,y+9),(x+w//2,y+h-1),
                (x+w//2-1,y+h-1),(x,y+9)]
    elif kind=='scutum':
        points=[(x+2,y),(x+w-3,y),(x+w-1,y+2),(x+w-1,y+h-3),
                (x+w-3,y+h-1),(x+2,y+h-1),(x,y+h-3),(x,y+2)]
    elif kind=='pavise':
        points=[(x+3,y),(x+w-4,y),(x+w-1,y+3),(x+w-1,y+h-1),(x,y+h-1),(x,y+3)]
    else:  # Hungarian targe with an intentional upper corner cutout.
        points=[(x,y),(x+w-5,y),(x+w-5,y+4),(x+w-1,y+4),
                (x+w-1,y+h-5),(x+w-5,y+h-1),(x+2,y+h-1),(x,y+h-4)]
    d.polygon(points,fill=1)
    return {(i,j) for j in range(32) for i in range(32) if im.getpixel((i,j))}

def rim(mask):
    return {(x,y) for x,y in mask if any((x+dx,y+dy) not in mask
             for dx in (-1,0,1) for dy in (-1,0,1) if dx or dy)}

def boss(kind):
    if kind in ('heater','kite','pavise','targe'):return set()
    r=2 if kind in ('aspis','scutum') else 1
    return {(x,y) for x in range(15-r,17+r) for y in range(15-r,17+r)
            if not (x in (15-r,16+r) and y in (15-r,16+r))}

def skin(kind,metal,col,mask,border,bossmask):
    im=Image.new('RGBA',(32,32));p=im.load()
    cloth={'kite':(55,91,142),'heater':(126,39,46),'scutum':(145,44,35),
           'pavise':(44,100,82),'targe':(50,83,113),'kalkan':(130,77,41)}
    for x,y in mask:
        if (x,y) in border:
            color=shade(col,1.16 if (x-1,y) not in mask or (x,y-1) not in mask else .78)
        elif (x,y) in bossmask:
            color=shade(col,1.16 if x<=15 and y<=15 else .83 if x>=17 or y>=17 else 1)
        elif kind in ('buckler','aspis','rotella'):
            color=shade(col,.91 if (x+y//3)%5==0 else 1)
        elif kind=='round':
            color=(115,76,43,255) if x%3==0 else (158,111,62,255)
            if (x<16)==(y<16):color=(60,86,112,255)
        else:
            base=cloth[kind];color=shade(base,.88 if x%4==0 else 1)
            if kind in ('heater','kite','pavise') and (x in (15,16) or y in (12,13)):
                color=(220,197,133,255)
            if kind=='scutum' and (abs(x-16)+abs(y-16))%8 in (0,1):color=(225,181,73,255)
            if kind=='kalkan' and (x+y)%4==0:color=(180,131,71,255)
            if kind=='targe' and x+y in (29,30,31):color=(226,207,146,255)
        if metal=='damascus' and (x+2*y)%6==0 and ((x,y) in border or kind in ('buckler','aspis','rotella')):
            color=shade(col,.70)
        p[x,y]=color
    return im

def geometry(kind,mask,border,bossmask):
    vox={}
    def fill(x0,y0,z0,x1,y1,z1,role):
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1):vox[x,y,z]=role
    for px,py in mask:
        x,y=23-px,23-py
        fill(x,y,9 if (px,py) in border else 10,x+1,y+1,14 if (px,py) in border else 13,'rim' if (px,py) in border else 'board')
        if (px,py) in bossmask:fill(x,y,7,x+1,y+1,10,'boss')
    # Two short supports and one rear grip. The fist is centred on the grip,
    # not on the shield face or the bounding box of an imported icon.
    fill(7,5,13,9,6,17,'leather');fill(7,10,13,9,11,17,'leather')
    fill(7,6,15,9,10,17,'grip')
    faces=defaultdict(set)
    directions={'west':(-1,0,0),'east':(1,0,0),'down':(0,-1,0),
                'up':(0,1,0),'north':(0,0,-1),'south':(0,0,1)}
    for (x,y,z),role in vox.items():
        for side,(dx,dy,dz) in directions.items():
            if (x+dx,y+dy,z+dz) in vox:continue
            texture='front' if side=='north' and role in ('board','rim','boss') else 'back' if role=='board' else 'leather' if role in ('leather','grip') else 'metal'
            if dx:plane=x+(dx>0);uv=(z,y)
            elif dy:plane=y+(dy>0);uv=(x,z)
            else:plane=z+(dz>0);uv=(x,y)
            faces[side,plane,texture].add(uv)
    elements=[]
    for (side,plane,texture),cells in sorted(faces.items()):
        while cells:
            u,v=min(cells,key=lambda p:(p[1],p[0]));w=1;h=1
            while (u+w,v) in cells:w+=1
            while all((i,v+h) in cells for i in range(u,u+w)):h+=1
            cells.difference_update((i,j) for i in range(u,u+w) for j in range(v,v+h))
            if side in ('north','south'):
                z=plane/2;lo=[u,v,z if side=='north' else z-.5];hi=[u+w,v+h,z+.5 if side=='north' else z]
            elif side in ('west','east'):
                lo=[plane if side=='west' else plane-1,v,u/2];hi=[plane+1 if side=='west' else plane,v+h,(u+w)/2]
            else:
                lo=[u,plane if side=='down' else plane-1,v/2];hi=[u+w,plane+1 if side=='down' else plane,(v+h)/2]
            if texture=='front':uv=[(24-(u+w))/2,(24-(v+h))/2,(24-u)/2,(24-v)/2]
            else:uv=[0,0,min(16,w/(4 if side in ('west','east') else 2)),min(16,h/(4 if side in ('up','down') else 2))]
            elements.append({'from':lo,'to':hi,'faces':{side:{'texture':'#'+texture,'uv':uv}}})
    return elements

def display(blocking=False):
    third=[36,-30,0] if blocking else [72,-90,0]
    first=[0,0,0] if blocking else [0,-15,-5]
    translation=[-3,2,0] if blocking else [3,-1,2]
    return {**{f'thirdperson_{hand}hand':{'rotation':third,'translation':[0,0,0],'scale':[1,1,1]} for hand in ('right','left')},
            **{f'firstperson_{hand}hand':{'rotation':first,'translation':translation,'scale':[.85,.85,.85]} for hand in ('right','left')},
            'ground':{'rotation':[90,0,0],'translation':[0,2,0],'scale':[.4,.4,.4]},
            'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.65,.65,.65]},
            'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[0,0,0]}}

def main():
    colors=palette();report={}
    (TEX/'item/shields').mkdir(parents=True,exist_ok=True)
    # Opaque wood and leather: exact pixels, no filtered sprite edges.
    for name,base in [('back',(142,94,52)),('leather',(93,54,32))]:
        im=Image.new('RGBA',(32,32));p=im.load()
        for y in range(32):
            for x in range(32):p[x,y]=shade(base,.72 if x%4==0 else 1.1 if x%4==1 else 1)
        im.save(TEX/'item/shields'/f'{name}.png')
    for metal,(_,col) in colors.items():
        im=Image.new('RGBA',(32,32),shade(col,1));im.save(TEX/'item/shields'/f'{metal}_edge.png')
    for kind in KINDS:
        mask=silhouette(kind);border=rim(mask);bossmask=boss(kind)&mask
        elements=geometry(kind,mask,border,bossmask)
        put(MODELS/'shields'/f'{kind}.json',{'ambientocclusion':False,'gui_light':'front','elements':elements,'display':display()})
        report[kind]={'width':SIZES[kind][0],'height':SIZES[kind][1],'faces':len(elements),'grip':[8,8,8]}
        for metal,(_,col) in colors.items():
            id=f'{metal}_{kind}_shield'
            sprite=skin(kind,metal,col,mask,border,bossmask)
            sprite.save(TEX/'item'/f'{id}.png')
            (TEX/'item'/f'{id}.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":false}}\n')
            textures={'front':f'epocharmory:item/{id}','back':'epocharmory:item/shields/back',
                      'metal':f'epocharmory:item/shields/{metal}_edge','leather':'epocharmory:item/shields/leather',
                      'particle':f'epocharmory:item/shields/{metal}_edge'}
            for blocking in (False,True):
                suffix='_blocking' if blocking else ''
                put(MODELS/f'{id}{suffix}_3d.json',{'parent':f'epocharmory:item/shields/{kind}',
                    'textures':textures,**({'display':display(True)} if blocking else {})})
                root={'loader':'neoforge:separate_transforms','gui_light':'front','textures':{'particle':textures['particle']},
                      'base':{'parent':f'epocharmory:item/{id}{suffix}_3d'},
                      'perspectives':{'gui':{'parent':f'epocharmory:item/{id}_icon'}}}
                if not blocking:root['overrides']=[{'predicate':{'blocking':1},'model':f'epocharmory:item/{id}_blocking'}]
                put(MODELS/f'{id}{suffix}.json',root)
            put(MODELS/f'{id}_icon.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'epocharmory:item/{id}'}})
    (ROOT/'tools/shield-geometry.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({'shields':100,'shapes':10,'grip':[8,8,8],'status':'generated'}))

if __name__=='__main__':main()
