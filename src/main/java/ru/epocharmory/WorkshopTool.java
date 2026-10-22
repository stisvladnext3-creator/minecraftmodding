package ru.epocharmory;
import net.minecraft.world.item.*;
public final class WorkshopTool extends Item {
    public WorkshopTool(Properties p){super(p);}
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack repair){return repair.is(Items.IRON_INGOT);}
}
