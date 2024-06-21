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
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import cientistavuador.newrenderingpipeline.util.raycast.BVHStore;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class N3DModelStore {

    public static final String MAGIC_FILE_IDENTIFIER = "32bd5c10-240f-457f-9c0d-eb44f590e1b1";

    public static final int VERSION = 1;
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final String INDENT = "    ";

    //utility functions
    private static String stringToXML(String s, String name) {
        return name + "=\"" + s + "\"";
    }

    private static String encodedStringToXML(String s, String name) {
        return stringToXML(URLEncoder.encode(s, StandardCharsets.UTF_8), name);
    }

    private static String floatToXML(float f, String name) {
        return stringToXML(Float.toString(f), name);
    }

    private static String vec3ToXML(Vector3fc pos, String prefix) {
        return floatToXML(pos.x(), prefix + "X") + " " + floatToXML(pos.y(), prefix + "Y") + " " + floatToXML(pos.z(), prefix + "Z");
    }

    private static String colorRGBAToXML(Vector4fc color, String prefix) {
        return floatToXML(color.x(), prefix + "R") + " " + floatToXML(color.y(), prefix + "G") + " " + floatToXML(color.z(), prefix + "B") + " " + floatToXML(color.w(), prefix + "A");
    }

    private static String colorRGBToXML(Vector3fc color, String prefix) {
        return floatToXML(color.x(), prefix + "R") + " " + floatToXML(color.y(), prefix + "G") + " " + floatToXML(color.z(), prefix + "B");
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

        m.add(floatToXML(matrix.m02(), "m02"));
        m.add(floatToXML(matrix.m12(), "m12"));
        m.add(floatToXML(matrix.m22(), "m22"));
        m.add(floatToXML(matrix.m32(), "m32"));

        m.add(floatToXML(matrix.m03(), "m03"));
        m.add(floatToXML(matrix.m13(), "m13"));
        m.add(floatToXML(matrix.m23(), "m23"));
        m.add(floatToXML(matrix.m33(), "m33"));

        int largestLength = 0;
        for (String s : m) {
            int len = s.length();
            if (len > largestLength) {
                largestLength = len;
            }
        }

        for (int i = 0; i < m.size(); i++) {
            String at = m.get(i);
            m.set(i, at + " ".repeat(largestLength - at.length()));
        }

        b.append(indent).append(m.get(0)).append(' ').append(m.get(1)).append(' ').append(m.get(2)).append(' ').append(m.get(3)).append('\n');
        b.append(indent).append(m.get(4)).append(' ').append(m.get(5)).append(' ').append(m.get(6)).append(' ').append(m.get(7)).append('\n');
        b.append(indent).append(m.get(8)).append(' ').append(m.get(9)).append(' ').append(m.get(10)).append(' ').append(m.get(11)).append('\n');
        b.append(indent).append(m.get(12)).append(' ').append(m.get(13)).append(' ').append(m.get(14)).append(' ').append(m.get(15));

        return b.toString();
    }

    private static String commentaryToXML(String commentary) {
        return "<!-- " + commentary + " -->";
    }

    private static String generateFileName(AtomicInteger counter, String file, String extension) {
        return "f" + counter.getAndIncrement() + "_" + URLEncoder.encode(file, StandardCharsets.UTF_8) + "." + extension;
    }

    private static String decodeString(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static Vector3f readVec3(Element element, String attributeName) {
        String[] attributes = {
            attributeName + "X",
            attributeName + "Y",
            attributeName + "Z"
        };

        boolean isNull = false;
        for (int i = 0; i < attributes.length; i++) {
            if (!element.hasAttribute(attributes[i])) {
                isNull = true;
                break;
            }
        }

        if (isNull) {
            return null;
        }

        return new Vector3f(
                Float.parseFloat(element.getAttribute(attributes[0])),
                Float.parseFloat(element.getAttribute(attributes[1])),
                Float.parseFloat(element.getAttribute(attributes[2]))
        );
    }

    private static Vector3f readRGB(Element element, String attributeName) {
        String[] attributes = {
            attributeName + "R",
            attributeName + "G",
            attributeName + "B"
        };

        boolean isNull = false;
        for (int i = 0; i < attributes.length; i++) {
            if (!element.hasAttribute(attributes[i])) {
                isNull = true;
                break;
            }
        }

        if (isNull) {
            return new Vector3f(1f);
        }

        return new Vector3f(
                Float.parseFloat(element.getAttribute(attributes[0])),
                Float.parseFloat(element.getAttribute(attributes[1])),
                Float.parseFloat(element.getAttribute(attributes[2]))
        );
    }

    private static Vector4f readRGBA(Element element, String attributeName) {
        String[] attributes = {
            attributeName + "R",
            attributeName + "G",
            attributeName + "B",
            attributeName + "A"
        };

        boolean isNull = false;
        for (int i = 0; i < attributes.length; i++) {
            if (!element.hasAttribute(attributes[i])) {
                isNull = true;
                break;
            }
        }

        if (isNull) {
            return new Vector4f(1f);
        }

        return new Vector4f(
                Float.parseFloat(element.getAttribute(attributes[0])),
                Float.parseFloat(element.getAttribute(attributes[1])),
                Float.parseFloat(element.getAttribute(attributes[2])),
                Float.parseFloat(element.getAttribute(attributes[3]))
        );
    }

    private static float f(Element element, String s) {
        return Float.parseFloat(element.getAttribute(s));
    }

    private static Matrix4f readMatrix(Element e) {
        Matrix4f matrix = new Matrix4f(
                f(e, "m00"), f(e, "m01"), f(e, "m02"), f(e, "m03"),
                f(e, "m10"), f(e, "m11"), f(e, "m12"), f(e, "m13"),
                f(e, "m20"), f(e, "m21"), f(e, "m22"), f(e, "m23"),
                f(e, "m30"), f(e, "m31"), f(e, "m32"), f(e, "m33")
        );

        return matrix;
    }
    
    //classes
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

    private static class StoreMesh {

        public NMesh object;

        public String id;

        public String name;
        public String file;
        public String bvhFile;
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
            b.append(indent).append(INDENT).append(stringToXML(this.bvhFile, "bvhFile")).append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.sha256, "sha256")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(vec3ToXML(this.min, "min")).append('\n');
            b.append(indent).append(INDENT).append(vec3ToXML(this.max, "max")).append('\n');
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

    private static class StoreGeometryDefinition {

        public String id;
        
        public String meshId;
        public String materialId;

        public Vector3f animatedMin;
        public Vector3f animatedMax;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();
            b.append(indent).append("<geometryDefinition").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.meshId, "meshId")).append('\n');
            if (this.materialId != null) {
                b.append(indent).append(INDENT).append(stringToXML(this.materialId, "materialId")).append('\n');
            }
            b.append(indent).append(INDENT).append('\n');
            b.append(indent).append(INDENT).append(vec3ToXML(this.animatedMin, "animatedMin")).append('\n');
            b.append(indent).append(INDENT).append(vec3ToXML(this.animatedMax, "animatedMax")).append('\n');
            b.append(indent).append("/>");

            return b.toString();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.meshId);
            hash = 67 * hash + Objects.hashCode(this.materialId);
            hash = 67 * hash + Objects.hashCode(this.animatedMin);
            hash = 67 * hash + Objects.hashCode(this.animatedMax);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StoreGeometryDefinition other = (StoreGeometryDefinition) obj;
            if (!Objects.equals(this.meshId, other.meshId)) {
                return false;
            }
            if (!Objects.equals(this.materialId, other.materialId)) {
                return false;
            }
            if (!Objects.equals(this.animatedMin, other.animatedMin)) {
                return false;
            }
            return Objects.equals(this.animatedMax, other.animatedMax);
        }
    }

    private static class StoreNodeGeometry {

        public NGeometry object;
        public String name;
        public String definitionId;

        public String toXML() {
            return "<geometry " + encodedStringToXML(this.name, "name") +" definitionId=\"" + this.definitionId + "\"/>";
        }
    }

    private static class StoreMatrix {

        public String id;
        public Matrix4f matrix;

        public String toXML(String indent) {
            StringBuilder b = new StringBuilder();

            b.append(indent).append("<matrix").append('\n');
            b.append(indent).append(INDENT).append(stringToXML(this.id, "id")).append('\n');
            b.append(matrixToXML(this.matrix, indent + INDENT)).append('\n');
            b.append(indent).append("/>");

            return b.toString();
        }
    }

    private static class StoreNode {

        public N3DModelNode object;

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

        public Map<String, StoreGeometryDefinition> geometriesDefinitions;
        public Map<NGeometry, StoreGeometryDefinition> geometriesDefinitionsObjectMap;

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
            b.append(INDENT).append(vec3ToXML(this.min, "min")).append('\n');
            b.append(INDENT).append(vec3ToXML(this.max, "max")).append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append(vec3ToXML(this.animatedMin, "animatedMin")).append('\n');
            b.append(INDENT).append(vec3ToXML(this.animatedMax, "animatedMax")).append('\n');
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
            if (!this.geometriesDefinitionsObjectMap.isEmpty()) {
                b.append(INDENT).append(commentaryToXML("Geometries Definitions")).append('\n');
                for (StoreGeometryDefinition geometry : this.geometriesDefinitionsObjectMap.values()) {
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

    //write
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
            storeGeometry.name = geometry.getName();
            storeGeometry.definitionId = model.geometriesDefinitionsObjectMap.get(geometry).id;
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

        zipOut.putNextEntry(new ZipEntry(MAGIC_FILE_IDENTIFIER));
        zipOut.closeEntry();

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
            storeAnimation.file = generateFileName(fileCounter, animation.getName(), "anm");
            store.animations.add(storeAnimation);

            zipOut.putNextEntry(new ZipEntry(storeAnimation.file));
            NAnimationStore.writeAnimation(animation, zipOut);
            zipOut.closeEntry();
        }

        store.texturesObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfTextures(); i++) {
            NTextures textures = model.getTextures(i);

            StoreTextures storeTextures = new StoreTextures();

            storeTextures.id = "txs_" + texCounter.getAndIncrement();
            storeTextures.file = generateFileName(fileCounter, textures.getName(), "txs");

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

            mesh.generateBVH();

            StoreMesh storeMesh = new StoreMesh();
            storeMesh.id = "msh_" + mshCounter.getAndIncrement();
            storeMesh.name = mesh.getName();
            storeMesh.file = generateFileName(fileCounter, mesh.getName(), "msh");
            storeMesh.bvhFile = generateFileName(fileCounter, mesh.getName(), "bvh");
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

            zipOut.putNextEntry(new ZipEntry(storeMesh.bvhFile));
            BVHStore.writeBVH(zipOut, mesh.getBVH());
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

        Map<StoreGeometryDefinition, StoreGeometryDefinition> definitionsMap = new HashMap<>();
        store.geometriesDefinitionsObjectMap = new HashMap<>();
        for (int i = 0; i < model.getNumberOfGeometries(); i++) {
            NGeometry geometry = model.getGeometry(i);

            StoreGeometryDefinition storeGeometry = new StoreGeometryDefinition();
            storeGeometry.id = "gmd_" + geoCounter.getAndIncrement();
            storeGeometry.meshId = store.meshesObjectMap.get(geometry.getMesh()).id;

            StoreMaterial material = store.materialsObjectMap.get(geometry.getMaterial());
            if (material != null) {
                storeGeometry.materialId = material.id;
            }

            storeGeometry.animatedMin = new Vector3f(geometry.getAnimatedAabbMin());
            storeGeometry.animatedMax = new Vector3f(geometry.getAnimatedAabbMax());

            StoreGeometryDefinition alreadyDefined = definitionsMap.get(storeGeometry);
            if (alreadyDefined != null) {
                storeGeometry = alreadyDefined;
                geoCounter.decrementAndGet();
            } else {
                definitionsMap.put(storeGeometry, storeGeometry);
            }

            store.geometriesDefinitionsObjectMap.put(geometry, storeGeometry);
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
                storeMatrix.id = "mtx_" + mtxCounter.getAndIncrement();
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
    
    //read
    private static Map<String, byte[]> readVirtualFileSystem(InputStream input) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(input, StandardCharsets.UTF_8);

        Map<String, byte[]> fs = new HashMap<>();

        ZipEntry e;
        while ((e = zipIn.getNextEntry()) != null) {
            if (e.isDirectory()) {
                continue;
            }
            fs.put(e.getName(), zipIn.readAllBytes());
        }

        return fs;
    }

    private static StoreNode readSceneGraph(Element nodeElement) {
        StoreNode node = new StoreNode();

        node.name = decodeString(nodeElement.getAttribute("name"));
        if (nodeElement.hasAttribute("matrixId")) {
            node.matrixId = nodeElement.getAttribute("matrixId");
        }

        node.geometries = new ArrayList<>();
        NodeList geometriesNode = nodeElement.getElementsByTagName("geometry");
        for (int i = 0; i < geometriesNode.getLength(); i++) {
            Element geometryElement = (Element) geometriesNode.item(i);
            if (geometryElement.getParentNode() != nodeElement) {
                continue;
            }

            StoreNodeGeometry storeNodeGeometry = new StoreNodeGeometry();
            storeNodeGeometry.name = decodeString(geometryElement.getAttribute("name"));
            storeNodeGeometry.definitionId = geometryElement.getAttribute("definitionId");
            node.geometries.add(storeNodeGeometry);
        }

        node.children = new ArrayList<>();
        NodeList children = nodeElement.getElementsByTagName("node");
        for (int i = 0; i < children.getLength(); i++) {
            Element childElement = (Element) children.item(i);
            if (childElement.getParentNode() != nodeElement) {
                continue;
            }

            node.children.add(readSceneGraph(childElement));
        }

        return node;
    }

    private static N3DModelNode buildNode(StoreModel model, StoreNode node) {
        List<NGeometry> geometries = new ArrayList<>();
        for (StoreNodeGeometry geometry : node.geometries) {
            StoreGeometryDefinition definition = model.geometriesDefinitions.get(geometry.definitionId);

            NMaterial mat = null;
            if (definition.materialId != null) {
                mat = model.materials.get(definition.materialId).object;
            }

            geometry.object = new NGeometry(
                    geometry.name,
                    model.meshes.get(definition.meshId).object,
                    mat,
                    definition.animatedMin, definition.animatedMax
            );

            geometries.add(geometry.object);
        }

        List<N3DModelNode> children = new ArrayList<>();
        for (StoreNode child : node.children) {
            children.add(buildNode(model, child));
        }

        Matrix4fc transformation = IDENTITY;
        if (node.matrixId != null) {
            transformation = model.matrices.get(node.matrixId).matrix;
        }

        return new N3DModelNode(
                node.name,
                transformation,
                geometries.toArray(NGeometry[]::new),
                children.toArray(N3DModelNode[]::new)
        );
    }

    private static N3DModel buildN3DModel(Map<String, byte[]> fs, StoreModel model) {
        ExecutorService service = Executors.newCachedThreadPool();

        for (StoreAnimation animation : model.animations) {
            service.execute(() -> {
                try {
                    animation.object = NAnimationStore.readAnimation(new ByteArrayInputStream(fs.get(animation.file)));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        for (StoreTextures textures : model.textures.values()) {
            service.execute(() -> {
                try {
                    textures.object = NTexturesStore.readTextures(new ByteArrayInputStream(fs.get(textures.file)));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        for (StoreMesh mesh : model.meshes.values()) {
            service.execute(() -> {
                try {
                    List<String> bonesList = new ArrayList<>();
                    for (Integer boneIndex : mesh.bones) {
                        bonesList.add(model.bones.get(boneIndex).name);
                    }

                    MeshStore.MeshStoreOutput out = MeshStore.decode(new ByteArrayInputStream(fs.get(mesh.file)));

                    float[] vertices = out.vertices();
                    int[] indices = out.indices();

                    mesh.object = new NMesh(
                            mesh.name,
                            vertices, indices,
                            bonesList.toArray(String[]::new),
                            mesh.min, mesh.max,
                            mesh.sha256
                    );

                    if (mesh.bvhFile != null) {
                        BVH bvh = BVHStore.readBVH(
                                new ByteArrayInputStream(fs.get(mesh.bvhFile)),
                                vertices, indices,
                                NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ,
                                mesh.object
                        );

                        mesh.object.setBVH(bvh);
                    } else {
                        mesh.object.generateBVH();
                    }

                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        try {
            service.shutdown();
            boolean result = service.awaitTermination(2, TimeUnit.HOURS);
            if (!result) {
                throw new RuntimeException("Model reading took too long!");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        for (StoreMaterial material : model.materials.values()) {
            NMaterial mat = new NMaterial(material.name);

            if (material.texturesId != null) {
                mat.setTextures(model.textures.get(material.texturesId).object);
            }

            mat.setMinExponent(material.minExponent);
            mat.setMaxExponent(material.maxExponent);

            mat.setParallaxHeightCoefficient(material.parallaxHeightCoefficient);
            mat.setParallaxMinLayers(material.parallaxMinLayers);
            mat.setParallaxMaxLayers(material.parallaxMaxLayers);

            mat.getDiffuseColor().set(material.diffuse);
            mat.getSpecularColor().set(material.specular);
            mat.getEmissiveColor().set(material.emissive);
            mat.getReflectionColor().set(material.reflection);

            material.object = mat;
        }

        List<NAnimation> animations = new ArrayList<>();
        for (StoreAnimation animation : model.animations) {
            animations.add(animation.object);
        }

        model.object = new N3DModel(
                model.name,
                buildNode(model, model.rootNode),
                animations.toArray(NAnimation[]::new),
                model.min,
                model.max,
                model.animatedMin,
                model.animatedMax
        );
        model.object.generateAnimatedAabb();

        return model.object;
    }
    
    public static N3DModel readModel(String jarFile) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(ClassLoader.getSystemResourceAsStream(jarFile))) {
            return readModel(stream);
        }
    }
    
    public static N3DModel readModel(InputStream input) throws IOException {
        Map<String, byte[]> fs = readVirtualFileSystem(input);

        if (fs.get(MAGIC_FILE_IDENTIFIER) == null) {
            throw new IllegalArgumentException("Invalid n3dm file!");
        }

        Document modelXml;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            modelXml = builder.parse(new ByteArrayInputStream(fs.get("model.xml")));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException(ex);
        }

        Element rootNode = modelXml.getDocumentElement();
        rootNode.normalize();

        StoreModel storeModel = new StoreModel();

        storeModel.name = decodeString(rootNode.getAttribute("name"));

        storeModel.min = readVec3(rootNode, "min");
        storeModel.max = readVec3(rootNode, "max");
        storeModel.animatedMin = readVec3(rootNode, "animatedMin");
        storeModel.animatedMax = readVec3(rootNode, "animatedMax");

        storeModel.animations = new ArrayList<>();
        NodeList animations = rootNode.getElementsByTagName("animation");
        for (int i = 0; i < animations.getLength(); i++) {
            Element element = (Element) animations.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreAnimation storeAnimation = new StoreAnimation();

            storeAnimation.file = element.getAttribute("file");
            storeModel.animations.add(storeAnimation);
        }

        storeModel.textures = new HashMap<>();
        NodeList textures = rootNode.getElementsByTagName("textures");
        for (int i = 0; i < textures.getLength(); i++) {
            Element element = (Element) textures.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreTextures storeTextures = new StoreTextures();

            storeTextures.id = element.getAttribute("id");
            storeTextures.file = element.getAttribute("file");

            storeModel.textures.put(storeTextures.id, storeTextures);
        }

        storeModel.bones = new HashMap<>();
        NodeList bones = rootNode.getElementsByTagName("bone");
        for (int i = 0; i < bones.getLength(); i++) {
            Element element = (Element) bones.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreBone storeBone = new StoreBone();

            storeBone.index = Integer.parseInt(element.getAttribute("index"));
            storeBone.name = decodeString(element.getAttribute("name"));

            storeModel.bones.put(storeBone.index, storeBone);
        }

        storeModel.meshes = new HashMap<>();
        NodeList meshes = rootNode.getElementsByTagName("mesh");
        for (int i = 0; i < meshes.getLength(); i++) {
            Element element = (Element) meshes.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreMesh storeMesh = new StoreMesh();

            storeMesh.id = element.getAttribute("id");

            storeMesh.name = decodeString(element.getAttribute("name"));
            storeMesh.file = element.getAttribute("file");
            if (element.hasAttribute("bvhFile")) {
                storeMesh.bvhFile = element.getAttribute("bvhFile");
            }
            if (element.hasAttribute("sha256")) {
                storeMesh.sha256 = element.getAttribute("sha256");
            }

            storeMesh.min = readVec3(element, "min");
            storeMesh.max = readVec3(element, "max");

            storeMesh.bones = new ArrayList<>();
            if (element.hasAttribute("bones")) {
                String[] bonesArray = element.getAttribute("bones").split(Pattern.quote(","));
                for (int j = 0; j < bonesArray.length; j++) {
                    storeMesh.bones.add(Integer.valueOf(bonesArray[j]));
                }
            }

            storeModel.meshes.put(storeMesh.id, storeMesh);
        }

        storeModel.materials = new HashMap<>();
        NodeList materials = rootNode.getElementsByTagName("material");
        for (int i = 0; i < materials.getLength(); i++) {
            Element element = (Element) materials.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreMaterial storeMaterial = new StoreMaterial();

            storeMaterial.id = element.getAttribute("id");

            storeMaterial.name = decodeString(element.getAttribute("name"));
            if (element.hasAttribute("texturesId")) {
                storeMaterial.texturesId = element.getAttribute("texturesId");
            }

            storeMaterial.minExponent = Float.parseFloat(element.getAttribute("minExponent"));
            storeMaterial.maxExponent = Float.parseFloat(element.getAttribute("maxExponent"));

            storeMaterial.parallaxHeightCoefficient = Float.parseFloat(element.getAttribute("parallaxHeightCoefficient"));
            storeMaterial.parallaxMinLayers = Float.parseFloat(element.getAttribute("parallaxMinLayers"));
            storeMaterial.parallaxMaxLayers = Float.parseFloat(element.getAttribute("parallaxMaxLayers"));

            storeMaterial.diffuse = readRGBA(element, "diffuse");
            storeMaterial.specular = readRGB(element, "specular");
            storeMaterial.emissive = readRGB(element, "emissive");
            storeMaterial.reflection = readRGB(element, "reflection");

            storeModel.materials.put(storeMaterial.id, storeMaterial);
        }

        storeModel.geometriesDefinitions = new HashMap<>();
        NodeList geometries = rootNode.getElementsByTagName("geometryDefinition");
        for (int i = 0; i < geometries.getLength(); i++) {
            Element element = (Element) geometries.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreGeometryDefinition storeGeometry = new StoreGeometryDefinition();

            storeGeometry.id = element.getAttribute("id");
            
            storeGeometry.meshId = element.getAttribute("meshId");
            if (element.hasAttribute("materialId")) {
                storeGeometry.materialId = element.getAttribute("materialId");
            }

            storeGeometry.animatedMin = readVec3(element, "animatedMin");
            storeGeometry.animatedMax = readVec3(element, "animatedMax");

            storeModel.geometriesDefinitions.put(storeGeometry.id, storeGeometry);
        }

        storeModel.matrices = new HashMap<>();
        NodeList matrices = rootNode.getElementsByTagName("matrix");
        for (int i = 0; i < matrices.getLength(); i++) {
            Element element = (Element) matrices.item(i);
            if (element.getParentNode() != rootNode) {
                continue;
            }

            StoreMatrix storeMatrix = new StoreMatrix();

            storeMatrix.id = element.getAttribute("id");
            storeMatrix.matrix = readMatrix(element);

            storeModel.matrices.put(storeMatrix.id, storeMatrix);
        }

        Element rootElement = null;
        NodeList list = rootNode.getElementsByTagName("node");
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            if (element.getParentNode() == rootNode) {
                rootElement = element;
                break;
            }
        }

        storeModel.rootNode = readSceneGraph(rootElement);

        return buildN3DModel(fs, storeModel);
    }

    private N3DModelStore() {

    }

}
