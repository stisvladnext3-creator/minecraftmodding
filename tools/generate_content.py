"""Generate exact Minecraft resource data and import the three imagegen sprite atlases.
Requires Pillow; no Gradle or runtime dependency. Generated JSON is shipped in the source.
"""
from pathlib import Path
import json, math, re, shutil, random
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ART=ROOT/'art'
NS='epocharmory'
LANG={}; EN={}; RECIPES=[]; ITEMS=set(); GEAR=[]
METALS={
 'iron':('железа',(181,190,201)), 'bronze':('бронзы',(193,129,60)),
 'gold':('золота',(246,198,66)), 'steel':('стали',(164,180,199)),
 'damascus':('дамасской стали',(133,149,173)), 'aluminium':('алюминиевого сплава',(206,219,223)),
 'magnesium':('магниевого сплава',(192,207,207)), 'titanium':('титанового сплава',(140,160,182)),
 'diamond':('стали с алмазной кромкой',(91,218,219)), 'netherite':('незерита',(87,75,87))}
BASE=list(METALS)[:8]
WEAPONS=[('arming_sword','Одноручный меч','long_blade',1),('longsword','Двуручный меч','long_blade',2),('mace','Булава','head',1),('spear','Копьё','short_blade',1),('katana','Катана','long_blade',1),('dagger','Кинжал','short_blade',1),('morningstar','Моргенштерн','head',2),('battleaxe','Боевой топор','head',2),('halberd','Алебарда','head',2),('war_hammer','Боевой молот','head',2),('rapier','Рапира','long_blade',1),('sabre','Сабля','long_blade',1),('gladius','Гладиус','short_blade',2),('kukri','Кукри','short_blade',1),('katar','Катар','short_blade',1),('naginata','Нагината','long_blade',1),('pike','Пика','short_blade',1),('flail','Цепной кистень','head',2)]
SHIELDS=[('buckler','Баклер',2),('round','Круглый щит',3),('kite','Каплевидный щит',4),('heater','Треугольный щит',3),('scutum','Скутум',5),('aspis','Аспис',5),('pavise','Павеза',6),('kalkan','Калкан',2),('targe','Венгерский тарч',4),('rotella','Ротелла',4)]
STYLES={'mail':'Кольчуга','lamellar':'Ламелляр','brigandine':'Бригантина','plate':'Латы'}
PIECES=['helmet','chestplate','leggings','boots']
PIECES_RU=['Шлем','Нагрудник','Поножи','Ботинки']
STATIONS={'bloomery':'Сыродутная печь','crucible':'Тигельная печь','forge':'Кузнечный горн','forging_anvil':'Ковочная наковальня','quench_tub':'Закалочная ванна','tempering_furnace':'Печь отпуска','wire_bench':'Волочильный стан','riveting_bench':'Клепальный верстак','grindstone_bench':'Точильный станок','armorer_bench':'Верстак оружейника','electrolyzer':'Алхимический электролизёр'}

def put(path,data):
 p=RES/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def rid(s):return s if ':' in s or s.startswith('#') else f'{NS}:{s}'
def name(key,ru,block=False):
 LANG[f'{"block" if block else "item"}.{NS}.{key}']=ru
 EN[f'{"block" if block else "item"}.{NS}.{key}']=key.replace('_',' ').title()
 if not block:ITEMS.add(key)
def ws(key,station,inputs,out,count=1,tool='',time=60,heat=(0,1600),hot=False,by=None):
 data={'type':f'{NS}:workshop','station':station,'inputs':[{'item':rid(i),'count':n} for i,n in inputs], 'result':{'id':rid(out),'count':count},'time':time}
 if tool:data['tool']=rid(tool)
 if heat!=(0,1600):data.update(min_heat=heat[0],max_heat=heat[1])
 if hot:data['hot']=True
 if by:data['byproduct']={'id':rid(by[0]),'count':by[1]}
 put(Path('data')/NS/'recipe'/f'{key}.json',data);RECIPES.append((key,data))
