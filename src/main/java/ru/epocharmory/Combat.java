package ru.epocharmory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

public final class Combat {
    private static final ResourceLocation WEIGHT=EpochArmory.id("equipment_weight"),TWO_DAMAGE=EpochArmory.id("two_handed_damage"),TWO_SPEED=EpochArmory.id("two_handed_speed");
    public static int stamina(Player p){return p.getPersistentData().contains("epoch_stamina")?p.getPersistentData().getInt("epoch_stamina"):100;}
    public static void stamina(Player p,int value){p.getPersistentData().putInt("epoch_stamina",Math.max(0,Math.min(100,value)));}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        float penalty=0;
        for(var stack:p.getArmorSlots())if(stack.getItem() instanceof HistoricalArmor armor)penalty+=armor.weightPenalty();
        if(p.getUseItem().getItem() instanceof HistoricalShield shield)penalty+=shield.kind.slowdown*shield.metal.weight;
        modifier(p,Attributes.MOVEMENT_SPEED,WEIGHT,-Math.min(.25,penalty));
        boolean encumbered=p.getMainHandItem().getItem() instanceof HistoricalWeapon w&&w.kind.twoHanded&&!p.getOffhandItem().isEmpty();
        modifier(p,Attributes.ATTACK_DAMAGE,TWO_DAMAGE,encumbered?-.25:0);
        modifier(p,Attributes.ATTACK_SPEED,TWO_SPEED,encumbered?-.15:0);
        if(p.tickCount%2==0&&!p.isUsingItem()&&p.level().getGameTime()>p.getPersistentData().getLong("epoch_recover_at"))stamina(p,stamina(p)+1);
        if(p.tickCount%5==0)Networking.sync(p,stamina(p));
    }
    private static void modifier(Player p,net.minecraft.core.Holder<Attribute> attribute,ResourceLocation id,double amount){
        var instance=p.getAttribute(attribute);if(instance==null)return;
        var current=instance.getModifier(id);
        if(current!=null&&Math.abs(current.amount()-amount)<.00001)return;
        instance.removeModifier(id);if(amount!=0)instance.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event){
        if(event.getSource().getDirectEntity() instanceof LivingEntity attacker&&attacker.getMainHandItem().getItem() instanceof HistoricalWeapon weapon){
            event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,reduction)->reduction*(1-weapon.kind.penetration));
            LivingEntity target=event.getEntity();
            if((weapon.kind==WeaponKind.DAGGER||weapon.kind==WeaponKind.KATAR)&&target.getLookAngle().dot(attacker.position().subtract(target.position()).normalize())<-.3)
                event.setAmount(event.getAmount()*1.5f);
            if((weapon.kind==WeaponKind.SPEAR||weapon.kind==WeaponKind.PIKE)&&target.getDeltaMovement().dot(attacker.position().subtract(target.position()).normalize())>.12)
                event.setAmount(event.getAmount()*1.3f);
        }
    }
    @SubscribeEvent public static void armor(LivingDamageEvent.Pre event){
        String type=event.getSource().is(DamageTypeTags.IS_PROJECTILE)?"projectile":event.getSource().getDirectEntity() instanceof LivingEntity attacker?
            attacker.getMainHandItem().getItem() instanceof HistoricalWeapon w?w.kind.damageType:"blunt":"other";
        if(type.equals("other")||event.getSource().is(DamageTypeTags.BYPASSES_ARMOR))return;
        float correction=0;
        for(var stack:event.getEntity().getArmorSlots())if(stack.getItem() instanceof HistoricalArmor a){
            correction+=switch(a.style){
                case MAIL->type.equals("slash")?-.03f:type.equals("blunt")?.025f:0;
                case LAMELLAR->type.equals("projectile")?-.03f:type.equals("slash")?-.015f:0;
                case BRIGANDINE->-.015f;
                case PLATE->type.equals("slash")?-.03f:type.equals("pierce")?-.02f:0;
            };
        }
        event.setNewDamage(Math.max(0,event.getNewDamage()*(1+correction)));
    }
    @SubscribeEvent public static void shield(LivingShieldBlockEvent event){
        if(!event.getOriginalBlock())return;
        LivingEntity blocker=event.getEntity();
        var attacker=event.getDamageSource().getDirectEntity() instanceof LivingEntity living?living:null;
        boolean flail=attacker!=null&&attacker.getMainHandItem().getItem() instanceof HistoricalWeapon w&&w.kind==WeaponKind.FLAIL;
        if(!(blocker.getUseItem().getItem() instanceof HistoricalShield shield)){
            if(flail)event.setBlockedDamage(event.getOriginalBlockedDamage()*.55f);
            return;
        }
        if(blocker.getMainHandItem().getItem() instanceof HistoricalWeapon w&&w.kind.twoHanded){event.setBlocked(false);return;}
        boolean parry=blocker.getTicksUsingItem()>=5&&blocker.getTicksUsingItem()<=5+shield.kind.parryTicks;
        int cost=Math.max(1,Math.round((shield.kind.stamina+event.getOriginalBlockedDamage()*.5f)*shield.metal.weight*(parry?.5f:1)));
        if(blocker instanceof Player p&&!p.isCreative()){
            if(stamina(p)<cost){event.setBlocked(false);p.getCooldowns().addCooldown(shield,40);p.stopUsingItem();return;}
            stamina(p,stamina(p)-cost);p.getPersistentData().putLong("epoch_recover_at",p.level().getGameTime()+20);
            if(p instanceof ServerPlayer sp)Networking.sync(sp,stamina(p));
        }
        float fraction=event.getDamageSource().is(DamageTypeTags.IS_PROJECTILE)?shield.kind.projectile:shield.kind.melee;
        if(parry){fraction=1;if(attacker!=null){attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,1));if(attacker instanceof Player p)p.getCooldowns().addCooldown(attacker.getMainHandItem().getItem(),8);}}
        if(flail)fraction*=.65f;
        event.setBlockedDamage(event.getOriginalBlockedDamage()*fraction);
        event.setShieldDamage(event.getOriginalBlockedDamage()*(parry?.35f:1));
    }
}
