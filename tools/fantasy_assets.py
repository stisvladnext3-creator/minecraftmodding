"""Reproducible fantasy art pass; preserves all item IDs and gameplay data.

The imagegen-authored material sheet is baked into exact UV islands. Geometry,
UV baking, inventory projections and technical previews share the same meshes.
Run with Python 3 and Pillow: python tools/fantasy_assets.py
"""
from pathlib import Path
import json, math
from functools import lru_cache
from PIL import Image
import generate_armor as armor
import generate_weapons as weapons
import generate_shields as shields

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/epocharmory'
ART = ROOT / 'art/fantasy'
ARMOR_SIZE = 512
PALETTE = armor.metals()
ACCENTS = {'iron': (106, 190, 216), 'bronze': (76, 199, 162),
           'gold': (185, 100, 224), 'steel': (77, 202, 233),
           'damascus': (184, 112, 237), 'aluminium': (86, 191, 220),
           'magnesium': (130, 209, 142), 'titanium': (131, 154, 240),
           'diamond': (68, 239, 230), 'netherite': (228, 105, 62)}
STYLES = {'mail': 'RANGER', 'lamellar': 'DRAKE WARDEN',
          'brigandine': 'NIGHT HUNTER', 'plate': 'CROWN SENTINEL'}


@lru_cache(maxsize=None)
def tile(index):
    """Sample well inside each generated tile, excluding tile boundaries."""
    sheet = Image.open(ART / 'material_atlas.png').convert('RGB')
    x, y = index % 4, index // 4
    w, h = sheet.size
    return sheet.crop((round((x+.06)*w/4), round((y+.06)*h/4),
                       round((x+.94)*w/4), round((y+.94)*h/4))).resize((64, 64), Image.Resampling.BOX)


@lru_cache(maxsize=None)
def swatch(role, metal, style='plate'):
    col = PALETTE[metal][1]
    accent = ACCENTS[metal]
    base_role = role
    if role in ('visor', 'lining', 'fuller'):
        index, base = 2, (19, 26, 39)
    elif role in ('gem', 'rune'):
        index = 14 if metal in ('damascus', 'gold', 'titanium') else 13 if metal == 'netherite' else 15 if metal in ('bronze', 'magnesium') else 12
        base = accent
    elif role in ('trim', 'rim'):
        index, base = 1, (173, 126, 52) if metal != 'netherite' else (156, 86, 54)
    elif role in ('leather', 'grip'):
        index, base = 7, (62, 39, 33)
    elif role in ('wood', 'back'):
        index, base = 11, (81, 55, 39)
    elif role in ('padded',):
        index, base = 6, (32, 38, 48)
    elif role == 'cloth':
        index, base = (4, (88, 27, 43)) if style == 'brigandine' else (5, (27, 65, 72))
    elif role == 'mail':
        index, base = 8, tuple(round(c*.73) for c in col)
    elif role == 'lamellar':
        index, base = 9, tuple(round(c*.87) for c in col)
    else:
        index, base = (3 if metal == 'damascus' else 0), col
        if role == 'edge': base = tuple(min(255, round(c*1.2+8)) for c in col)
        if role == 'spine': base = tuple(round(c*.78) for c in col)
    source = tile(index)
    out = Image.new('RGBA', source.size)
    pixels = []
    for rgb in source.getdata():
        lum = sum(rgb)/3/128
        contrast = .52 if role in ('mail', 'lamellar', 'gem', 'rune') else .24
        k = 1 + max(-.7, min(.8, lum-1))*contrast
        pixels.append(tuple(max(0, min(255, round(v*k))) for v in base)+(255,))
    out.putdata(pixels)
    return out


def patch(w, h, role, metal, style='plate'):
    # Exact nearest-neighbour UV bake; source art is never stretched per-face.
    src = swatch(role, metal, style)
    result = Image.new('RGBA', (w, h))
    for y in range(0, h, 64):
        for x in range(0, w, 64): result.paste(src, (x, y))
    return result


