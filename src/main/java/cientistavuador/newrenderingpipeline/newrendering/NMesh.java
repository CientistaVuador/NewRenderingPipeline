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
import cientistavuador.newrenderingpipeline.util.CryptoUtils;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
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

    private static class WrappedVertexArrays {

        public int vao = 0;
    }

    private static class WrappedBuffer {

        public int buffer = 0;
    }

    private final String name;
    private final float[] vertices;
    private final int[] indices;
    private final String[] bones;

    private final boolean lightmapped;

    private final Vector3f aabbMin = new Vector3f();
    private final Vector3f aabbMax = new Vector3f();
    private final Vector3f aabbCenter = new Vector3f();

    private final WrappedVertexArrays wrappedVao = new WrappedVertexArrays();
    private final WrappedBuffer wrappedVbo = new WrappedBuffer();
    private final WrappedBuffer wrappedEbo = new WrappedBuffer();

    private final Vector3f meshColor = new Vector3f(1f, 0f, 1f);
    private final String sha256;

    private BVH bvh = null;

    public NMesh(
            String name,
            float[] vertices, int[] indices,
            String[] bones,
            Vector3fc min, Vector3fc max,
            String sha256
    ) {
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
            bones = new String[0];
        }
        if (bones.length > MAX_AMOUNT_OF_BONES) {
            throw new IllegalArgumentException("Max amount of bones per mesh is " + MAX_AMOUNT_OF_BONES);
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

        if (min == null || max == null) {
            MeshUtils.aabb(this.vertices,
                    NMesh.VERTEX_SIZE,
                    NMesh.OFFSET_POSITION_XYZ,
                    this.aabbMin, this.aabbMax, this.aabbCenter
            );
        } else {
            this.aabbMin.set(min);
            this.aabbMax.set(max);
            this.aabbCenter.set(this.aabbMin).add(this.aabbMax).mul(0.5f);
        }
        
        if (sha256 == null) {
            ByteBuffer data = ByteBuffer.allocate(
                    this.vertices.length * Float.BYTES
                    + this.indices.length * Integer.BYTES
            );
            
            for (float f:this.vertices) {
                data.putFloat(f);
            }
            
            for (int i:this.indices) {
                data.putInt(i);
            }
            
            this.sha256 = CryptoUtils.sha256(data.flip());
        } else {
            this.sha256 = sha256;
        }
        
        if (name == null) {
            this.name = this.sha256;
        } else {
            this.name = name;
        }
        
        calculateMeshColor();
        registerForCleaning();
    }

    public NMesh(String name, float[] vertices, int[] indices, String[] bones) {
        this(name, vertices, indices, bones, null, null, null);
    }
    
    public NMesh(String name, float[] vertices, int[] indices) {
        this(name, vertices, indices, null);
    }

    private void calculateMeshColor() {
        Random r = new Random();

        float hue = r.nextFloat();
        float saturation = r.nextFloat(0.50f) + 0.50f;
        float brightness = r.nextFloat(0.25f) + 0.75f;

        int argb = Color.HSBtoRGB(hue, saturation, brightness);
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = ((argb >> 0) & 0xFF) / 255f;

        this.meshColor.set(red, green, blue);
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

    public String getBone(int index) {
        return this.bones[index];
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
        if (this.bvh != null) {
            return;
        }
        this.bvh = BVH.create(this, this.vertices, this.indices, NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ);
    }

    public BVH getBVH() {
        return this.bvh;
    }

    public void setBVH(BVH bvh) {
        this.bvh = bvh;
    }

    public Vector3fc getMeshColor() {
        return meshColor;
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
                    StringUtils.truncateStringTo255Bytes("vao_" + meshId + "_" + this.name)
            );

            KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, vbo,
                    StringUtils.truncateStringTo255Bytes("vbo_" + meshId + "_" + this.name)
            );

            KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, ebo,
                    StringUtils.truncateStringTo255Bytes("ebo_" + meshId + "_" + this.name)
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
