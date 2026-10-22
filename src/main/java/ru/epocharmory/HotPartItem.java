package ru.epocharmory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class HotPartItem extends Item {
    public HotPartItem(Properties p){super(p);}
    public static long clock(Level level) {return level.getServer()!=null?level.getServer().overworld().getGameTime():level.getGameTime();}
    public static boolean isHot(ItemStack stack,Level level) {
        return stack.getItem() instanceof HotPartItem && stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getLong("hot_until")>clock(level);
    }
    public static void heat(ItemStack stack,Level level) {
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.putLong("hot_until",clock(level)+3600);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.epocharmory.hot_part").withStyle(ChatFormatting.GOLD));
        // Tooltips are intentionally independent of a client's dimension clock.
        lines.add(Component.translatable("tooltip.epocharmory.reheat").withStyle(ChatFormatting.GRAY));
    }
}
