"""Orthographic, textured renders of shipped meshes, not in-game screenshots.

Also bakes the 160 armor inventory sprites from their actual slot geometry.
Requires Pillow and numpy. python tools/render_fantasy.py
"""
from pathlib import Path
import json,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/epocharmory'
ART=ROOT/'art/fantasy'
BONES={'head':(0,0,0),'body':(0,0,0),'right_arm':(-5,2,0),'left_arm':(5,2,0),
       'right_leg':(-1.9,12,0),'left_leg':(1.9,12,0)}
METALS=('iron','bronze','gold','steel','damascus','aluminium','magnesium','titanium','diamond','netherite')
LABELS={'mail':'СЛЕДОПЫТ','lamellar':'ДРАКОНИЙ СТРАЖ','brigandine':'НОЧНОЙ ОХОТНИК','plate':'СТРАЖ КОРОНЫ'}


def armor_mesh(style,slot=None):
    data=json.loads((ASSETS/'armor_geometry'/f'{style}.json').read_text())
    mesh=[]
    for key,bones in data['pieces'].items():
        if slot and slot!=key:continue
        for bone,faces in bones.items():
            origin=np.array(BONES[bone])
            for f in faces:mesh.append((np.array(f['v'])+origin,f['uv']))
    return mesh


def weapon_mesh(kind):
    data=json.loads((ASSETS/'models/item/weapons'/f'{kind}.json').read_text())
    mesh=[]
    for el in data['elements']:
        side,face=next(iter(el['faces'].items()))
        a,b=el['from'],el['to'];x,y,z=a;xx,yy,zz=b
        verts=[(x,y,z),(xx,y,z),(xx,yy,z),(x,yy,z),(x,y,zz),(xx,y,zz),(xx,yy,zz),(x,yy,zz)]
        indices={'north':(1,0,3,2),'south':(4,5,6,7),'west':(0,4,7,3),
                 'east':(5,1,2,6),'down':(0,1,5,4),'up':(3,7,6,2)}[side]
        # Weapon Y grows upward; armor Y grows downward.
        p=np.array([verts[i] for i in indices],dtype=float);p[:,1]*=-1
        # Reflection reverses winding; preserve matching UV corner order.
        p=p[[1,0,3,2]]
        mesh.append((p,[round(v*16) for v in face['uv']]))
    return mesh


def render(mesh,texture,width=256,height=384,yaw=-22,pitch=5,fixed_span=None):
    ay,ax=map(math.radians,(yaw,pitch))
    ry=np.array([[math.cos(ay),0,math.sin(ay)],[0,1,0],[-math.sin(ay),0,math.cos(ay)]])
    rx=np.array([[1,0,0],[0,math.cos(ax),-math.sin(ax)],[0,math.sin(ax),math.cos(ax)]])
    rotation=rx@ry
    transformed=[(p@rotation.T,uv) for p,uv in mesh]
    pts=np.concatenate([p for p,_ in transformed])
    lo,hi=pts.min(axis=0),pts.max(axis=0)
    scale=min((width-12)/(hi[0]-lo[0]),(height-12)/(hi[1]-lo[1]))
    if fixed_span is not None:scale=min((width-12)/24,(height-12)/fixed_span)
    centre=(lo+hi)/2
    out=np.zeros((height,width,4),dtype=np.uint8);depth=np.full((height,width),np.inf)
    tex=np.array(texture.convert('RGBA'));th,tw=tex.shape[:2]
    light=np.array([-.45,-.65,-.9]);light/=np.linalg.norm(light)
    for points,uv in transformed:
        normal=np.cross(points[1]-points[0],points[2]-points[0]);norm=np.linalg.norm(normal)
        if norm<1e-10:continue
        normal/=norm
        if normal[2]>=0:continue
        shade=.58+.42*max(0,float(normal@light))
        pos=points.copy();pos[:,:2]=(pos[:,:2]-centre[:2])*scale+[width/2,height/2]
        x0,y0,x1,y1=uv
        coords=np.array([(x0,y0),(x1,y0),(x1,y1),(x0,y1)],dtype=float)
        for ids in ((0,1,2),(0,2,3)):
            p=pos[list(ids)];uvs=coords[list(ids)]
            xmin=max(0,math.floor(p[:,0].min()));xmax=min(width-1,math.ceil(p[:,0].max()))
            ymin=max(0,math.floor(p[:,1].min()));ymax=min(height-1,math.ceil(p[:,1].max()))
            if xmax<xmin or ymax<ymin:continue
            xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
            den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
            if abs(den)<1e-9:continue
            aa=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den
            bb=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;cc=1-aa-bb
            z=aa*p[0,2]+bb*p[1,2]+cc*p[2,2]
            subdepth=depth[ymin:ymax+1,xmin:xmax+1]
            mask=(aa>=-1e-6)&(bb>=-1e-6)&(cc>=-1e-6)&(z<subdepth)
            u=np.clip((aa*uvs[0,0]+bb*uvs[1,0]+cc*uvs[2,0]).astype(int),0,tw-1)
            v=np.clip((aa*uvs[0,1]+bb*uvs[1,1]+cc*uvs[2,1]).astype(int),0,th-1)
            colors=tex[v,u].copy();colors[:,:,:3]=(colors[:,:,:3]*shade).astype(np.uint8)
            mask &= colors[:,:,3]>128
            subdepth[mask]=z[mask]
            out[ymin:ymax+1,xmin:xmax+1][mask]=colors[mask]
    return Image.fromarray(out)


