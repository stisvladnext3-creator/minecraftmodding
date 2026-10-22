package ru.epocharmory;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.*;

public record WorkshopRecipe(String station,List<Part> inputs,ItemStack result,ItemStack byproduct,
        String tool,int time,int minHeat,int maxHeat,boolean hot) implements Recipe<WorkshopRecipe.Input> {
    public static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,EpochArmory.ID);
    public static final DeferredHolder<RecipeType<?>,RecipeType<WorkshopRecipe>> TYPE=TYPES.register("workshop",()->new RecipeType<>(){public String toString(){return "epocharmory:workshop";}});
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,EpochArmory.ID);
    public static final DeferredHolder<RecipeSerializer<?>,Serializer> SERIALIZER=SERIALIZERS.register("workshop",Serializer::new);
    public record Part(String item,int count) {
        static final Codec<Part> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("item").forGetter(Part::item),Codec.intRange(1,64).optionalFieldOf("count",1).forGetter(Part::count)).apply(i,Part::new));
        public Ingredient ingredient() {return item.startsWith("#")?Ingredient.of(TagKey.create(Registries.ITEM,ResourceLocation.parse(item.substring(1)))):Ingredient.of(Content.item(item));}
        public boolean test(ItemStack stack) {return !stack.isEmpty() && ingredient().test(stack);}
        public ItemStack preview() {var items=ingredient().getItems();return items.length==0?ItemStack.EMPTY:items[0].copyWithCount(count);}
    }
    public record Input(Station station,List<ItemStack> stacks,ItemStack tool) implements RecipeInput {
        @Override public ItemStack getItem(int i){return stacks.get(i);}
        @Override public int size(){return stacks.size();}
    }
    public WorkshopRecipe {
        if(inputs.isEmpty()||inputs.size()>6||time<1||minHeat<0||maxHeat<minHeat)throw new IllegalArgumentException("Invalid workshop recipe");
        Station.named(station);
        inputs=List.copyOf(inputs);result=result.copy();byproduct=byproduct.copy();
    }
    /** Allocation counts every input once, including overlapping item tags. */
    public int[] allocation(Input in,Level level,boolean checkHeat) {
        int[] remaining=in.stacks.stream().mapToInt(ItemStack::getCount).toArray();
        int[] used=new int[remaining.length];
        // Exact ingredients first; broad tags may use only the remaining stacks.
        var ordered=new ArrayList<>(inputs);
        ordered.sort(Comparator.comparing(p->p.item.startsWith("#")));
        for(Part part:ordered) {
            int need=part.count;
            for(int s=0;s<remaining.length&&need>0;s++) {
                ItemStack stack=in.getItem(s);
                if(!part.test(stack)||checkHeat&&hot&&stack.getItem() instanceof HotPartItem&&!HotPartItem.isHot(stack,level))continue;
                int take=Math.min(need,remaining[s]);used[s]+=take;remaining[s]-=take;need-=take;
            }
            if(need>0)return null;
        }
        return used;
    }
    public boolean toolMatches(ItemStack stack){return tool.isEmpty()||stack.is(Content.item(tool));}
    @Override public boolean matches(Input in,Level level) {return in.station.key.equals(station)&&toolMatches(in.tool)&&allocation(in,level,true)!=null;}
    @Override public ItemStack assemble(Input in,HolderLookup.Provider registries){return result.copy();}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return result.copy();}
    @Override public NonNullList<Ingredient> getIngredients(){var list=NonNullList.<Ingredient>create();inputs.forEach(p->list.add(p.ingredient()));return list;}
    @Override public boolean canCraftInDimensions(int w,int h){return w*h>=inputs.size();}
    @Override public RecipeSerializer<?> getSerializer(){return SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return TYPE.get();}
    @Override public boolean isSpecial(){return true;}
    @Override public boolean isIncomplete(){return false;}

    public static final class Serializer implements RecipeSerializer<WorkshopRecipe> {
        private static final MapCodec<WorkshopRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.STRING.fieldOf("station").forGetter(WorkshopRecipe::station),
            Part.CODEC.listOf().fieldOf("inputs").forGetter(WorkshopRecipe::inputs),
            ItemStack.CODEC.fieldOf("result").forGetter(WorkshopRecipe::result),
            ItemStack.CODEC.optionalFieldOf("byproduct",ItemStack.EMPTY).forGetter(WorkshopRecipe::byproduct),
            Codec.STRING.optionalFieldOf("tool","").forGetter(WorkshopRecipe::tool),
            Codec.intRange(1,12000).optionalFieldOf("time",60).forGetter(WorkshopRecipe::time),
            Codec.intRange(0,1600).optionalFieldOf("min_heat",0).forGetter(WorkshopRecipe::minHeat),
            Codec.intRange(0,1600).optionalFieldOf("max_heat",1600).forGetter(WorkshopRecipe::maxHeat),
            Codec.BOOL.optionalFieldOf("hot",false).forGetter(WorkshopRecipe::hot)).apply(i,WorkshopRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf,WorkshopRecipe> STREAM=new StreamCodec<>() {
            @Override public WorkshopRecipe decode(RegistryFriendlyByteBuf b) {
                String station=b.readUtf(64);int count=b.readVarInt();
                if(count<1||count>6)throw new IllegalArgumentException("Recipe input count");
                var parts=new ArrayList<Part>();for(int i=0;i<count;i++)parts.add(new Part(b.readUtf(256),b.readVarInt()));
                return new WorkshopRecipe(station,parts,ItemStack.STREAM_CODEC.decode(b),ItemStack.OPTIONAL_STREAM_CODEC.decode(b),b.readUtf(128),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,WorkshopRecipe r) {
                b.writeUtf(r.station,64);b.writeVarInt(r.inputs.size());
                for(Part p:r.inputs){b.writeUtf(p.item,256);b.writeVarInt(p.count);}
                ItemStack.STREAM_CODEC.encode(b,r.result);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,r.byproduct);
                b.writeUtf(r.tool,128);b.writeVarInt(r.time);b.writeVarInt(r.minHeat);b.writeVarInt(r.maxHeat);b.writeBoolean(r.hot);
            }
        };
        @Override public MapCodec<WorkshopRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,WorkshopRecipe> streamCodec(){return STREAM;}
    }
}
