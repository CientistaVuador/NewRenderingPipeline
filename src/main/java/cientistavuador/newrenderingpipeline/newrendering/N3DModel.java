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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class N3DModel {

    private final String name;
    private final N3DModelNode rootNode;
    private final NAnimation[] animations;
    private final Map<String, Integer> animationsMap = new HashMap<>();

    private final Vector3f aabbMin = new Vector3f();
    private final Vector3f aabbMax = new Vector3f();
    private final Vector3f aabbCenter = new Vector3f();
    
    private final N3DModelNode[] nodes;
    private final Map<String, Integer> nodesMap = new HashMap<>();
    private final String[] bones;
    private final NMesh[] meshes;
    
    private final Vector3f animatedAabbMin = new Vector3f();
    private final Vector3f animatedAabbMax = new Vector3f();
    private final Vector3f animatedAabbCenter = new Vector3f();
    private boolean animatedAabbGenerated = false;
    
    private final int indicesCount;
    private final int verticesCount;
    
    public N3DModel(String name, N3DModelNode rootNode) {
        this(name, rootNode, null);
    }
    
    public N3DModel(String name, N3DModelNode rootNode, NAnimation[] animations) {
        this.name = name;
        this.rootNode = rootNode;
        
        if (animations == null) {
            animations = new NAnimation[0];
        }
        this.animations = animations;
        for (int i = 0; i < animations.length; i++) {
            this.animationsMap.put(animations[i].getName(), i);
        }
        
        int indicesCounter = 0;
        int verticesCounter = 0;
        
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        
        List<N3DModelNode> nodesList = new ArrayList<>();
        Set<String> boneList = new HashSet<>();
        Set<NMesh> meshList = new HashSet<>();
        
        Queue<N3DModelNode> current = new ArrayDeque<>();
        Queue<N3DModelNode> next = new ArrayDeque<>();
        
        current.add(rootNode);
        
        do {
            Vector3f transformed = new Vector3f();

            N3DModelNode currentNode;
            while ((currentNode = current.poll()) != null) {
                currentNode.recalculateMatrices();
                nodesList.add(currentNode);
                
                Matrix4fc totalTransformation = currentNode.getToRootSpace();
                
                for (int geometryIndex = 0; geometryIndex < currentNode.getNumberOfGeometries(); geometryIndex++) {
                    NGeometry g = currentNode.getGeometry(geometryIndex);
                    
                    NMesh mesh = g.getMesh();
                    
                    if (!meshList.contains(mesh)) {
                        meshList.add(mesh);
                    }
                    
                    int bonesLength = mesh.getAmountOfBones();
                    for (int i = 0; i < bonesLength; i++) {
                        String boneName = mesh.getBone(i);
                        if (!boneList.contains(boneName)) {
                            boneList.add(boneName);
                        }
                    }
                    
                    float[] vertices = mesh.getVertices();
                    int numVertices = vertices.length / NMesh.VERTEX_SIZE;

                    for (int i = 0; i < numVertices; i++) {
                        transformed.set(
                                vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 0],
                                vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 1],
                                vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 2]
                        );
                        
                        totalTransformation.transformProject(transformed);
                        
                        minX = Math.min(minX, transformed.x());
                        minY = Math.min(minY, transformed.y());
                        minZ = Math.min(minZ, transformed.z());

                        maxX = Math.max(maxX, transformed.x());
                        maxY = Math.max(maxY, transformed.y());
                        maxZ = Math.max(maxZ, transformed.z());
                    }
                    
                    verticesCounter += mesh.getVertices().length / NMesh.VERTEX_SIZE;
                    indicesCounter += mesh.getIndices().length;
                }
                
                for (int i = 0; i < currentNode.getNumberOfChildren(); i++) {
                    next.add(currentNode.getChild(i));
                }
            }
            
            Queue<N3DModelNode> a = current;
            Queue<N3DModelNode> b = next;
            current = b;
            next = a;
        } while (!current.isEmpty());
        
        for (NAnimation animation:this.animations) {
            for (int i = 0; i < animation.getNumberOfBoneAnimations(); i++) {
                String boneName = animation.getBoneAnimation(i).getBoneName();
                if (!boneList.contains(boneName)) {
                    boneList.add(boneName);
                }
            }
        }
        
        this.aabbMin.set(minX, minY, minZ);
        this.aabbMax.set(maxX, maxY, maxZ);
        this.aabbCenter.set(
                (minX * 0.5f) + (maxX * 0.5f),
                (minY * 0.5f) + (maxY * 0.5f),
                (minZ * 0.5f) + (maxZ * 0.5f)
        );
        
        this.animatedAabbMin.set(this.aabbMin);
        this.animatedAabbMax.set(this.aabbMax);
        this.animatedAabbCenter.set(this.aabbCenter);
        
        this.nodes = nodesList.toArray(N3DModelNode[]::new);
        for (int i = 0; i < this.nodes.length; i++) {
            this.nodesMap.put(this.nodes[i].getName(), i);
        }
        this.bones = boneList.toArray(String[]::new);
        this.meshes = meshList.toArray(NMesh[]::new);
        
        this.verticesCount = verticesCounter;
        this.indicesCount = indicesCounter;
    }

    public String getName() {
        return name;
    }

    public N3DModelNode getRootNode() {
        return rootNode;
    }
    
    public int getNumberOfAnimations() {
        return this.animations.length;
    }
    
    public NAnimation getAnimation(int index) {
        return this.animations[index];
    }
    
    public NAnimation getAnimation(String name) {
        Integer index = this.animationsMap.get(name);
        if (index == null) {
            return null;
        }
        return getAnimation(index);
    }

    public Vector3fc getAabbMin() {
        return aabbMin;
    }

    public Vector3fc getAabbMax() {
        return aabbMax;
    }

    public Vector3fc getAabbCenter() {
        return aabbCenter;
    }    

    public int getNumberOfNodes() {
        return this.nodes.length;
    }
    
    public N3DModelNode getNode(int index) {
        return this.nodes[index];
    }
    
    public N3DModelNode getNode(String name) {
        Integer index = this.nodesMap.get(name);
        if (index == null) {
            return null;
        }
        return getNode(index);
    }
    
    public int getNumberOfBones() {
        return this.bones.length;
    }
    
    public String getBone(int index) {
        return this.bones[index];
    }
    
    public int getNumberOfMeshes() {
        return this.meshes.length;
    }
    
    public NMesh getMesh(int index) {
        return this.meshes[index];
    }

    public void generateAnimatedAabb() {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        
        for (N3DModelNode node:this.nodes) {
            for (int i = 0; i < node.getNumberOfGeometries(); i++) {
                NGeometry geometry = node.getGeometry(i);
                geometry.generateAnimatedAabb(this);
                
                Vector3fc geoMin = geometry.getAnimatedAabbMin();
                Vector3fc geoMax = geometry.getAnimatedAabbMax();
                
                minX = Math.min(minX, geoMin.x());
                minY = Math.min(minY, geoMin.y());
                minZ = Math.min(minZ, geoMin.z());
                
                maxX = Math.max(maxX, geoMax.x());
                maxY = Math.max(maxY, geoMax.y());
                maxZ = Math.max(maxZ, geoMax.z());
            }
        }
        
        this.animatedAabbMin.set(minX, minY, minZ);
        this.animatedAabbMax.set(maxX, maxY, maxZ);
        this.animatedAabbCenter.set(
                (minX * 0.5f) + (maxX * 0.5f),
                (minY * 0.5f) + (maxY * 0.5f),
                (minZ * 0.5f) + (maxZ * 0.5f)
        );
        
        this.animatedAabbGenerated = true;
    }
    
    public Vector3fc getAnimatedAabbMin() {
        return animatedAabbMin;
    }

    public Vector3fc getAnimatedAabbMax() {
        return animatedAabbMax;
    }

    public Vector3fc getAnimatedAabbCenter() {
        return animatedAabbCenter;
    }

    public boolean isAnimatedAabbGenerated() {
        return animatedAabbGenerated;
    }
    
    public int getIndicesCount() {
        return indicesCount;
    }

    public int getVerticesCount() {
        return verticesCount;
    }
    
}