class FantasyArmor(armor.Armor):
    def __init__(self, style):
        super().__init__(style)
        self.facets = []

    def face(self, slot, bone, points, role, outward):
        p = [list(v) for v in points]
        u = [p[1][i]-p[0][i] for i in range(3)]
        v = [p[2][i]-p[0][i] for i in range(3)]
        n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        if sum(n[i]*outward[i] for i in range(3)) < 0: p.reverse()
        w = max(math.dist(p[0], p[1]), math.dist(p[2], p[3]))
        h = max(math.dist(p[1], p[2]), math.dist(p[3], p[0]))
        self.facets.append((slot, bone, {'v': p, 'material': role,
                                      'pixels': [max(1, math.ceil(w*2)), max(1, math.ceil(h*2))]}))

    def loft(self, slot, bone, rings, role='metal', bevel=.7):
        """Closed eight-sided shell; rings are (y,width,depth,cx,cz)."""
        sections=[]
        for y,w,d,cx,cz in rings:
            b=min(bevel,w/4,d/4)
            sections.append([(cx+x,y,cz+z) for x,z in
                [(-w/2+b,-d/2),(w/2-b,-d/2),(w/2,-d/2+b),(w/2,d/2-b),
                 (w/2-b,d/2),(-w/2+b,d/2),(-w/2,d/2-b),(-w/2,-d/2+b)]])
        for j in range(len(sections)-1):
            for i in range(8):
                a,b=sections[j],sections[j+1]
                mid=[sum(p[k] for p in (a[i],a[(i+1)%8],b[i],b[(i+1)%8]))/4 for k in range(3)]
                cx=(rings[j][3]+rings[j+1][3])/2;cz=(rings[j][4]+rings[j+1][4])/2
                self.face(slot,bone,[a[i],a[(i+1)%8],b[(i+1)%8],b[i]],role,(mid[0]-cx,0,mid[2]-cz))
        for j,direction in ((0,-1),(-1,1)):
            section=sections[j]
            center=tuple(sum(p[k] for p in section)/8 for k in range(3))
            for i in range(0,8,2):
                self.face(slot,bone,[center,section[i],section[(i+1)%8],section[(i+2)%8]],role,(0,direction,0))
        self.cubes+=1

    def resolve(self):
        super().resolve()
        for slot,bone,f in self.facets:
            self.pieces[slot].setdefault(bone,[]).append(f);self.faces.append(f)


def gem(a, slot, bone, x, y, z, scale=1, role='gem'):
    # Raised four-facet diamond, with a separate metal setting.
    a.box(slot,bone,x-scale*.75,y-scale*.9,z+.12,scale*1.5,scale*1.8,.35,'trim')
    v=[(x,y-scale*.8,z),(x+scale*.6,y,z),(x,y+scale*.8,z),(x-scale*.6,y,z)]
    tip=(x,y,z-scale*.4)
    for i in range(4):
        mid=tuple((v[i][k]+v[(i+1)%4][k])/2 for k in range(3))
        # Four nondegenerate quads meet at the raised jewel tip.
        q1=tuple((v[i][k]*2+tip[k])/3 for k in range(3))
        q2=tuple((v[(i+1)%4][k]*2+tip[k])/3 for k in range(3))
        a.face(slot,bone,[v[i],v[(i+1)%4],q2,q1],role,(0,0,-1))
    inner=[tuple((p[k]*2+tip[k])/3 for k in range(3)) for p in v]
    a.face(slot,bone,inner,role,(0,0,-1))


