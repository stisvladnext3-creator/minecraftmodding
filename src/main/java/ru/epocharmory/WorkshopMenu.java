package ru.epocharmory;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class WorkshopMenu extends AbstractContainerMenu {
    public final Container inventory;
    public final ContainerData data;
    public final Station station;
    public final WorkshopEntity entity;
    public final List<RecipeHolder<WorkshopRecipe>> recipes;
    private final Inventory playerInventory;
    public WorkshopMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,requireEntity(inv,buf));}
    private static WorkshopEntity requireEntity(Inventory inv,RegistryFriendlyByteBuf b){
        var e=inv.player.level().getBlockEntity(b.readBlockPos());
        if(e instanceof WorkshopEntity workshop)return workshop;
        throw new IllegalStateException("Missing workshop");
    }
    public WorkshopMenu(int id,Inventory inv,WorkshopEntity entity){
        super(Workshops.MENU.get(),id);this.entity=entity;station=entity.station();
        inventory=entity;data=entity.data;playerInventory=inv;recipes=WorkshopEntity.recipes(inv.player.level(),station);
        for(int i=0;i<6;i++)addSlot(new Slot(inventory,i,166+(i%3)*20,50+(i/3)*20));
        addSlot(new Slot(inventory,6,244,50){@Override public boolean mayPlace(ItemStack s){return s.getItem() instanceof WorkshopTool;}});
        addSlot(new Slot(inventory,7,244,78){@Override public boolean mayPlace(ItemStack s){return WorkshopEntity.fuelTime(s,station)>0;}});
        addSlot(new Slot(inventory,8,310,52){@Override public boolean mayPlace(ItemStack s){return false;}});
        addSlot(new Slot(inventory,9,310,78){@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,x+y*9+9,166+x*18,160+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(inv,x,166+x*18,218));
        addDataSlots(data);
    }
    public WorkshopRecipe selected(){int i=data.get(0);return i>=0&&i<recipes.size()?recipes.get(i).value():null;}
    @Override public boolean stillValid(Player p){return entity.stillValid(p);}
    @Override public boolean clickMenuButton(Player p,int button){
        if(p.level().isClientSide||!stillValid(p))return false;
        if(button>=1000){entity.select(button-1000);broadcastChanges();return true;}
        if(button==0)return entity.stroke(p);
        if(button==1)return fill(p);
        if(button==2||button==3){entity.target=Math.max(20,Math.min(1400,entity.target+(button==2?-50:50)));entity.setChanged();return true;}
        return false;
    }
    /** Only an empty input area is auto-filled. Planning completes before any inventory mutation. */
    private boolean fill(Player p){
        var r=selected();if(r==null)return false;
        for(int i=0;i<6;i++)if(!inventory.getItem(i).isEmpty()){p.displayClientMessage(Component.translatable("message.epocharmory.empty_inputs"),true);return false;}
        var plan=new ArrayList<ItemStack>();var source=new ArrayList<ItemStack>();
        for(int i=0;i<36;i++)source.add(playerInventory.getItem(i).copy());
        for(var part:r.inputs()){
            int need=part.count();
            for(int s=0;s<source.size()&&need>0;s++)if(part.test(source.get(s))){
                int take=Math.min(need,source.get(s).getCount());
                ItemStack moved=source.get(s).copyWithCount(take);source.get(s).shrink(take);need-=take;
                boolean merged=false;for(var existing:plan)if(ItemStack.isSameItemSameComponents(existing,moved)&&existing.getCount()+take<=existing.getMaxStackSize()){existing.grow(take);merged=true;break;}
                if(!merged)plan.add(moved);
            }
            if(need>0||plan.size()>6){p.displayClientMessage(Component.translatable("message.epocharmory.missing_items"),true);return false;}
        }
        for(int i=0;i<36;i++)playerInventory.setItem(i,source.get(i));
        for(int i=0;i<plan.size();i++)inventory.setItem(i,plan.get(i));
        if(inventory.getItem(WorkshopEntity.TOOL).isEmpty()&&!r.tool().isEmpty()){
            for(int i=0;i<36;i++)if(playerInventory.getItem(i).is(Content.item(r.tool()))){inventory.setItem(WorkshopEntity.TOOL,playerInventory.removeItem(i,1));break;}
        }
        playerInventory.setChanged();broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<10){if(!moveItemStackTo(stack,10,46,true))return ItemStack.EMPTY;}
        else if(stack.getItem() instanceof WorkshopTool){if(!moveItemStackTo(stack,6,7,false))return ItemStack.EMPTY;}
        else if(WorkshopEntity.fuelTime(stack,station)>0){
            // Fuel may also be a recipe ingredient (charcoal and redstone).
            var r=selected();boolean ingredient=r!=null&&r.inputs().stream().anyMatch(part->part.test(stack));
            if(!moveItemStackTo(stack,ingredient?0:7,ingredient?6:8,false))return ItemStack.EMPTY;
        } else if(!moveItemStackTo(stack,0,6,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;
        slot.onTake(p,stack);return copy;
    }
}
