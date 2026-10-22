package ru.epocharmory.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import ru.epocharmory.*;

/** Pixel-textured cuboid armor meshes. Each slot owns only its own geometry.
 * NeoForge copies the wearer's poses and visibility onto these seven bone roots.
 * The normal ModelPart path retains baby transforms, packed light and glint.
 */
public final class HistoricalArmorModel extends HumanoidModel<LivingEntity> {
    private static final Map<ArmorStyle,JsonObject> GEOMETRY=new EnumMap<>(ArmorStyle.class);
    private static final String[] BONES={"head","hat","body","right_arm","left_arm","right_leg","left_leg"};
    public HistoricalArmorModel(ArmorStyle style,EquipmentSlot slot){super(root(style,slot));}
    public static void clearGeometry(){GEOMETRY.clear();}
    private static ModelPart root(ArmorStyle style,EquipmentSlot slot){
        JsonObject data=GEOMETRY.computeIfAbsent(style,s->{
            var id=EpochArmory.id("armor_geometry/"+s.key+".json");
            try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(id)){
                return JsonParser.parseReader(reader).getAsJsonObject();
            }catch(IOException ex){throw new IllegalStateException("Missing armor geometry: "+id,ex);}
        });
        var parts=data.getAsJsonObject("pieces").getAsJsonObject(slot.getName());
        float size=data.get("texture_size").getAsFloat();
        Map<String,ModelPart> bones=new HashMap<>();
        for(String name:BONES){
            List<ModelPart.Cube> cubes=new ArrayList<>();
            if(parts.has(name))cubes.add(new MeshCube(parts.getAsJsonArray(name),size));
            ModelPart bone=new ModelPart(cubes,Map.of());
            switch(name){
                case "right_arm" -> bone.setPos(-5,2,0);
                case "left_arm" -> bone.setPos(5,2,0);
                case "right_leg" -> bone.setPos(-1.9f,12,0);
                case "left_leg" -> bone.setPos(1.9f,12,0);
            }
            bones.put(name,bone);
        }
        return new ModelPart(List.of(),bones);
    }
    private record Face(Vector3f[] positions,float[] uv,Vector3f normal){}
    private static final class MeshCube extends ModelPart.Cube {
        private final Face[] faces;
        MeshCube(JsonArray data,float size){
            super(0,0,0,0,0,0,0,0,0,0,0,false,size,size,Set.of());
            faces=new Face[data.size()];
            for(int i=0;i<faces.length;i++){
                JsonObject face=data.get(i).getAsJsonObject();
                JsonArray points=face.getAsJsonArray("v"),rect=face.getAsJsonArray("uv");
                Vector3f[] positions=new Vector3f[4];
                for(int j=0;j<4;j++){
                    JsonArray p=points.get(j).getAsJsonArray();
                    positions[j]=new Vector3f(p.get(0).getAsFloat()/16,p.get(1).getAsFloat()/16,p.get(2).getAsFloat()/16);
                }
                Vector3f normal=new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0])).normalize();
                faces[i]=new Face(positions,new float[]{rect.get(0).getAsFloat()/size,rect.get(1).getAsFloat()/size,rect.get(2).getAsFloat()/size,rect.get(3).getAsFloat()/size},normal);
            }
        }
        @Override public void compile(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
            Vector3f normal=new Vector3f(),position=new Vector3f();
            for(Face face:faces){
                pose.transformNormal(face.normal,normal);
                for(int i=0;i<4;i++){
                    pose.pose().transformPosition(face.positions[i],position);
                    float u=(i==0||i==3)?face.uv[0]:face.uv[2];
                    float v=i<2?face.uv[1]:face.uv[3];
                    out.addVertex(position.x,position.y,position.z,color,u,v,overlay,light,normal.x,normal.y,normal.z);
                }
            }
        }
    }
}
