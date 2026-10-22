package ru.epocharmory;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class WorkshopBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public final Station station;
    private final MapCodec<WorkshopBlock> codec;
    public WorkshopBlock(Station station,Properties p) {
        super(p);this.station=station;codec=simpleCodec(props->new WorkshopBlock(station,props));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return codec;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){return defaultBlockState().setValue(FACING,ctx.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext ctx){return Block.box(0,0,0,16,station==Station.ANVIL?13:16,16);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WorkshopEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,Workshops.ENTITY.get(),WorkshopEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof WorkshopEntity entity)player.openMenu(entity,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState s,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!s.is(next.getBlock())&&level.getBlockEntity(pos) instanceof WorkshopEntity entity)Containers.dropContents(level,pos,entity);
        super.onRemove(s,level,pos,next,moved);
    }
}
