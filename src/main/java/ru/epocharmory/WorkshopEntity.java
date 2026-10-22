package ru.epocharmory;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One server-owned persistent inventory; both players observe the same atomic job. */
public final class WorkshopEntity extends BlockEntity implements Container,MenuProvider {
    public static final int TOOL=6,FUEL=7,OUTPUT=8,BYPRODUCT=9;
    private NonNullList<ItemStack> items=NonNullList.withSize(10,ItemStack.EMPTY);
    public int selected,progress,temperature=20,target,burn,status;
    private long lastStroke=-100;
    public final ContainerData data=new ContainerData(){
        @Override public int get(int i){return switch(i){case 0->selected;case 1->progress;case 2->temperature;case 3->target;case 4->burn;case 5->status;default->0;};}
        @Override public void set(int i,int v){switch(i){case 0->selected=v;case 1->progress=v;case 2->temperature=v;case 3->target=v;case 4->burn=v;case 5->status=v;}}
        @Override public int getCount(){return 6;}
    };
    public WorkshopEntity(BlockPos p,BlockState s){super(Workshops.ENTITY.get(),p,s);target=station().defaultTarget;}
    public Station station(){return ((WorkshopBlock)getBlockState().getBlock()).station;}
    public static List<RecipeHolder<WorkshopRecipe>> recipes(Level level,Station station){
        return level.getRecipeManager().getAllRecipesFor(WorkshopRecipe.TYPE.get()).stream().filter(h->h.value().station().equals(station.key)).sorted(Comparator.comparing(h->h.id().toString())).toList();
    }
    public WorkshopRecipe recipe(){var list=recipes(level,station());return selected>=0&&selected<list.size()?list.get(selected).value():null;}
    public void select(int index){var list=recipes(level,station());if(index<0||index>=list.size())return;selected=index;progress=0;var r=list.get(index).value();if(station().heated)target=Math.min(r.maxHeat(),Math.max(r.minHeat()+30,station().defaultTarget));setChanged();}
    public WorkshopRecipe.Input input(){return new WorkshopRecipe.Input(station(),List.copyOf(items.subList(0,6)),items.get(TOOL));}
    private boolean fits(int slot,ItemStack out){var s=items.get(slot);return out.isEmpty()||s.isEmpty()||ItemStack.isSameItemSameComponents(s,out)&&s.getCount()+out.getCount()<=s.getMaxStackSize();}
    public int check(WorkshopRecipe r){
        if(r==null||!r.station().equals(station().key)||r.allocation(input(),level,false)==null)return 1;
        if(!r.toolMatches(items.get(TOOL)))return 2;
        if(r.hot()&&r.allocation(input(),level,true)==null)return 3;
        if(!fits(OUTPUT,r.result())||!fits(BYPRODUCT,r.byproduct()))return 5;
        if(station().heated&&(temperature<r.minHeat()||temperature>r.maxHeat()))return 4;
        return 0;
    }
    public static int fuelTime(ItemStack stack,Station station){
        if(station==Station.ELECTROLYZER)return stack.is(Items.REDSTONE)?400:stack.is(Items.REDSTONE_BLOCK)?3600:0;
        if(stack.is(Items.COAL)||stack.is(Items.CHARCOAL))return 1600;
        if(stack.is(Items.COAL_BLOCK))return 16000;
        if(stack.is(Items.BLAZE_ROD))return 2400;
        return 0;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,WorkshopEntity e){
        var r=e.recipe();Station s=e.station();
        if(s.heated){
            if(e.burn<=0&&e.temperature<e.target&&r!=null&&r.allocation(e.input(),level,false)!=null){
                e.burn=fuelTime(e.items.get(FUEL),s);if(e.burn>0)e.items.get(FUEL).shrink(1);
            }
            if(e.burn>0){e.burn--;if(e.temperature<e.target)e.temperature=Math.min(e.target,e.temperature+5);}
            if(e.temperature>e.target||e.burn<=0&&level.getGameTime()%4==0)e.temperature=Math.max(20,e.temperature-1);
            boolean lit=e.burn>0;
            if(state.getValue(WorkshopBlock.LIT)!=lit)level.setBlock(pos,state.setValue(WorkshopBlock.LIT,lit),3);
        }
        e.status=e.check(r);
        if(e.status==1||e.status==2||e.status==3)e.progress=0;
        if(e.status==0&&!s.manual){e.progress++;if(e.progress>=r.time())e.finish(r);}
        if(level.getGameTime()%20==0)e.setChanged();
    }
    public boolean stroke(Player player){
        if(!station().manual||level==null||level.isClientSide||level.getGameTime()-lastStroke<3)return false;
        var r=recipe();if(check(r)!=0)return false;
        lastStroke=level.getGameTime();progress+=20;
        level.playSound(null,worldPosition,station()==Station.GRIND?SoundEvents.GRINDSTONE_USE:SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.35f,1.2f);
        if(progress>=r.time())finish(r);
        setChanged();return true;
    }
    public boolean finish(WorkshopRecipe r){
        if(check(r)!=0)return false;
        int[] used=r.allocation(input(),level,true);if(used==null)return false;
        ItemStack out=r.result().copy();if(out.getItem() instanceof HotPartItem)HotPartItem.heat(out,level);
        // A stack with a different hot-until value must not be merged with another batch.
        if(!fits(OUTPUT,out))return false;
        for(int i=0;i<6;i++)items.get(i).shrink(used[i]);
        merge(OUTPUT,out);merge(BYPRODUCT,r.byproduct().copy());
        if(!r.tool().isEmpty()){
            var tool=items.get(TOOL);int wear=station()==Station.ANVIL?2:1;
            if(tool.getDamageValue()+wear>=tool.getMaxDamage())items.set(TOOL,ItemStack.EMPTY);else tool.setDamageValue(tool.getDamageValue()+wear);
        }
        progress=0;setChanged();return true;
    }
    private void merge(int slot,ItemStack stack){if(stack.isEmpty())return;if(items.get(slot).isEmpty())items.set(slot,stack);else items.get(slot).grow(stack.getCount());}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider r){super.saveAdditional(tag,r);ContainerHelper.saveAllItems(tag,items,r);tag.putInt("Selected",selected);tag.putInt("Progress",progress);tag.putInt("Temperature",temperature);tag.putInt("Target",target);tag.putInt("Burn",burn);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider r){super.loadAdditional(tag,r);items=NonNullList.withSize(10,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,r);selected=Math.max(0,tag.getInt("Selected"));progress=Math.max(0,tag.getInt("Progress"));temperature=Math.max(20,tag.getInt("Temperature"));target=tag.contains("Target")?Math.max(20,Math.min(1400,tag.getInt("Target"))):station().defaultTarget;burn=Math.max(0,tag.getInt("Burn"));}
    @Override public int getContainerSize(){return 10;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){var out=ContainerHelper.removeItem(items,i,n);setChanged();return out;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){items.set(i,s);s.limitSize(s.getMaxStackSize());setChanged();}
    @Override public void clearContent(){items.clear();progress=0;setChanged();}
    @Override public boolean stillValid(Player p){return level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.epocharmory."+station().key);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new WorkshopMenu(id,inv,this);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot<TOOL||slot==TOOL&&stack.getItem() instanceof WorkshopTool||slot==FUEL&&fuelTime(stack,station())>0;}
}
