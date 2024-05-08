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

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class NMesh {

    private static final AtomicLong meshIds = new AtomicLong();
    
    public static final int MAX_AMOUNT_OF_BONES = 64;
    public static final int MAX_AMOUNT_OF_BONE_WEIGHTS = 4;
    
    public static final int VAO_INDEX_POSITION_XYZ = 0;
    public static final int VAO_INDEX_TEXTURE_XY = 1;
    public static final int VAO_INDEX_NORMAL_XYZ = 2;
    public static final int VAO_INDEX_TANGENT_XYZ = 3;
    public static final int VAO_INDEX_AMBIENT_OCCLUSION_X = 4;
    public static final int VAO_INDEX_BONE_IDS_XYZW = 5;
    public static final int VAO_INDEX_BONE_WEIGHTS_XYZW = 6;

    public static final int OFFSET_POSITION_XYZ = 0;
    public static final int OFFSET_TEXTURE_XY = OFFSET_POSITION_XYZ + 3;
    public static final int OFFSET_NORMAL_XYZ = OFFSET_TEXTURE_XY + 2;
    public static final int OFFSET_TANGENT_XYZ = OFFSET_NORMAL_XYZ + 3;
    public static final int OFFSET_AMBIENT_OCCLUSION_X = OFFSET_TANGENT_XYZ + 3;
    public static final int OFFSET_BONE_IDS_XYZW = OFFSET_AMBIENT_OCCLUSION_X + 1;
    public static final int OFFSET_BONE_WEIGHTS_XYZW = OFFSET_BONE_IDS_XYZW + 4;

    public static final int VERTEX_SIZE = OFFSET_BONE_WEIGHTS_XYZW + 4;

    private final String name;
    private final float[] vertices;
    private final int[] indices;
    private final NMeshBone[] bones;
    private final Map<String, Integer> bonesMap = new HashMap<>();

    private final boolean lightmapped;
    private final Vector3f aabbMin = new Vector3f();
    private final Vector3f aabbMax = new Vector3f();
    private final Vector3f aabbCenter = new Vector3f();

    private static class WrappedVertexArrays {

        public int vao = 0;
    }

    private static class WrappedBuffer {

        public int buffer = 0;
    }

    private final WrappedVertexArrays wrappedVao = new WrappedVertexArrays();
    private final WrappedBuffer wrappedVbo = new WrappedBuffer();
    private final WrappedBuffer wrappedEbo = new WrappedBuffer();

    private final String sha256;
    
    private BVH bvh = null;
    
    private final Vector3f animatedAabbMin = new Vector3f();
    private final Vector3f animatedAabbMax = new Vector3f();
    private final Vector3f animatedAabbCenter = new Vector3f();
    private boolean animatedAabbGenerated = false;
    
    public NMesh(String name, float[] vertices, int[] indices, NMeshBone[] bones) {
        Objects.requireNonNull(vertices, "Vertices is null");
        Objects.requireNonNull(indices, "Indices is null");

        if (indices.length % 3 != 0) {
            throw new IllegalArgumentException("The indices array does not contain triangles!");
        }

        if (vertices.length % VERTEX_SIZE != 0) {
            throw new IllegalArgumentException("The vertices array does not contain valid vertices!");
        }

        this.vertices = vertices;
        this.indices = indices;
        if (bones == null) {
            bones = new NMeshBone[0];
        }
        if (bones.length > MAX_AMOUNT_OF_BONES) {
            throw new IllegalArgumentException("Max amount of bones per mesh is "+MAX_AMOUNT_OF_BONES);
        }
        for (int i = 0; i < bones.length; i++) {
            this.bonesMap.put(bones[i].getName(), i);
        }
        this.bones = bones;
        
        boolean lightmap = true;
        for (int i = 0; i < this.indices.length; i++) {
            if (this.indices[i] != i) {
                lightmap = false;
                break;
            }
        }
        this.lightmapped = lightmap;

        MeshUtils.aabb(this.vertices,
                NMesh.VERTEX_SIZE,
                NMesh.OFFSET_POSITION_XYZ,
                this.aabbMin, this.aabbMax, this.aabbCenter
        );
        
        this.animatedAabbMin.set(this.aabbMin);
        this.animatedAabbMax.set(this.aabbMax);
        this.animatedAabbCenter.set(this.aabbCenter);

        registerForCleaning();

        String hash;
        {
            ByteBuffer buffer = ByteBuffer.wrap(new byte[(vertices.length * Float.BYTES) + (indices.length * Integer.BYTES)]);

            for (float f : vertices) {
                buffer.putFloat(f);
            }

            for (int i : indices) {
                buffer.putInt(i);
            }

            buffer.flip();

            byte[] data = buffer.array();

            byte[] sha256Bytes;
            try {
                sha256Bytes = MessageDigest.getInstance("SHA256").digest(data);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            }

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < sha256Bytes.length; i++) {
                String hex = Integer.toHexString(sha256Bytes[i] & 0xFF);
                if (hex.length() <= 1) {
                    b.append('0');
                }
                b.append(hex);
            }

            hash = b.toString();
        }

        this.sha256 = hash;

        if (name == null) {
            this.name = this.sha256;
        } else {
            this.name = name;
        }
    }

    private void registerForCleaning() {
        final WrappedVertexArrays finalVao = this.wrappedVao;
        final WrappedBuffer finalVbo = this.wrappedVbo;
        final WrappedBuffer finalEbo = this.wrappedEbo;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int vaoToClean = finalVao.vao;
                int vboToClean = finalVbo.buffer;
                int eboToClean = finalEbo.buffer;

                if (vaoToClean != 0) {
                    glDeleteVertexArrays(vaoToClean);
                    finalVao.vao = 0;
                }
                if (vboToClean != 0) {
                    glDeleteBuffers(vboToClean);
                    finalVbo.buffer = 0;
                }
                if (eboToClean != 0) {
                    glDeleteBuffers(eboToClean);
                    finalEbo.buffer = 0;
                }
            });
        });
    }

    public String getName() {
        return name;
    }

    public float[] getVertices() {
        return this.vertices;
    }

    public int[] getIndices() {
        return this.indices;
    }
    
    public int getAmountOfBones() {
        return this.bones.length;
    }
    
    public NMeshBone getBone(int index) {
        return this.bones[index];
    }
    
    public int indexOfBone(String name) {
        Integer index = this.bonesMap.get(name);
        if (index == null) {
            return -1;
        }
        return index;
    }
    
    public NMeshBone getBone(String name) {
        int index = indexOfBone(name);
        if (index < 0) {
            return null;
        }
        return getBone(index);
    }

    public boolean isLightmapped() {
        return lightmapped;
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

    public String getSha256() {
        return sha256;
    }
    
    public void generateBVH() {
        this.bvh = BVH.create(this, this.vertices, this.indices, NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ);
    }
    
    public BVH getBVH() {
        return this.bvh;
    }

    public void setBVH(BVH bvh) {
        this.bvh = bvh;
    }
    
    public void generateAnimatedAabb(N3DModel originalModel) {
        if (originalModel.getAnimations().length == 0) {
            return;
        }
        
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        
        Matrix4f totalBoneMatrix = new Matrix4f();
        Vector3f transformed = new Vector3f();
        Vector3f totalTransformation = new Vector3f();
        
        int[] bonesIds = new int[MAX_AMOUNT_OF_BONE_WEIGHTS];
        float[] bonesWeights = new float[MAX_AMOUNT_OF_BONE_WEIGHTS];
        
        for (NAnimation animation:originalModel.getAnimations()) {
            NAnimator animator = new NAnimator(originalModel, animation.getName());
            animator.setLooping(false);
            while (!animator.isFinished()) {
                animator.update(NAnimator.UPDATE_RATE);
                
                for (int i = 0; i < this.vertices.length; i += NMesh.VERTEX_SIZE) {
                    float x = this.vertices[i + NMesh.OFFSET_POSITION_XYZ + 0];
                    float y = this.vertices[i + NMesh.OFFSET_POSITION_XYZ + 1];
                    float z = this.vertices[i + NMesh.OFFSET_POSITION_XYZ + 2];
                    
                    bonesIds[0] = Float.floatToRawIntBits(this.vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 0]);
                    bonesIds[1] = Float.floatToRawIntBits(this.vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 1]);
                    bonesIds[2] = Float.floatToRawIntBits(this.vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 2]);
                    bonesIds[3] = Float.floatToRawIntBits(this.vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 3]);
                    
                    bonesWeights[0] = this.vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0];
                    bonesWeights[1] = this.vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 1];
                    bonesWeights[2] = this.vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 2];
                    bonesWeights[3] = this.vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 3];
                    
                    totalTransformation.zero();
                    for (int j = 0; j < bonesIds.length; j++) {
                        int boneId = bonesIds[j];
                        float boneWeight = bonesWeights[j];
                        
                        if (boneId >= 0) {
                            NMeshBone bone = getBone(boneId);
                            
                            Matrix4fc boneMatrix = animator.getBoneMatrix(bone.getName());
                            
                            totalBoneMatrix
                                    .set(boneMatrix)
                                    .mul(bone.getOffset());
                            
                            transformed.set(x, y, z);
                            totalBoneMatrix.transformProject(transformed);
                            transformed.mul(boneWeight);
                            
                            totalTransformation.add(transformed);
                        }
                    }
                    
                    minX = Math.min(minX, totalTransformation.x());
                    minY = Math.min(minY, totalTransformation.y());
                    minZ = Math.min(minZ, totalTransformation.z());
                    
                    maxX = Math.max(maxX, totalTransformation.x());
                    maxY = Math.max(maxY, totalTransformation.y());
                    maxZ = Math.max(maxZ, totalTransformation.z());
                }
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

    private void validateVAO() {
        if (this.wrappedVao.vao != 0) {
            return;
        }

        long meshId = NMesh.meshIds.getAndIncrement();

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, this.vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(VAO_INDEX_POSITION_XYZ);
        glVertexAttribPointer(VAO_INDEX_POSITION_XYZ, 3, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_POSITION_XYZ * Float.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_TEXTURE_XY);
        glVertexAttribPointer(VAO_INDEX_TEXTURE_XY, 2, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_TEXTURE_XY * Float.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_NORMAL_XYZ);
        glVertexAttribPointer(VAO_INDEX_NORMAL_XYZ, 3, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_NORMAL_XYZ * Float.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_TANGENT_XYZ);
        glVertexAttribPointer(VAO_INDEX_TANGENT_XYZ, 3, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_TANGENT_XYZ * Float.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_AMBIENT_OCCLUSION_X);
        glVertexAttribPointer(VAO_INDEX_AMBIENT_OCCLUSION_X, 1, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_AMBIENT_OCCLUSION_X * Float.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_BONE_IDS_XYZW);
        glVertexAttribIPointer(VAO_INDEX_BONE_IDS_XYZW, 4, GL_INT, VERTEX_SIZE * Float.BYTES, OFFSET_BONE_IDS_XYZW * Integer.BYTES);

        glEnableVertexAttribArray(VAO_INDEX_BONE_WEIGHTS_XYZW);
        glVertexAttribPointer(VAO_INDEX_BONE_WEIGHTS_XYZW, 4, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_BONE_WEIGHTS_XYZW * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, this.indices, GL_STATIC_DRAW);

        glBindVertexArray(0);

        this.wrappedVao.vao = vao;
        this.wrappedVbo.buffer = vbo;
        this.wrappedEbo.buffer = ebo;

        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_VERTEX_ARRAY, vao,
                    StringUtils.truncateStringTo255Bytes("vao_"+meshId+"_"+this.name)
            );
            
            KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, vbo,
                    StringUtils.truncateStringTo255Bytes("vbo_"+meshId+"_"+this.name)
            );
            
            KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, ebo,
                    StringUtils.truncateStringTo255Bytes("ebo_"+meshId+"_"+this.name)
            );
        }
    }

    public int getVAO() {
        validateVAO();
        return this.wrappedVao.vao;
    }

    public int getVBO() {
        validateVAO();
        return this.wrappedVbo.buffer;
    }

    public int getEBO() {
        validateVAO();
        return this.wrappedEbo.buffer;
    }

    public void manualFree() {
        int vao = this.wrappedVao.vao;
        int vbo = this.wrappedVbo.buffer;
        int ebo = this.wrappedEbo.buffer;

        if (vao != 0) {
            glDeleteVertexArrays(vao);
            this.wrappedVao.vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            this.wrappedVbo.buffer = 0;
        }
        if (ebo != 0) {
            glDeleteBuffers(ebo);
            this.wrappedEbo.buffer = 0;
        }
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.sha256);
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
        final NMesh other = (NMesh) obj;
        return Objects.equals(this.sha256, other.sha256);
    }
    
}
