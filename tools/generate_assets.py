"""Import imagegen sprite sheets and generate exact, editable Minecraft cuboid models/UV maps.
Atlas slicing and palette conversion are the production import step. Source sheets are retained.
"""
from pathlib import Path
import json,math,random,shutil
from PIL import Image,ImageDraw
from generate_content import ROOT,RES,NS,METALS,BASE,WEAPONS,SHIELDS,STYLES,PIECES,STATIONS,ITEMS,GEAR
ART=ROOT/'art';ART.mkdir(exist_ok=True)
TEX=RES/'assets'/NS/'textures'
def put(path,obj):
 p=RES/'assets'/NS/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+'\n',encoding='utf-8')
def save(image,path):p=TEX/path;p.parent.mkdir(parents=True,exist_ok=True);image.save(p)
def atlas(file,cols,rows,names):
 im=Image.open(ART/file).convert('RGBA');result={}
 for n,key in enumerate(names):
  x=n%cols;y=n//cols
  cell=im.crop((round(x*im.width/cols),round(y*im.height/rows),round((x+1)*im.width/cols),round((y+1)*im.height/rows)))
  box=cell.getbbox()
  if not box:raise ValueError('Empty sprite '+key)
  cell=cell.crop(box);cell.thumbnail((60,60),Image.Resampling.LANCZOS)
  sprite=Image.new('RGBA',(64,64));sprite.alpha_composite(cell,((64-cell.width)//2,(64-cell.height)//2));result[key]=sprite
 return result
def palette(image,col,damascus=False):
 im=image.copy();px=im.load()
 for y in range(im.height):
  for x in range(im.width):
   r,g,b,a=px[x,y]
   if a and max(r,g,b)-min(r,g,b)<42 and max(r,g,b)>40:
    light=(r*.26+g*.51+b*.23)/182
    if damascus:light*=.92+.10*math.sin(y*.9+math.sin(x*.6)*3)
    px[x,y]=tuple(max(0,min(255,int(c*light))) for c in col)+(a,)
 return im
def pattern(col,kind='metal',seed=11,size=32):
 rng=random.Random(seed);im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im)
 for y in range(size):
  for x in range(size):
   noise=rng.randrange(-7,8);v=noise
   if kind=='damascus':v+=int(16*math.sin(y*.9+math.sin(x*.45)*2.8))
   if kind=='wood':v+=int(14*math.sin(x*1.9+math.sin(y*.1)))
   if kind=='cloth':v+=(x%2-y%2)*5
   d.point((x,y),fill=tuple(max(0,min(255,c+v)) for c in col)+(255,))
 if kind=='metal':
  d.line((0,0,size-1,0),fill=tuple(min(255,c+40) for c in col));d.line((0,0,0,size-1),fill=tuple(min(255,c+20) for c in col))
 return im

# Armor sprites use the pixel-exact import in generate_armor.py.
for m,(ru,col) in METALS.items():
 save(pattern(col,'damascus' if m=='damascus' else 'metal'),Path('block')/(m+'_metal.png'))

# Small resource parts are exact pixel-native icons, as are the workstation surfaces.
for key in sorted(ITEMS-set(GEAR)):
 m=next((m for m in METALS if m in key),'iron');col=METALS[m][1]
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
 if 'ingot' in key or 'billet' in key:
  fill=(230,96,38,255) if key.startswith('hot_') or key=='folded_billet' else col+(255,)
  d.polygon([(3,13),(12,7),(27,10),(27,19),(18,25),(3,21)],fill=fill,outline=(35,32,35,255));d.line([(4,13),(18,17),(27,11)],fill=(248,224,180,255),width=2)
 elif 'rings' in key:
  for x,y in [(6,6),(13,7),(19,13),(7,17),(13,22)]:d.ellipse((x,y,x+8,y+7),outline=col+(255,),width=2)
 elif 'wire' in key:
  for x in range(6,25,3):d.ellipse((x-4,5,x+4,26),outline=col+(255,),width=1)
 elif 'rivets' in key:
  for x,y in [(7,8),(18,8),(11,19),(22,20)]:d.rectangle((x,y,x+2,y+8),fill=col+(255,));d.rectangle((x-2,y,x+4,y+2),fill=tuple(min(255,c+40) for c in col)+(255,))
 elif 'mail_panel' in key or 'lamellae' in key:
  for y in range(5,27,5):
   for x in range(5,27,5):d.ellipse((x,y,x+5,y+4),outline=col+(255,),width=2) if 'mail' in key else d.rectangle((x,y,x+4,y+5),fill=col+(255,),outline=(45,40,40,255))
 elif 'blade' in key:
  fill=(237,111,40,255) if key.startswith('hot') else col+(255,)
  d.polygon([(5,27),(9,20),(22,6),(27,4),(26,10),(12,24)],fill=fill,outline=(38,38,40,255));d.line((10,22,24,7),fill=(245,245,239,255))
 elif 'head' in key:
  d.rectangle((7,9,24,22),fill=(236,104,39,255) if key.startswith('hot') else col+(255,),outline=(35,35,39,255));d.rectangle((13,7,18,25),fill=tuple(max(0,c-45) for c in col)+(255,))
 elif 'plate' in key:
  d.polygon([(5,8),(22,5),(27,20),(10,27),(5,23)],fill=(230,99,35,255) if key.startswith('hot') else col+(255,),outline=(33,34,38,255));d.line((7,9,21,7,25,19),fill=(236,231,214,255),width=2)
 elif key in ['smith_hammer','tongs','metal_shears','rivet_punch']:
  if key=='smith_hammer':
   d.line((7,27,21,10),fill=(108,66,36,255),width=4);d.polygon([(13,5),(19,2),(28,12),(22,18)],fill=(168,181,195,255),outline=(31,35,39,255))
  elif key=='tongs' or key=='metal_shears':
   d.line((7,27,22,7),fill=(170,183,195,255),width=3);d.line((25,27,9,7),fill=(170,183,195,255),width=3);d.ellipse((12,13,18,19),fill=(202,167,78,255))
  else:d.line((9,27,22,5),fill=(163,178,193,255),width=4);d.rectangle((7,23,14,28),fill=(110,65,35,255))
 elif key=='smiths_codex':
  d.polygon([(4,8),(22,4),(27,8),(27,26),(9,29),(4,25)],fill=(100,56,35,255),outline=(28,24,23,255));d.polygon([(8,9),(23,6),(23,24),(8,27)],fill=(166,104,55,255));d.line((11,19,20,10),fill=(218,195,134,255),width=3)
 elif key in ['leather_strap','wooden_shaft','shield_board','padded_lining']:
  if key=='wooden_shaft':d.line((5,27,26,4),fill=(135,87,45,255),width=4);d.line((5,27,25,5),fill=(194,141,74,255))
  elif key=='leather_strap':d.line((6,25,11,7,23,7,21,25),fill=(130,79,41,255),width=4);d.rectangle((7,17,13,23),outline=(214,178,86,255),width=2)
  else:
   d.rectangle((5,5,26,27),fill=(180,150,102,255),outline=(60,48,31,255))
   for x in range(8,26,5):d.line((x,6,x,26),fill=(104,81,49,255))
   if key=='padded_lining':
    for y in range(8,27,5):d.line((6,y,25,y),fill=(217,199,159,255))
 else:
  c=(216,210,192,255) if key in ['flux','alumina','magnesia','clay_slurry'] else (166,109,72,255) if key=='bauxite' else (135,145,157,255)
  d.polygon([(3,22),(8,12),(14,15),(20,7),(27,17),(28,25),(15,28)],fill=c,outline=(44,39,35,255));d.line((8,20,13,18,17,20,22,13),fill=(234,218,177,255),width=2)
 save(im,Path('item')/(key+'.png'))

for name,col,kind in [('wood',(100,64,37),'wood'),('leather',(81,48,30),'cloth'),('stone',(96,101,107),'metal'),('brick',(98,70,62),'cloth'),('coal',(30,28,29),'cloth'),('coals',(214,88,27),'cloth'),('water',(44,107,154),'cloth'),('cloth',(152,49,53),'cloth'),('quartz',(199,190,169),'metal')]:
 image=pattern(col,kind)
 if name=='brick':
  d=ImageDraw.Draw(image)
  for y in range(0,32,8):d.line((0,y,31,y),fill=(51,45,44,255),width=1)
  for y in range(0,32,8):
   for x in range(0 if y%16 else 8,32,16):d.line((x,y,x,y+7),fill=(51,45,44,255))
 save(image,Path('block')/(name+'.png'))
for ore,col in [('tin',(173,180,182)),('bauxite',(174,96,64)),('rutile',(110,84,100)),('dolomite',(210,203,178))]:
 for prefix,base in [('',(111,113,118)),('deepslate_',(63,65,73))]:
  im=pattern(base,seed=4);d=ImageDraw.Draw(im)
  for x,y in [(4,6),(11,11),(22,4),(23,23),(6,23),(17,19)]:d.rectangle((x,y,x+4,y+3),fill=col+(255,));d.line((x,y,x+3,y),fill=tuple(min(255,c+28) for c in col)+(255,))
  save(im,Path('block')/(prefix+ore+'_ore.png'))

def box(x0,y0,z0,x1,y1,z1,tex='metal',uv=None,rotation=None):
 e={'from':[x0,y0,z0],'to':[x1,y1,z1],'faces':{side:{'texture':'#'+tex,'uv':uv or [0,0,16,16]} for side in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']=rotation
 return e

def station_elements(s):
 if s in ['bloomery','crucible','forge','tempering_furnace','electrolyzer']:
  if s=='electrolyzer':return [box(0,0,0,16,3,16,'metal'),box(1,3,2,7,13,14,'quartz'),box(9,3,2,15,13,14,'quartz'),box(2,13,4,6,15,12,'metal'),box(10,13,4,14,15,12,'metal'),box(5,12,7,11,14,9,'coals')]
  e=[box(0,0,0,16,3,16,'stone'),box(0,3,0,4,13,16,'brick'),box(12,3,0,16,13,16,'brick'),box(4,3,12,12,13,16,'brick'),box(4,3,1,12,4,12,'coals'),box(4,11,0,12,13,12,'brick')]
  if s=='bloomery':e += [box(2,13,3,14,17,15,'brick'),box(4,17,5,12,23,13,'brick')]
  if s=='forge':e += [box(0,13,0,16,15,16,'stone'),box(10,15,10,15,25,15,'brick')]
  if s=='crucible':e += [box(5,7,4,11,11,10,'metal'),box(4,10,3,12,12,11,'metal')]
  if s=='tempering_furnace':e += [box(3,4,0,13,11,1,'metal'),box(5,7,-.5,11,8,1,'wood')]
  return e
 if s=='quench_tub':return [box(1,0,1,15,2,15,'wood'),box(0,1,0,2,13,16,'wood'),box(14,1,0,16,13,16,'wood'),box(2,1,0,14,13,2,'wood'),box(2,1,14,14,13,16,'wood'),box(2,8,2,14,8.2,14,'water'),box(-.2,3,-.2,16.2,4,1,'metal'),box(-.2,10,-.2,16.2,11,1,'metal')]
 if s=='forging_anvil':return [box(1,0,1,15,4,15,'wood'),box(3,4,4,13,6,12),box(6,6,6,10,10,10),box(3,10,5,13,13,11),box(12,10,6,17,12,10),box(16,10.5,7,19,11.5,9)]
 e=[box(0,11,0,16,13,16,'wood')]+[box(x,0,z,x+2,11,z+2,'wood') for x in [1,13] for z in [1,13]]
 if s=='wire_bench':e += [box(3,13,2,5,19,7),box(11,13,2,13,19,7),box(4,16,3,12,18,5),box(2,14,10,14,15,12),box(13,15,4,15,18,5,'wood')]
 if s=='riveting_bench':e += [box(3,13,3,9,14,9),box(5,14,5,7,16,7),box(11,13,5,12,18,6,'wood'),box(9,17,4,14,19,7)]
 if s=='grindstone_bench':e += [box(3,13,6,5,18,10,'wood'),box(11,13,6,13,18,10,'wood'),box(4,14,5,12,22,11,'stone'),box(6,13,4,10,23,12,'stone'),box(3,17,7,13,19,9)]
 if s=='armorer_bench':e += [box(2,13,2,10,13.4,11,'cloth'),box(11,13,3,14,15,10),box(3,13,12,12,14,14,'leather')]
 return e
for s in STATIONS:
 for lit in [False,True]:
  model=s+('_lit' if lit else '')
  tx={k:f'{NS}:block/{k}' for k in ['wood','stone','brick','water','cloth','quartz','leather']};tx['metal']=f'{NS}:block/steel_metal';tx['coals']=f'{NS}:block/{"coals" if lit else "coal"}';tx['particle']=f'{NS}:block/stone'
  put(Path('models/block')/(model+'.json'),{'textures':tx,'elements':station_elements(s)})
 variants={f'facing={f},lit={str(lit).lower()}':{'model':f'{NS}:block/{s}'+('_lit' if lit else ''),'y':angle} for f,angle in [('north',0),('east',90),('south',180),('west',270)] for lit in [False,True]}
 put(Path('blockstates')/(s+'.json'),{'variants':variants})
 put(Path('models/item')/(s+'.json'),{'parent':f'{NS}:block/{s}','display':{'gui':{'rotation':[30,225,0],'translation':[0,-1,0],'scale':[.6,.6,.6]}}})

# Geometry and UV atlases are generated together, so regeneration cannot revert armor.
from generate_armor import main as generate_worn_armor
generate_worn_armor()

# Shields use closed voxel shells and grip-centred transforms.
from generate_shields import main as generate_historical_shields
generate_historical_shields()

# Detailed weapon solids and hand poses share one canonical grip.
from generate_weapons import main as generate_historical_weapons
generate_historical_weapons()
