package ru.epocharmory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class HistoricalArmor extends ArmorItem {
    public final Metal metal;
    public final ArmorStyle style;
    public HistoricalArmor(Holder<ArmorMaterial> material,Type type,Properties props,Metal metal,ArmorStyle style) {
        super(material,type,props);this.metal=metal;this.style=style;
    }
    public float weightPenalty() {return Math.max(0,style.slowdown*metal.weight-metal.mobility)/4;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.epocharmory.style."+style.key).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.epocharmory.piece_weight",String.format(java.util.Locale.ROOT,"%.1f",weightPenalty()*100)).withStyle(ChatFormatting.GRAY));
        if(metal.composite())lines.add(Component.translatable("tooltip.epocharmory.composite").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
