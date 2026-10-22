package ru.epocharmory.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.epocharmory.*;

/** Real renderer contact sheets; excluded from the installed JAR. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class ArmorVisualProbe {
    private static int page=-1,ticks,total,clearFrames;
    private static boolean reloading;
    private static volatile boolean reloadDone;
    private static volatile Throwable failure;
    private static final String[] PAGES={"plate","mail","lamellar","brigandine","materials","slots","poses","reload","inventory"};
    private static void success(Minecraft mc){System.out.println("EPOCH_ARMOR_SUCCESS: four styles, ten materials, independent slots, wide/slim players, poses, mixed gear, resource reload and actual inventory");page=999;mc.stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("epocharmory.armorSmoke")||page==999)return;
        var mc=Minecraft.getInstance();total++;ticks++;
        try{
            if(failure!=null)throw new IllegalStateException("Reload failed",failure);
            if(total>3600)throw new IllegalStateException("Armor QA timeout "+page);
            if(mc.player==null||mc.level==null)return;
            if(page<0&&mc.player.isDeadOrDying()){if(ticks%20==1)mc.player.respawn();return;}
            if(mc.getOverlay()!=null){clearFrames=0;return;}else clearFrames++;
            if(page==8){
                mc.player.deathTime=0;
                if(ticks>20&&mc.player.getItemBySlot(EquipmentSlot.HEAD).is(Content.item("steel_plate_helmet"))&&!(mc.screen instanceof InventoryScreen))mc.setScreen(new InventoryScreen(mc.player));
                if(ticks>45&&mc.screen instanceof InventoryScreen){Screenshot.grab(mc.gameDirectory,"epoch-armor-square-inventory.png",mc.getMainRenderTarget(),c->{});success(mc);}
                return;
            }
            if(page<0){
                var id=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setNoGravity(true);p.setInvulnerable(true);p.setHealth(20);p.deathTime=0;
                    p.teleportTo(p.serverLevel(),.5,65,.5,180,0);
                });
                mc.options.hideGui=true;page=0;ticks=0;mc.setScreen(new Sheet(PAGES[page]));
            }
            else if(!reloading&&clearFrames>3&&ticks>30&&mc.screen instanceof Sheet sheet&&sheet.frames>5){
                Screenshot.grab(mc.gameDirectory,"epoch-armor-square-"+PAGES[page]+".png",mc.getMainRenderTarget(),c->System.out.println("EPOCH_ARMOR_SCREENSHOT "+c.getString()));
                page++;ticks=0;
                if(page==7){reloading=true;mc.reloadResourcePacks().whenComplete((v,e)->{failure=e;reloadDone=true;});}
                else if(page==8){
                    mc.setScreen(null);var id=mc.player.getUUID();
                    mc.getSingleplayerServer().execute(()->{
                        var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setNoGravity(true);p.teleportTo(p.serverLevel(),.5,65,.5,180,0);p.setHealth(20);p.setInvulnerable(true);
                        p.getInventory().clearContent();for(var type:Content.PIECES)p.setItemSlot(type.getSlot(),Content.stack("steel_plate_"+type.getName()));p.inventoryMenu.broadcastChanges();
                    });
                }
                else if(page<PAGES.length)mc.setScreen(new Sheet(PAGES[page]));
                else success(mc);
            }
            if(reloading&&reloadDone){reloading=false;mc.setScreen(new Sheet(PAGES[page]));ticks=0;}
        }catch(Throwable e){e.printStackTrace();System.out.println("EPOCH_ARMOR_FAILED");page=999;mc.stop();}
    }
    private static RemotePlayer player(String style,String metal,EquipmentSlot only,boolean slim,boolean crouch){
        var mc=Minecraft.getInstance();RemotePlayer p=null;
        for(int i=0;i<100;i++){
            var id=UUID.nameUUIDFromBytes(("epoch-armor-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p=new RemotePlayer(mc.level,new GameProfile(id,"Armorer"));
            if(p.getSkin().model().id().equals(slim?"slim":"default"))break;
        }
        for(var type:Content.PIECES)if(only==null||type.getSlot()==only)p.setItemSlot(type.getSlot(),Content.stack(metal+"_"+style+"_"+type.getName()));
        p.yBodyRot=p.yBodyRotO=180;p.yHeadRot=p.yHeadRotO=180;p.setYRot(180);p.yRotO=180;
        if(crouch){p.setPose(Pose.CROUCHING);p.setShiftKeyDown(true);}
        return p;
    }
    private static final class Sheet extends Screen {
        final String mode;int frames;final List<RemotePlayer> players=new ArrayList<>();
        Sheet(String mode){super(Component.literal("Epoch Armory · Fantasy Reforged 1.1.0 · броня"));this.mode=mode;}
        @Override protected void init(){
            players.clear();frames=0;
            if(mode.equals("materials"))for(var metal:Metal.values())players.add(player("plate",metal.key,null,false,false));
            else if(mode.equals("slots"))for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})players.add(player("plate","steel",slot,false,false));
            else if(mode.equals("poses")){
                players.add(player("plate","steel",null,false,true));players.add(player("plate","steel",null,true,false));players.add(player("plate","steel",null,false,false));
                players.get(2).walkAnimation.update(.8f,1);players.get(2).walkAnimation.update(.8f,1);
                players.get(2).yHeadRot=players.get(2).yHeadRotO=205;
                var mixed=player("plate","steel",null,false,false);
                mixed.setItemSlot(EquipmentSlot.HEAD,Content.stack("netherite_plate_helmet"));
                mixed.setItemSlot(EquipmentSlot.CHEST,Content.stack("bronze_lamellar_chestplate"));
                mixed.setItemSlot(EquipmentSlot.LEGS,Content.stack("damascus_brigandine_leggings"));
                mixed.setItemSlot(EquipmentSlot.FEET,Content.stack("gold_mail_boots"));players.add(mixed);
            }else for(int i=0;i<3;i++)players.add(player(mode.equals("reload")?"plate":mode,"steel",null,false,false));
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            frames++;g.fill(0,0,width,height,0xFF181D26);g.drawCenteredString(font,title,width/2,10,0xFFE3C590);
            g.drawCenteredString(font,mode,width/2,25,0xFFCDD5DF);
            if(mode.equals("materials")){
                int cell=width/5;
                for(int i=0;i<players.size();i++){
                    int cx=(i%5)*cell+cell/2,cy=48+(i/5)*(height-55)/2;
                    renderPlayer(g,players.get(i),cx,cy+70,56,-.22f);
                    g.drawCenteredString(font,Metal.values()[i].key,cx,cy+138,0xFFBAC8D9);
                }
            }else{
                int count=players.size(),cell=width/count;
                for(int i=0;i<count;i++){
                    int cx=cell*i+cell/2;
                    float angle=mode.equals("slots")?-.18f:mode.equals("poses")?-.35f:new float[]{0,-1.05f,3.14f}[i];
                    renderPlayer(g,players.get(i),cx,height/2+12,Math.min(132,(height-90)/2.2f),angle);
                    g.drawCenteredString(font,mode.equals("slots")?new String[]{"helmet","chestplate","leggings","boots"}[i]:mode.equals("poses")?new String[]{"crouch","slim","walk / head turn","mixed"}[i]:new String[]{"front","side","back"}[i],cx,height-35,0xFFC2CBD7);
                    if(!mode.equals("slots")&&!mode.equals("poses")){
                        int x=cx-34;for(var type:Content.PIECES){g.renderItem(Content.stack("steel_"+(mode.equals("reload")?"plate":mode)+"_"+type.getName()),x,height-23);x+=18;}
                    }
                }
            }
        }
        private void renderPlayer(GuiGraphics g,RemotePlayer p,int cx,int cy,float scale,float angle){
            InventoryScreen.renderEntityInInventory(g,cx,cy,scale,new Vector3f(0,p.getBbHeight()/2,0),new Quaternionf().rotateZ((float)Math.PI).rotateY(angle).rotateX(.035f),null,p);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