def helmet(a):
    s=a.style
    # All silhouettes taper and bevel; none use the old rectangular cap.
    if s=='plate':
        a.loft('head','head',[(-9.5,6.5,6.6,0,0),(-8,9.4,9,0,0),(-3.5,9.4,9.4,0,0),(0,7.5,8.8,0,.3)])
        a.box('head','head',-3.75,-5.5,-4.9,7.5,.75,.5,'visor')
        a.box('head','head',-.25,-6,-5.2,.5,5.5,.5,'trim')
        # Paladin crest and cheek guards, distinct from the drake horns.
        a.loft('head','head',[(-11.2,1,4.8,0,.5),(-9.3,2,6,0,.3)],'trim',.3)
        for sign in (-1,1):
            a.loft('head','head',[(-3.8,1.8,2.2,sign*3.75,-3.4),(-.2,1.1,2.1,sign*3.1,-3.6)],'trim',.3)
        gem(a,'head','head',0,-7.2,-4.95,.7)
    elif s=='lamellar':
        a.loft('head','head',[(-9.3,6,6.6,0,.1),(-7.7,9.2,9,0,0),(-5,9.3,9.3,0,0)],'metal')
        for sign in (-1,1):
            a.loft('head','head',[(-5,2.5,8,sign*3.6,.6),(.6,2,6.5,sign*3.8,1.6)],'lamellar',.5)
            # Swept segmented horns with a restrained width.
            for i in range(3):
                a.loft('head','head',[(-8-i*.9,1.5-i*.3,2-i*.3,sign*(4.3+i*.5),.9+i*.6),(-7.2-i*.9,1.8-i*.3,2.2-i*.3,sign*(4+i*.5),.6+i*.6)],'trim',.3)
        a.box('head','head',-3.5,-5.2,-4.75,7,1,.5,'visor')
        a.box('head','head',-.5,-5,-4.9,1,4.5,.6,'metal')
        a.loft('head','head',[(-4.9,8.5,1.5,0,4),(1,8,1.5,0,4.2)],'lamellar',.5)
        gem(a,'head','head',0,-7,-4.7,.65)
    elif s=='brigandine':
        a.loft('head','head',[(-10,5.7,6.5,0,.6),(-8,9.2,9,0,.3),(-3,9,9.2,0,.4),(.3,7,8,0,.9)],'cloth')
        a.box('head','head',-3.25,-5.75,-4.65,6.5,1,.5,'visor')
        a.loft('head','head',[(-4.75,6.5,1.3,0,-4.4),(-.25,4.5,1.8,0,-3.9)],'metal',.4)
        a.box('head','head',-.25,-8.5,-4.75,.5,2,.5,'trim')
        for sign in (-1,1): gem(a,'head','head',sign*2.3,-5.25,-4.98,.27)
    else:
        a.loft('head','head',[(-9.25,6.4,7,0,.3),(-7.75,9,9,0,.2),(-5.3,9.4,9,0,.3)],'metal')
        for sign in (-1,1):
            a.loft('head','head',[(-5.3,2,8,sign*3.8,.6),(1.2,2.3,7,sign*3.6,1)],'mail',.4)
        a.loft('head','head',[(-5,7.5,1.2,0,4.2),(1.5,8.6,1.3,0,4.1)],'mail')
        a.box('head','head',-.4,-7.3,-4.8,.8,4.2,.5,'trim')
        a.box('head','head',-4,-6.1,-4.7,8,.5,.5,'trim')
        gem(a,'head','head',0,-8,-4.5,.55)


