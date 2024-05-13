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

import cientistavuador.newrenderingpipeline.util.MeshStore;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class N3DModelStore {
    
    public static final int VERSION = 1;
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final String INDENT = "    ";

    private static class StoreAnimation {

        public NAnimation object;

        public String file;

        public String toXML() {
            return "<animation file=\"" + this.file + "\"/>";
        }
    }

    private static class StoreTextures {

        public NTextures object;

        public String id;
        public String file;

        public String toXML() {
            return "<textures id=\"" + this.id + "\" file=\"" + this.file + "\"/>";
        }
    }

    private static class StoreBone {

        public int index;
        public String name;

        public String toXML() {
            return "<bone index=\"" + this.index + "\" name=\"" + this.name + "\"/>";
        }
    }

    private static String stringToXML(String s, String name) {
        return name + "=\"" + s + "\"";
    }

    private static String encodedStringToXML(String s, String name) {
        return stringToXML(URLEncoder.encode(s, StandardCharsets.UTF_8), name);
    }

    private static String floatToXML(float f, String name) {
        return stringToXML(Float.toString(f), name);
    }
    
    private static String posToXML(Vector3fc pos, String prefix) {
        return floatToXML(pos.x(), prefix + "X") + " " + floatToXML(pos.y(), prefix + "Y") + " " + floatToXML(pos.z(), prefix + "Z");
    }

    private static class StoreMesh {

        public NMesh object;

        public String id;

        public String name;
        public String file;
        public String sha256;

        public Vector3f min;
        public Vector3f max;

        public List<Integer> bones;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();

            b.append(indent).append("<mesh").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(encodedStringToXML(this.name, "name")).append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.file, "file")).append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.sha256, "sha256")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(posToXML(this.min, "min")).append('\n');
            b.append(indent).append(INDENT).append(posToXML(this.max, "max")).append('\n');
            if (!this.bones.isEmpty()) {
                b.append(indent).append(INDENT).append('\n');
                b.append(indent).append(INDENT).append("bones=\"");
                for (int i = 0; i < this.bones.size(); i++) {
                    b.append(this.bones.get(i));
                    if (i != (this.bones.size() - 1)) {
                        b.append(",");
                    }
                }
                b.append("\"").append('\n');
            }
            b.append(indent).append("/>");

            return b.toString();
        }
    }

    private static String colorRGBAToXML(Vector4fc color, String prefix) {
        return floatToXML(color.x(), prefix + "R") + " " + floatToXML(color.y(), prefix + "G") + " " + floatToXML(color.z(), prefix + "B") + " " + floatToXML(color.w(), prefix + "A");
    }

    private static String colorRGBToXML(Vector3fc color, String prefix) {
        return floatToXML(color.x(), prefix + "R") + " " + floatToXML(color.y(), prefix + "G") + " " + floatToXML(color.z(), prefix + "B");
    }

    private static class StoreMaterial {

        public NMaterial object;

        public String id;

        public String name;
        public String texturesId;

        public float minExponent;
        public float maxExponent;

        public float parallaxHeightCoefficient;
        public float parallaxMinLayers;
        public float parallaxMaxLayers;

        public Vector4f diffuse;
        public Vector3f specular;
        public Vector3f emissive;
        public Vector3f reflection;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();

            b.append(indent).append("<material").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(encodedStringToXML(this.name, "name")).append('\n');
            if (this.texturesId != null) {
                b.append(indent).append(INDENT).append(stringToXML(this.texturesId, "texturesId")).append('\n');
            }
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(floatToXML(this.minExponent, "minExponent")).append('\n');
            b.append(indent).append(INDENT).append(floatToXML(this.maxExponent, "maxExponent")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(floatToXML(this.parallaxHeightCoefficient, "parallaxHeightCoefficient")).append('\n');
            b.append(indent).append(INDENT).append(floatToXML(this.parallaxMinLayers, "parallaxMinLayers")).append('\n');
            b.append(indent).append(INDENT).append(floatToXML(this.parallaxMaxLayers, "parallaxMaxLayers")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(colorRGBAToXML(this.diffuse, "diffuse")).append('\n');
            b.append(indent).append(INDENT).append(colorRGBToXML(this.specular, "specular")).append('\n');
            b.append(indent).append(INDENT).append(colorRGBToXML(this.emissive, "emissive")).append('\n');
            b.append(indent).append(INDENT).append(colorRGBToXML(this.reflection, "reflection")).append('\n');
            b.append(indent).append("/>");

            return b.toString();
        }
    }

    private static class StoreGeometry {

        public NGeometry object;

        public String id;

        public String name;

        public String meshId;
        public String materialId;

        public Vector3f animatedMin;
        public Vector3f animatedMax;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();

            b.append(indent).append("<geometry").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(encodedStringToXML(this.name, "name")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.meshId, "meshId")).append('\n');
            if (this.materialId != null) {
                b.append(indent).append(INDENT).append(stringToXML(this.materialId, "materialId")).append('\n');
            }
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(posToXML(this.animatedMin, "animatedMin")).append('\n');
            b.append(indent).append(INDENT).append(posToXML(this.animatedMax, "animatedMax")).append('\n');
            b.append(indent).append("/>");

            return b.toString();
        }
    }

    private static class StoreNodeGeometry {

        public StoreGeometry object;

        public String id;

        public String toXML() {
            return "<geometry id=\"" + this.id + "\"/>";
        }
    }

    private static String matrixToXML(Matrix4fc matrix, String indent) {
        StringBuilder b = new StringBuilder();
        
        List<String> m = new ArrayList<>();
        
        m.add(floatToXML(matrix.m00(), "m00"));
        m.add(floatToXML(matrix.m10(), "m10"));
        m.add(floatToXML(matrix.m20(), "m20"));
        m.add(floatToXML(matrix.m30(), "m30"));
        
        m.add(floatToXML(matrix.m01(), "m01"));
        m.add(floatToXML(matrix.m11(), "m11"));
        m.add(floatToXML(matrix.m21(), "m21"));
        m.add(floatToXML(matrix.m31(), "m31"));
        
        m.add(floatToXML(matrix.m01(), "m02"));
        m.add(floatToXML(matrix.m11(), "m12"));
        m.add(floatToXML(matrix.m21(), "m22"));
        m.add(floatToXML(matrix.m31(), "m32"));
        
        m.add(floatToXML(matrix.m01(), "m03"));
        m.add(floatToXML(matrix.m11(), "m13"));
        m.add(floatToXML(matrix.m21(), "m23"));
        m.add(floatToXML(matrix.m31(), "m33"));
        
        int largestLength = 0;
        for (String s:m) {
            int len = s.length();
            if (len > largestLength) {
                largestLength = len;
            }
        }
        
        for (int i = 0; i < m.size(); i++) {
            String at = m.get(i);
            m.set(i, at+" ".repeat(largestLength - at.length()));
        }
        
        b.append(indent).append(m.get(0)).append(' ').append(m.get(1)).append(' ').append(m.get(2)).append(' ').append(m.get(3)).append('\n');
        b.append(indent).append(m.get(4)).append(' ').append(m.get(5)).append(' ').append(m.get(6)).append(' ').append(m.get(7)).append('\n');
        b.append(indent).append(m.get(8)).append(' ').append(m.get(9)).append(' ').append(m.get(10)).append(' ').append(m.get(11)).append('\n');
        b.append(indent).append(m.get(12)).append(' ').append(m.get(13)).append(' ').append(m.get(14)).append(' ').append(m.get(15));
        
        return b.toString();
    }
    
    private static class StoreMatrix {
        public String id;
        public Matrix4f matrix;
        
        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();
            
            b.append(indent).append("<matrix").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(matrixToXML(this.matrix, indent+INDENT)).append('\n');
            b.append(indent).append("/>");
            
            return b.toString();
        }
    }

    private static class StoreNode {

        public N3DModelNode object;

        public StoreNode parent;

        public String name;
        public String matrixId;
        
        public List<StoreNodeGeometry> geometries;
        public List<StoreNode> children;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();
            
            b.append(indent).append("<node ").append(encodedStringToXML(this.name, "name"));
            if (this.matrixId != null) {
                b.append(' ').append(stringToXML(this.matrixId, "matrixId"));
            }
            b.append(">").append('\n');
            for (int i = 0; i < this.geometries.size(); i++) {
                b.append(indent).append(INDENT).append(this.geometries.get(i).toXML()).append('\n');
            }
            for (int i = 0; i < this.children.size(); i++) {
                b.append(this.children.get(i).toXML(indent + INDENT)).append('\n');
            }
            b.append(indent).append("</node>");
            
            return b.toString();
        }
    }

    private static String commentaryToXML(String commentary) {
        return "<!-- " + commentary + " -->";
    }

    private static class StoreModel {

        public N3DModel object;

        public String name;

        public Vector3f min;
        public Vector3f max;

        public Vector3f animatedMin;
        public Vector3f animatedMax;

        public List<StoreAnimation> animations;

        public Map<String, StoreTextures> textures;
        public Map<NTextures, StoreTextures> texturesObjectMap;

        public Map<Integer, StoreBone> bones;
        public Map<String, StoreBone> bonesObjectMap;

        public Map<String, StoreMesh> meshes;
        public Map<NMesh, StoreMesh> meshesObjectMap;

        public Map<String, StoreMaterial> materials;
        public Map<NMaterial, StoreMaterial> materialsObjectMap;

        public Map<String, StoreGeometry> geometries;
        public Map<NGeometry, StoreGeometry> geometriesObjectMap;
        
        public Map<String, StoreMatrix> matrices;
        public Map<Matrix4fc, StoreMatrix> matricesObjectMap;
        
        public StoreNode rootNode;

        public String toXML() {
            StringBuilder b = new StringBuilder();
            
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append('\n');
            b.append("<model").append('\n');
            b.append(INDENT).append(stringToXML(Integer.toString(VERSION), "version")).append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append(encodedStringToXML(this.name, "name")).append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append(posToXML(this.min, "min")).append('\n');
            b.append(INDENT).append(posToXML(this.max, "max")).append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append(posToXML(this.animatedMin, "animatedMin")).append('\n');
            b.append(INDENT).append(posToXML(this.animatedMax, "animatedMax")).append('\n');
            b.append(">").append('\n');
            if (!this.animations.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Animations")).append('\n');
                for (StoreAnimation animation : this.animations) {
                    b.append(INDENT).append(animation.toXML()).append('\n');
                }
            }
            if (!this.texturesObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Textures")).append('\n');
                for (StoreTextures tex : this.texturesObjectMap.values()) {
                    b.append(INDENT).append(tex.toXML()).append('\n');
                }
            }
            if (!this.bonesObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Bones")).append('\n');
                for (StoreBone bone : this.bonesObjectMap.values()) {
                    b.append(INDENT).append(bone.toXML()).append('\n');
                }
            }
            if (!this.meshesObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Meshes")).append('\n');
                for (StoreMesh mesh : this.meshesObjectMap.values()) {
                    b.append(mesh.toXML(INDENT)).append('\n');
                }
            }
            if (!this.materialsObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Materials")).append('\n');
                for (StoreMaterial material : this.materialsObjectMap.values()) {
                    b.append(material.toXML(INDENT)).append('\n');
                }
            }
            if (!this.geometriesObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Geometries")).append('\n');
                for (StoreGeometry geometry : this.geometriesObjectMap.values()) {
                    b.append(geometry.toXML(INDENT)).append('\n');
                }
            }
            if (!this.matricesObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Matrices")).append('\n');
                for (StoreMatrix matrix : this.matricesObjectMap.values()) {
                    b.append(matrix.toXML(INDENT)).append('\n');
                }
            }
            b.append(INDENT).append(commentaryToXML("Scene Graph")).append('\n');
            b.append(this.rootNode.toXML(INDENT)).append('\n');
            b.append("</model>");

            return b.toString();
        }
    }

    private static StoreNode buildSceneGraphNode(StoreModel model, N3DModelNode currentNode) {
        StoreNode node = new StoreNode();
        node.name = currentNode.getName();
        StoreMatrix matrix = model.matricesObjectMap.get(currentNode.getTransformation());
        if (matrix != null) {
            node.matrixId = matrix.id;
        }
        
        node.geometries = new ArrayList<>();
        for (int i = 0; i < currentNode.getNumberOfGeometries(); i++) {
            NGeometry geometry = currentNode.getGeometry(i);

            StoreNodeGeometry storeGeometry = new StoreNodeGeometry();
            storeGeometry.id = model.geometriesObjectMap.get(geometry).id;
            node.geometries.add(storeGeometry);
        }

        node.children = new ArrayList<>();
        for (int i = 0; i < currentNode.getNumberOfChildren(); i++) {
            N3DModelNode child = currentNode.getChild(i);

            node.children.add(buildSceneGraphNode(model, child));
        }

        return node;
    }

    public static void writeModel(N3DModel model, OutputStream output) throws IOException {
        ZipOutputStream zipOut = new ZipOutputStream(output, StandardCharsets.UTF_8);

        AtomicInteger fileCounter = new AtomicInteger();
        
        AtomicInteger texCounter = new AtomicInteger();
        AtomicInteger mshCounter = new AtomicInteger();
        AtomicInteger matCounter = new AtomicInteger();
        AtomicInteger geoCounter = new AtomicInteger();
        AtomicInteger mtxCounter = new AtomicInteger();
        
        StoreModel store = new StoreModel();

        store.name = model.getName();

        store.min = new Vector3f(model.getAabbMin());
        store.max = new Vector3f(model.getAabbMax());

        model.generateAnimatedAabb();
        store.animatedMin = new Vector3f(model.getAnimatedAabbMin());
        store.animatedMax = new Vector3f(model.getAnimatedAabbMax());

        store.animations = new ArrayList<>();
        for (int i = 0; i < model.getNumberOfAnimations(); i++) {
            NAnimation animation = model.getAnimation(i);

            StoreAnimation storeAnimation = new StoreAnimation();
            
            storeAnimation.file 
                    = "f" + fileCounter.getAndIncrement()
                    + "_" + URLEncoder.encode(animation.getName(), StandardCharsets.UTF_8)
                    + ".animation";
            
            store.animations.add(storeAnimation);
            
            zipOut.putNextEntry(new ZipEntry(storeAnimation.file));
            NAnimationStore.writeAnimation(animation, zipOut);
            zipOut.closeEntry();
        }

        store.texturesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfTextures(); i++) {
            NTextures textures = model.getTextures(i);

            StoreTextures storeTextures = new StoreTextures();

            storeTextures.id = "tex_" + texCounter.getAndIncrement();
            storeTextures.file 
                    = "f" + fileCounter.getAndIncrement()
                    + "_" + URLEncoder.encode(textures.getName(), StandardCharsets.UTF_8)
                    + ".textures";

            store.texturesObjectMap.put(textures, storeTextures);

            zipOut.putNextEntry(new ZipEntry(storeTextures.file));
            NTexturesStore.writeTextures(textures, zipOut);
            zipOut.closeEntry();
        }

        store.bonesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfBones(); i++) {
            String bone = model.getBone(i);

            StoreBone storeBone = new StoreBone();
            storeBone.index = i;
            storeBone.name = bone;

            store.bonesObjectMap.put(bone, storeBone);
        }

        store.meshesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfMeshes(); i++) {
            NMesh mesh = model.getMesh(i);

            StoreMesh storeMesh = new StoreMesh();
            storeMesh.id = "msh_" + mshCounter.getAndIncrement();
            storeMesh.name = mesh.getName();
            storeMesh.file
                    = "f" + fileCounter.getAndIncrement()
                    + "_" + URLEncoder.encode(mesh.getName(), StandardCharsets.UTF_8)
                    + ".mesh";
            storeMesh.sha256 = mesh.getSha256();

            storeMesh.min = new Vector3f(mesh.getAabbMin());
            storeMesh.max = new Vector3f(mesh.getAabbMax());

            storeMesh.bones = new ArrayList<>();
            for (int j = 0; j < mesh.getAmountOfBones(); j++) {
                String bone = mesh.getBone(j);
                StoreBone storeBone = store.bonesObjectMap.get(bone);
                if (storeBone != null) {
                    storeMesh.bones.add(storeBone.index);
                }
            }

            store.meshesObjectMap.put(mesh, storeMesh);

            zipOut.putNextEntry(new ZipEntry(storeMesh.file));
            MeshStore.encode(mesh.getVertices(), NMesh.VERTEX_SIZE, mesh.getIndices(), zipOut);
            zipOut.closeEntry();
        }

        store.materialsObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfMaterials(); i++) {
            NMaterial material = model.getMaterial(i);

            StoreMaterial storeMaterial = new StoreMaterial();
            storeMaterial.id = "mat_" + matCounter.getAndIncrement();
            storeMaterial.name = material.getName();

            StoreTextures textures = store.texturesObjectMap.get(material.getTextures());
            if (textures != null) {
                storeMaterial.texturesId = textures.id;
            }

            storeMaterial.minExponent = material.getMinExponent();
            storeMaterial.maxExponent = material.getMaxExponent();

            storeMaterial.parallaxHeightCoefficient = material.getParallaxHeightCoefficient();
            storeMaterial.parallaxMinLayers = material.getParallaxMinLayers();
            storeMaterial.parallaxMaxLayers = material.getParallaxMaxLayers();

            storeMaterial.diffuse = new Vector4f(material.getDiffuseColor());
            storeMaterial.specular = new Vector3f(material.getSpecularColor());
            storeMaterial.emissive = new Vector3f(material.getEmissiveColor());
            storeMaterial.reflection = new Vector3f(material.getReflectionColor());

            store.materialsObjectMap.put(material, storeMaterial);
        }

        store.geometriesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfGeometries(); i++) {
            NGeometry geometry = model.getGeometry(i);

            StoreGeometry storeGeometry = new StoreGeometry();
            storeGeometry.id = "geo_" + geoCounter.getAndIncrement();
            storeGeometry.name = geometry.getName();
            storeGeometry.meshId = store.meshesObjectMap.get(geometry.getMesh()).id;

            StoreMaterial material = store.materialsObjectMap.get(geometry.getMaterial());
            if (material != null) {
                storeGeometry.materialId = material.id;
            }

            storeGeometry.animatedMin = new Vector3f(geometry.getAnimatedAabbMin());
            storeGeometry.animatedMax = new Vector3f(geometry.getAnimatedAabbMax());

            store.geometriesObjectMap.put(geometry, storeGeometry);
        }
        
        store.matricesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfNodes(); i++) {
            N3DModelNode node = model.getNode(i);
            
            Matrix4fc matrix = node.getTransformation();
            if (matrix.equals(IDENTITY)) {
                continue;
            }
            
            if (!store.matricesObjectMap.containsKey(matrix)) {
                StoreMatrix storeMatrix = new StoreMatrix();
                storeMatrix.id = "mtx_"+mtxCounter.getAndIncrement();
                storeMatrix.matrix = new Matrix4f(matrix);
                store.matricesObjectMap.put(storeMatrix.matrix, storeMatrix);
            }
        }
        
        store.rootNode = buildSceneGraphNode(store, model.getRootNode());
        
        zipOut.putNextEntry(new ZipEntry("model.xml"));
        String modelXml = store.toXML();
        zipOut.write(modelXml.getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();

        zipOut.finish();
    }

    private N3DModelStore() {

    }

}
