/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.newrenderingpipeline.newrendering;

import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.Pair;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapUVs;
import cientistavuador.newrenderingpipeline.util.raycast.UserMesh;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.primitives.Rectanglei;

/**
 *
 * @author Cien
 */
public class NMap {

    public static final int DEFAULT_LIGHTMAP_MARGIN = 5;
    
    public static String mapObjectPrefix(String mapName, int objectIndex, String objectName) {
        return "map_"+mapName+"_"+objectIndex+"_"+objectName;
    }
    
    private final String name;
    private final N3DObject[] objects;
    
    private final int lightmapMargin;
    private final float lightmapPixelToWorldRatio;
    private final int lightmapSize;
    private final Rectanglei[] lightmapRectangles;
    
    private final UserMesh userMesh;
    
    public NMap(String name, Collection<N3DObject> objects, int lightmapMargin, float lightmapPixelToWorldRatio) {
        this.name = name;
        this.lightmapMargin = lightmapMargin;
        this.lightmapPixelToWorldRatio = lightmapPixelToWorldRatio;
        
        float[] transformedVertices = new float[NMesh.VERTEX_SIZE * 64];
        int transformedVerticesIndex = 0;
        
        class GeometryOffset {
            NGeometry geometry;
            int offset;
            int length;

            GeometryOffset(NGeometry geometry, int offset, int length) {
                this.geometry = geometry;
                this.offset = offset;
                this.length = length;
            }
        }
        
        class ObjectGeometries {
            N3DObject object;
            List<GeometryOffset> offsets;
        }
        
        List<ObjectGeometries> objectsGeometries = new ArrayList<>();
        
        Matrix4f modelMatrix = new Matrix4f();
        
        Matrix4f transformation = new Matrix4f();
        Matrix3f transformationNormal = new Matrix3f();
        
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();
        
        for (N3DObject obj:objects) {
            ObjectGeometries objectGeometries = new ObjectGeometries();
            objectGeometries.object = obj;
            objectGeometries.offsets = new ArrayList<>();
            
            obj.calculateModelMatrix(modelMatrix, null);
            
            N3DModel model = obj.getN3DModel();
            for (int i = 0; i < model.getNumberOfGeometries(); i++) {
                NGeometry geometry = model.getGeometry(i);
                NMesh mesh = geometry.getMesh();
                
                transformation
                        .set(modelMatrix)
                        .mul(geometry.getParent().getToRootSpace())
                        ;
                transformation.normal(transformationNormal);
                
                float[] unindexed = MeshUtils.unindex(mesh.getVertices(), mesh.getIndices(), NMesh.VERTEX_SIZE).getA();
                
                for (int v = 0; v < unindexed.length; v += NMesh.VERTEX_SIZE) {
                    position.set(
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 2]
                    );
                    normal.set(
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 2]
                    );
                    tangent.set(
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 2]
                    );
                    
                    transformation.transformProject(position);
                    transformationNormal.transform(normal);
                    transformationNormal.transform(tangent);
                    
                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 0] = position.x();
                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 1] = position.y();
                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 2] = position.z();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 0] = normal.x();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 1] = normal.y();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 2] = normal.z();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 0] = tangent.x();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 1] = tangent.y();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 2] = tangent.z();
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 0] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 1] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 2] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 3] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0] = 1f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 1] = 0f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 2] = 0f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 3] = 0f;
                }
                
                if ((transformedVertices.length - transformedVerticesIndex) < unindexed.length) {
                    transformedVertices = Arrays.copyOf(transformedVertices, (transformedVertices.length * 2) + unindexed.length);
                }
                
                System.arraycopy(
                        unindexed, 0,
                        transformedVertices, transformedVerticesIndex, unindexed.length
                );
                objectGeometries.offsets.add(new GeometryOffset(geometry, transformedVerticesIndex, unindexed.length));
                transformedVerticesIndex += unindexed.length;
            }
            
            objectsGeometries.add(objectGeometries);
        }
        transformedVertices = Arrays.copyOf(transformedVertices, transformedVerticesIndex);
        
        LightmapUVs.GeneratorOutput output = MeshUtils.generateLightmapUVs(
                transformedVertices, NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ,
                this.lightmapMargin, this.lightmapPixelToWorldRatio, 1f, 1f, 1f
        );
        
        this.lightmapSize = output.getLightmapSize();
        
        LightmapUVs.LightmapperQuad[] quads = output.getQuads();
        this.lightmapRectangles = new Rectanglei[quads.length];
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            LightmapUVs.LightmapperQuad quad = quads[i];
            this.lightmapRectangles[i] = new Rectanglei(
                    quad.getX(), quad.getY(),
                    quad.getX() + quad.getWidth(), quad.getY() + quad.getHeight()
            );
        }
        
        float[] uvs = output.getUVs();
        for (int i = 0; i < transformedVertices.length; i += NMesh.VERTEX_SIZE) {
            transformedVertices[i + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 0] = uvs[((i / NMesh.VERTEX_SIZE) * 2) + 0];
            transformedVertices[i + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 1] = uvs[((i / NMesh.VERTEX_SIZE) * 2) + 1];
        }
        
        List<N3DObject> resultObjects = new ArrayList<>();
        
        List<Pair<N3DObject, NGeometry>> userObjects = new ArrayList<>();
        List<float[]> userVertices = new ArrayList<>();
        List<int[]> userIndices = new ArrayList<>();
        
        int objectCounter = 0;
        for (ObjectGeometries obj:objectsGeometries) {
            List<NGeometry> newGeometries = new ArrayList<>();
            int geometryCounter = 0;
            for (GeometryOffset geo:obj.offsets) {
                float[] unindexedVertices = Arrays.copyOfRange(transformedVertices, geo.offset, geo.offset + geo.length);
                
                Pair<float[], int[]> pair = MeshUtils.generateIndices(unindexedVertices, NMesh.VERTEX_SIZE);
                
                float[] vertices = pair.getA();
                int[] indices = pair.getB();
                
                NMesh mesh = new NMesh(geo.geometry.getMesh().getName(), vertices, indices);
                mesh.generateBVH();
                newGeometries.add(new NGeometry(
                        mapObjectPrefix(this.name, geometryCounter, geo.geometry.getName()),
                        mesh,
                        geo.geometry.getMaterial()
                ));
                geometryCounter++;
            }
            N3DObject object = new N3DObject(
                    mapObjectPrefix(this.name, objectCounter, obj.object.getName()),
                    new N3DModel(
                            mapObjectPrefix(this.name, objectCounter, obj.object.getN3DModel().getName()),
                            new N3DModelNode(
                                    "root", null,
                                    newGeometries.toArray(NGeometry[]::new), null
                            )
                    )
            );
            resultObjects.add(object);
            objectCounter++;
            
            for (NGeometry g:newGeometries) {
                userObjects.add(new Pair<>(object, g));
                userVertices.add(g.getMesh().getVertices());
                userIndices.add(g.getMesh().getIndices());
            }
        }
        
        this.objects = resultObjects.toArray(N3DObject[]::new);
        this.userMesh = UserMesh.create(
                userVertices.toArray(float[][]::new),
                userIndices.toArray(int[][]::new),
                null,
                userObjects.toArray(Object[]::new),
                NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ
        );
    }

    public String getName() {
        return name;
    }
    
    public int getNumberOfObjects() {
        return this.objects.length;
    }
    
    public N3DObject getObject(int index) {
        return this.objects[index];
    }
    
    public int getLightmapMargin() {
        return lightmapMargin;
    }

    public float getLightmapPixelToWorldRatio() {
        return lightmapPixelToWorldRatio;
    }

    public int getLightmapSize() {
        return lightmapSize;
    }
    
    public int getNumberOfLightmapRectangles() {
        return this.lightmapRectangles.length;
    }
    
    public Rectanglei getLightmapRectangle(int index) {
        return this.lightmapRectangles[index];
    }
    
    public UserMesh getUserMesh() {
        return userMesh;
    }
    
}
