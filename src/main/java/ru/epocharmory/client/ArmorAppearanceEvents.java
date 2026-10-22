package ru.epocharmory.client;

import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import ru.epocharmory.*;

/** Hide skin overlays under armor; vanilla resets them for each player render. */
@EventBusSubscriber(modid=EpochArmory.ID,value=Dist.CLIENT)
public final class ArmorAppearanceEvents {
    @SubscribeEvent public static void before(RenderPlayerEvent.Pre event){
        var player=event.getEntity();
        if(player.isSpectator())return;
        var model=event.getRenderer().getModel();
        if(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HistoricalArmor)model.hat.visible=false;
        if(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof HistoricalArmor){
            model.jacket.visible=false;model.leftSleeve.visible=false;model.rightSleeve.visible=false;
        }
        if(player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof HistoricalArmor){
            model.leftPants.visible=false;model.rightPants.visible=false;
        }
    }
}
