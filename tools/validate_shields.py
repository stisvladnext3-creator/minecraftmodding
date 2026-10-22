"""Audit actual exported shield surfaces, UV opacity and item model routes."""
from pathlib import Path
from collections import Counter, defaultdict
import json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
MODELS=ASSETS/'models/item'
KINDS=('buckler','round','kite','heater','scutum','aspis','pavise','kalkan','targe','rotella')
METALS=('iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite')
faces=0
for kind in KINDS:
    geo=json.loads((MODELS/'shields'/f'{kind}.json').read_text())
    edges=Counter();surfaces=defaultdict(set)
    for el in geo['elements']:
        assert len(el['faces'])==1,kind
        side,f=next(iter(el['faces'].items()));lo=[round(x*2) for x in el['from']];hi=[round(x*2) for x in el['to']]
        axis=0 if side in ('west','east') else 1 if side in ('down','up') else 2
        plane=lo[axis] if side in ('west','down','north') else hi[axis]
        a,b=[i for i in range(3) if i!=axis]
        assert min(f['uv'])>=0 and max(f['uv'])<=16
        for u in range(lo[a],hi[a]):
            for v in range(lo[b],hi[b]):
                cell=(u,v);assert cell not in surfaces[axis,plane,side],(kind,'overlapping exterior faces')
                surfaces[axis,plane,side].add(cell)
                points=[]
                for du,dv in [(0,0),(1,0),(1,1),(0,1)]:
                    p=[0,0,0];p[axis]=plane;p[a]=u+du;p[b]=v+dv;points.append(tuple(p))
                for i in range(4):edges[tuple(sorted((points[i],points[(i+1)%4])))]+=1
        faces+=1
    assert edges and set(edges.values())=={2},(kind,'open or non-manifold shell',Counter(edges.values()))
    for metal in METALS:
        key=f'{metal}_{kind}_shield'
        root=json.loads((MODELS/f'{key}.json').read_text())
        assert root['overrides']==[{'predicate':{'blocking':1},'model':f'epocharmory:item/{key}_blocking'}]
        icon=Image.open(ASSETS/'textures/item'/f'{key}.png').convert('RGBA')
        assert icon.size==(32,32) and set(icon.getchannel('A').getdata())=={0,255}
        assert json.loads((ASSETS/'textures/item'/f'{key}.png.mcmeta').read_text())['texture']['blur'] is False
        for el in geo['elements']:
            face=next(iter(el['faces'].values()))
            if face['texture']!='#front':continue
            x0,y0,x1,y1=[round(v*2) for v in face['uv']]
            assert x1>x0 and y1>y0
            assert all(icon.getpixel((x,y))[3]==255 for x in range(x0,x1) for y in range(y0,y1)),(key,'transparent front UV')
        idle=json.loads((MODELS/f'{key}_3d.json').read_text())
        block=json.loads((MODELS/f'{key}_blocking_3d.json').read_text())
        assert idle['parent']==block['parent']==f'epocharmory:item/shields/{kind}'
        for model in (root,json.loads((MODELS/f'{key}_blocking.json').read_text())):
            assert model['perspectives']['gui']['parent']==f'epocharmory:item/{key}_icon'
        for displays in (geo['display'],block['display']):
            # ItemTransform mirrors yaw, roll and X itself for the left hand.
            # Identical authored values are intentional, not doubled offsets.
            for view in ('thirdperson','firstperson'):
                assert displays[f'{view}_righthand']==displays[f'{view}_lefthand']
            for hand in ('right','left'):
                assert displays[f'thirdperson_{hand}hand']['translation']==[0,0,0]
report={'status':'passed','shields':100,'shapes':10,'exterior_faces':faces,'closed_manifold_shells':True,
        'overlapping_faces':False,'front_uv_opaque':True,'grip':[8,8,8],'left_hand':'engine_mirrored','blocking':'separate_pose_same_geometry'}
(ROOT/'tools/shield-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