def torso(a):
    s=a.style
    mat={'plate':'metal','mail':'mail','lamellar':'lamellar','brigandine':'cloth'}[s]
    a.loft('chest','body',[(.25,8.7,4.9,0,0),(3,9.4,6.2,0,-.15),(8.8,7.5,4.8,0,0),(11.3,8.4,5.1,0,0)],mat)
    # Each construction has its own physical front, not a material swap.
    if s=='plate':
        a.loft('chest','body',[(1,6.6,.9,0,-3),(4,7.4,1.1,0,-3.15),(7.8,5.4,.8,0,-2.9)],'metal',.4)
        for sign in (-1,1):
            a.box('chest','body',sign*2.25-.25,1,-3.85,.5,3.75,.3,'trim')
        a.loft('chest','body',[(1.6,1.2,.8,0,-3.7),(4.5,1,.9,0,-3.8),(8.4,.6,.6,0,-3.2)],'trim',.2)
        for y in (7.8,8.6):
            a.loft('chest','body',[(y,6.6,.8,0,-2.9),(y+.55,6.8,.9,0,-3)],'metal',.3)
    elif s=='lamellar':
        for row in range(4):
            y=1.1+row*1.8;z=-3.25+row*.13
            for col in range(4):
                cx=-2.7+col*1.8
                a.loft('chest','body',[(y,1.65,.5,cx,z),(y+1.65,1.6,.65,cx,z-.15)],'lamellar',.25)
                a.box('chest','body',cx-.25,y+.3,z-.6,.5,.5,.3,'trim')
        for side in (-1,1):
            a.loft('chest','body',[(7.7,2.6,1,side*2.55,-2.6),(11.8,3.7,1,side*2.6,-3.2)],'lamellar',.4)
    elif s=='brigandine':
        for sign in (-1,1):
            a.loft('chest','body',[(1.2,2.6,.8,sign*2.2,-3),(5.6,2.8,.9,sign*2.1,-3.15),(8,2.2,.7,sign*1.9,-2.8)],'metal',.35)
        for i in range(7):
            a.box('chest','body',-3.5+i*.9,1.2+i*.9,-3.9,.9,.65,.4,'leather')
        a.box('chest','body',1.5,6.2,-4.1,1.25,.8,.3,'trim')
        # Short split mantle, weighted behind the torso, not a full-length cape.
        a.loft('chest','body',[(0,8,1,0,2.65),(7.7,9.4,1.1,0,3.4)],'cloth',.45)
    a.loft('chest','body',[(-.5,6.5,5.5,0,0),(.3,7.8,5.9,0,0)],'trim',.6)
    a.centered('chest','body',9.1,8.6,.85,5.6,'leather')
    a.box('chest','body',-.8,9.05,-3,1.6,1,.5,'trim')
    gem(a,'chest','body',0,3.5,-4.35 if s=='plate' else -3.85,.9 if s=='plate' else .7)
    # Split articulated faulds stay above the leg pivots.
    for sign in (-1,1):
        if s!='lamellar':
            a.loft('chest','body',[(10.4,3.1,1.1,sign*2.2,-2.65),(11.8,3.4,1.1,sign*2.35,-3)],mat,.3)
    if s=='mail':
        for i in range(6): a.box('chest','body',-3+i,1.2+i*.7,-3.25,1,.65,.4,'leather')
    for bone,cx,sign in [('right_arm',-1,-1),('left_arm',1,1)]:
        a.loft('chest',bone,[(-1.4,4.4,4.5,cx,0),(8.8,4.2,4.3,cx,0)],'padded',.4)
        size=7.2 if s=='plate' else 6.7 if s=='lamellar' else (6.4 if sign<0 else 5.3) if s=='brigandine' else 5.8
        a.loft('chest',bone,[(-3.1,size-1,5.4,cx+sign*.25,0),(-1.9,size,6.2,cx+sign*.35,0),(.7,size-.4,6,cx+sign*.35,0)],'lamellar' if s=='lamellar' else 'metal',.8)
        a.loft('chest',bone,[(.8,size-.2,6.1,cx+sign*.35,0),(1.3,size-.3,6.2,cx+sign*.35,0)],'trim',.7)
        a.loft('chest',bone,[(1.4,5.2,5.4,cx,0),(3.7,4.7,4.9,cx,0)],mat,.6)
        a.loft('chest',bone,[(5.5,4.8,5,cx,0),(8.9,4.5,4.8,cx,0)],'metal',.6)
        a.centered('chest',bone,8.9,4.8,.5,5,'trim',cx)
        gem(a,'chest',bone,cx,-1.2,-3.25,.55)
        if s=='lamellar':
            for i in range(2):
                a.loft('chest',bone,[(-4.9+i*.8,1,1.5,cx+sign*(2+i*.4),1+i*1.1),(-2.8+i*.35,2.2,2.2,cx+sign*(1.5+i*.35),.7+i*1.1)],'trim',.3)
        elif s=='plate':
            a.loft('chest',bone,[(-3.9,2.5,2,cx+sign*1.2,1),(-3,3.3,3,cx+sign*1.1,.7)],'trim',.4)


