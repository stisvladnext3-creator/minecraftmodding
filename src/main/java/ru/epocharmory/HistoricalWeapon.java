package ru.epocharmory;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

public final class HistoricalWeapon extends SwordItem {
    public final Metal metal;
    public final WeaponKind kind;
    public HistoricalWeapon(Metal metal,WeaponKind kind) {
        super(metal,Content.props(metal).durability(metal.uses).attributes(attributes(metal,kind)));
        this.metal=metal;this.kind=kind;
    }
    private static ItemAttributeModifiers attributes(Metal m,WeaponKind k) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,k.damage+m.bonus-1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,k.speed-4,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE,new AttributeModifier(EpochArmory.id("weapon_reach"),k.reach-3,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build();
    }
    @Override public boolean canDisableShield(ItemStack stack,ItemStack shield,LivingEntity target,LivingEntity attacker) {
        return kind==WeaponKind.BATTLEAXE || kind==WeaponKind.HALBERD;
    }
    @Override public boolean canPerformAction(ItemStack stack,ItemAbility action) {
        return (kind==WeaponKind.BATTLEAXE && ItemAbilities.DEFAULT_AXE_ACTIONS.contains(action)) || super.canPerformAction(stack,action);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.epocharmory.reach",kind.reach).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.epocharmory.penetration",Math.round(kind.penetration*100)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.epocharmory.type."+kind.damageType).withStyle(ChatFormatting.GRAY));
        if(kind==WeaponKind.LONGSWORD) lines.add(Component.translatable("tooltip.epocharmory.longsword_hands").withStyle(ChatFormatting.GOLD));
        else if(kind.twoHanded) lines.add(Component.translatable("tooltip.epocharmory.two_handed").withStyle(ChatFormatting.GOLD));
        if(kind==WeaponKind.DAGGER || kind==WeaponKind.KATAR)lines.add(Component.translatable("tooltip.epocharmory.backstab").withStyle(ChatFormatting.DARK_PURPLE));
        if(kind==WeaponKind.BATTLEAXE||kind==WeaponKind.HALBERD)lines.add(Component.translatable("tooltip.epocharmory.shield_break").withStyle(ChatFormatting.GOLD));
    }
}
