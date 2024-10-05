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
package cientistavuador.newrenderingpipeline.util;

import cientistavuador.newrenderingpipeline.Main;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class DebugRenderer {

    public static void init() {
        
    }
    
    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            layout (location = 0) in vec4 vertexPosition;
            layout (location = 1) in vec3 vertexNormal;
            layout (location = 2) in vec3 vertexColor;
            
            out vec3 fragColor;
            
            void main() {
                fragColor = (0.50 + (abs(dot(normalize(vertexNormal), -normalize(vec3(1.0, -1.0, 1.0)))) * 0.50)) * vertexColor;
                gl_Position = vertexPosition;
            }
            """,
            """
            #version 330 core
            
            in vec3 fragColor;
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                outputColor = vec4(fragColor, 1.0);
            }
            """
    );

    public static final int OFFSET_POSITION = 0;
    public static final int OFFSET_NORMAL = OFFSET_POSITION + 4;
    public static final int OFFSET_COLOR = OFFSET_NORMAL + 3;

    public static final int VERTEX_SIZE = OFFSET_COLOR + 3;

    private static final ConcurrentLinkedQueue<CompletableFuture<FloatBuffer>> renderQueue = new ConcurrentLinkedQueue<>();
    
    private static int vboA;
    private static int vboB;
    
    private static int vaoA;
    private static int vaoB;
    
    private static int createVBO() {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 64, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return vbo;
    }
    
    private static int createVAO(int vbo) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_POSITION * Float.BYTES);
        
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_NORMAL * Float.BYTES);
        
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, OFFSET_COLOR * Float.BYTES);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        glBindVertexArray(0);
        return vao;
    }
    
    static {
        vboA = createVBO();
        vboB = createVBO();
        vaoA = createVAO(vboA);
        vaoB = createVAO(vboB);
    }
    
    private static void flipBuffers() {
        int vao = vaoA;
        int vbo = vboA;
        vaoA = vaoB;
        vboA = vboB;
        vaoB = vao;
        vboB = vbo;
    }
    
    public static class VertexStream {

        private final Matrix4fc projection;
        private final Matrix4fc view;
        private final Matrix4fc model;
        private final float red;
        private final float green;
        private final float blue;

        private float[] vertices = new float[64];
        private int verticesIndex = 0;
        
        private boolean done = false;
        
        private VertexStream(
                Matrix4fc projection,
                Matrix4fc view,
                Matrix4fc model,
                float red, float green, float blue
        ) {
            Objects.requireNonNull(projection, "Projection is null.");
            Objects.requireNonNull(view, "View is null.");
            Objects.requireNonNull(model, "Model is null.");
            this.projection = new Matrix4f(projection);
            this.view = new Matrix4f(view);
            this.model = new Matrix4f(model);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public Matrix4fc getProjection() {
            return projection;
        }

        public Matrix4fc getView() {
            return view;
        }

        public Matrix4fc getModel() {
            return model;
        }

        public float getRed() {
            return red;
        }

        public float getGreen() {
            return green;
        }

        public float getBlue() {
            return blue;
        }

        public VertexStream push(float x, float y, float z) {
            if (this.done) {
                throw new IllegalStateException("Stream is done.");
            }
            if ((this.verticesIndex + 3) > this.vertices.length) {
                this.vertices = Arrays.copyOf(this.vertices, (this.vertices.length * 2) + 3);
            }
            this.vertices[this.verticesIndex + 0] = x;
            this.vertices[this.verticesIndex + 1] = y;
            this.vertices[this.verticesIndex + 2] = z;
            this.verticesIndex += 3;
            return this;
        }
        
        public VertexStream pushMesh(float[] vertices, int[] indices, int vertexSize, int xyzOffset) {
            for (int i = 0; i < indices.length; i++) {
                int index = indices[i];
                int offset = (index * vertexSize) + xyzOffset;
                push(
                        vertices[offset + 0],
                        vertices[offset + 1],
                        vertices[offset + 2]
                );
            }
            return this;
        }
        
        public void end() {
            if (this.done) {
                throw new IllegalStateException("Stream is done.");
            }
            if (this.verticesIndex % 3 != 0) {
                throw new IllegalArgumentException("Not triangulated.");
            }
            final float[] verticesReference = this.vertices;
            final int verticesAmount = this.verticesIndex;
            this.done = true;
            renderQueue.add(CompletableFuture.supplyAsync(() -> {
                int components = VERTEX_SIZE * (verticesAmount / 3);
                FloatBuffer memory = memAllocFloat(components);
                try {
                    Vector3f recycled = new Vector3f();
                    for (int i = 0; i < components; i += VERTEX_SIZE) {
                        int offset = (i / VERTEX_SIZE) * 3;
                        recycled.set(
                                verticesReference[offset + 0],
                                verticesReference[offset + 1],
                                verticesReference[offset + 2]
                        );
                        this.model.transformPosition(recycled);
                        memory
                                .put(recycled.x()).put(recycled.y()).put(recycled.z()).put(1f)
                                .put(0f).put(0f).put(0f)
                                .put(this.red).put(this.green).put(this.blue)
                                ;
                    }
                    memory.flip();
                    
                    Vector3f v0 = new Vector3f();
                    Vector3f v1 = new Vector3f();
                    Vector3f v2 = new Vector3f();
                    for (int i = 0; i < components; i += (VERTEX_SIZE * 3)) {
                        v0.set(i + (VERTEX_SIZE * 0), memory);
                        v1.set(i + (VERTEX_SIZE * 1), memory);
                        v2.set(i + (VERTEX_SIZE * 2), memory);
                        v1.sub(v0).normalize();
                        v2.sub(v0).normalize();
                        v0.set(v1).cross(v2).normalize();
                        v0.get(i + (VERTEX_SIZE * 0) + OFFSET_NORMAL, memory);
                        v0.get(i + (VERTEX_SIZE * 1) + OFFSET_NORMAL, memory);
                        v0.get(i + (VERTEX_SIZE * 2) + OFFSET_NORMAL, memory);
                    }
                    
                    Matrix4f projectionView = new Matrix4f(this.projection).mul(this.view);
                    Vector4f position = new Vector4f();
                    for (int i = 0; i < components; i += VERTEX_SIZE) {
                        position.set(i, memory);
                        projectionView.transform(position);
                        position.get(i, memory);
                    }
                    
                    return memory;
                } catch (Throwable t) {
                    memFree(memory);
                    throw t;
                }
            }));
        }
    }
    
    public static VertexStream begin(
                Matrix4fc projection,
                Matrix4fc view,
                Matrix4fc model,
                float red, float green, float blue
    ) {
        return new VertexStream(projection, view, model, red, green, blue);
    }
    
    public static void render() {
        List<FloatBuffer> list = new ArrayList<>();
        try {
            int size = 0;
            CompletableFuture<FloatBuffer> cf;
            while ((cf = renderQueue.poll()) != null) {
                FloatBuffer e = cf.join();
                list.add(e);
                size += e.limit();
            }
            if (size == 0) {
                return;
            }
            
            glBindBuffer(GL_ARRAY_BUFFER, vboA);
            glBufferData(GL_ARRAY_BUFFER, size * Float.BYTES, GL_STREAM_DRAW);
            ByteBuffer bufferData = glMapBufferRange(GL_ARRAY_BUFFER, 0, size * Float.BYTES, GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
            Objects.requireNonNull(bufferData, "Buffer is null.");
            FloatBuffer buffer = bufferData.asFloatBuffer();
            for (FloatBuffer verts:list) {
                buffer.put(verts);
                verts.flip();
            }
            buffer.flip();
            if (!glUnmapBuffer(GL_ARRAY_BUFFER)) {
                throw new RuntimeException("Corrupted buffer.");
            }
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            
            glUseProgram(SHADER_PROGRAM);
            glBindVertexArray(vaoA);
            glDrawArrays(GL_TRIANGLES, 0, size / VERTEX_SIZE);
            glBindVertexArray(0);
            glUseProgram(0);
            
            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += size / VERTEX_SIZE;
            
            flipBuffers();
        } finally {
            for (FloatBuffer e:list) {
                memFree(e);
            }
        }
    }
    
    private DebugRenderer() {

    }
}
