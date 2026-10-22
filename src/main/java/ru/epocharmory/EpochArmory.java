package ru.epocharmory;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(EpochArmory.ID)
public final class EpochArmory {
    public static final String ID="epocharmory";
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID,path); }
    public EpochArmory(IEventBus bus) {
        Content.ITEMS.register(bus); Content.MATERIALS.register(bus); Content.TABS.register(bus);
        Workshops.BLOCKS.register(bus); Workshops.ENTITIES.register(bus); Workshops.MENUS.register(bus);
        WorkshopRecipe.TYPES.register(bus); WorkshopRecipe.SERIALIZERS.register(bus);
        bus.addListener(Networking::register);
        NeoForge.EVENT_BUS.register(Combat.class);
    }
}
