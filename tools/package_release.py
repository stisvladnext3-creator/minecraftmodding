"""Package a built mod and its self-contained source project. Run after gradlew build."""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile

root=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'dist'
out.mkdir(parents=True,exist_ok=True)
version=re.search(r'^mod_version=(.+)$',(root/'gradle.properties').read_text(),re.M)[1]
jar=root/'build/libs'/f'epocharmory-{version}+mc1.21.1.jar'
manifest=json.loads((root/'tools/content_manifest.json').read_text())
with zipfile.ZipFile(jar) as z:
 names=z.namelist()
 assert f'version="{version}"' in z.read('META-INF/neoforge.mods.toml').decode()
 for style in ['mail','lamellar','brigandine','plate']:
  geometry=json.loads(z.read(f'assets/epocharmory/armor_geometry/{style}.json'))
  assert geometry['texture_size']==512 and geometry['geometry']=='faceted_fantasy_mesh' and set(geometry['pieces'])=={'head','chest','legs','feet'}
 for kind in ['buckler','round','kite','heater','scutum','aspis','pavise','kalkan','targe','rotella']:
  shield=json.loads(z.read(f'assets/epocharmory/models/item/shields/{kind}.json'))
  assert shield['elements'] and all(len(e['faces'])==1 for e in shield['elements'])
  for metal in ['iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite']:
   key=f'{metal}_{kind}_shield'
   model=json.loads(z.read(f'assets/epocharmory/models/item/{key}_3d.json'))
   assert model['parent']==f'epocharmory:item/shields/{kind}'
 for kind in json.loads((root/'tools/weapon-geometry.json').read_text()):
  weapon=json.loads(z.read(f'assets/epocharmory/models/item/weapons/{kind}.json'))
  assert weapon['elements'] and all(len(e['faces'])==1 for e in weapon['elements'])
  for metal in ['iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite']:
   key=f'{metal}_{kind}'
   model=json.loads(z.read(f'assets/epocharmory/models/item/{key}_3d.json'))
   assert model['parent']==f'epocharmory:item/weapons/{kind}'
   assert json.loads(z.read(f'assets/epocharmory/models/item/{key}_icon.json'))['parent']==f'epocharmory:item/{key}_3d'
 assert not any(n.startswith('ru/epocharmory/test/') or n=='data/epocharmory/structure/empty.nbt' for n in names)
 recipes=[n for n in names if n.startswith('data/epocharmory/recipe/') and n.endswith('.json')]
 assert sum(json.loads(z.read(n)).get('type')=='epocharmory:workshop' for n in recipes)==manifest['custom_recipes']
 for p in (root/'src/main/resources').rglob('*'):
  if not p.is_file():continue
  name=p.relative_to(root/'src/main/resources').as_posix()
  if name.startswith('data/epocharmory/structure/empty.'):continue
  assert z.read(name)==p.read_bytes(),f'Built resource differs from source: {name}'
shutil.copy2(jar,out/jar.name)
archive=out/f'EpochArmory-{version}-NeoForge-1.21.1-source.zip'
temporary_archive=archive.with_suffix('.zip.tmp')
files=[]
for directory in ['src','gradle','tools','art']:
 files.extend(p for p in (root/directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts and not p.name.endswith('.tmp'))
for file in ['.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','README.md','README_RU.md','HISTORY_RU.md','BALANCE_RU.md','CRAFTS_RU.md','VALIDATION_RU.md','LICENSE']:
 files.append(root/file)
with zipfile.ZipFile(temporary_archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for p in sorted(files):z.write(p,Path('EpochArmory-'+version)/p.relative_to(root))
with zipfile.ZipFile(temporary_archive) as z:assert z.testzip() is None
temporary_archive.replace(archive)
for p in [out/jar.name,archive]:
 print(json.dumps({'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}))
