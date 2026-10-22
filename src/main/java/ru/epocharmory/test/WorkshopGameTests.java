package ru.epocharmory.test;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import ru.epocharmory.*;

@GameTestHolder(EpochArmory.ID)
@PrefixGameTestTemplate(false)
public final class WorkshopGameTests {
    private static ServerPlayer player(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"EpochTest"));
        var pos=h.absolutePos(new BlockPos(2,2,2));p.setPos(pos.getX(),pos.getY(),pos.getZ());p.getInventory().clearContent();
        for(var slot:EquipmentSlot.values())p.setItemSlot(slot,ItemStack.EMPTY);
        return p;
    }
    private static WorkshopEntity station(GameTestHelper h,Station type){
        h.setBlock(3,1,3,Workshops.STATIONS.get(type).get());return (WorkshopEntity)h.getBlockEntity(new BlockPos(3,1,3));
    }
    private static WorkshopRecipe recipe(GameTestHelper h,String id){
        return h.getLevel().getRecipeManager().getAllRecipesFor(WorkshopRecipe.TYPE.get()).stream().filter(r->r.id().equals(EpochArmory.id(id))).findFirst().orElseThrow().value();
    }
    private static void select(GameTestHelper h,WorkshopEntity e,String id){
        var list=WorkshopEntity.recipes(h.getLevel(),e.station());for(int i=0;i<list.size();i++)if(list.get(i).id().equals(EpochArmory.id(id))){e.select(i);return;}throw new IllegalStateException(id);
    }
    private static void fill(GameTestHelper h,WorkshopEntity e,WorkshopRecipe r){
        e.clearContent();int i=0;for(var part:r.inputs()){
            var stack=part.preview();if(stack.getItem() instanceof HotPartItem)HotPartItem.heat(stack,h.getLevel());e.setItem(i++,stack);
        }
        if(!r.tool().isEmpty())e.setItem(6,Content.stack(r.tool()));
        e.temperature=Math.max(20,r.minHeat());
    }
    @GameTest(template="empty") public static void completeEquipmentMatrix(GameTestHelper h){
        int weapons=0,shields=0,armor=0;
        for(Metal m:Metal.values()){
            for(var w:WeaponKind.values()){h.assertTrue(Content.item(m.key+"_"+w.key) instanceof HistoricalWeapon,"Missing weapon");weapons++;}
            for(var s:ShieldKind.values()){h.assertTrue(Content.item(m.key+"_"+s.key+"_shield") instanceof HistoricalShield,"Missing shield");shields++;}
            for(var s:ArmorStyle.values())for(var p:Content.PIECES){h.assertTrue(Content.item(m.key+"_"+s.key+"_"+p.getName()) instanceof HistoricalArmor,"Missing armor");armor++;}
        }
        h.assertTrue(weapons==180&&shields==100&&armor==160,"Exact requested matrix");h.succeed();
    }
    @GameTest(template="empty") public static void everyRecipeLoadsAndMatchesItsIngredients(GameTestHelper h){
        var all=h.getLevel().getRecipeManager().getAllRecipesFor(WorkshopRecipe.TYPE.get());h.assertTrue(all.size()==707,"Expected 707 workshop recipes, got "+all.size());
        for(var holder:all){var r=holder.value();var stacks=new ArrayList<ItemStack>();
            for(var part:r.inputs()){var s=part.preview();h.assertTrue(!s.isEmpty(),"Unresolved ingredient "+holder.id());if(s.getItem() instanceof HotPartItem)HotPartItem.heat(s,h.getLevel());stacks.add(s);}
            while(stacks.size()<6)stacks.add(ItemStack.EMPTY);
            var in=new WorkshopRecipe.Input(Station.named(r.station()),stacks,r.tool().isEmpty()?ItemStack.EMPTY:Content.stack(r.tool()));
            h.assertTrue(r.matches(in,h.getLevel()),"Recipe does not match its own ingredients: "+holder.id());
            h.assertTrue(!r.result().isEmpty()&&r.result().getCount()<=r.result().getMaxStackSize(),"Invalid result: "+holder.id());
        }h.succeed();
    }
    @GameTest(template="empty") public static void allocationCannotSpendOneInputTwice(GameTestHelper h){
        var r=new WorkshopRecipe("forging_anvil",List.of(new WorkshopRecipe.Part("minecraft:iron_ingot",2),new WorkshopRecipe.Part("minecraft:iron_ingot",2)),new ItemStack(Items.GOLD_INGOT),ItemStack.EMPTY,"",20,0,1600,false);
        var inputs=new ArrayList<ItemStack>(List.of(new ItemStack(Items.IRON_INGOT,3),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
        h.assertTrue(!r.matches(new WorkshopRecipe.Input(Station.ANVIL,inputs,ItemStack.EMPTY),h.getLevel()),"Cannot double-count 3 ingots as 4");inputs.set(1,new ItemStack(Items.IRON_INGOT));
        h.assertTrue(r.matches(new WorkshopRecipe.Input(Station.ANVIL,inputs,ItemStack.EMPTY),h.getLevel()),"Must combine multiple stacks");h.succeed();
    }
    @GameTest(template="empty") public static void hotMetalExpiresAndCanBeReheated(GameTestHelper h){
        var e=station(h,Station.ANVIL);var r=recipe(h,"form_steel_plate");fill(h,e,r);
        h.assertTrue(e.check(r)==0,"Freshly heated metal must work");
        var data=new CompoundTag();data.putLong("hot_until",HotPartItem.clock(h.getLevel())-1);e.getItem(0).set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        h.assertTrue(e.check(r)==3&&!e.finish(r),"Cold metal must not forge");HotPartItem.heat(e.getItem(0),h.getLevel());h.assertTrue(e.finish(r),"Reheated metal must forge");
        h.assertTrue(HotPartItem.isHot(e.getItem(8),h.getLevel()),"The shaped output retains its own heat window");h.succeed();
    }
    @GameTest(template="empty") public static void forgingConsumesExactlyOnceAndWearsTool(GameTestHelper h){
        var e=station(h,Station.ANVIL);var r=recipe(h,"form_steel_long_blade");fill(h,e,r);
        h.assertTrue(e.finish(r),"Forge should complete");h.assertTrue(e.getItem(0).isEmpty(),"Exactly 2 billets consumed");
        h.assertTrue(e.getItem(6).getDamageValue()==2,"Hammer wears by 2");h.assertTrue(e.getItem(8).is(Content.item("hot_steel_long_blade")),"Expected long blade");
        h.assertTrue(!e.finish(r)&&e.getItem(8).getCount()==1,"No repeated completion or duplicated output");h.succeed();
    }
    @GameTest(template="empty") public static void quenchReturnsBucketAndRespectsBlockedOutputs(GameTestHelper h){
        var e=station(h,Station.QUENCH);var r=recipe(h,"quench_steel_plate_batch");fill(h,e,r);
        e.setItem(9,new ItemStack(Items.DIRT,64));h.assertTrue(!e.finish(r)&&e.getItem(0).getCount()==4,"Blocked bucket output cannot consume inputs");
        e.setItem(9,ItemStack.EMPTY);h.assertTrue(e.finish(r),"Quench batch succeeds");h.assertTrue(e.getItem(8).getCount()==4&&e.getItem(9).is(Items.BUCKET)&&e.getItem(1).isEmpty(),"4 plates, 1 empty bucket, exact water consumption");h.succeed();
    }
    @GameTest(template="empty") public static void furnaceHeatsConsumesFuelAndProducesSteel(GameTestHelper h){
        var e=station(h,Station.CRUCIBLE);select(h,e,"a_steel");var r=e.recipe();fill(h,e,r);e.temperature=20;e.setItem(7,new ItemStack(Items.CHARCOAL,1));
        h.assertTrue(!e.finish(r),"Cold furnace cannot skip heating");
        for(int i=0;i<700;i++)WorkshopEntity.tick(h.getLevel(),e.getBlockPos(),e.getBlockState(),e);
        h.assertTrue(e.getItem(8).is(Content.item("steel_ingot"))&&e.getItem(8).getCount()==2,"Actual furnace ticker produces two steel ingots");
        h.assertTrue(e.getItem(7).isEmpty()&&e.getItem(0).isEmpty()&&e.getItem(1).isEmpty()&&e.getItem(2).isEmpty(),"One fuel and exact recipe ingredients consumed");h.succeed();
    }
    @GameTest(template="empty") public static void inventoryHeatAndJobSurviveSerialization(GameTestHelper h){
        var e=station(h,Station.FORGE);select(h,e,"heat_steel");fill(h,e,e.recipe());e.progress=31;e.temperature=888;e.target=950;e.burn=123;
        var tag=e.saveWithoutMetadata(h.getLevel().registryAccess());var loaded=new WorkshopEntity(e.getBlockPos(),e.getBlockState());loaded.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(loaded.progress==31&&loaded.temperature==888&&loaded.target==950&&loaded.burn==123&&loaded.selected==e.selected,"Furnace state lost");
        h.assertTrue(ItemStack.isSameItemSameComponents(loaded.getItem(0),e.getItem(0)),"Input state lost");h.succeed();
    }
    @GameTest(template="empty") public static void menuAutofillIsAtomicAndServerValidated(GameTestHelper h){
        var e=station(h,Station.ANVIL);select(h,e,"a_refine_bloom");var p=player(h);p.setPos(e.getBlockPos().getX(),e.getBlockPos().getY()+1,e.getBlockPos().getZ());
        var menu=new WorkshopMenu(1,p.getInventory(),e);
        h.assertTrue(!menu.clickMenuButton(p,1),"Missing ingredients must fail");
        p.getInventory().setItem(0,new ItemStack(Content.item("iron_bloom"),3));p.getInventory().setItem(1,Content.stack("smith_hammer"));
        h.assertTrue(menu.clickMenuButton(p,1),"Valid autofill succeeds");h.assertTrue(e.getItem(0).getCount()==1&&p.getInventory().getItem(0).getCount()==2&&e.getItem(6).getItem() instanceof WorkshopTool,"Exact movement and tool transfer");
        h.assertTrue(!menu.clickMenuButton(p,1),"Occupied slots cannot be overwritten");h.assertTrue(p.getInventory().getItem(0).getCount()==2,"Repeated autofill must not spend items");
        int old=e.selected;menu.clickMenuButton(p,Integer.MAX_VALUE);h.assertTrue(e.selected==old,"Invalid recipe index rejected");h.succeed();
    }
    @GameTest(template="empty") public static void quickMoveCannotDuplicateOutput(GameTestHelper h){
        var e=station(h,Station.ARMORER);e.setItem(8,Content.stack("steel_katana"));var p=player(h);var menu=new WorkshopMenu(1,p.getInventory(),e);
        menu.quickMoveStack(p,8);menu.quickMoveStack(p,8);
        int count=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Content.item("steel_katana")))count+=p.getInventory().getItem(i).getCount();
        h.assertTrue(count==1&&e.getItem(8).isEmpty(),"Shift-click must move only one weapon");h.succeed();
    }
    @GameTest(template="empty") public static void reachAndDamageOnlyApplyInMainHand(GameTestHelper h){
        var p=player(h);var pike=Content.stack("steel_pike");p.setItemSlot(EquipmentSlot.MAINHAND,pike);
        // The component itself is authoritative; query its MAINHAND applicability.
        var attrs=pike.getAttributeModifiers();final double[] reach={0};attrs.forEach(EquipmentSlot.MAINHAND,(a,m)->{if(a.equals(Attributes.ENTITY_INTERACTION_RANGE))reach[0]+=m.amount();});
        h.assertTrue(Math.abs(reach[0]-2.5)<.001,"Pike extends vanilla reach from 3 to 5.5");reach[0]=0;attrs.forEach(EquipmentSlot.OFFHAND,(a,m)->{if(a.equals(Attributes.ENTITY_INTERACTION_RANGE))reach[0]+=m.amount();});
        h.assertTrue(reach[0]==0,"Offhand must not add reach");h.succeed();
    }
    @GameTest(template="empty") public static void bluntWeaponReallyReducesArmorMitigation(GameTestHelper h){
        var p=player(h);var target1=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(5,2,3));var target2=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(7,2,3));
        for(var piece:Content.PIECES){target1.setItemSlot(piece.getSlot(),Content.stack("steel_plate_"+piece.getName()));target2.setItemSlot(piece.getSlot(),Content.stack("steel_plate_"+piece.getName()));}
        // Let vanilla equip attributes be applied before the two real damage calls.
        h.runAfterDelay(2,()->{
            p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_arming_sword"));target1.hurt(p.damageSources().playerAttack(p),10);
            p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_war_hammer"));target2.hurt(p.damageSources().playerAttack(p),10);
            h.assertTrue(target2.getHealth()<target1.getHealth(),"Hammer must inflict more health damage through equal armor");h.succeed();
        });
    }
    @GameTest(template="empty") public static void shieldStaminaAndGuardBreak(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.OFFHAND,Content.stack("steel_scutum_shield"));p.startUsingItem(InteractionHand.OFF_HAND);Combat.stamina(p,100);
        var mob=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(5,2,3));var container=new DamageContainer(p.damageSources().mobAttack(mob),10);var event=new LivingShieldBlockEvent(p,container,true);
        Combat.shield(event);h.assertTrue(event.getBlocked()&&event.getBlockedDamage()>9&&Combat.stamina(p)<100,"Scutum blocks 97% and spends stamina");
        Combat.stamina(p,0);var broken=new LivingShieldBlockEvent(p,new DamageContainer(p.damageSources().mobAttack(mob),10),true);Combat.shield(broken);
        h.assertTrue(!broken.getBlocked()&&!p.isUsingItem()&&p.getCooldowns().isOnCooldown(Content.item("steel_scutum_shield")),"Empty stamina breaks guard with cooldown");h.succeed();
    }
    @GameTest(template="empty") public static void equipmentPenaltiesDisappearAfterUnequip(GameTestHelper h){
        var p=player(h);double base=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for(var piece:Content.PIECES)p.setItemSlot(piece.getSlot(),Content.stack("gold_plate_"+piece.getName()));
        Combat.tick(new PlayerTickEvent.Post(p));h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED)<base,"Heavy gold armor has a movement cost");
        for(var piece:Content.PIECES)p.setItemSlot(piece.getSlot(),ItemStack.EMPTY);Combat.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<.00001,"Unequipping must remove penalties");h.succeed();
    }
    @GameTest(template="empty") public static void longswordCannotAttackWithAnyOffhandItem(GameTestHelper h){
        var p=player(h);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var target=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(5,2,3));float health=target.getHealth();
        for(var metal:Metal.values())for(var offhand:List.of(new ItemStack(Items.SHIELD),Content.stack("steel_heater_shield"),new ItemStack(Items.APPLE))){
            int originalCount=offhand.getCount();
            p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack(metal.key+"_longsword"));p.setItemSlot(EquipmentSlot.OFFHAND,offhand);
            p.attack(target);
            h.assertTrue(target.getHealth()==health,"Occupied offhand must prevent real Player.attack, including vanilla shield and ordinary items");
            h.assertTrue(!target.hurt(p.damageSources().playerAttack(p),10),"Direct damage path must also reject an occupied longsword");
            h.assertTrue(target.getHealth()==health&&p.getOffhandItem().getCount()==originalCount,"Blocked attack must not deal damage or consume offhand items");
        }
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        h.runAfterDelay(25,()->{
            p.attack(target);
            h.assertTrue(target.getHealth()<health,"Emptying the offhand must immediately restore a real longsword attack");h.succeed();
        });
    }
    @GameTest(template="empty") public static void longswordDisablesOffhandUseAndRestoresOtherWeapons(GameTestHelper h){
        var p=player(h);p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_longsword"));
        var target=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(5,2,3));
        for(var item:List.of(new ItemStack(Items.SHIELD),Content.stack("steel_heater_shield"),new ItemStack(Items.APPLE))){
            p.setItemSlot(EquipmentSlot.OFFHAND,item);p.startUsingItem(InteractionHand.OFF_HAND);
            h.assertTrue(!p.isUsingItem(),"Longsword must prevent the actual offhand item-use lifecycle");
            var click=new PlayerInteractEvent.RightClickItem(p,InteractionHand.OFF_HAND);NeoForge.EVENT_BUS.post(click);
            h.assertTrue(click.isCanceled()&&click.getCancellationResult()==InteractionResult.FAIL,"Offhand right click must be denied through the registered event bus");
            var interact=new PlayerInteractEvent.EntityInteract(p,InteractionHand.OFF_HAND,target);NeoForge.EVENT_BUS.post(interact);
            h.assertTrue(interact.isCanceled(),"Offhand entity interaction must also be denied");
        }
        p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_arming_sword"));p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.OFF_HAND);
        h.assertTrue(p.isUsingItem(),"One-handed sword must still allow a vanilla shield");h.succeed();
    }
    @GameTest(template="empty") public static void equippingLongswordInterruptsAnExistingShieldBlock(GameTestHelper h){
        var p=player(h);var mob=h.spawnWithNoFreeWill(EntityType.HUSK,new BlockPos(5,2,3));
        for(var shield:List.of(new ItemStack(Items.SHIELD),Content.stack("steel_heater_shield"))){
            p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_arming_sword"));p.setItemSlot(EquipmentSlot.OFFHAND,shield);p.startUsingItem(InteractionHand.OFF_HAND);
            h.assertTrue(p.isUsingItem(),"Control: shield was active before changing the weapon");
            p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_longsword"));Combat.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!p.isUsingItem(),"Switching to longsword must terminate an already raised shield");
            var block=new LivingShieldBlockEvent(p,new DamageContainer(p.damageSources().mobAttack(mob),10),true);Combat.shield(block);
            h.assertTrue(!block.getBlocked(),"Neither vanilla nor mod shields may block alongside a longsword");
        }
        h.succeed();
    }
}
