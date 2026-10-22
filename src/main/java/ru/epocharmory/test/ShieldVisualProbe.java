package ru.epocharmory.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.epocharmory.*;

/** Actual Minecraft renderers and first-person gameplay. Excluded from release. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class ShieldVisualProbe {
    private static final String[] SHEETS={"idle_left","idle_right","block_left","block_right","grip","profile","materials","poses","reload"};
    private static int stage=-1,ticks,total,clearFrames;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static boolean reloading;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("epocharmory.shieldSmoke")||stage==999)return;
        var mc=Minecraft.getInstance();total++;ticks++;
        try{
            if(failure!=null)throw new IllegalStateException("Shield reload failed",failure);
            if(total>4800)throw new IllegalStateException("Shield QA timeout at "+stage);
            if(mc.player==null||mc.level==null)return;
            if(stage<0&&mc.player.isDeadOrDying()){if(ticks%20==1)mc.player.respawn();return;}
            if(mc.getOverlay()!=null){clearFrames=0;return;}else clearFrames++;
            if(stage<0){
                mc.options.hideGui=true;mc.options.bobView().set(false);
                for(var metal:Metal.values())for(var kind:ShieldKind.values()){
                    var stack=Content.stack(metal.key+"_"+kind.key+"_shield");
                    var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                    if(model==mc.getModelManager().getMissingModel())throw new IllegalStateException("Missing "+stack);
                    for(var context:new ItemDisplayContext[]{ItemDisplayContext.GUI,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND}){
                        var baked=model.applyTransform(context,new com.mojang.blaze3d.vertex.PoseStack(),context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
                        var quads=baked.getQuads(null,null,net.minecraft.util.RandomSource.create());
                        if(quads.isEmpty())throw new IllegalStateException("Empty shield model "+stack+" "+context);
                        for(var quad:quads)if(quad.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))throw new IllegalStateException("Missing shield texture "+stack+" "+context);
                    }
                }
                stage=0;ticks=0;mc.setScreen(new Sheet(SHEETS[stage]));
            }else if(stage<SHEETS.length){
                if(reloading){if(ready){reloading=false;ready=false;ticks=0;mc.setScreen(new Sheet(SHEETS[stage]));}return;}
                if(clearFrames>3&&ticks>25&&mc.screen instanceof Sheet s&&s.frames>5){
                    grab(mc,SHEETS[stage]);stage++;ticks=0;
                    if(stage==8){reloading=true;mc.reloadResourcePacks().whenComplete((v,e)->{failure=e;ready=true;});}
                    else if(stage<SHEETS.length)mc.setScreen(new Sheet(SHEETS[stage]));
                    else setupFirstPerson(mc);
                }
            }else{
                int index=stage-SHEETS.length;
                boolean blocking=index%4>=2;InteractionHand hand=index%2==0?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
                if(!ready||ticks<12)return;
                if(blocking&&!mc.player.isUsingItem())mc.player.startUsingItem(hand);
                if(ticks>22&&clearFrames>3){
                    if(mc.options.hideGui)throw new IllegalStateException("First-person hand rendering disabled");
                    if(blocking&&!mc.player.isUsingItem())throw new IllegalStateException("First person did not raise shield");
                    var kind=ShieldKind.values()[index/4];
                    grab(mc,"first_"+kind.key+"_"+(hand==InteractionHand.OFF_HAND?"left":"right")+(blocking?"_block":"_idle"));
                    stage++;ticks=0;
                    if(stage-SHEETS.length==40){System.out.println("EPOCH_SHIELD_SUCCESS: 100 baked shields, ten shapes, both hands, block poses, slim/crouch, resource reload and 40 actual first-person views");stage=999;mc.stop();}
                    else setupFirstPerson(mc);
                }
            }
        }catch(Throwable e){e.printStackTrace();System.out.println("EPOCH_SHIELD_FAILED");stage=999;mc.stop();}
    }
    private static void grab(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"epoch-shield-"+name+".png",mc.getMainRenderTarget(),c->{});System.out.println("EPOCH_SHIELD_SCREENSHOT "+name);}
    private static void setupFirstPerson(Minecraft mc){
        ready=false;mc.setScreen(null);mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.mouseHandler.grabMouse();var id=mc.player.getUUID();int index=stage-SHEETS.length;
        var kind=ShieldKind.values()[index/4];boolean block=index%4>=2;
        var hand=index%2==0?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        mc.player.stopUsingItem();
        mc.getSingleplayerServer().execute(()->{
            try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);
                p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);p.setNoGravity(true);p.setHealth(20);p.deathTime=0;
                p.teleportTo(p.serverLevel(),.5,65,.5,180,0);p.stopUsingItem();p.getInventory().clearContent();
                p.setItemInHand(hand,Content.stack("diamond_"+kind.key+"_shield"));p.inventoryMenu.broadcastChanges();
                if(block)p.startUsingItem(hand);
                ready=true;
            }catch(Throwable e){failure=e;}
        });
    }
    private static final class ProbePlayer extends RemotePlayer {
        final InteractionHand blocking;
        ProbePlayer(GameProfile profile,InteractionHand blocking){super(Minecraft.getInstance().level,profile);this.blocking=blocking;}
        @Override public boolean isUsingItem(){return blocking!=null;}
        @Override public InteractionHand getUsedItemHand(){return blocking==null?InteractionHand.MAIN_HAND:blocking;}
        @Override public ItemStack getUseItem(){return blocking==null?ItemStack.EMPTY:getItemInHand(blocking);}
        @Override public int getUseItemRemainingTicks(){return blocking==null?0:72000;}
    }
    private static ProbePlayer player(String kind,String metal,InteractionHand hand,boolean block,boolean slim,boolean crouch){
        ProbePlayer p=null;
        for(int i=0;i<100;i++){
            var id=UUID.nameUUIDFromBytes(("epoch-shield-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p=new ProbePlayer(new GameProfile(id,"Shieldwright"),block?hand:null);
            if(p.getSkin().model().id().equals(slim?"slim":"default"))break;
        }
        p.setItemInHand(hand,Content.stack(metal+"_"+kind+"_shield"));
        if(hand==InteractionHand.OFF_HAND)p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;p.setYRot(180);p.yRotO=180;
        if(crouch){p.setPose(Pose.CROUCHING);p.setShiftKeyDown(true);}
        return p;
    }
    private static final class Sheet extends Screen {
        final String mode;int frames;final List<ProbePlayer> players=new ArrayList<>();final List<String> labels=new ArrayList<>();
        Sheet(String mode){super(Component.literal("Epoch Armory · Fantasy Reforged 1.1.0 · щиты"));this.mode=mode;}
        void add(String kind,String metal,InteractionHand hand,boolean block,boolean slim,boolean crouch,String label){players.add(player(kind,metal,hand,block,slim,crouch));labels.add(label);}
        @Override protected void init(){
            players.clear();labels.clear();frames=0;
            if(mode.equals("grip")){
                add("round","diamond",InteractionHand.MAIN_HAND,false,false,false,"right · idle");add("round","diamond",InteractionHand.OFF_HAND,false,false,false,"left · idle");
                add("round","diamond",InteractionHand.MAIN_HAND,true,false,false,"right · block");add("round","diamond",InteractionHand.OFF_HAND,true,false,false,"left · block");
            }else if(mode.equals("profile")){
                for(String kind:new String[]{"buckler","round","scutum","pavise"})add(kind,"steel",InteractionHand.OFF_HAND,false,false,false,kind+" · back / grip");
            }else if(mode.equals("poses")){
                add("pavise","steel",InteractionHand.OFF_HAND,true,true,false,"slim · block");add("kite","steel",InteractionHand.OFF_HAND,true,false,true,"crouch · block");
                add("targe","steel",InteractionHand.MAIN_HAND,false,false,true,"right · crouch");add("heater","diamond",InteractionHand.OFF_HAND,true,false,false,"armor · block");
                for(var type:Content.PIECES)players.get(3).setItemSlot(type.getSlot(),Content.stack("diamond_plate_"+type.getName()));
            }else if(mode.equals("materials"))for(var m:Metal.values())add("round",m.key,InteractionHand.OFF_HAND,true,false,false,m.key);
            else for(var k:ShieldKind.values())add(k.key,"steel",mode.endsWith("right")?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND,mode.startsWith("block")||mode.equals("reload"),false,false,k.key);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            frames++;g.fill(0,0,width,height,0xFF181D26);g.drawCenteredString(font,title,width/2,9,0xFFE3C590);g.drawCenteredString(font,mode,width/2,23,0xFFD0D8E1);
            int count=players.size(),cols=count>4?5:4,rows=(count+cols-1)/cols,cell=width/cols,rh=(height-42)/rows;
            for(int i=0;i<count;i++){
                int cx=cell*(i%cols)+cell/2,cy=42+(i/cols)*rh;
                float scale=rows==2?60:Math.min(mode.equals("grip")?90:103,(height-90)/2.2f);
                float angle=mode.equals("profile")?2.55f:mode.equals("grip")?(i%2==0?-.42f:.42f):-.12f;
                var p=players.get(i);
                InventoryScreen.renderEntityInInventory(g,cx,cy+rh/2-7,scale,new Vector3f(0,p.getBbHeight()/2,0),new Quaternionf().rotateZ((float)Math.PI).rotateY(angle).rotateX(.025f),null,p);
                g.drawCenteredString(font,labels.get(i),cx,cy+rh-24,0xFFCED8E4);
                g.renderItem(p.getItemInHand(p.getOffhandItem().getItem() instanceof HistoricalShield?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND),cx-8,cy+rh-19);
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
