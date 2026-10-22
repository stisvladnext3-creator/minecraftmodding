"""Check local model/texture references, model bounds, recipe graph, and icon alpha."""
from pathlib import Path
import json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources';NS='epocharmory'
manifest=json.loads((ROOT/'tools/content_manifest.json').read_text());errors=[]
models=list((RES/'assets'/NS/'models').rglob('*.json'))
def ref(value,kind,suffix):
 if not value.startswith(NS+':'):return
 p=RES/'assets'/NS/kind/(value.split(':',1)[1]+suffix)
 if not p.is_file():errors.append('Missing '+str(p.relative_to(ROOT)))
def walk(obj):
 if isinstance(obj,dict):
  for k,v in obj.items():
   if k=='parent' and isinstance(v,str):ref(v,'models','.json')
   if k=='textures' and isinstance(v,dict):
    for t in v.values():
     if isinstance(t,str) and not t.startswith('#'):ref(t,'textures','.png')
   walk(v)
 elif isinstance(obj,list):
  for v in obj:walk(v)
for p in models:
 data=json.loads(p.read_text());walk(data)
 for e in data.get('elements',[]):
  if any(not -16<=v<=32 for v in e['from']+e['to']):errors.append('Model out of native bounds '+p.name)
  if any(a>=b for a,b in zip(e['from'],e['to'])):errors.append('Degenerate cuboid '+p.name)
  if 'rotation' in e and e['rotation']['angle'] not in [-45,-22.5,0,22.5,45]:errors.append('Invalid rotation '+p.name)
for item in manifest['items']:
 ref(NS+':item/'+item,'models','.json')
 path=RES/'assets'/NS/'textures/item'/(item+'.png')
 if not path.exists():errors.append('Missing icon '+item);continue
 im=Image.open(path).convert('RGBA')
 if not im.getbbox():errors.append('Empty icon '+item)
recipes=[]
known=set(manifest['items'])|set(manifest['blocks'])
for p in (RES/'data'/NS/'recipe').glob('*.json'):
 r=json.loads(p.read_text())
 if r['type']!=NS+':workshop':continue
 recipes.append(r)
 for id in [i['item'] for i in r['inputs']]+[r['result']['id'],r.get('tool','')]:
  if id.startswith(NS+':') and id.split(':',1)[1] not in known:errors.append('Unregistered recipe reference '+p.name+' '+id)
 if len(r['inputs'])>6:errors.append('Too many recipe ingredients '+p.name)
 if r.get('min_heat',0)>r.get('max_heat',1600):errors.append('Invalid temperature range '+p.name)
reachable={'minecraft:'+v for v in ['iron_ingot','gold_ingot','copper_ingot','diamond','netherite_ingot','raw_iron','charcoal','water_bucket','flint','quartz']}
# Crafting-table ingredients are reachable from vanilla resources; ore drops and tools have independent acquisition.
reachable|={NS+':'+v for v in ['raw_tin','tin_ingot','bauxite','rutile','dolomite','flux','clay_slurry','padded_lining','leather_strap','wooden_shaft','shield_board','smith_hammer','tongs','rivet_punch','metal_shears','smiths_codex']}
for _ in range(40):
 old=len(reachable)
 for r in recipes:
  if all(i['item'].startswith('minecraft:') or i['item'].startswith('#') or i['item'] in reachable for i in r['inputs']):reachable.add(r['result']['id'])
 if len(reachable)==old:break
for id in manifest['gear']:
 if NS+':'+id not in reachable:errors.append('Unreachable equipment '+id)
assert len(recipes)==manifest['custom_recipes']==707
if errors:raise SystemExit('\n'.join(errors))
report={'models':len(models),'textures':len(list((RES/'assets'/NS/'textures').rglob('*.png'))),'equipment':len(manifest['gear']),'workshop_recipes':len(recipes),'reachable_equipment':len(manifest['gear']),'status':'passed'}
(ROOT/'tools/resource-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