def legs(a):
    s=a.style;mat='mail' if s=='mail' else 'lamellar' if s=='lamellar' else 'metal'
    a.centered('legs','body',10.3,8.3,2.2,4.5,'lining')
    for bone,sign in [('right_leg',-1),('left_leg',1)]:
        cx=sign*.35
        # The soft liner covers the player's actual leg, including the inner
        # face. Only rigid plates shift outward to preserve the centre seam.
        a.loft('legs',bone,[(0,4.4,4.6,0,0),(4.5,4.3,4.5,0,0),(7.5,4.4,4.7,0,0)],'padded',.5)
        # Raised cuisses and knee cops expose dark joints between hard plates.
        a.loft('legs',bone,[(.4,3.5,1.3,cx,-2.5),(4.7,3.2,1.1,cx,-2.65)],mat,.4)
        a.loft('legs',bone,[(5,3.3,1.2,cx,-2.6),(6,3.9,1.8,cx,-2.8),(7.3,3.1,1,cx,-2.5)],'metal',.4)
        a.box('legs',bone,cx-.25,1,-3.3,.5,2.5,.3,'trim')
        gem(a,'legs',bone,cx,6.1,-3.9,.4)
        a.centered('feet',bone,7.5,4.3,4.75,4.5,'lining')
        a.loft('feet',bone,[(7.6,4.2,4.7,cx,0),(10.8,4,4.7,cx,0)],mat,.5)
        a.loft('feet',bone,[(7.55,4.3,4.9,cx,0),(8,4.4,5,cx,0)],'trim',.5)
        a.loft('feet',bone,[(10.7,4,6,cx,-.65),(11.5,4.1,7,cx,-1.2),(12,3.5,6.8,cx,-1.3)],'metal',.7)
        a.loft('feet',bone,[(12,4.2,7.1,cx,-1.2),(12.3,4.3,7.2,cx,-1.2)],'leather',.6)


def armor_texture(a,metal,col):
    image=Image.new('RGBA',(ARMOR_SIZE,ARMOR_SIZE))
    for f in a.faces:
        x,y,x1,y1=f['uv'];w,h=x1-x,y1-y
        p=patch(w,h,f['material'],metal,a.style)
        image.paste(p.resize((w+4,h+4),Image.Resampling.NEAREST),(x-2,y-2));image.paste(p,(x,y))
    return image