def shaped(key,pattern,keys,out=None,count=1):
 put(Path('data')/NS/'recipe'/f'{key}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:({'tag':v[1:]} if v.startswith('#') else {'item':rid(v)}) for k,v in keys.items()},'result':{'id':rid(out or key),'count':count}})
def shapeless(key,ingredients,out=None,count=1):
 put(Path('data')/NS/'recipe'/f'{key}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'tag':i[1:]} if i.startswith('#') else {'item':rid(i)} for i in ingredients],'result':{'id':rid(out or key),'count':count}})
def simple_model(key):put(Path('assets')/NS/'models/item'/f'{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'{NS}:item/{key}'}})

SIMPLE={'iron_bloom':'Железная крица','slag':'Шлак','flux':'Известковый флюс','tin_ingot':'Оловянный слиток','raw_tin':'Необработанное олово','bauxite':'Боксит','rutile':'Рутил','dolomite':'Доломит','alumina':'Глинозём','magnesia':'Магнезия','titanium_sponge':'Титановая губка','leather_strap':'Кожаные ремешки','padded_lining':'Стёганая подкладка','wooden_shaft':'Древко','shield_board':'Дощатая основа щита','clay_slurry':'Глиняная обмазка','folded_billet':'Горячий сварочный пакет','smith_hammer':'Кузнечный молот','tongs':'Кузнечные клещи','rivet_punch':'Клепальный пробойник','metal_shears':'Ножницы по металлу','smiths_codex':'Кодекс кузнеца'}
for k,v in SIMPLE.items():name(k,v)
for m in ['steel','damascus']:
 name('clay_hardened_'+m+'_blade','Клинок после дифференциальной закалки из '+METALS[m][0])
 name('tempered_hamon_'+m+'_blade','Отпущенный клинок катаны из '+METALS[m][0])
 name('hamon_'+m+'_blade','Клинок катаны с хамоном из '+METALS[m][0])
for m in BASE:
 ru,col=METALS[m]
 if m not in ['iron','gold']:name(m+'_ingot','Слиток '+ru)
 name('hot_'+m+'_billet','Горячая заготовка из '+ru)
 for part,pru in [('short_blade','короткий клинок'),('long_blade','длинный клинок'),('head','головка оружия'),('plate','пластина')]:
  for stage,sru in [('hot','Горячая деталь'),('quenched','Охлаждённая деталь'),('tempered','Деталь после термообработки'),('finished','Готовая деталь')]:name(f'{stage}_{m}_{part}',f'{sru}: {pru} из {ru}')
 for p,pru in [('wire','Проволока'),('rings','Кольца'),('mail_panel','Клёпаное кольчужное полотно'),('lamellae','Ряд ламелей'),('rivets','Заклёпки')]:name(m+'_'+p,pru+' из '+ru)

shapeless('flux',['minecraft:calcite'],count=4)
shapeless('leather_strap',['minecraft:leather'],count=4)
shapeless('padded_lining',['#minecraft:wool','#minecraft:wool','minecraft:leather','minecraft:string','minecraft:string'])
shapeless('wooden_shaft',['minecraft:stick','minecraft:stick','leather_strap'],count=2)
shapeless('shield_board',['#minecraft:planks']*6+['minecraft:leather']*2,count=2)
shapeless('clay_slurry',['minecraft:clay_ball']*4+['minecraft:charcoal','minecraft:water_bucket'],count=4)
shapeless('smiths_codex',['minecraft:book','minecraft:iron_nugget'])
shaped('smith_hammer',[' II',' SI','S  '],{'I':'minecraft:iron_ingot','S':'minecraft:stick'})
shaped('tongs',['I I',' S ',' S '],{'I':'minecraft:iron_ingot','S':'minecraft:stick'})
shaped('rivet_punch',[' I','S '],{'I':'minecraft:iron_ingot','S':'minecraft:stick'})
shaped('metal_shears',[' I','IF'],{'I':'minecraft:iron_ingot','F':'minecraft:flint'})
patterns={
 'bloomery':(['BBB','BFB','BBB'],{'B':'minecraft:bricks','F':'minecraft:furnace'}),
 'crucible':(['T T','BFB','BBB'],{'T':'minecraft:terracotta','B':'minecraft:bricks','F':'minecraft:blast_furnace'}),
 'forge':(['S S','SFS','BBB'],{'S':'minecraft:stone_bricks','B':'minecraft:bricks','F':'minecraft:furnace'}),
 'forging_anvil':(['IBI',' I ','LLL'],{'I':'minecraft:iron_ingot','B':'minecraft:iron_block','L':'#minecraft:logs'}),
 'quench_tub':(['I I','ICI','LLL'],{'I':'minecraft:iron_nugget','C':'minecraft:cauldron','L':'#minecraft:planks'}),
 'tempering_furnace':(['BTB','BFB','BBB'],{'B':'minecraft:bricks','T':'minecraft:iron_trapdoor','F':'minecraft:furnace'}),
 'wire_bench':(['III','P P','PPP'],{'I':'minecraft:iron_bars','P':'#minecraft:planks'}),
 'riveting_bench':(['III','PCP','P P'],{'I':'minecraft:iron_ingot','C':'minecraft:crafting_table','P':'#minecraft:planks'}),
 'grindstone_bench':([' S ','LGL','P P'],{'S':'minecraft:smooth_stone_slab','L':'minecraft:stick','G':'minecraft:grindstone','P':'#minecraft:planks'}),
 'armorer_bench':(['LIL','PCP','P P'],{'L':'minecraft:leather','I':'minecraft:iron_ingot','C':'minecraft:crafting_table','P':'#minecraft:logs'}),
 'electrolyzer':(['CRC','IFI','CRC'],{'C':'minecraft:copper_block','R':'minecraft:redstone_block','I':'minecraft:iron_block','F':'minecraft:blast_furnace'})}
for s,(p,k) in patterns.items():name(s,STATIONS[s],True);shaped(s,p,k)
for typ in ['minecraft:smelting','minecraft:blasting']:
 put(Path('data')/NS/'recipe'/('tin_'+typ.split(':')[1]+'.json'),{'type':typ,'ingredient':{'item':rid('raw_tin')},'result':{'id':rid('tin_ingot'),'count':1},'experience':.5,'cookingtime':200 if typ.endswith('smelting') else 100})
ws('a_iron_bloom','bloomery',[('minecraft:raw_iron',2),('minecraft:charcoal',2),('flux',1)],'iron_bloom',3,time=240,heat=(1050,1350),by=('slag',1))
ws('a_refine_bloom','forging_anvil',[('iron_bloom',1)],'minecraft:iron_ingot',tool='smith_hammer',time=60)
ws('a_bronze','crucible',[('minecraft:copper_ingot',3),('tin_ingot',1),('flux',1)],'bronze_ingot',4,time=160,heat=(1000,1350))
ws('a_steel','crucible',[('minecraft:iron_ingot',2),('minecraft:charcoal',1),('flux',1)],'steel_ingot',2,time=260,heat=(1100,1400),by=('slag',1))
ws('a_folded_packet','forge',[('minecraft:iron_ingot',2),('steel_ingot',2),('flux',1)],'folded_billet',2,time=160,heat=(1050,1350))
ws('a_damascus','forging_anvil',[('folded_billet',1)],'damascus_ingot',1,tool='smith_hammer',time=160,hot=True)
ws('reheat_folded','forge',[('folded_billet',1)],'folded_billet',time=60,heat=(1050,1350))
ws('a_alumina','crucible',[('bauxite',3),('flux',1),('minecraft:charcoal',1)],'alumina',2,time=200,heat=(1100,1400),by=('slag',1))
ws('a_magnesia','crucible',[('dolomite',3),('flux',1)],'magnesia',2,time=200,heat=(1000,1350),by=('slag',1))
ws('a_aluminium','electrolyzer',[('alumina',3),('minecraft:copper_ingot',1)],'aluminium_ingot',3,time=300,heat=(850,1100))
ws('a_magnesium','electrolyzer',[('magnesia',3),('aluminium_ingot',1)],'magnesium_ingot',3,time=300,heat=(850,1100))
ws('a_titanium_sponge','electrolyzer',[('rutile',2),('magnesium_ingot',2),('minecraft:quartz',1)],'titanium_sponge',1,time=400,heat=(900,1200))
ws('a_titanium','crucible',[('titanium_sponge',1),('aluminium_ingot',1)],'titanium_ingot',2,time=320,heat=(1150,1400))

HEATS={'iron':(750,1100),'steel':(800,1150),'damascus':(800,1100),'bronze':(600,900),'gold':(400,750),'aluminium':(350,650),'magnesium':(300,550),'titanium':(850,1200)}
TEMPS={'iron':(450,650),'steel':(250,450),'damascus':(250,450),'bronze':(300,500),'gold':(300,500),'aluminium':(180,280),'magnesium':(160,260),'titanium':(500,700)}
for m in BASE:
 feed='minecraft:'+m+'_ingot' if m in ['iron','gold'] else m+'_ingot'
 ws('heat_'+m,'forge',[(feed,1)],'hot_'+m+'_billet',time=80,heat=HEATS[m])
 for shape,cost in [('short_blade',1),('long_blade',2),('head',2),('plate',1)]:
  hot=f'hot_{m}_{shape}'
  ws('form_'+m+'_'+shape,'forging_anvil',[(f'hot_{m}_billet',cost)],hot,tool='smith_hammer',time=60 if shape=='short_blade' else 100,hot=True)
  for batch in [1,4]:
   ws('quench_'+m+'_'+shape+('_batch' if batch>1 else ''),'quench_tub',[(hot,batch),('minecraft:water_bucket',1)],f'quenched_{m}_{shape}',batch,time=40 if batch==1 else 80,hot=True,by=('minecraft:bucket',1))
  ws('temper_'+m+'_'+shape,'tempering_furnace',[(f'quenched_{m}_{shape}',1)],f'tempered_{m}_{shape}',time=100,heat=TEMPS[m])
  ws('grind_'+m+'_'+shape,'grindstone_bench',[(f'tempered_{m}_{shape}',1),('minecraft:flint',1)],f'finished_{m}_{shape}',tool='tongs',time=40)
 for p in ['billet','short_blade','long_blade','head','plate']:
  ws('reheat_'+m+'_'+p,'forge',[(f'hot_{m}_{p}',1)],f'hot_{m}_{p}',time=50,heat=HEATS[m])
 ws('wire_'+m,'wire_bench',[(feed,1)],m+'_wire',8,tool='tongs',time=80)
 ws('rings_'+m,'wire_bench',[(m+'_wire',4)],m+'_rings',16,tool='metal_shears',time=60)
 ws('rivets_'+m,'wire_bench',[(f'finished_{m}_plate',1)],m+'_rivets',16,tool='metal_shears',time=40)
 ws('mail_panel_'+m,'riveting_bench',[(m+'_rings',8),(m+'_rivets',2)],m+'_mail_panel',1,tool='rivet_punch',time=120)
 ws('lamellae_'+m,'riveting_bench',[(f'finished_{m}_plate',1),('leather_strap',1)],m+'_lamellae',4,tool='rivet_punch',time=60)
for m in ['steel','damascus']:
 ws('hamon_quench_'+m,'quench_tub',[(f'hot_{m}_long_blade',1),('clay_slurry',1),('minecraft:water_bucket',1)],f'clay_hardened_{m}_blade',time=100,hot=True,by=('minecraft:bucket',1))
 ws('hamon_temper_'+m,'tempering_furnace',[(f'clay_hardened_{m}_blade',1)],f'tempered_hamon_{m}_blade',time=160,heat=(250,450))
 ws('hamon_finish_'+m,'grindstone_bench',[(f'tempered_hamon_{m}_blade',1),('minecraft:flint',1)],f'hamon_{m}_blade',tool='tongs',time=80)

for m,(ru,col) in METALS.items():
 core='steel' if m=='diamond' else 'damascus' if m=='netherite' else m
 extra=[('minecraft:diamond',2)] if m=='diamond' else [('minecraft:netherite_ingot',1)] if m=='netherite' else []
 for key,wru,part,cost in WEAPONS:
  name(m+'_'+key,wru+' из '+ru);GEAR.append(m+'_'+key)
  component=f'hamon_{core}_blade' if key=='katana' and core in ['steel','damascus'] else f'finished_{core}_{part}'
  inputs=[(component,cost),('wooden_shaft',3 if key=='pike' else 2 if key in ['spear','halberd','naginata'] else 1),('leather_strap',2),(core+'_rivets',2)]
  if key=='flail':inputs.append((core+'_rings',4))
  ws('assemble_'+m+'_'+key,'armorer_bench',inputs+extra,m+'_'+key,tool='smith_hammer',time=160 if key in ['katana','rapier','longsword'] else 80)
 for key,sru,cost in SHIELDS:
  id=m+'_'+key+'_shield';name(id,sru+' из '+ru);GEAR.append(id)
  inputs=[(f'finished_{core}_plate',cost),('shield_board',2 if key in ['scutum','pavise'] else 1),('leather_strap',2),(core+'_rivets',4)]
  ws('assemble_'+id,'armorer_bench',inputs+extra,id,tool='rivet_punch',time=60+cost*20)
 for style,sru in STYLES.items():
  for i,p in enumerate(PIECES):
   id=f'{m}_{style}_{p}';name(id,f'{PIECES_RU[i]} · {sru.lower()} из {ru}');GEAR.append(id)
   if style=='mail':part=core+'_mail_panel';cost=[4,8,7,4][i]
   elif style=='lamellar':part=core+'_lamellae';cost=[6,12,10,6][i]
   else:part=f'finished_{core}_plate';cost=([3,6,5,3] if style=='brigandine' else [5,8,7,4])[i]
   inputs=[(part,cost),('padded_lining',1),('leather_strap',2),(core+'_rivets',4 if style=='mail' else 8)]
   ws('assemble_'+id,'armorer_bench',inputs+extra,id,tool='rivet_punch',time=120 if style=='mail' else 160 if style=='plate' else 100)

# Ore resources and data-driven generation; only newly generated chunks receive deposits.
for ore,ru,drop,size,lo,hi,tries in [('tin','Оловянная руда','raw_tin',7,0,96,8),('bauxite','Бокситовая руда','bauxite',6,32,144,7),('rutile','Рутиловая руда','rutile',4,-48,24,4),('dolomite','Доломитовая руда','dolomite',7,-16,80,7)]:
 targets=[]
 for prefix,tag in [('', 'minecraft:stone_ore_replaceables'),('deepslate_','minecraft:deepslate_ore_replaceables')]:
  id=prefix+ore+'_ore';name(id,('Глубинная '+ru.lower()) if prefix else ru,True)
  targets.append({'target':{'predicate_type':'minecraft:tag_match','tag':tag},'state':{'Name':rid(id)}})
  put(Path('data')/NS/'loot_table/blocks'/f'{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[{'type':'minecraft:item','name':rid(id),'conditions':[{'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}]},{'type':'minecraft:item','name':rid(drop),'functions':[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'function':'minecraft:explosion_decay'}]}]}]}]})
  put(Path('assets')/NS/'models/block'/f'{id}.json',{'parent':'minecraft:block/cube_all','textures':{'all':f'{NS}:block/{id}'}})
  put(Path('assets')/NS/'models/item'/f'{id}.json',{'parent':f'{NS}:block/{id}'})
  put(Path('assets')/NS/'blockstates'/f'{id}.json',{'variants':{'':{'model':f'{NS}:block/{id}'}}})
 put(Path('data')/NS/'worldgen/configured_feature'/f'{ore}_ore.json',{'type':'minecraft:ore','config':{'size':size,'discard_chance_on_air_exposure':0.0,'targets':targets}})
 put(Path('data')/NS/'worldgen/placed_feature'/f'{ore}_ore.json',{'feature':rid(ore+'_ore'),'placement':[{'type':'minecraft:count','count':tries},{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':lo},'max_inclusive':{'absolute':hi}}},{'type':'minecraft:biome'}]})
put(Path('data')/NS/'neoforge/biome_modifier/ores.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':[rid(o+'_ore') for o in ['tin','bauxite','rutile','dolomite']],'step':'underground_ores'})
allblocks=list(STATIONS)+[p+o+'_ore' for o in ['tin','bauxite','rutile','dolomite'] for p in ['','deepslate_']]
for s in STATIONS:put(Path('data')/NS/'loot_table/blocks'/f'{s}.json',{'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'}],'entries':[{'type':'minecraft:item','name':rid(s)}]}]})
for tag in ['mineable/pickaxe','needs_iron_tool']:put(Path('data/minecraft/tags/block')/(tag+'.json'),{'replace':False,'values':[rid(b) for b in allblocks]})
for m in BASE:
 feed='minecraft:'+m+'_ingot' if m in ['iron','gold'] else rid(m+'_ingot')
 put(Path('data/c/tags/item/ingots')/(m+'.json'),{'replace':False,'values':[feed]})
