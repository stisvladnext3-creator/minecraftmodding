package ru.epocharmory;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class Workshops {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EpochArmory.ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EpochArmory.ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EpochArmory.ID);
    public static final Map<Station,DeferredBlock<WorkshopBlock>> STATIONS=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<BlockItem>> BLOCK_ITEMS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<Block>> ORES=new LinkedHashMap<>();
    static {
        for(Station station:Station.values()) {
            var block=BLOCKS.register(station.key,()->new WorkshopBlock(station,BlockBehaviour.Properties.of().strength(3.5f,6).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(s->station.heated&&s.getValue(WorkshopBlock.LIT)?10:0)));
            STATIONS.put(station,block);BLOCK_ITEMS.put(station.key,Content.ITEMS.registerSimpleBlockItem(station.key,block));
        }
        for(String ore:List.of("tin","bauxite","rutile","dolomite"))for(String prefix:List.of("","deepslate_")) {
            String id=prefix+ore+"_ore";
            var block=BLOCKS.register(id,()->new Block(BlockBehaviour.Properties.ofFullCopy(prefix.isEmpty()?Blocks.IRON_ORE:Blocks.DEEPSLATE_IRON_ORE)));
            ORES.put(id,block);BLOCK_ITEMS.put(id,Content.ITEMS.registerSimpleBlockItem(id,block));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WorkshopEntity>> ENTITY=ENTITIES.register("workshop",()->BlockEntityType.Builder.of(WorkshopEntity::new,STATIONS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<WorkshopMenu>> MENU=MENUS.register("workshop",()->IMenuTypeExtension.create(WorkshopMenu::new));
}