def new_weapon(kind):
    a=weapons.Weapon(kind)
    # The grip centre remains (8,8,8); material variants share exact geometry.
    if kind in weapons.POLES:
        a.box(7.5,-3,7.5,8.5,24,8.5,'wood');a.grip(11,5,True)
        for y in (-2,3,13,19):a.box(7,y,7,9,y+.5,9,'trim')
        a.box(7,-4,7,9,-2,9,'metal')
        if kind=='halberd':
            a.profile([(8,20),(12,21),(16,19),(18,20),(18,28),(16,29),(13,26),(8,25)],7,8.5)
            a.profile([(8,23),(4,26),(2,25),(4,22),(8,21)],7,9,'spine')
            a.blade(24,31,3)
        elif kind=='naginata':
            a.curved_blade(20,31,4,3)
            a.box(6,19,6.5,10,20,9.5,'trim')
        else:
            a.blade(22 if kind=='spear' else 25,31,5 if kind=='spear' else 3.5,leaf=True)
            if kind=='spear':
                a.profile([(8,23),(4.5,22),(5,25),(8,27),(11,25),(11.5,22)],7,9,'trim')
        a.box(7,20,6.5,9,22,7,'gem')
    elif kind in ('arming_sword','longsword','gladius','dagger','katana','sabre','rapier','kukri','katar'):
        top=12 if kind in ('longsword','katana','sabre') else 11
        if kind=='katar':
            a.box(4.5,4,7,5.5,14,9,'spine');a.box(10.5,4,7,11.5,14,9,'spine')
            a.box(5,7,7,11,9,9,'leather')
            a.profile([(4,13),(12,13),(11,18),(8,26),(5,18)],7.5,8.5)
            a.box(7.5,13,7,8.5,21,9,'trim');a.box(7.5,14,6.5,8.5,17,7,'gem')
        else:
            a.grip(top,2 if kind in ('longsword','katana') else 4);a.pommel(1 if kind in ('longsword','katana') else 3)
            if kind=='katana':
                a.box(5.5,11.5,6.5,10.5,12.5,9.5,'trim');a.curved_blade(13,31,3.5,2.5)
                a.box(7,12.5,7,9,14,9,'gem')
            elif kind=='sabre':
                a.curved_blade(13,30,4.5,3);a.guard(12,7)
                a.ring(11,8,8,5,9,1,'xy','trim')
                a.box(7,13,6.5,9,15,7,'gem')
            elif kind=='rapier':
                a.blade(12,31,1.5);a.guard(11,8)
                a.ring(8,10,8,7,4,1,'xy','trim');a.ring(8,10,8,6,4,1,'zy','trim')
                a.ring(10.5,7,8,5,8,1,'xy','metal')
                a.box(7,10,6.5,9,11,7,'gem')
            elif kind=='kukri':
                a.profile([(7,12),(10,13),(12,17),(16,20),(16,25),(14,27),(11,24),(8,19)],7.5,8.5)
                a.guard(11,5);a.box(7,12,7,9,14,9,'trim')
                a.box(12,20,7,13,23,7.5,'gem')
            else:
                end={'arming_sword':29,'longsword':31,'dagger':23,'gladius':26}[kind]
                width={'arming_sword':4,'longsword':5,'dagger':3,'gladius':5}[kind]
                a.blade(top+1,end,width,leaf=kind=='gladius')
                span=6 if kind=='dagger' else 10 if kind=='longsword' else 8
                a.profile([(8-span/2,top-2),(8-span/2-1,top),(7,top+1),(8,top+2),(9,top+1),(9+span/2,top),(8+span/2,top-2),(9,top),(7,top)],7,9,'trim')
                a.box(7,top,6.5,9,top+2,9.5,'metal')
                a.box(7.5,top+.5,6,8.5,top+1.5,6.5,'gem')
                if kind=='longsword':
                    a.profile([(5.5,15),(7,16),(8,15),(9,16),(10.5,15),(10,18),(8,17),(6,18)],7,9,'spine')
                # Inlaid coloured fuller; not detached floating particles.
                for y in range(top+4,end-3,3):
                    a.box(7.5,y,7,8,y+1,7.5,'rune')
    else:
        a.box(7,3,7,9,22,9,'wood');a.grip(12,4);a.pommel(2)
        if kind=='mace':
            a.box(6.5,18,6.5,9.5,26,9.5,'spine')
            for z in (6,9):
                a.profile([(5,18),(11,18),(12,22),(10.5,26),(5.5,26),(4,22)],z,z+1,'metal')
            a.box(7.5,18,4.5,8.5,26,11.5,'metal')
            a.box(6,17.5,6,10,18.5,10,'trim');a.box(6,26,6,10,27,10,'trim')
            a.box(7,20,4,9,23,4.5,'gem')
        elif kind=='war_hammer':
            a.box(2,19,5.5,12,25,10.5,'metal')
            a.box(1,18.5,5,3,25.5,11,'edge')
            a.box(3,18.5,5,4,25.5,11,'trim')
            a.profile([(12,20),(15,20),(18,17),(17,21),(14,24),(12,24)],7,9,'spine')
            a.box(7,20,5,10,24,5.5,'trim');a.box(7.5,21,4.5,9.5,23,5,'gem')
        elif kind=='battleaxe':
            a.box(6.5,19,6.5,9.5,27,9.5,'spine')
            a.profile([(9,20),(13,19),(16,17),(19,19),(18,26),(16,29),(13,27),(11,24),(9,24)],7,9)
            a.profile([(7,22),(4,24),(2,23),(3,26),(5,27),(7,25)],7,9)
            a.box(6,19,6,10,20,10,'trim');a.box(6,26,6,10,27,10,'trim')
            a.box(7,21,6,9,24,6.5,'gem')
        else:
            cx,cy=(8,23) if kind=='morningstar' else (16,20)
            if kind=='flail':
                # Real alternating open links; never a solid diagonal bar.
                for i in range(5):a.ring(8+i*1.5,23-i*.6,8,3,3,1,'xy' if i%2==0 else 'zy','trim' if i%2 else 'metal')
            a.box(cx-2.5,cy-2.5,6,cx+2.5,cy+2.5,10,'spine')
            a.box(cx-2,cy-2,5.5,cx+2,cy+2,10.5,'metal')
            for axis in (-3,-2,-1,1,2,3):
                c=[cx,cy,8];c[abs(axis)-1]+=2.5*(1 if axis>0 else -1)
                a.spike(*c,axis,length=3)
            a.box(cx-1,cy-1,5,cx+1,cy+1,5.5,'gem')
        a.box(6.5,15,6.5,9.5,16,9.5,'trim')
    # Grip bindings are geometry, with highlights on their upper edges.
    if kind!='katar':
        for y in (5,7,9):a.box(6.5,y,6.5,9.5,y+.5,9.5,'leather')
    a.resolve()
    return a


