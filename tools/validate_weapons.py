"""Check the exported weapon shells, UVs, texture pixels and item poses."""
from pathlib import Path
from collections import Counter, defaultdict
import json, math, re
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
MODELS=ASSETS/'models/item'
KINDS=re.findall(r'^\s+\w+\("([a-z_]+)"', (ROOT/'src/main/java/ru/epocharmory/WeaponKind.java').read_text(),re.M)
METALS=('iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite')
assert len(KINDS)==18
total_faces=0;reports={};issues=[]
for kind in KINDS:
    model=json.loads((MODELS/'weapons'/f'{kind}.json').read_text())
    edges=Counter();surfaces=defaultdict(set);uv_pixels=set();regions=[]
    for el in model['elements']:
        assert len(el['faces'])==1
        assert min(el['from']+el['to'])>=-16 and max(el['from']+el['to'])<=32
        assert all(a<b for a,b in zip(el['from'],el['to']))
        assert all(abs(v*2-round(v*2))<1e-6 for v in el['from']+el['to'])
        side,f=next(iter(el['faces'].items()));lo=[round(x*2) for x in el['from']];hi=[round(x*2) for x in el['to']]
        axis=0 if side in ('west','east') else 1 if side in ('down','up') else 2
        plane=lo[axis] if side in ('west','down','north') else hi[axis]
        a,b=[i for i in range(3) if i!=axis]
        for u in range(lo[a],hi[a]):
            for v in range(lo[b],hi[b]):
                assert (u,v) not in surfaces[axis,plane,side],(kind,'overlapping faces')
                surfaces[axis,plane,side].add((u,v));points=[]
                for du,dv in ((0,0),(1,0),(1,1),(0,1)):
                    p=[0,0,0];p[axis]=plane;p[a]=u+du;p[b]=v+dv;points.append(tuple(p))
                for i in range(4):edges[tuple(sorted((points[i],points[(i+1)%4])))]+=1
        assert f['texture']=='#surface' and 0<=min(f['uv'])<max(f['uv'])<=16
        uv=[round(x*16) for x in f['uv']];x0,y0,x1,y1=uv
        assert x1>x0 and y1>y0
        region={(x,y) for x in range(x0-1,x1+1) for y in range(y0-1,y1+1)}
        assert not uv_pixels&region,(kind,'UV overlap')
        uv_pixels|=region;regions.append((x0,y0,x1,y1))
    if set(edges.values())!={2}:issues.append((kind,'open or non-manifold shell',dict(Counter(edges.values()))))
    dsp=model['display']
    for view in ('firstperson','thirdperson'):
        assert dsp[f'{view}_lefthand']==dsp[f'{view}_righthand'],(kind,'double left-hand mirroring')
    assert dsp['thirdperson_righthand']['translation']==[0,0,0]
    assert abs(dsp['firstperson_righthand']['rotation'][1])<90,(kind,'blade edge-on in first person')
    for metal in METALS:
        key=f'{metal}_{kind}'
        child=json.loads((MODELS/f'{key}_3d.json').read_text())
        assert child['parent']==f'epocharmory:item/weapons/{kind}'
        assert child['textures']['surface']==f'epocharmory:item/weapons/{key}'
        root=json.loads((MODELS/f'{key}.json').read_text())
        assert root['base']['parent']==f'epocharmory:item/{key}_3d'
        assert root['perspectives']['gui']['parent']==f'epocharmory:item/{key}_icon'
        assert json.loads((MODELS/f'{key}_icon.json').read_text())['parent']==f'epocharmory:item/{key}_3d'
        path=ASSETS/'textures/item/weapons'/f'{key}.png';im=Image.open(path).convert('RGBA')
        assert im.size==(256,256)
        assert json.loads(path.with_suffix('.png.mcmeta').read_text())['texture']['blur'] is False
        alpha=im.getchannel('A')
        for x0,y0,x1,y1 in regions:
            assert alpha.crop((x0,y0,x1,y1)).getextrema()==(255,255),(key,'transparent UV region')
        icon=Image.open(ASSETS/'textures/item'/f'{key}.png').convert('RGBA')
        assert icon.size==(64,64) and icon.getbbox()
    total_faces+=len(model['elements'])
    reports[kind]={'exterior_faces':len(model['elements']),'closed_shell':True}
assert not issues,issues
report={'status':'passed','weapons':180,'shape_models':18,'exterior_faces':total_faces,'closed_manifold_shells':True,
        'uv_overlap':False,'opaque_uv_regions':True,'grip':[8,8,8],'left_hand':'engine_mirrored',
        'inventory':'same_3d_geometry_as_held_model','per_shape':reports}
(ROOT/'tools/weapon-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='per_shape'}))
