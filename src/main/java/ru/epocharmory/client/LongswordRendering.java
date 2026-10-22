package ru.epocharmory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Vector3f;
import ru.epocharmory.*;

/** Both hands share the real sword grip, in first and third person. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class LongswordRendering {
    public static final EnumProxy<HumanoidModel.ArmPose> POSE=new EnumProxy<>(HumanoidModel.ArmPose.class,true,(IArmPoseTransformer)(model,entity,arm)->{
        boolean right=arm==HumanoidArm.RIGHT;
        var main=right?model.rightArm:model.leftArm;
        var support=right?model.leftArm:model.rightArm;
        float sign=right?1:-1,t=model.attackTime;
        float body=Mth.sin(Mth.sqrt(t)*Mth.TWO_PI)*.2f*sign;
        float progress=1-t;progress=1-progress*progress*progress*progress;
        float strike=Mth.sin(progress*Mth.PI)*1.2f+Mth.sin(t*Mth.PI)*-(model.head.xRot-.7f)*.75f;
        main.xRot=-1.08f+model.head.xRot*.3f-(right?0:body);
        support.xRot=-1.18f+model.head.xRot*.3f-strike-(right?body:0);
        main.yRot=-.35f*sign+model.head.yRot*.2f-2*body;
        support.yRot=.69f*sign+model.head.yRot*.2f;
        main.zRot=0;support.zRot=-Mth.sin(t*Mth.PI)*.4f;
    });

    @SubscribeEvent public static void hands(RenderHandEvent event){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||!Combat.readyLongsword(player))return;
        if(event.getHand()==InteractionHand.OFF_HAND){event.setCanceled(true);return;}
        if(!(event.getItemStack().getItem() instanceof HistoricalWeapon w)||w.kind!=WeaponKind.LONGSWORD)return;
        event.setCanceled(true);
        PoseStack pose=event.getPoseStack();pose.pushPose();
        boolean left=player.getMainArm()==HumanoidArm.LEFT;float sign=left?-1:1,swing=event.getSwingProgress();
        float root=Mth.sqrt(swing),arc=Mth.sin(root*Mth.PI);
        pose.translate(sign*(.32f-.4f*arc),-.42f+.2f*Mth.sin(root*Mth.TWO_PI)-event.getEquipProgress()*.6f,-1.0f-.2f*Mth.sin(swing*Mth.PI));
        // Same swing axes as ItemInHandRenderer; the two wrists move with the
        // weapon throughout the swing rather than drawing an unrelated hand.
        pose.mulPose(Axis.YP.rotationDegrees(sign*(45-20*Mth.sin(swing*swing*Mth.PI))));
        pose.mulPose(Axis.ZP.rotationDegrees(sign*-20*arc));
        pose.mulPose(Axis.XP.rotationDegrees(-80*arc));
        pose.mulPose(Axis.YP.rotationDegrees(sign*-45));
        var context=left?ItemDisplayContext.FIRST_PERSON_LEFT_HAND:ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        mc.getItemRenderer().renderStatic(player,event.getItemStack(),context,left,pose,event.getMultiBufferSource(),player.level(),event.getPackedLight(),OverlayTexture.NO_OVERLAY,player.getId());
        if(!player.isInvisible()){
            var model=mc.getItemRenderer().getModel(event.getItemStack(),player.level(),player,0);
            var grip=new PoseStack();model.applyTransform(context,grip,left);
            Vector3f upper=grip.last().pose().transformPosition(new Vector3f(0,.12f,0));
            Vector3f lower=grip.last().pose().transformPosition(new Vector3f(0,-.23f,0));
            arm(event,pose,player.getMainArm(),upper);
            arm(event,pose,player.getMainArm().getOpposite(),lower);
        }
        pose.popPose();
    }

    private static void arm(RenderHandEvent event,PoseStack pose,HumanoidArm side,Vector3f wrist){
        var mc=Minecraft.getInstance();var player=mc.player;
        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
        var model=renderer.getModel();boolean right=side==HumanoidArm.RIGHT;
        ModelPart arm=right?model.rightArm:model.leftArm,sleeve=right?model.rightSleeve:model.leftSleeve;
        PartPose armPose=arm.storePose(),sleevePose=sleeve.storePose();
        boolean armVisible=arm.visible,sleeveVisible=sleeve.visible;
        pose.pushPose();
        pose.translate(wrist.x,wrist.y,wrist.z);
        pose.mulPose(Axis.YP.rotationDegrees(right?25:-25));
        pose.mulPose(Axis.XP.rotationDegrees(-70));
        float center=player.getSkin().model().id().equals("slim")?.5f:1;
        pose.translate((right?center:-center)/16,-9f/16,0);
        arm.loadPose(PartPose.ZERO);sleeve.loadPose(PartPose.ZERO);arm.visible=true;sleeve.visible=true;
        var skin=player.getSkin().texture();
        arm.render(pose,event.getMultiBufferSource().getBuffer(RenderType.entitySolid(skin)),event.getPackedLight(),OverlayTexture.NO_OVERLAY);
        if(player.isModelPartShown(right?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE))
            sleeve.render(pose,event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(skin)),event.getPackedLight(),OverlayTexture.NO_OVERLAY);
        arm.loadPose(armPose);sleeve.loadPose(sleevePose);arm.visible=armVisible;sleeve.visible=sleeveVisible;
        pose.popPose();
    }
}