def weapon_texture(a,metal,col):
    image=Image.new('RGBA',(256,256))
    for f in a.faces:
        x,y,x1,y1=f['uv'];p=patch(x1-x,y1-y,f['role'],metal)
        image.paste(p.resize((p.width+2,p.height+2),Image.Resampling.NEAREST),(x-1,y-1));image.paste(p,(x,y))
    return image


def weapon_color(role,col,metal,x,y,side):
    return swatch(role,metal).getpixel((x%64,y%64))


def shield_skin(kind,metal,col,mask,border,bossmask):
    im=Image.new('RGBA',(32,32));p=im.load()
    for x,y in mask:
        role='trim' if (x,y) in border else 'metal' if (x,y) in bossmask else 'wood' if kind=='round' else 'cloth' if kind in ('kite','heater','pavise','targe') else 'metal'
        color=swatch(role,metal,'brigandine' if kind in ('heater','targe') else 'plate').getpixel((x*2%64,y*2%64))
        # Heraldic diamond and engraved rays are legible at inventory size.
        d=abs(x-15.5)+abs(y-15.5)
        if (x,y) not in border and (x,y) not in bossmask:
            if 3<=d<=4.5 or abs(x-15.5)<1 and 5<abs(y-15.5)<8:color=swatch('trim',metal).getpixel((x,y))
            if d<2.5:color=ACCENTS[metal]+(255,)
            if kind in ('scutum','pavise') and abs(abs(x-15.5)-abs(y-15.5)*.5)<.6:color=swatch('trim',metal).getpixel((x,y))
        if (x,y) in border and (x+y)%4==0:color=tuple(min(255,v+35) for v in color[:3])+(255,)
        p[x,y]=color
    return im


def export_armor():
    report={}
    armor.SIZE=ARMOR_SIZE
    for style in STYLES:
        a=FantasyArmor(style);helmet(a);torso(a);legs(a);a.resolve();height=armor.pack(a)
        data={'format':1,'geometry':'faceted_fantasy_mesh','texture_size':ARMOR_SIZE,'pixel_density':2,
              'pieces':{s:{b:[{'v':f['v'],'uv':f['uv']} for f in fs] for b,fs in bones.items()} for s,bones in a.pieces.items()}}
        (ASSETS/'armor_geometry'/f'{style}.json').write_text(json.dumps(data,separators=(',',':'))+'\n')
        for metal,(_,col) in PALETTE.items():
            tex=armor_texture(a,metal,col)
            for layer in (1,2):tex.save(ASSETS/'textures/models/armor'/f'{metal}_{style}_layer_{layer}.png',optimize=True)
        report[style]={'name':STYLES[style],'shells_and_details':a.cubes,'faces':len(a.faces),'used_atlas_height':height}
    (ROOT/'tools/armor-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Fantasy armor:',json.dumps(report))


def main():
    ART.mkdir(exist_ok=True,parents=True)
    export_armor()
    weapons.make=new_weapon;weapons.texture=weapon_texture;weapons.color=weapon_color
    weapons.main()
    shields.skin=shield_skin
    shields.main()
    (ART/'palette.json').write_text(json.dumps({'styles':STYLES,'accents':ACCENTS,'material_source':'material_atlas.png'},indent=2)+'\n')
    print('Fantasy assets exported. Item IDs, recipes and combat stats are unchanged.')


if __name__=='__main__': main()
