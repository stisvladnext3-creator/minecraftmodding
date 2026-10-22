package ru.epocharmory.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.epocharmory.*;

public final class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private EditBox search;
    private int page;
    private List<Integer> filtered=List.of();
    private Button work;
    public WorkshopScreen(WorkshopMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=338;imageHeight=242;inventoryLabelX=166;inventoryLabelY=148;}
    private void send(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        super.init();search=addRenderableWidget(new EditBox(font,leftPos+8,topPos+24,136,17,Component.translatable("screen.epocharmory.search")));
        search.setHint(Component.translatable("screen.epocharmory.search"));search.setResponder(s->{page=0;filter();});filter();
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);}).bounds(leftPos+8,topPos+202,36,18).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(filtered.size()-1)/7),page+1);}).bounds(leftPos+108,topPos+202,36,18).build());
        work=addRenderableWidget(Button.builder(Component.translatable("screen.epocharmory.work"),b->send(0)).bounds(leftPos+166,topPos+126,78,18).build());work.active=menu.station.manual;
        addRenderableWidget(Button.builder(Component.translatable("screen.epocharmory.fill"),b->send(1)).bounds(leftPos+249,topPos+126,78,18).build());
        if(menu.station.heated){
            addRenderableWidget(Button.builder(Component.literal("−"),b->send(2)).bounds(leftPos+289,topPos+96,18,16).build());
            addRenderableWidget(Button.builder(Component.literal("+"),b->send(3)).bounds(leftPos+309,topPos+96,18,16).build());
        }
    }
    private void filter(){
        if(search==null)return;String q=search.getValue().toLowerCase(Locale.ROOT);
        var list=new ArrayList<Integer>();for(int i=0;i<menu.recipes.size();i++){
            var holder=menu.recipes.get(i);var r=holder.value();
            if(q.isBlank()||r.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)||holder.id().toString().contains(q)||r.inputs().stream().anyMatch(p->p.preview().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)))list.add(i);
        }filtered=list;
    }
    private void ghost(GuiGraphics g,ItemStack stack,int x,int y){
        if(stack.isEmpty())return;g.renderFakeItem(stack,leftPos+x,topPos+y);g.renderItemDecorations(font,stack,leftPos+x,topPos+y);
        g.fill(leftPos+x,topPos+y,leftPos+x+16,topPos+y+16,0x660F1016);
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+340,y+244,0xFF8C704A);g.fill(x,y,x+338,y+242,0xFF191C23);
        g.fill(x,y,x+338,y+3,0xFFD5B57B);g.fill(x+152,y+16,x+153,y+234,0xFF4D443A);
        for(var slot:menu.slots){int sx=x+slot.x,sy=y+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xFF74634D);g.fill(sx,sy,sx+16,sy+16,0xFF30343C);}
        for(int row=0;row<7;row++){
            int index=page*7+row;if(index>=filtered.size())break;
            int actual=filtered.get(index),ry=y+46+row*22;var r=menu.recipes.get(actual).value();
            g.fill(x+7,ry-1,x+145,ry+19,actual==menu.data.get(0)?0xFF62513A:0xFF292E37);
            g.renderFakeItem(r.result(),x+10,ry+1);
            g.drawString(font,font.plainSubstrByWidth(r.result().getHoverName().getString(),111),x+30,ry+5,0xFFE9E0CD,false);
        }
        g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(filtered.size()+6)/7),x+76,y+207,0xFFB9B1A4);
        var r=menu.selected();if(r==null)return;
        for(int i=0;i<r.inputs().size();i++)if(!menu.getSlot(i).hasItem())ghost(g,r.inputs().get(i).preview(),menu.getSlot(i).x,menu.getSlot(i).y);
        if(!menu.getSlot(6).hasItem()&&!r.tool().isEmpty())ghost(g,Content.stack(r.tool()),244,50);
        if(!menu.getSlot(8).hasItem())ghost(g,r.result(),310,52);
        if(!menu.getSlot(9).hasItem())ghost(g,r.byproduct(),310,78);
        g.fill(x+271,y+58,x+300,y+64,0xFF3B404A);
        g.fill(x+271,y+58,x+271+Math.min(29,29*menu.data.get(1)/Math.max(1,r.time())),y+64,0xFFDCB875);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),320),8,9,0xFFF1D4A0,false);
        g.drawString(font,playerInventoryTitle,166,148,0xFFB9B1A4,false);
        var r=menu.selected();if(r==null)return;
        g.drawString(font,font.plainSubstrByWidth(r.result().getHoverName().getString(),162),166,25,0xFFE5DDCD,false);
        g.drawString(font,Component.translatable("screen.epocharmory.inputs"),166,39,0xFF9EA6B4,false);
        g.drawString(font,Component.translatable("screen.epocharmory.tool"),241,39,0xFF9EA6B4,false);
        g.drawString(font,Component.translatable("screen.epocharmory.result"),302,39,0xFF9EA6B4,false);
        if(menu.station.heated)g.drawString(font,menu.data.get(2)+" / "+menu.data.get(3)+" °C",166,99,0xFFDDAD68,false);
        else g.drawString(font,Component.translatable("screen.epocharmory.strokes",(r.time()+19)/20),166,99,0xFF9EA6B4,false);
        g.drawString(font,font.plainSubstrByWidth(Component.translatable("screen.epocharmory.status."+menu.data.get(5)).getString(),162),166,114,menu.data.get(5)==0?0xFFACCD9B:0xFFD0A889,false);
        g.drawString(font,Component.translatable("screen.epocharmory.recipe_count",filtered.size()),8,230,0xFF969DAA,false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&mx>=leftPos+7&&mx<leftPos+145&&my>=topPos+46&&my<topPos+200){
            int index=page*7+((int)my-topPos-46)/22;if(index<filtered.size()){send(1000+filtered.get(index));return true;}
        }return super.mouseClicked(mx,my,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        super.render(g,mx,my,delta);renderTooltip(g,mx,my);
        var r=menu.selected();if(r!=null)for(int i=0;i<r.inputs().size();i++){
            var slot=menu.getSlot(i);if(!slot.hasItem()&&isHovering(slot.x,slot.y,16,16,mx,my))g.renderTooltip(font,r.inputs().get(i).preview(),mx,my);
        }
        if(mx>=leftPos+7&&mx<leftPos+145&&my>=topPos+46&&my<topPos+200){
            int index=page*7+(my-topPos-46)/22;if(index<filtered.size()){
                var recipe=menu.recipes.get(filtered.get(index)).value();var lines=new ArrayList<Component>();
                lines.add(recipe.result().getHoverName());
                for(var part:recipe.inputs())lines.add(Component.literal(part.count()+" × ").append(part.preview().getHoverName()));
                if(!recipe.tool().isEmpty())lines.add(Component.translatable("screen.epocharmory.required_tool",Content.stack(recipe.tool()).getHoverName()));
                if(recipe.minHeat()>0)lines.add(Component.literal(recipe.minHeat()+"–"+recipe.maxHeat()+" °C"));
                g.renderComponentTooltip(font,lines,mx,my);
            }
        }
    }
}
