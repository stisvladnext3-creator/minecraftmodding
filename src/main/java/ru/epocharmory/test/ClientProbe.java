package ru.epocharmory.test;

import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.epocharmory.*;
import ru.epocharmory.client.*;

/** Development-only real client checks and screenshots; never shipped in the mod JAR. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class ClientProbe {
    private static int stage,ticks,total;
    private static volatile String failure;
    private static void next(int s){stage=s;ticks=0;System.out.println("EPOCH_QA_STAGE "+s);}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"epoch-"+name+".png",mc.getMainRenderTarget(),c->System.out.println("EPOCH_SCREENSHOT "+name));}
    private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();failure=e.toString();}});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("epocharmory.smoke")||stage==999)return;
        var mc=Minecraft.getInstance();ticks++;total++;
        try{
            if(failure!=null)throw new IllegalStateException(failure);
            if(total>5500)throw new IllegalStateException("Timeout stage "+stage+" screen "+mc.screen);
            if(stage==0&&mc.player!=null&&mc.getSingleplayerServer()!=null){
                server(mc,p->{var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(6000);
                    for(int x=-9;x<=9;x++)for(int z=-3;z<=12;z++)level.setBlock(new BlockPos(x,64,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
                    int x=-5;for(var s:Station.values())level.setBlock(new BlockPos(x++,65,0),Workshops.STATIONS.get(s).get().defaultBlockState(),3);
                    p.setGameMode(GameType.SURVIVAL);p.teleportTo(level,0.5,65,9.5,180,15);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.getInventory().clearContent();
                    p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_mace"));p.setItemSlot(EquipmentSlot.OFFHAND,Content.stack("steel_rotella_shield"));
                    for(var piece:Content.PIECES)p.setItemSlot(piece.getSlot(),Content.stack("steel_plate_"+piece.getName()));p.inventoryMenu.broadcastChanges();
                });next(1);
            } else if(stage==1&&ticks>70&&mc.player.getMainHandItem().is(Content.item("steel_mace"))){
                for(var holder:Content.ALL.values()){
                    var stack=new ItemStack(holder.get());var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                    if(model==mc.getModelManager().getMissingModel())throw new IllegalStateException("Missing model "+stack.getItem());
                }
                capture(mc,"workshop-and-mace");mc.setScreen(new InventoryScreen(mc.player));next(2);
            } else if(stage==2&&ticks>25){capture(mc,"plate-armor");mc.setScreen(new Catalog());next(3);
            } else if(stage==3&&ticks>25){capture(mc,"inventory-catalog");mc.setScreen(null);setArmor(mc,ArmorStyle.MAIL);next(4);
            } else if(stage==4&&ticks>30){mc.setScreen(new InventoryScreen(mc.player));next(5);
            } else if(stage==5&&ticks>20){capture(mc,"mail-armor");mc.setScreen(null);setArmor(mc,ArmorStyle.LAMELLAR);next(6);
            } else if(stage==6&&ticks>30){mc.setScreen(new InventoryScreen(mc.player));next(7);
            } else if(stage==7&&ticks>20){capture(mc,"lamellar-armor");mc.setScreen(null);setArmor(mc,ArmorStyle.BRIGANDINE);next(8);
            } else if(stage==8&&ticks>30){mc.setScreen(new InventoryScreen(mc.player));next(9);
            } else if(stage==9&&ticks>20){capture(mc,"brigandine-armor");mc.setScreen(null);
                server(mc,p->{p.teleportTo(p.serverLevel(),4.5,65,3.5,180,10);var pos=new BlockPos(4,65,0);p.serverLevel().setBlock(pos,Workshops.STATIONS.get(Station.ARMORER).get().defaultBlockState(),3);
                    var bench=(WorkshopEntity)p.serverLevel().getBlockEntity(pos);var list=WorkshopEntity.recipes(p.level(),Station.ARMORER);int index=0;while(!list.get(index).id().equals(EpochArmory.id("assemble_steel_katana")))index++;bench.select(index);bench.clearContent();
                    int slot=1;for(var part:bench.recipe().inputs())p.getInventory().setItem(slot++,part.preview());p.getInventory().setItem(slot,Content.stack("smith_hammer"));p.openMenu(bench,pos);
                });next(10);
            } else if(stage==10&&ticks>30&&mc.screen instanceof WorkshopScreen screen){capture(mc,"armorer-recipe");mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,1);next(11);
            } else if(stage==11&&ticks>15&&mc.screen instanceof WorkshopScreen screen){
                if(screen.getMenu().getSlot(8).hasItem()){capture(mc,"armorer-finished");mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,8,0,ClickType.QUICK_MOVE,mc.player);next(12);return;}
                if(!screen.getMenu().getSlot(0).hasItem()){
                    if(ticks<50)return;
                    server(mc,p->System.out.println("EPOCH_SERVER_MENU "+p.containerMenu+" pos="+p.position()+" inv="+p.getInventory().items));
                    capture(mc,"autofill-failure");throw new IllegalStateException("Autofill did not synchronize");
                }
                if(ticks%4==0)mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,0);
            } else if(stage==12&&ticks>20&&mc.screen instanceof WorkshopScreen){
                int count=mc.player.getInventory().items.stream().filter(s->s.is(Content.item("steel_katana"))).mapToInt(ItemStack::getCount).sum();if(count!=1)throw new IllegalStateException("Expected exactly one real katana, got "+count);
                mc.player.closeContainer();CodexScreen.open();next(13);
            } else if(stage==13&&ticks>25){capture(mc,"codex");mc.setScreen(null);server(mc,p->p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_katana")));next(14);
            } else if(stage==14&&ticks>30){capture(mc,"katana-in-hand");server(mc,p->{p.setItemSlot(EquipmentSlot.MAINHAND,Content.stack("steel_war_hammer"));p.setItemSlot(EquipmentSlot.OFFHAND,Content.stack("steel_pavise_shield"));});next(15);
            } else if(stage==15&&ticks>30){mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.OFF_HAND);next(16);
            } else if(stage==16&&ticks>15){capture(mc,"pavise-blocking");mc.gameMode.releaseUsingItem(mc.player);System.out.println("EPOCH_CLIENT_SUCCESS: models, four armor styles, shield blocking, recipe sync, autofill, actual server crafting, exact output and codex");next(17);
            } else if(stage==17&&ticks>30)mc.stop();
        }catch(Throwable e){e.printStackTrace();System.out.println("EPOCH_CLIENT_FAILED");stage=999;mc.stop();}
    }
    private static void setArmor(Minecraft mc,ArmorStyle s){server(mc,p->{for(var piece:Content.PIECES)p.setItemSlot(piece.getSlot(),Content.stack("steel_"+s.key+"_"+piece.getName()));p.inventoryMenu.broadcastChanges();});}
    private static final class Catalog extends Screen {
        Catalog(){super(Component.literal("Кузница эпох · проверка игровых ресурсов"));}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            g.fill(0,0,width,height,0xFF191C23);g.drawCenteredString(font,title,width/2,12,0xFFE4C18A);
            int x0=(width-18*26)/2;
            for(int row=0;row<Metal.values().length;row++){
                var m=Metal.values()[row];g.drawString(font,m.key,x0-70,43+row*23,0xFFCFD2D7,false);
                for(int col=0;col<18;col++)g.renderItem(Content.stack(m.key+"_"+WeaponKind.values()[col].key),x0+col*26,40+row*23);
            }
            for(int i=0;i<10;i++)g.renderItem(Content.stack("steel_"+ShieldKind.values()[i].key+"_shield"),x0+i*26,278);
            int index=0;for(var s:ArmorStyle.values())for(var p:Content.PIECES)g.renderItem(Content.stack("steel_"+s.key+"_"+p.getName()),x0+(index++%18)*26,312);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
