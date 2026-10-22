package ru.epocharmory;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/** Values are a game balance, not claims about historical strength. */
public enum Metal implements Tier {
    IRON("iron", "железа", -0.5f, 420, 15, 0.00f, 0, 1.00f),
    BRONZE("bronze", "бронзы", -1.0f, 350, 17, -0.02f, 0, 1.07f),
    GOLD("gold", "золота", -2.0f, 180, 30, -0.04f, -3, 1.20f),
    STEEL("steel", "стали", 0f, 800, 15, 0f, 1, 1.00f),
    DAMASCUS("damascus", "дамасской стали", 0.5f, 1120, 20, 0f, 1, 1.00f),
    ALUMINIUM("aluminium", "алюминиевого сплава", -1f, 390, 16, 0.03f, -2, 0.70f),
    MAGNESIUM("magnesium", "магниевого сплава", -1.5f, 300, 18, 0.04f, -3, 0.60f),
    TITANIUM("titanium", "титанового сплава", 0.5f, 1400, 12, 0.015f, 1, 0.82f),
    DIAMOND("diamond", "стали с алмазной кромкой", 1f, 1561, 10, 0f, 1, 1.00f),
    NETHERITE("netherite", "незерита", 2f, 2031, 15, -0.005f, 1, 1.08f);

    public final String key, russian;
    public final float bonus, mobility, weight;
    public final int uses, enchantability, armorBonus;
    Metal(String key, String russian, float bonus, int uses, int ench, float mobility, int armorBonus, float weight) {
        this.key=key; this.russian=russian; this.bonus=bonus; this.uses=uses;
        this.enchantability=ench; this.mobility=mobility; this.armorBonus=armorBonus; this.weight=weight;
    }
    public boolean composite() { return this==DIAMOND || this==NETHERITE; }
    public Metal core() { return this==DIAMOND ? STEEL : this==NETHERITE ? DAMASCUS : this; }
    public boolean hardenable() { return this==STEEL || this==DAMASCUS; }
    public String feedstock() {
        return switch(this) {
            case IRON -> "minecraft:iron_ingot";
            case GOLD -> "minecraft:gold_ingot";
            case DIAMOND -> "minecraft:diamond";
            case NETHERITE -> "minecraft:netherite_ingot";
            default -> "epocharmory:"+key+"_ingot";
        };
    }
    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return 6; }
    @Override public float getAttackDamageBonus() { return 0; }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Content.item(feedstock())); }
}
