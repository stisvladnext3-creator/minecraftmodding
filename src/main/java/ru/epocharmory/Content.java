package ru.epocharmory;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.*;

public final class Content {
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(EpochArmory.ID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,EpochArmory.ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,EpochArmory.ID);
    public static final Map<String,DeferredItem<? extends Item>> ALL=new LinkedHashMap<>();
    public static final List<ArmorItem.Type> PIECES=List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS);
    public static final Map<String,Holder<ArmorMaterial>> ARMOR_MATERIALS=new LinkedHashMap<>();
    public static final Set<String> HOT=new HashSet<>();
    public static final Set<String> TOOLS=Set.of("smith_hammer","tongs","rivet_punch","metal_shears");
    static {
        for(String id:List.of("iron_bloom","slag","flux","tin_ingot","raw_tin","bauxite","rutile","dolomite","alumina","magnesia","titanium_sponge","leather_strap","padded_lining","wooden_shaft","shield_board","clay_slurry")) simple(id);
        hot("folded_billet");
        for(String metal:List.of("steel","damascus")){simple("clay_hardened_"+metal+"_blade");simple("tempered_hamon_"+metal+"_blade");simple("hamon_"+metal+"_blade");}
        for(String id:TOOLS) ALL.put(id,ITEMS.register(id,()->new WorkshopTool(new Item.Properties().durability(512))));
        ALL.put("smiths_codex",ITEMS.register("smiths_codex",()->new CodexItem(new Item.Properties().stacksTo(1))));
        for(Metal m:Metal.values()) {
            if(!m.composite()) {
                if(m!=Metal.IRON && m!=Metal.GOLD) simple(m.key+"_ingot");
                hot("hot_"+m.key+"_billet");
                for(String shape:List.of("short_blade","long_blade","head","plate")) {
                    hot("hot_"+m.key+"_"+shape);
                    for(String stage:List.of("quenched","tempered","finished"))simple(stage+"_"+m.key+"_"+shape);
                }
                for(String part:List.of("wire","rings","mail_panel","lamellae","rivets"))simple(m.key+"_"+part);
            }
            for(ArmorStyle style:ArmorStyle.values()) {
                String key=m.key+"_"+style.key;
                Holder<ArmorMaterial> material=MATERIALS.register(key,()->makeArmorMaterial(m,style));
                ARMOR_MATERIALS.put(key,material);
                for(var piece:PIECES) {
                    String id=key+"_"+piece.getName();
                    ALL.put(id,ITEMS.register(id,()->new HistoricalArmor(material,piece,props(m).durability(piece.getDurability(Math.max(10,m.uses/30))),m,style)));
                }
            }
            for(WeaponKind kind:WeaponKind.values()) {
                String id=m.key+"_"+kind.key;
                ALL.put(id,ITEMS.register(id,()->new HistoricalWeapon(m,kind)));
            }
            for(ShieldKind kind:ShieldKind.values()) {
                String id=m.key+"_"+kind.key+"_shield";
                ALL.put(id,ITEMS.register(id,()->new HistoricalShield(m,kind)));
            }
        }
        TABS.register("weapons",()->CreativeModeTab.builder().title(Component.translatable("tab.epocharmory.weapons"))
            .icon(()->stack("steel_longsword")).displayItems((p,o)->ALL.values().stream().map(DeferredItem::get).filter(i->i instanceof HistoricalWeapon).forEach(o::accept)).build());
        TABS.register("armor",()->CreativeModeTab.builder().title(Component.translatable("tab.epocharmory.armor"))
            .icon(()->stack("steel_plate_helmet")).displayItems((p,o)->ALL.values().stream().map(DeferredItem::get).filter(i->i instanceof HistoricalArmor || i instanceof HistoricalShield).forEach(o::accept)).build());
        TABS.register("workshop",()->CreativeModeTab.builder().title(Component.translatable("tab.epocharmory.workshop"))
            .icon(()->stack("smiths_codex")).displayItems((p,o)-> {
                Workshops.BLOCK_ITEMS.values().forEach(i->o.accept(i.get()));
                ALL.values().stream().map(DeferredItem::get).filter(i->!(i instanceof HistoricalWeapon || i instanceof HistoricalArmor || i instanceof HistoricalShield)).forEach(o::accept);
            }).build());
    }
    public static Item.Properties props(Metal m) {var p=new Item.Properties();return m==Metal.NETHERITE?p.fireResistant():p;}
    private static void simple(String id) { ALL.put(id,ITEMS.registerSimpleItem(id)); }
    private static void hot(String id) { HOT.add(id);ALL.put(id,ITEMS.register(id,()->new HotPartItem(new Item.Properties().stacksTo(16)))); }
    public static Item item(String id) {return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":")?id:EpochArmory.ID+":"+id));}
    public static ItemStack stack(String id) {return new ItemStack(item(id));}
    private static ArmorMaterial makeArmorMaterial(Metal m,ArmorStyle s) {
        var defense=new EnumMap<ArmorItem.Type,Integer>(ArmorItem.Type.class);
        for(int i=0;i<4;i++) {
            int bonus=m.armorBonus>0 && (i==1||i==3)?1:m.armorBonus<0 && (i==1||i==2)?-1:0;
            defense.put(PIECES.get(i),Math.max(1,s.armor[i]+bonus));
        }
        defense.put(ArmorItem.Type.BODY,defense.get(ArmorItem.Type.CHESTPLATE));
        float tough=s==ArmorStyle.MAIL?0:switch(m) {case STEEL->1;case DAMASCUS->1.5f;case DIAMOND,TITANIUM->2;case NETHERITE->3;default->0;};
        return new ArmorMaterial(defense,m.enchantability,SoundEvents.ARMOR_EQUIP_IRON,m::getRepairIngredient,
            List.of(new ArmorMaterial.Layer(EpochArmory.id(m.key+"_"+s.key))),tough,m==Metal.NETHERITE?.10f:0);
    }
}
