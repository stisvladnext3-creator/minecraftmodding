package ru.epocharmory.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import ru.epocharmory.*;

@EventBusSubscriber(modid=EpochArmory.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientEvents {
    private static final Map<String,HumanoidModel<?>> MODELS=new HashMap<>();
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(Workshops.MENU.get(),WorkshopScreen::new);}
    @SubscribeEvent public static void bake(EntityRenderersEvent.AddLayers e){MODELS.clear();HistoricalArmorModel.clearGeometry();}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent e){
        Item[] armor=Content.ALL.values().stream().map(h->h.get()).filter(i->i instanceof HistoricalArmor).toArray(Item[]::new);
        e.registerItem(new IClientItemExtensions(){
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
                if(!(stack.getItem() instanceof HistoricalArmor a))return original;
                String key=a.style.key+"_"+slot.getName();
                return MODELS.computeIfAbsent(key,k->new HistoricalArmorModel(a.style,slot));
            }
        },armor);
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{
        for(var holder:Content.ALL.values())if(holder.get() instanceof HistoricalShield shield)
            ItemProperties.register(shield,net.minecraft.resources.ResourceLocation.withDefaultNamespace("blocking"),(stack,level,entity,seed)->entity!=null&&entity.isUsingItem()&&entity.getUseItem()==stack?1:0);
    });}
    @SubscribeEvent public static void hud(RegisterGuiLayersEvent e){
        e.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR,EpochArmory.id("stamina"),(g,delta)->{
            var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
            boolean shield=mc.player.getOffhandItem().getItem() instanceof HistoricalShield||mc.player.getMainHandItem().getItem() instanceof HistoricalShield;
            if(!shield)return;
            int x=g.guiWidth()/2-48,y=g.guiHeight()-62;
            g.fill(x-1,y-1,x+97,y+6,0xDD171A20);g.fill(x,y,x+96,y+5,0xFF39404A);
            g.fill(x,y,x+Math.round(96*ClientState.stamina/100f),y+5,ClientState.stamina<25?0xFFCE6D57:0xFFD8B878);
        });
    }
}