for tag,vals in {'swords':[rid(m+'_'+w[0]) for m in METALS for w in WEAPONS], 'head_armor':[rid(m+'_'+s+'_helmet') for m in METALS for s in STYLES], 'chest_armor':[rid(m+'_'+s+'_chestplate') for m in METALS for s in STYLES], 'leg_armor':[rid(m+'_'+s+'_leggings') for m in METALS for s in STYLES], 'foot_armor':[rid(m+'_'+s+'_boots') for m in METALS for s in STYLES]}.items():put(Path('data/minecraft/tags/item')/(tag+'.json'),{'replace':False,'values':vals})
put(Path('data/minecraft/tags/item/enchantable/sword.json'),{'replace':False,'values':['#minecraft:swords']})
put(Path('data/minecraft/tags/item/enchantable/durability.json'),{'replace':False,'values':[rid(i) for i in GEAR]+[rid(i) for i in ['smith_hammer','tongs','rivet_punch','metal_shears']]})
put(Path('data/minecraft/tags/item/piglin_loved.json'),{'replace':False,'values':[rid(i) for i in GEAR if i.startswith('gold_')]})

UI={
 'tab.epocharmory.weapons':'Кузница эпох · Оружие','tab.epocharmory.armor':'Кузница эпох · Доспехи и щиты','tab.epocharmory.workshop':'Кузница эпох · Мастерская',
 'tooltip.epocharmory.reach':'Дальность: %s блока','tooltip.epocharmory.penetration':'Пробитие брони: %s%%','tooltip.epocharmory.type.slash':'Рубящее оружие','tooltip.epocharmory.type.pierce':'Колющее оружие','tooltip.epocharmory.type.blunt':'Дробящее оружие',
 'tooltip.epocharmory.two_handed':'Двуручное: освободи вторую руку','tooltip.epocharmory.longsword_hands':'Двуручный хват: вторая рука должна быть пустой','message.epocharmory.longsword_hands':'Для двуручного меча освободи вторую руку','tooltip.epocharmory.backstab':'Удар со спины: +50%% урона','tooltip.epocharmory.shield_break':'Отключает щит противника','tooltip.epocharmory.shield_block':'Блок: ближний бой %s%% · снаряды %s%%',
 'tooltip.epocharmory.shield_stamina':'Стойкость: %s за удар · парирование %s с','tooltip.epocharmory.shield_slow':'Нагрузка при блоке: −%s%% скорости','tooltip.epocharmory.parry':'Ранний блок — полное парирование',
 'tooltip.epocharmory.style.mail':'Кольчуга: рубящие −12%%, дробящие +10%% при полном комплекте','tooltip.epocharmory.style.lamellar':'Ламелляр: снаряды −12%% при полном комплекте','tooltip.epocharmory.style.brigandine':'Бригантина: физический урон −6%% при полном комплекте','tooltip.epocharmory.style.plate':'Латы: рубящие −12%%, колющие −8%% при полном комплекте',
 'tooltip.epocharmory.piece_weight':'Нагрузка предмета: −%s%% скорости','tooltip.epocharmory.composite':'Композит на металлической основе','tooltip.epocharmory.hot_part':'Горячая деталь: рабочее окно 3 минуты','tooltip.epocharmory.reheat':'Остыло? Нагрей заново в горне.',
 'screen.epocharmory.search':'Поиск рецептов…','screen.epocharmory.work':'Работать','screen.epocharmory.fill':'Вложить','screen.epocharmory.inputs':'Материалы','screen.epocharmory.tool':'Инструм.','screen.epocharmory.result':'Выход','screen.epocharmory.strokes':'Рабочих действий: %s','screen.epocharmory.required_tool':'Инструмент: %s','screen.epocharmory.recipe_count':'Найдено рецептов: %s',
 'screen.epocharmory.status.0':'Готово к работе','screen.epocharmory.status.1':'Не хватает материалов','screen.epocharmory.status.2':'Нужен инструмент','screen.epocharmory.status.3':'Металл остыл — нужен горн','screen.epocharmory.status.4':'Нужная температура / топливо','screen.epocharmory.status.5':'Забери готовые предметы',
 'message.epocharmory.empty_inputs':'Сначала освободи входные ячейки станка.','message.epocharmory.missing_items':'В инвентаре не хватает материалов.'}
