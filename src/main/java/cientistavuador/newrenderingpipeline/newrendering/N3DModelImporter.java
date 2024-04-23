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
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import static org.lwjgl.assimp.Assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class N3DModelImporter {
    
    public static double DEFAULT_TICKS_PER_SECOND = 1000.0;
    
    public static final int DEFAULT_FLAGS = aiProcess_CalcTangentSpace
            | aiProcess_Triangulate
            | aiProcess_TransformUVCoords
            | aiProcess_FindDegenerates
            | aiProcess_GenNormals
            | aiProcess_RemoveRedundantMaterials
            | aiProcess_ImproveCacheLocality
            | aiProcess_SplitLargeMeshes
            | aiProcess_LimitBoneWeights
            | aiProcess_FindInvalidData
            | aiProcess_FindInstances
            | aiProcess_SortByPType
            | aiProcess_OptimizeGraph
            | aiProcess_OptimizeMeshes
            | aiProcess_EmbedTextures;
    //| aiProcess_SplitByBoneCount;

    public static final AIPropertyStore DEFAULT_PROPERTIES;

    static {
        DEFAULT_PROPERTIES = Assimp.aiCreatePropertyStore();

        Assimp.aiSetImportPropertyInteger(DEFAULT_PROPERTIES, AI_CONFIG_PP_LBW_MAX_WEIGHTS, NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS);
        Assimp.aiSetImportPropertyInteger(DEFAULT_PROPERTIES, AI_CONFIG_PP_SBBC_MAX_BONES, NMesh.MAX_AMOUNT_OF_BONES);
    }

    public static N3DModel importFromFile(String file) throws IOException {
        Objects.requireNonNull(file, "File is null.");
        AIScene modelScene = Assimp.aiImportFileExWithProperties(
                file,
                DEFAULT_FLAGS,
                null,
                DEFAULT_PROPERTIES
        );
        return process(modelScene);
    }

    public static N3DModel importFromJarFile(String jarFile) throws IOException {
        Objects.requireNonNull(jarFile, "File is null.");
        try (InputStream jarStream = ClassLoader.getSystemResourceAsStream(jarFile)) {
            return importFromMemory(jarStream.readAllBytes());
        }
    }

    public static N3DModel importFromStream(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "Stream is null.");
        return importFromMemory(stream.readAllBytes());
    }

    public static N3DModel importFromMemory(byte[] memory) {
        Objects.requireNonNull(memory, "Memory is null.");
        ByteBuffer nativeMemory = MemoryUtil.memAlloc(memory.length).put(memory).flip();
        try {
            AIScene modelScene = Assimp.aiImportFileFromMemoryWithProperties(
                    nativeMemory,
                    DEFAULT_FLAGS,
                    "glb",
                    DEFAULT_PROPERTIES
            );

            return process(modelScene);
        } finally {
            MemoryUtil.memFree(nativeMemory);
        }
    }

    private static N3DModel process(AIScene modelScene) {
        if (modelScene == null) {
            throw new RuntimeException("Failed to import.");
        }

        try {
            if ((modelScene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0) {
                throw new RuntimeException("Failed to import.");
            }

            AINode rootNode = modelScene.mRootNode();
            if (rootNode == null) {
                throw new RuntimeException("Failed to import.");
            }

            return new N3DModelImporter(modelScene).process();
        } finally {
            aiFreeScene(modelScene);
        }
    }

    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private final AIScene scene;

    private final Map<String, NTexturesIO.LoadedImage> loadedImages = new HashMap<>();
    private final Map<Integer, NMaterial> loadedMaterials = new HashMap<>();
    private final Map<Integer, NGeometry> loadedGeometries = new HashMap<>();

    private final List<NAnimation> loadedAnimations = new ArrayList<>();

    private N3DModelImporter(AIScene scene) {
        this.scene = scene;
    }

    private void loadImages() {
        PointerBuffer images = this.scene.mTextures();
        if (images == null) {
            return;
        }

        List<Future<Pair<Pair<String, Integer>, NTexturesIO.LoadedImage>>> futureImages = new ArrayList<>();

        int amountOfImages = this.scene.mNumTextures();
        for (int i = 0; i < amountOfImages; i++) {
            final int imageIndex = i;

            AITexture tex = AITexture.createSafe(images.get(imageIndex));
            if (tex == null) {
                continue;
            }

            final String fileName = tex.mFilename().dataString();
            
            if (tex.mHeight() == 0) {
                byte[] data = new byte[tex.mWidth()];
                tex.pcDataCompressed().get(data);

                futureImages.add(this.service.submit(() -> {
                    return new Pair<>(new Pair<>(fileName, imageIndex), NTexturesIO.loadImage(data));
                }));
            } else {
                int width = tex.mWidth();
                int height = tex.mHeight();

                AITexel.Buffer texels = tex.pcData();

                byte[] data = new byte[width * height * 4];

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        AITexel texel = texels.get(x + (((height - 1) - y) * width));

                        data[0 + (x * 4) + (y * 4 * width)] = texel.r();
                        data[1 + (x * 4) + (y * 4 * width)] = texel.g();
                        data[2 + (x * 4) + (y * 4 * width)] = texel.b();
                        data[3 + (x * 4) + (y * 4 * width)] = texel.a();
                    }
                }

                NTexturesIO.LoadedImage loaded = new NTexturesIO.LoadedImage(width, height, data);

                this.loadedImages.put(fileName, loaded);
                this.loadedImages.put("*" + i, loaded);
            }
        }

        for (Future<Pair<Pair<String, Integer>, NTexturesIO.LoadedImage>> futurePair : futureImages) {
            try {
                Pair<Pair<String, Integer>, NTexturesIO.LoadedImage> pair = futurePair.get();
                this.loadedImages.put(pair.getA().getA(), pair.getB());
                this.loadedImages.put("*" + pair.getA().getB(), pair.getB());
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private NTexturesIO.LoadedImage getMaterialTexture(AIMaterial material, int type) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString pathString = AIString.calloc(stack);
            
            int result = aiGetMaterialTexture(material,
                    type,
                    0,
                    pathString,
                    null,
                    null,
                    null,
                    null,
                    null,
                    (IntBuffer) null
            );

            if (result != aiReturn_SUCCESS) {
                return null;
            }
            
            return this.loadedImages.get(pathString.dataString());
        }
    }

    private void loadMaterials() {
        PointerBuffer materials = this.scene.mMaterials();
        if (materials == null) {
            return;
        }

        List<Future<Pair<Integer, NMaterial>>> futureMaterials = new ArrayList<>();

        int amountOfMaterials = this.scene.mNumMaterials();
        for (int i = 0; i < amountOfMaterials; i++) {
            final int materialIndex = i;

            AIMaterial material = AIMaterial.createSafe(materials.get(materialIndex));
            if (material == null) {
                continue;
            }

            NTexturesIO.LoadedImage tempDiffuseImage = getMaterialTexture(material, aiTextureType_BASE_COLOR);
            if (tempDiffuseImage == null) {
                tempDiffuseImage = getMaterialTexture(material, aiTextureType_DIFFUSE);
            }
            NTexturesIO.LoadedImage diffuseImage = tempDiffuseImage;

            NTexturesIO.LoadedImage tempHeightImage = getMaterialTexture(material, aiTextureType_HEIGHT);
            if (tempHeightImage == null) {
                tempHeightImage = getMaterialTexture(material, aiTextureType_DISPLACEMENT);
            }

            NTexturesIO.LoadedImage heightImage = tempHeightImage;
            
            NTexturesIO.LoadedImage tempAoInvertedexponentReflectivenessImage = getMaterialTexture(material, aiTextureType_METALNESS);
            if (tempAoInvertedexponentReflectivenessImage == null) {
                tempAoInvertedexponentReflectivenessImage = getMaterialTexture(material, aiTextureType_SPECULAR);
                if (tempAoInvertedexponentReflectivenessImage != null) {
                    int width = tempAoInvertedexponentReflectivenessImage.width;
                    int height = tempAoInvertedexponentReflectivenessImage.height;
                    byte[] data = tempAoInvertedexponentReflectivenessImage.pixelData;
                    
                    for (int pixel = 0; pixel < width * height; pixel++) {
                        byte r = data[(pixel * 4) + 0];
                        
                        data[(pixel * 4) + 0] = (byte) 255;
                        data[(pixel * 4) + 1] = (byte) (255 - (r & 0xFF));
                        data[(pixel * 4) + 2] = (byte) 0;
                        data[(pixel * 4) + 3] = (byte) 255;
                    }
                }
            }
            
            NTexturesIO.LoadedImage aoInvertedexponentReflectivenessImage = tempAoInvertedexponentReflectivenessImage;
            NTexturesIO.LoadedImage normalImage = getMaterialTexture(material, aiTextureType_NORMALS);
            
            futureMaterials.add(this.service.submit(() -> {
                byte[] diffuseMap = null;

                if (diffuseImage != null) {
                    diffuseMap = diffuseImage.pixelData;
                }

                byte[] aoMap = null;
                byte[] invertedExponentMap = null;
                byte[] reflectivenessMap = null;

                if (aoInvertedexponentReflectivenessImage != null) {
                    int width = aoInvertedexponentReflectivenessImage.width;
                    int height = aoInvertedexponentReflectivenessImage.height;

                    int pixels = width * height;

                    aoMap = new byte[pixels * 4];
                    invertedExponentMap = new byte[pixels * 4];
                    reflectivenessMap = new byte[pixels * 4];

                    for (int j = 0; j < pixels; j++) {
                        int ao = aoInvertedexponentReflectivenessImage.pixelData[(j * 4) + 0] & 0xFF;
                        int invertedExponent = aoInvertedexponentReflectivenessImage.pixelData[(j * 4) + 1] & 0xFF;
                        int reflectiveness = aoInvertedexponentReflectivenessImage.pixelData[(j * 4) + 2] & 0xFF;

                        aoMap[(j * 4) + 0] = (byte) ao;
                        invertedExponentMap[(j * 4) + 0] = (byte) invertedExponent;
                        reflectivenessMap[(j * 4) + 0] = (byte) reflectiveness;
                    }
                }

                byte[] heightMap = null;

                if (heightImage != null) {
                    heightMap = heightImage.pixelData;
                }

                byte[] normalMap = null;

                if (normalImage != null) {
                    normalMap = normalImage.pixelData;
                }

                int textureWidth = -1;
                int textureHeight = -1;

                if (diffuseImage != null) {
                    textureWidth = diffuseImage.width;
                    textureHeight = diffuseImage.height;
                }

                if (heightImage != null) {
                    textureWidth = heightImage.width;
                    textureHeight = heightImage.height;
                }

                if (aoInvertedexponentReflectivenessImage != null) {
                    textureWidth = aoInvertedexponentReflectivenessImage.width;
                    textureHeight = aoInvertedexponentReflectivenessImage.height;
                }

                if (normalImage != null) {
                    textureWidth = normalImage.width;
                    textureHeight = normalImage.height;
                }

                NTextures textures;

                if (textureWidth != -1 && textureHeight != -1) {
                    textures = NTexturesIO.load(
                            "textures_" + materialIndex,
                            textureWidth, textureHeight,
                            diffuseMap,
                            aoMap,
                            heightMap,
                            invertedExponentMap,
                            normalMap,
                            reflectivenessMap
                    );
                } else {
                    textures = NTextures.NULL_TEXTURE;
                }

                NMaterial mat = new NMaterial("material_" + materialIndex);
                mat.setTextures(textures);

                return new Pair<>(materialIndex, mat);
            }));
        }

        Map<String, NTextures> loadedTextures = new HashMap<>();

        for (Future<Pair<Integer, NMaterial>> futurePair : futureMaterials) {
            try {
                Pair<Integer, NMaterial> pair = futurePair.get();

                int index = pair.getA();
                NMaterial material = pair.getB();

                NTextures textures = material.getTextures();

                String sha256 = textures.getSha256();
                NTextures alreadyLoaded = loadedTextures.get(sha256);

                if (alreadyLoaded != null) {
                    material.setTextures(alreadyLoaded);
                } else {
                    loadedTextures.put(sha256, textures);
                }

                this.loadedMaterials.put(index, material);
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    private void clearImages() {
        this.loadedImages.clear();
    }

    private void loadMeshes() {
        PointerBuffer meshes = this.scene.mMeshes();
        if (meshes == null) {
            return;
        }

        List<Future<Pair<Integer, NGeometry>>> futureGeometries = new ArrayList<>();

        int amountOfMeshes = this.scene.mNumMeshes();
        for (int i = 0; i < amountOfMeshes; i++) {
            final int meshIndex = i;

            AIMesh mesh = AIMesh.createSafe(meshes.get(meshIndex));
            if (mesh == null) {
                continue;
            }

            AIVector3D.Buffer positions = mesh.mVertices();
            AIVector3D.Buffer uvs = mesh.mTextureCoords(0);
            AIVector3D.Buffer normals = mesh.mNormals();
            AIVector3D.Buffer tangents = mesh.mTangents();

            int amountOfFaces = mesh.mNumFaces();
            AIFace.Buffer faces = mesh.mFaces();

            int amountOfBones = mesh.mNumBones();
            PointerBuffer bones = mesh.mBones();

            String meshName = mesh.mName().dataString();

            if (faces == null) {
                continue;
            }

            futureGeometries.add(this.service.submit(() -> {
                float[] vertices = new float[amountOfFaces * 3 * NMesh.VERTEX_SIZE];
                int verticesIndex = 0;

                List<NMeshBone> meshBones = new ArrayList<>();
                Map<Integer, List<Pair<Integer, Float>>> boneVertexWeightMap = new HashMap<>();

                if (bones != null) {
                    for (int boneIndex = 0; boneIndex < amountOfBones; boneIndex++) {
                        AIBone bone = AIBone.create(bones.get(boneIndex));
                        String boneName = bone.mName().dataString();
                        AIMatrix4x4 o = bone.mOffsetMatrix();
                        Matrix4f offsetMatrix = new Matrix4f(
                                o.a1(), o.b1(), o.c1(), o.d1(),
                                o.a2(), o.b2(), o.c2(), o.d2(),
                                o.a3(), o.b3(), o.c3(), o.d3(),
                                o.a4(), o.b4(), o.c4(), o.d4()
                        );

                        meshBones.add(new NMeshBone(boneName, offsetMatrix));

                        int numWeights = bone.mNumWeights();
                        AIVertexWeight.Buffer weights = bone.mWeights();
                        if (numWeights != 0 && weights != null) {
                            for (int weightIndex = 0; weightIndex < numWeights; weightIndex++) {
                                AIVertexWeight weight = weights.get(weightIndex);
                                
                                int vertexIndex = weight.mVertexId();

                                List<Pair<Integer, Float>> weightList = boneVertexWeightMap.get(vertexIndex);
                                if (weightList == null) {
                                    weightList = new ArrayList<>();
                                    boneVertexWeightMap.put(vertexIndex, weightList);
                                }

                                weightList.add(new Pair<>(boneIndex, weight.mWeight()));
                            }
                        }
                    }
                }

                for (int faceIndex = 0; faceIndex < amountOfFaces; faceIndex++) {
                    AIFace face = faces.get(faceIndex);
                    if (face.mNumIndices() != 3) {
                        continue;
                    }
                    for (int vertex = 0; vertex < 3; vertex++) {
                        int index = face.mIndices().get(vertex);

                        float posX = 0f;
                        float posY = 0f;
                        float posZ = 0f;

                        if (positions != null) {
                            AIVector3D pos = positions.get(index);
                            posX = pos.x();
                            posY = pos.y();
                            posZ = pos.z();
                        }

                        float texX = 0f;
                        float texY = 0f;

                        if (uvs != null) {
                            AIVector3D uv = uvs.get(index);
                            texX = uv.x();
                            texY = uv.y();
                        }

                        float norX = 0f;
                        float norY = 0f;
                        float norZ = 0f;

                        if (normals != null) {
                            AIVector3D normal = normals.get(index);
                            norX = normal.x();
                            norY = normal.y();
                            norZ = normal.z();
                        }

                        float tanX = 0f;
                        float tanY = 0f;
                        float tanZ = 0f;

                        if (tangents != null) {
                            AIVector3D tangent = tangents.get(index);
                            tanX = tangent.x();
                            tanY = tangent.y();
                            tanZ = tangent.z();
                        }

                        float ao = 1f;

                        vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 0] = posX;
                        vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 1] = posY;
                        vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 2] = posZ;

                        vertices[verticesIndex + NMesh.OFFSET_TEXTURE_XY + 0] = texX;
                        vertices[verticesIndex + NMesh.OFFSET_TEXTURE_XY + 1] = texY;

                        vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 0] = norX;
                        vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 1] = norY;
                        vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 2] = norZ;

                        vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 0] = tanX;
                        vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 1] = tanY;
                        vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 2] = tanZ;

                        vertices[verticesIndex + NMesh.OFFSET_AMBIENT_OCCLUSION_X + 0] = ao;

                        vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 0] = Float.intBitsToFloat(-1);
                        vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 1] = Float.intBitsToFloat(-1);
                        vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 2] = Float.intBitsToFloat(-1);
                        vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 3] = Float.intBitsToFloat(-1);

                        List<Pair<Integer, Float>> boneVertexWeightList = boneVertexWeightMap.get(index);
                        if (boneVertexWeightList != null) {
                            for (int j = 0; j < NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS; j++) {
                                if (j >= boneVertexWeightList.size()) {
                                    break;
                                }
                                Pair<Integer, Float> pair = boneVertexWeightList.get(j);

                                int bone = pair.getA();
                                float weight = pair.getB();

                                vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + j] = Float.intBitsToFloat(bone);
                                vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + j] = weight;
                            }
                        }

                        verticesIndex += NMesh.VERTEX_SIZE;
                    }
                }

                Pair<float[], int[]> newMesh = MeshUtils.generateIndices(vertices, NMesh.VERTEX_SIZE);

                float[] finalVertices = newMesh.getA();
                int[] finalIndices = newMesh.getB();

                NMesh loadedMesh = new NMesh(
                        meshName,
                        finalVertices, finalIndices,
                        BVH.create(
                                finalVertices, finalIndices,
                                NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ
                        ),
                        meshBones.toArray(NMeshBone[]::new)
                );

                NMaterial material = this.loadedMaterials.get(mesh.mMaterialIndex());
                if (material == null) {
                    material = NMaterial.NULL_MATERIAL;
                }

                NGeometry geometry = new NGeometry(meshName, loadedMesh, material);

                return new Pair<>(
                        meshIndex,
                        geometry
                );
            }));
        }

        Map<String, NMesh> loadedMeshes = new HashMap<>();

        for (Future<Pair<Integer, NGeometry>> futurePair : futureGeometries) {
            try {
                Pair<Integer, NGeometry> pair = futurePair.get();

                int geometryIndex = pair.getA();
                NGeometry geometry = pair.getB();

                NMesh mesh = geometry.getMesh();

                String sha256 = mesh.getSha256();
                NMesh alreadyLoaded = loadedMeshes.get(sha256);

                if (alreadyLoaded != null) {
                    geometry = new NGeometry(geometry.getName(), alreadyLoaded, geometry.getMaterial());
                } else {
                    loadedMeshes.put(sha256, mesh);
                }

                this.loadedGeometries.put(geometryIndex, geometry);
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void clearMaterials() {
        this.loadedMaterials.clear();
    }

    private void loadAnimations() {
        PointerBuffer sceneAnimations = this.scene.mAnimations();
        if (sceneAnimations == null) {
            return;
        }

        int numberOfAnimations = this.scene.mNumAnimations();
        for (int i = 0; i < numberOfAnimations; i++) {
            AIAnimation sceneAnimation = AIAnimation.createSafe(sceneAnimations.get(i));
            if (sceneAnimation == null) {
                continue;
            }

            List<NBoneAnimation> boneAnimations = new ArrayList<>();

            String name = sceneAnimation.mName().dataString();
            
            double tps = sceneAnimation.mTicksPerSecond();
            if (tps == 0.0) {
                tps = DEFAULT_TICKS_PER_SECOND;
            }
            tps = 1.0 / tps;
            
            float duration = (float) (sceneAnimation.mDuration() * tps);
            
            int numberOfChannels = sceneAnimation.mNumChannels();
            PointerBuffer channels = sceneAnimation.mChannels();
            if (channels != null) {
                for (int j = 0; j < numberOfChannels; j++) {
                    AINodeAnim channel = AINodeAnim.createSafe(channels.get(j));
                    if (channel != null) {
                        String boneName = channel.mNodeName().dataString();

                        AIVectorKey.Buffer positionsBuffer = channel.mPositionKeys();
                        AIQuatKey.Buffer rotationsBuffer = channel.mRotationKeys();
                        AIVectorKey.Buffer scalingsBuffer = channel.mScalingKeys();

                        int numPosition = channel.mNumPositionKeys();
                        int numRotation = channel.mNumRotationKeys();
                        int numScaling = channel.mNumScalingKeys();

                        float[] positionTimes = new float[numPosition];
                        float[] rotationTimes = new float[numRotation];
                        float[] scalingTimes = new float[numScaling];

                        float[] positions = new float[numPosition * 3];
                        float[] rotations = new float[numRotation * 4];
                        float[] scaling = new float[numScaling * 3];

                        if (positionsBuffer != null) {
                            for (int k = 0; k < numPosition; k++) {
                                AIVectorKey key = positionsBuffer.get(k);

                                positionTimes[k] = (float) (key.mTime() * tps);

                                AIVector3D pos = key.mValue();

                                positions[(k * 3) + 0] = pos.x();
                                positions[(k * 3) + 1] = pos.y();
                                positions[(k * 3) + 2] = pos.z();
                            }
                        }

                        if (rotationsBuffer != null) {
                            for (int k = 0; k < numRotation; k++) {
                                AIQuatKey key = rotationsBuffer.get(k);

                                rotationTimes[k] = (float) (key.mTime() * tps);

                                AIQuaternion rotation = key.mValue();

                                rotations[(k * 4) + 0] = rotation.x();
                                rotations[(k * 4) + 1] = rotation.y();
                                rotations[(k * 4) + 2] = rotation.z();
                                rotations[(k * 4) + 3] = rotation.w();
                            }
                        }

                        if (scalingsBuffer != null) {
                            for (int k = 0; k < numScaling; k++) {
                                AIVectorKey key = scalingsBuffer.get(k);

                                scalingTimes[k] = (float) (key.mTime() * tps);

                                AIVector3D pos = key.mValue();

                                scaling[(k * 3) + 0] = pos.x();
                                scaling[(k * 3) + 1] = pos.y();
                                scaling[(k * 3) + 2] = pos.z();
                            }
                        }

                        boneAnimations.add(new NBoneAnimation(
                                boneName,
                                positionTimes, positions,
                                rotationTimes, rotations,
                                scalingTimes, scaling
                        ));
                    }
                }
            }

            this.loadedAnimations.add(new NAnimation(name, duration, boneAnimations.toArray(NBoneAnimation[]::new)));
        }
    }

    private N3DModelNode recursiveNodeGeneration(AINode node) {
        AIMatrix4x4 t = node.mTransformation();

        String name = node.mName().dataString();
        Matrix4f transformation = new Matrix4f(
                t.a1(), t.b1(), t.c1(), t.d1(),
                t.a2(), t.b2(), t.c2(), t.d2(),
                t.a3(), t.b3(), t.c3(), t.d3(),
                t.a4(), t.b4(), t.c4(), t.d4()
        );

        List<NGeometry> geometries = new ArrayList<>();

        int amountOfGeometries = node.mNumMeshes();
        IntBuffer geometriesIndex = node.mMeshes();
        if (geometriesIndex != null) {
            for (int i = 0; i < amountOfGeometries; i++) {
                NGeometry geometry = this.loadedGeometries.get(geometriesIndex.get(i));
                if (geometry != null) {
                    geometries.add(geometry);
                }
            }
        }

        List<N3DModelNode> children = new ArrayList<>();

        int amountOfChildren = node.mNumChildren();
        PointerBuffer childrenBuffer = node.mChildren();
        if (childrenBuffer != null) {
            for (int i = 0; i < amountOfChildren; i++) {
                AINode child = AINode.createSafe(childrenBuffer.get(i));
                if (child != null) {
                    children.add(recursiveNodeGeneration(child));
                }
            }
        }

        return new N3DModelNode(
                name,
                transformation,
                geometries.toArray(NGeometry[]::new),
                children.toArray(N3DModelNode[]::new)
        );
    }

    private N3DModelNode generateRootNode() {
        return recursiveNodeGeneration(this.scene.mRootNode());
    }

    private N3DModel process() {
        try {
            loadImages();
            loadMaterials();
            clearImages();
            loadMeshes();
            clearMaterials();

            loadAnimations();

            return new N3DModel(
                    this.scene.mName().dataString(),
                    generateRootNode(),
                    this.loadedAnimations.toArray(NAnimation[]::new)
            );
        } finally {
            this.service.shutdownNow();
        }
    }

}
