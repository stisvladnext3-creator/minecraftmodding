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

/** Native model/hand regression captures. Not included in the installed JAR. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class WeaponVisualProbe {
    private static final String[] SHEETS={"vanilla_reference","sizes","two_handed","catalog","held_right_a","held_left_a","held_right_b","held_left_b","held_right_c","held_left_c","details","materials","poses","reload"};
    private static final int MOD_FIRST_VIEWS=WeaponKind.values().length*4;
    private static final int TOTAL_FIRST_VIEWS=MOD_FIRST_VIEWS+6;
    private static int stage=-1,ticks,total,clearFrames,swingTick;
    private static boolean reloading,swung;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("epocharmory.weaponSmoke")||stage==999)return;
        var mc=Minecraft.getInstance();total++;ticks++;
        try{
            if(failure!=null)throw new IllegalStateException("Weapon QA failed",failure);
            if(total>6000)throw new IllegalStateException("Weapon QA timeout at "+stage);
            if(mc.player==null||mc.level==null)return;
            if(stage<0&&mc.player.isDeadOrDying()){if(ticks%20==1)mc.player.respawn();return;}
            if(mc.getOverlay()!=null){clearFrames=0;return;}else clearFrames++;
            if(stage<0){
                mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fov().set(70);
                int bakedCount=0,alignmentCount=0;
                var vanilla=mc.getItemRenderer().getModel(new ItemStack(Items.DIAMOND_SWORD),mc.level,mc.player,0);
                for(var metal:Metal.values())for(var kind:WeaponKind.values()){
                    var stack=Content.stack(metal.key+"_"+kind.key);var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
                    if(model==mc.getModelManager().getMissingModel())throw new IllegalStateException("Missing weapon "+stack);
                    for(var context:new ItemDisplayContext[]{ItemDisplayContext.GUI,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED}){
                        boolean left=context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                        var pose=new com.mojang.blaze3d.vertex.PoseStack();
                        var baked=model.applyTransform(context,pose,left);
                        if(context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND){
                            var reference=new com.mojang.blaze3d.vertex.PoseStack();
                            vanilla.applyTransform(context,reference,left);
                            // Compare the real baked transforms: vanilla's sprite
                            // blade runs along X+Y, while our mesh runs along Y.
                            var actual=pose.last().pose().transformDirection(new Vector3f(0,1,0)).normalize();
                            var expected=reference.last().pose().transformDirection(new Vector3f(1,1,0)).normalize();
                            if(actual.dot(expected)<.99999f)throw new IllegalStateException("Weapon points away from vanilla sword: "+stack+" "+context+" actual="+actual+" expected="+expected);
                            alignmentCount++;
                        }
                        var quads=baked.getQuads(null,null,net.minecraft.util.RandomSource.create());
                        if(quads.isEmpty())throw new IllegalStateException("Empty weapon model "+stack+" "+context);
                        for(var q:quads)if(q.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))throw new IllegalStateException("Missing weapon texture "+stack+" "+context);
                        bakedCount++;
                    }
                }
                System.out.println("EPOCH_WEAPON_BAKED "+bakedCount+" contexts without missing models/textures");
                System.out.println("EPOCH_WEAPON_ALIGNMENT "+alignmentCount+" held directions match the actual vanilla diamond sword");
                var holder=player("longsword",InteractionHand.MAIN_HAND,false,false);
                for(var metal:Metal.values()){
                    var stack=Content.stack(metal.key+"_longsword");holder.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var extension=net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(stack);
                    var armPose=extension.getArmPose(holder,InteractionHand.MAIN_HAND,stack);
                    if(armPose==null||!armPose.isTwoHanded())throw new IllegalStateException("Missing two-hand longsword pose: "+metal);
                    holder.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.APPLE));
                    if(extension.getArmPose(holder,InteractionHand.MAIN_HAND,stack)!=null)throw new IllegalStateException("Two-hand pose must not pretend the occupied offhand is free");
                    holder.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                }
                System.out.println("EPOCH_LONGSWORD_POSE: all ten materials use a two-handed pose only with an empty offhand");
                stage=0;ticks=0;mc.setScreen(new Sheet(SHEETS[stage]));
            }else if(stage<SHEETS.length){
                if(reloading){if(ready){reloading=false;ready=false;ticks=0;mc.setScreen(new Sheet(SHEETS[stage]));}return;}
                if(clearFrames>3&&ticks>23&&mc.screen instanceof Sheet s&&s.frames>5){
                    grab(mc,SHEETS[stage]);stage++;ticks=0;
                    if(stage==SHEETS.length-1){reloading=true;ready=false;mc.reloadResourcePacks().whenComplete((v,e)->{failure=e;ready=true;});}
                    else if(stage<SHEETS.length)mc.setScreen(new Sheet(SHEETS[stage]));
                    else setupFirstPerson(mc);
                }
            }else{
                int index=stage-SHEETS.length;boolean vanillaReference=index>=MOD_FIRST_VIEWS&&index<MOD_FIRST_VIEWS+4,leftMain=index>=MOD_FIRST_VIEWS+4;
                var kind=vanillaReference?null:leftMain?WeaponKind.LONGSWORD:WeaponKind.values()[index/4];boolean attack=leftMain?index==TOTAL_FIRST_VIEWS-1:index%4>=2;
                var hand=leftMain?InteractionHand.MAIN_HAND:index%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
                if(!ready||ticks<12)return;
                if(!mc.player.getItemInHand(hand).is(vanillaReference?Items.DIAMOND_SWORD:Content.item("steel_"+kind.key))){if(ticks>80)throw new IllegalStateException("Equipped item did not synchronize");return;}
                if(attack&&!swung&&ticks>=17){mc.player.swing(hand);swung=true;swingTick=ticks;}
                if(clearFrames>3&&(!attack&&ticks>22||attack&&swung&&ticks>=swingTick+3)){
                    if(mc.options.hideGui)throw new IllegalStateException("First-person hand rendering disabled");
                    grab(mc,"first_"+(vanillaReference?"vanilla_sword":kind.key)+"_"+(leftMain?"left_main":hand==InteractionHand.MAIN_HAND?"right":"left")+(attack?"_swing":"_idle"));
                    stage++;ticks=0;
                    if(stage-SHEETS.length==TOTAL_FIRST_VIEWS){System.out.println("EPOCH_WEAPON_SUCCESS: 180 weapons, 18 shapes, 1260 baked contexts, 720 vanilla direction comparisons, two-hand longsword poses, sizes, both hands, attacks, slim/crouch, shield/armor, reload, 72 weapon, 4 vanilla and 2 left-main longsword first-person views");stage=999;mc.stop();}
                    else setupFirstPerson(mc);
                }
            }
        }catch(Throwable e){e.printStackTrace();System.out.println("EPOCH_WEAPON_FAILED");stage=999;mc.stop();}
    }
    private static void grab(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"epoch-weapon-"+name+".png",mc.getMainRenderTarget(),c->{});System.out.println("EPOCH_WEAPON_SCREENSHOT "+name);}
    private static void setupFirstPerson(Minecraft mc){
        ready=false;swung=false;mc.setScreen(null);mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.mouseHandler.grabMouse();
        int index=stage-SHEETS.length;boolean vanillaReference=index>=MOD_FIRST_VIEWS&&index<MOD_FIRST_VIEWS+4,leftMain=index>=MOD_FIRST_VIEWS+4;
        var kind=vanillaReference?null:leftMain?WeaponKind.LONGSWORD:WeaponKind.values()[index/4];var hand=leftMain?InteractionHand.MAIN_HAND:index%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;var id=mc.player.getUUID();
        mc.player.stopUsingItem();
        mc.getSingleplayerServer().execute(()->{
            try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);
                p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setMainArm(leftMain?HumanoidArm.LEFT:HumanoidArm.RIGHT);p.setInvulnerable(true);p.setNoGravity(true);p.setHealth(20);p.deathTime=0;
                p.teleportTo(p.serverLevel(),.5,65,.5,180,0);p.stopUsingItem();p.getInventory().clearContent();
                p.setItemInHand(hand,vanillaReference?new ItemStack(Items.DIAMOND_SWORD):Content.stack("steel_"+kind.key));p.inventoryMenu.broadcastChanges();ready=true;
            }catch(Throwable e){failure=e;}
        });
    }
    private static RemotePlayer player(String kind,InteractionHand hand,boolean slim,boolean crouch){
        RemotePlayer p=null;
        for(int i=0;i<100;i++){
            var id=UUID.nameUUIDFromBytes(("epoch-weapon-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p=new RemotePlayer(Minecraft.getInstance().level,new GameProfile(id,"Weaponsmith"));
            if(p.getSkin().model().id().equals(slim?"slim":"default"))break;
        }
        p.setItemInHand(hand,Content.stack("steel_"+kind));
        p.yBodyRot=p.yBodyRotO=p.yHeadRot=p.yHeadRotO=180;p.setYRot(180);p.yRotO=180;
        if(crouch){p.setPose(Pose.CROUCHING);p.setShiftKeyDown(true);}
        return p;
    }
    private static final class Sheet extends Screen {
        final String mode;int frames;final List<RemotePlayer> players=new ArrayList<>();final List<String> labels=new ArrayList<>();final List<ItemStack> items=new ArrayList<>();
        Sheet(String mode){super(Component.literal("Epoch Armory · Fantasy Reforged 1.1.1 · оружие"));this.mode=mode;}
        @Override protected void init(){
            players.clear();labels.clear();items.clear();frames=0;
            if(mode.equals("vanilla_reference")){
                for(var hand:new InteractionHand[]{InteractionHand.MAIN_HAND,InteractionHand.OFF_HAND}){
                    var reference=player("arming_sword",hand,false,false);reference.setItemInHand(hand,new ItemStack(Items.DIAMOND_SWORD));
                    players.add(reference);labels.add("vanilla · "+hand);
                    players.add(player("arming_sword",hand,false,false));labels.add("mod sword · "+hand);
                    players.add(player("pike",hand,false,false));labels.add("mod pike · "+hand);
                }
            }else if(mode.equals("sizes")){
                for(String key:new String[]{"dagger","gladius","arming_sword","longsword","spear","pike"}){players.add(player(key,InteractionHand.MAIN_HAND,false,false));labels.add(key);}
            }else if(mode.equals("two_handed")){
                players.add(player("longsword",InteractionHand.MAIN_HAND,false,false));labels.add("two hands · right");
                var left=player("longsword",InteractionHand.MAIN_HAND,false,false);left.setMainArm(HumanoidArm.LEFT);players.add(left);labels.add("two hands · left");
                players.add(player("longsword",InteractionHand.MAIN_HAND,true,false));labels.add("slim");
                players.add(player("longsword",InteractionHand.MAIN_HAND,false,true));labels.add("crouch");
                var attack=player("longsword",InteractionHand.MAIN_HAND,false,false);attack.attackAnim=attack.oAttackAnim=.5f;players.add(attack);labels.add("two-hand swing");
                var blocked=player("longsword",InteractionHand.MAIN_HAND,false,false);blocked.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));players.add(blocked);labels.add("offhand occupied · disabled");
            }else if(mode.equals("catalog")||mode.equals("reload"))for(var k:WeaponKind.values()){items.add(Content.stack("steel_"+k.key));labels.add(k.russian);}
            else if(mode.equals("details"))for(String key:new String[]{"katana","mace","battleaxe","rapier","war_hammer","flail"}){items.add(Content.stack("steel_"+key));labels.add(key);}
            else if(mode.equals("materials"))for(var m:Metal.values()){items.add(Content.stack(m.key+"_katana"));labels.add(m.key);}
            else if(mode.equals("poses")){
                players.add(player("katana",InteractionHand.MAIN_HAND,true,false));labels.add("slim · katana");
                players.add(player("halberd",InteractionHand.OFF_HAND,false,true));labels.add("left · crouch");
                var knight=player("arming_sword",InteractionHand.MAIN_HAND,false,false);knight.setItemInHand(InteractionHand.OFF_HAND,Content.stack("steel_heater_shield"));
                for(var type:Content.PIECES)knight.setItemSlot(type.getSlot(),Content.stack("steel_plate_"+type.getName()));players.add(knight);labels.add("armor / shield");
                var left=player("katar",InteractionHand.MAIN_HAND,false,false);left.setMainArm(HumanoidArm.LEFT);players.add(left);labels.add("left main arm");
                var attack=player("flail",InteractionHand.MAIN_HAND,false,false);attack.attackAnim=attack.oAttackAnim=.5f;players.add(attack);labels.add("swing · flail");
                var walk=player("pike",InteractionHand.MAIN_HAND,false,false);walk.walkAnimation.update(.8f,1);walk.walkAnimation.update(.8f,1);players.add(walk);labels.add("walking · pike");
            }else{
                int group=mode.endsWith("a")?0:mode.endsWith("b")?1:2;var hand=mode.contains("left")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
                for(int i=group*6;i<group*6+6;i++){var k=WeaponKind.values()[i];players.add(player(k.key,hand,false,false));labels.add(k.russian);}
            }
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            frames++;g.fill(0,0,width,height,0xFF181D26);g.drawCenteredString(font,title,width/2,9,0xFFE3C590);
            g.drawCenteredString(font,mode.equals("catalog")?"18 объёмных моделей · сталь":mode,width/2,23,0xFFCAD5E1);
            if(!items.isEmpty()){
                int cols=items.size()==18?6:items.size()==10?5:3,rows=(items.size()+cols-1)/cols,cell=width/cols,rh=(height-43)/rows;
                float scale=items.size()==18?5:items.size()==10?6:7;
                for(int i=0;i<items.size();i++){
                    int cx=(i%cols)*cell+cell/2,cy=43+(i/cols)*rh;
                    g.pose().pushPose();g.pose().translate(cx-8*scale,cy+(rh-23-16*scale)/2,0);g.pose().scale(scale,scale,scale);g.renderItem(items.get(i),0,0);g.pose().popPose();
                    g.drawCenteredString(font,labels.get(i),cx,cy+rh-18,0xFFCAD5E1);
                }
            }else{
                int cols=3,cell=width/cols,rh=(height-43)/2;
                for(int i=0;i<players.size();i++){
                    int cx=(i%cols)*cell+cell/2,cy=43+(i/cols)*rh;var p=players.get(i);
                    InventoryScreen.renderEntityInInventory(g,cx,cy+rh/2,49,new Vector3f(0,p.getBbHeight()/2+.13f,0),new Quaternionf().rotateZ((float)Math.PI).rotateY(-.45f).rotateX(.025f),null,p);
                    g.drawCenteredString(font,labels.get(i),cx,cy+rh-18,0xFFCAD5E1);
                }
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