LANG.update(UI);EN.update(UI)
EN.update({'tooltip.epocharmory.longsword_hands':'Two-handed grip: your offhand must be empty','message.epocharmory.longsword_hands':'Empty your offhand to use the two-handed sword'})
for k in ITEMS:simple_model(k)
put(Path('assets')/NS/'lang/ru_ru.json',LANG);put(Path('assets')/NS/'lang/en_us.json',EN)
put(Path('data')/NS/'advancement/recipes/root.json',{'criteria':{'has_ingot':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:iron_ingot'}]}}},'rewards':{'recipes':[rid(s) for s in STATIONS]+[rid(s) for s in ['smiths_codex','smith_hammer','tongs','rivet_punch','metal_shears','leather_strap','padded_lining','wooden_shaft','shield_board','flux','clay_slurry']]}})

# A complete text catalogue; custom recipes are also browsable in every station.
lines=['# Все рецепты мастерской','', 'Расходы ниже — игровые партии, а не реальные килограммы металла. Топливо указано отдельно в README_RU.md.','']
for station,sru in STATIONS.items():
 lines+=['## '+sru,'','| Результат | Материалы | Инструмент | Время / действия | Нагрев |','|---|---|---|---|---|']
 def n(id):return LANG.get('item.'+id.replace(':','.'),LANG.get('block.'+id.replace(':','.'),id))
 for key,r in RECIPES:
  if r['station']!=station:continue
  inp='; '.join(str(p['count'])+' × '+n(p['item']) for p in r['inputs'])
  manual=station in ['forging_anvil','wire_bench','riveting_bench','grindstone_bench','armorer_bench']
  timing=str(math.ceil(r['time']/20))+' действий' if manual else str(r['time']/20)+' с'
  lines.append(f"| {r['result']['count']} × {n(r['result']['id'])} | {inp} | {n(r.get('tool','—'))} | {timing} | {str(r.get('min_heat',0))+'–'+str(r.get('max_heat',1600))+' °C' if r.get('min_heat') else '—'} |")
 lines+=['']
(ROOT/'CRAFTS_RU.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(ROOT/'tools/content_manifest.json').write_text(json.dumps({'items':sorted(ITEMS),'gear':GEAR,'blocks':allblocks,'custom_recipes':len(RECIPES)},ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Generated {len(ITEMS)} items, {len(GEAR)} equipment, {len(RECIPES)} workshop recipes, {len(allblocks)} blocks')
