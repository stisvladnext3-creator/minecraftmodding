package ru.epocharmory;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Networking {
    public record Stamina(int value) implements CustomPacketPayload {
        public static final Type<Stamina> TYPE=new Type<>(EpochArmory.id("stamina"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Stamina> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Stamina::value,Stamina::new);
        @Override public Type<Stamina> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(Stamina.TYPE,Stamina.CODEC,(payload,context)->context.enqueueWork(()->ru.epocharmory.client.ClientState.stamina=payload.value()));}
    public static void sync(ServerPlayer p,int value){if(p.connection!=null)PacketDistributor.sendToPlayer(p,new Stamina(value));}
}
