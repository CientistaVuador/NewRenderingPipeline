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
import java.nio.FloatBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class GPUOcclusion {

    private static final int VAO;
    private static final int COUNT;
    
    public static final float MARGIN = 0.02f;
    
    public static final float SMALL_CUBE_SCALE = 1f - MARGIN;
    public static final float LARGE_CUBE_SCALE = 1f + MARGIN;
    
    static {
        final float sa = SMALL_CUBE_SCALE;
        final float sb = LARGE_CUBE_SCALE;
        
        float[] vertices = {
            -1f * sa, -1f * sa, -1f * sa,
            1f * sa, -1f * sa, -1f * sa,
            -1f * sa, -1f * sa, 1f * sa,
            1f * sa, -1f * sa, 1f * sa,
            -1f * sa, 1f * sa, -1f * sa,
            1f * sa, 1f * sa, -1f * sa,
            -1f * sa, 1f * sa, 1f * sa,
            1f * sa, 1f * sa, 1f * sa,
            
            -1f, -1f, -1f,
            1f, -1f, -1f,
            -1f, -1f, 1f,
            1f, -1f, 1f,
            -1f, 1f, -1f,
            1f, 1f, -1f,
            -1f, 1f, 1f,
            1f, 1f, 1f,
            
            -1f * sb, -1f * sb, -1f * sb,
            1f * sb, -1f * sb, -1f * sb,
            -1f * sb, -1f * sb, 1f * sb,
            1f * sb, -1f * sb, 1f * sb,
            -1f * sb, 1f * sb, -1f * sb,
            1f * sb, 1f * sb, -1f * sb,
            -1f * sb, 1f * sb, 1f * sb,
            1f * sb, 1f * sb, 1f * sb,
        };
        int[] indices = {
            0, 5, 1, 0, 4, 5,
            2, 3, 7, 2, 7, 6,
            0, 2, 6, 0, 6, 4,
            1, 7, 3, 1, 5, 7,
            4, 6, 5, 5, 6, 7,
            0, 3, 2, 0, 1, 3,
            8, 13, 9, 8, 12, 13,
            10, 11, 15, 10, 15, 14,
            8, 10, 14, 8, 14, 12,
            9, 15, 11, 9, 13, 15,
            12, 14, 13, 13, 14, 15,
            8, 11, 10, 8, 9, 11,
            16, 21, 17, 16, 20, 21,
            18, 19, 23, 18, 23, 22,
            16, 18, 22, 16, 22, 20,
            17, 23, 19, 17, 21, 23,
            20, 22, 21, 21, 22, 23,
            16, 19, 18, 16, 17, 19
        };
        
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        glBindVertexArray(0);
        
        VAO = vao;
        COUNT = indices.length;
    }

    private static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            uniform mat4 projectionViewModel;
            
            layout (location = 0) in vec3 vertexPosition;
            
            void main() {
                gl_Position = projectionViewModel * vec4(vertexPosition, 1.0);
            }
            """,
            """
            #version 330 core
            
            layout (location = 0) out vec4 fragColor;
            
            void main() {
                fragColor = vec4(1.0);
            }
            """
    );

    private static final int UNIFORM_PROJECTION_VIEW_MODEL = glGetUniformLocation(SHADER_PROGRAM, "projectionViewModel");
    
    public static void init() {

    }

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    
    public static boolean testAabPoint(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float px, float py, float pz
    ) {
        return !(px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ);
    }
    
    public static boolean testCamera(
            float camx, float camy, float camz,
            float nearPlaneMargin,
            float x, float y, float z,
            float width, float height, float depth
    ) {
        float minX = -1f * LARGE_CUBE_SCALE * width * 0.5f;
        float minY = -1f * LARGE_CUBE_SCALE * height * 0.5f;
        float minZ = -1f * LARGE_CUBE_SCALE * depth * 0.5f;
        
        minX += x;
        minY += y;
        minZ += z;
        
        float maxX = 1f * LARGE_CUBE_SCALE * width * 0.5f;
        float maxY = 1f * LARGE_CUBE_SCALE * height * 0.5f;
        float maxZ = 1f * LARGE_CUBE_SCALE * depth * 0.5f;
        
        maxX += x;
        maxY += y;
        maxZ += z;
        
        return testAabPoint(
                minX, minY, minZ,
                maxX, maxY, maxZ,
                camx, camy, camz
        );
    }
    
    public static void occlusionQuery(
            Matrix4fc projection,
            Matrix4fc view,
            float x, float y, float z,
            float width, float height, float depth,
            final int queryObject
    ) {
        Matrix4f model = new Matrix4f()
                .translate(x, y, z)
                .scale(width * 0.5f, height * 0.5f, depth * 0.5f)
                ;
        final Matrix4f projectionViewModel = new Matrix4f()
                .set(projection)
                .mul(view)
                .mul(model);
        TASKS.add(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matrixData = stack.mallocFloat(4 * 4);
                projectionViewModel.get(matrixData);
                glUniformMatrix4fv(UNIFORM_PROJECTION_VIEW_MODEL, false, matrixData);
            }
            
            glBeginQuery(GL_SAMPLES_PASSED, queryObject);
            glDrawElements(GL_TRIANGLES, COUNT, GL_UNSIGNED_INT, 0);
            glEndQuery(GL_SAMPLES_PASSED);
            
            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += COUNT;
        });
    }

    public static void executeQueries() {
        glUseProgram(SHADER_PROGRAM);

        glColorMask(false, false, false, false);
        glDepthMask(false);
        
        glBindVertexArray(VAO);
        
        Runnable r;
        while ((r = TASKS.poll()) != null) {
            r.run();
        }
        
        glBindVertexArray(0);

        glColorMask(true, true, true, true);
        glDepthMask(true);

        glUseProgram(0);
    }

    private GPUOcclusion() {

    }
}