def font(size):return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',size)


def armor_catalog():
    sheet=Image.new('RGB',(1440,980),(14,19,29));d=ImageDraw.Draw(sheet)
    d.text((45,30),'EPOCH ARMORY / FANTASY REFORGED',font=font(32),fill=(229,210,164))
    d.text((47,80),'Новые игровые модели · сталь / незерит · технический рендер',font=font(18),fill=(142,163,185))
    for col,style in enumerate(LABELS):
        x=25+col*355
        d.rounded_rectangle((x,130,x+340,925),radius=14,fill=(22,29,42),outline=(43,57,77))
        d.text((x+18,150),LABELS[style],font=font(20),fill=(229,210,164))
        mesh=armor_mesh(style)
        for i,metal in enumerate(('steel','netherite')):
            tex=Image.open(ASSETS/'textures/models/armor'/f'{metal}_{style}_layer_1.png')
            image=render(mesh,tex,320,340,yaw=-18 if i==0 else 22)
            sheet.paste(image,(x+10,193+i*350),image)
    sheet.save(ART/'armor_catalog.png')


def icons():
    for style in LABELS:
        for slot,piece in (('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')):
            mesh=armor_mesh(style,slot)
            for metal in METALS:
                tex=Image.open(ASSETS/'textures/models/armor'/f'{metal}_{style}_layer_1.png')
                image=render(mesh,tex,64,64,yaw=-22,pitch=5).resize((32,32),Image.Resampling.NEAREST)
                image.save(ASSETS/'textures/item'/f'{metal}_{style}_{piece}.png')
    print('Baked 160 armor icons from the new meshes.')


def weapon_catalog():
    kinds=list(json.loads((ROOT/'tools/weapon-geometry.json').read_text()))
    labels=['Одноручный меч','Двуручный меч','Булава','Копьё','Катана','Кинжал','Моргенштерн','Боевой топор',
            'Алебарда','Боевой молот','Рапира','Сабля','Гладиус','Кукри','Катар','Нагината','Пика','Кистень']
    sheet=Image.new('RGB',(1440,1040),(14,19,29));d=ImageDraw.Draw(sheet)
    d.text((40,25),'EPOCH ARMORY / 18 НОВЫХ ФОРМ ОРУЖИЯ',font=font(29),fill=(229,210,164))
    d.text((42,72),'Единый масштаб оружия в руке · технический рендер игровых моделей',font=font(18),fill=(142,163,185))
    for i,kind in enumerate(kinds):
        x=15+i%6*238;y=120+i//6*300
        d.rounded_rectangle((x,y,x+225,y+280),radius=12,fill=(22,29,42),outline=(43,57,77))
        tex=Image.open(ASSETS/'textures/item/weapons'/f'steel_{kind}.png')
        model=json.loads((ASSETS/'models/item/weapons'/f'{kind}.json').read_text())
        scale=model['display']['thirdperson_righthand']['scale'][0]
        mesh=[(p*scale,uv) for p,uv in weapon_mesh(kind)]
        image=render(mesh,tex,205,222,yaw=-25,pitch=5,fixed_span=46)
        sheet.paste(image,(x+10,y+8),image)
        d.text((x+12,y+239),labels[i],font=font(17),fill=(214,224,235))
        length=json.loads((ROOT/'tools/weapon-geometry.json').read_text())[kind]['held_length']/16
        d.text((x+12,y+261),f'{length:.2f} блока',font=font(13),fill=(142,163,185))
    sheet.save(ART/'weapon_catalog.png')


if __name__=='__main__':
    icons();armor_catalog();weapon_catalog()
