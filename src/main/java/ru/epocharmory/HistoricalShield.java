package ru.epocharmory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class HistoricalShield extends ShieldItem {
    public final Metal metal;
    public final ShieldKind kind;
    public HistoricalShield(Metal m,ShieldKind k) {
        super(Content.props(m).durability(Math.round(m.uses*k.durability)));
        metal=m;kind=k;
    }
    @Override public int getEnchantmentValue() {return metal.enchantability;}
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack repair) {return metal.getRepairIngredient().test(repair);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.epocharmory.shield_block",Math.round(kind.melee*100),Math.round(kind.projectile*100)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.epocharmory.shield_stamina",Math.round(kind.stamina*metal.weight),kind.parryTicks/20f).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.epocharmory.shield_slow",Math.round(kind.slowdown*metal.weight*100)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.epocharmory.parry").withStyle(ChatFormatting.GOLD));
    }
}
