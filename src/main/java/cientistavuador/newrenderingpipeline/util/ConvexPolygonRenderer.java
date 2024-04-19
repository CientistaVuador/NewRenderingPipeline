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

import java.util.ArrayList;
import static org.lwjgl.opengl.GL33C.*;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class ConvexPolygonRenderer {

    private static final int WHITE_TEXTURE;

    static {
        WHITE_TEXTURE = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, WHITE_TEXTURE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8, new int[]{0xFF_FF_FF_FF});

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            layout (location = 0) in vec2 vertexPosition;
            layout (location = 1) in vec2 vertexUV;
            layout (location = 2) in vec4 vertexColor;
            
            out vec2 outUV;
            out vec4 outColor;
            
            void main() {
                outColor = vertexColor;
                outUV = vertexUV;
                gl_Position = vec4(vertexPosition.x, vertexPosition.y, 0.0, 1.0);
            }
            """,
            """
            #version 330 core
            
            uniform sampler2D polyTexture;
            
            in vec2 outUV;
            in vec4 outColor;
            
            layout (location = 0) out vec4 fragColor;
            
            void main() {
                fragColor = outColor * texture2D(polyTexture, outUV);
            }
            """
    );
    
    private static final int UNIFORM_POLY_TEXTURE = glGetUniformLocation(SHADER_PROGRAM, "polyTexture");
    private static final int VERTEX_SIZE = 2 + 2 + 4;
    
    private static final int VAO;
    private static final int VBO;

    static {
        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);
        
        VBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        glBufferData(GL_ARRAY_BUFFER, 4, GL_STREAM_DRAW);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, 2 * Float.BYTES);

        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_SIZE * Float.BYTES, 4 * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        glBindVertexArray(0);
    }
    
    private static final Vector4fc DEFAULT_COLOR = new Vector4f(1f, 1f, 1f, 1f);

    public static void polyStaticInit() {

    }

    private static final ConvexPolygonRenderer INSTANCE = new ConvexPolygonRenderer();

    public static ConvexPolygonRenderer polyInstance() {
        return INSTANCE;
    }

    public static void polyBegin() {
        INSTANCE.clearState();
    }

    public static void polyTexture(int texture) {
        INSTANCE.setTexture(texture);
    }

    public static void polyTextureRotate(float rotation) {
        INSTANCE.getTextureTransformationMatrix().rotateZ((float) Math.toRadians(rotation));
    }

    public static void polyTextureTranslate(float x, float y) {
        INSTANCE.getTextureTransformationMatrix().translate(x, y, 0f);
    }

    public static void polyTextureScale(float x, float y) {
        INSTANCE.getTextureTransformationMatrix().scale(x, y, 0f);
    }

    public static void polyTextureIdentity() {
        INSTANCE.getTextureTransformationMatrix().identity();
    }

    public static void polyConvexColor(float r, float g, float b, float a) {
        INSTANCE.setConvexColor(r, g, b, a);
    }

    public static void polyConvexRotate(float rotation) {
        INSTANCE.getConvexTransformationMatrix().rotateZ((float) Math.toRadians(rotation));
    }

    public static void polyConvexTranslate(float x, float y) {
        INSTANCE.getConvexTransformationMatrix().translate(x, y, 0f);
    }

    public static void polyConvexScale(float x, float y) {
        INSTANCE.getConvexTransformationMatrix().scale(x, y, 0f);
    }

    public static void polyConvexIdentity() {
        INSTANCE.getConvexTransformationMatrix().identity();
    }

    public static void polyConvex(float... vertices) {
        INSTANCE.pushConvexPolygon(vertices);
    }

    public static void polyRotate(float rotation) {
        INSTANCE.getTransformationMatrix().rotateZ((float) Math.toRadians(rotation));
    }

    public static void polyTranslate(float x, float y) {
        INSTANCE.getTransformationMatrix().translate(x, y, 0f);
    }

    public static void polyScale(float x, float y) {
        INSTANCE.getTransformationMatrix().scale(x, y, 0f);
    }

    public static void polyIdentity() {
        INSTANCE.getTransformationMatrix().identity();
    }

    public static void polyColor(float r, float g, float b, float a) {
        INSTANCE.setPolygonColor(r, g, b, a);
    }
    
    public static boolean polyTestAabbPoint(float px, float py) {
        return INSTANCE.testAabbPoint(px, py);
    }
    
    public static void polyFinish() {
        INSTANCE.finish();
    }

    public static void polyDraw() {
        INSTANCE.draw();
    }

    private final Vector4f convexColor = new Vector4f(DEFAULT_COLOR);
    private final Vector4f polygonColor = new Vector4f(DEFAULT_COLOR);
    private int texture = 0;

    private float[] convexPolygonVertices = new float[64];
    private int convexPolygonVerticesIndex = 0;

    private int[] convexPolygonVerticesCount = new int[64];
    private int convexPolygonVerticesCountIndex = 0;

    private final Matrix4f transformationMatrix = new Matrix4f();
    private final Matrix4f textureTransformationMatrix = new Matrix4f();
    private final Matrix4f convexTransformationMatrix = new Matrix4f();
    private final Vector4f transformationVector = new Vector4f();

    private float maxX = Float.NEGATIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;
    private float minX = Float.POSITIVE_INFINITY;
    private float minY = Float.POSITIVE_INFINITY;

    private static class QueuedDraw {

        public final float[] verts;
        public final int[] count;
        public final int[] first;
        public final int texture;

        public QueuedDraw(float[] verts, int[] count, int[] first, int texture) {
            this.verts = verts;
            this.count = count;
            this.first = first;
            this.texture = texture;
        }
    }

    private final List<QueuedDraw> queuedDraws = new ArrayList<>();

    private ConvexPolygonRenderer() {

    }

    public Vector4fc getConvexColor() {
        return convexColor;
    }

    public void setConvexColor(float r, float g, float b, float a) {
        this.convexColor.set(r, g, b, a);
    }

    public void setConvexColor(Vector4fc color) {
        setConvexColor(color.x(), color.y(), color.z(), color.w());
    }

    public Vector4fc getPolygonColor() {
        return polygonColor;
    }

    public void setPolygonColor(float r, float g, float b, float a) {
        this.polygonColor.set(r, g, b, a);
    }

    public void setPolygonColor(Vector4fc color) {
        setPolygonColor(color.x(), color.y(), color.z(), color.w());
    }

    public int getTexture() {
        return texture;
    }

    public void setTexture(int texture) {
        this.texture = texture;
    }

    public Matrix4f getTransformationMatrix() {
        return transformationMatrix;
    }

    public Matrix4f getTextureTransformationMatrix() {
        return textureTransformationMatrix;
    }

    public Matrix4f getConvexTransformationMatrix() {
        return convexTransformationMatrix;
    }

    public void clearState() {
        this.convexColor.set(DEFAULT_COLOR);
        this.polygonColor.set(DEFAULT_COLOR);
        this.transformationMatrix.identity();
        this.textureTransformationMatrix.identity();

        this.texture = 0;
        this.textureTransformationMatrix.identity();

        this.convexTransformationMatrix.identity();

        this.convexPolygonVerticesIndex = 0;
        this.convexPolygonVerticesCountIndex = 0;

        this.maxX = Float.NEGATIVE_INFINITY;
        this.maxY = Float.NEGATIVE_INFINITY;
        this.minX = Float.POSITIVE_INFINITY;
        this.minY = Float.POSITIVE_INFINITY;
    }

    public void pushConvexPolygon(float... points) {
        if (points.length % 2 != 0) {
            throw new RuntimeException("Points must be an even number.");
        }

        int amountOfVertices = points.length / 2;

        float[] polygonVerts = new float[amountOfVertices * VERTEX_SIZE];

        for (int i = 0; i < polygonVerts.length; i += VERTEX_SIZE) {
            float x = points[((i / VERTEX_SIZE) * 2) + 0];
            float y = points[((i / VERTEX_SIZE) * 2) + 1];
            float r = this.convexColor.x();
            float g = this.convexColor.y();
            float b = this.convexColor.z();
            float a = this.convexColor.w();

            this.transformationVector.set(x, y, 0f, 1f);
            this.convexTransformationMatrix.transformProject(this.transformationVector);

            x = this.transformationVector.x();
            y = this.transformationVector.y();

            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);

            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);

            polygonVerts[i + 0] = x;
            polygonVerts[i + 1] = y;
            polygonVerts[i + 2] = 0f;
            polygonVerts[i + 3] = 0f;
            polygonVerts[i + 4] = r;
            polygonVerts[i + 5] = g;
            polygonVerts[i + 6] = b;
            polygonVerts[i + 7] = a;
        }

        if ((this.convexPolygonVerticesIndex + polygonVerts.length) > this.convexPolygonVertices.length) {
            this.convexPolygonVertices = Arrays.copyOf(this.convexPolygonVertices, (this.convexPolygonVertices.length * 2) + polygonVerts.length);
        }

        System.arraycopy(
                polygonVerts, 0,
                this.convexPolygonVertices, this.convexPolygonVerticesIndex,
                polygonVerts.length
        );
        this.convexPolygonVerticesIndex += polygonVerts.length;

        if (this.convexPolygonVerticesCountIndex >= this.convexPolygonVerticesCount.length) {
            this.convexPolygonVerticesCount = Arrays.copyOf(this.convexPolygonVerticesCount, (this.convexPolygonVerticesCount.length * 2) + 1);
        }

        this.convexPolygonVerticesCount[this.convexPolygonVerticesCountIndex] = amountOfVertices;
        this.convexPolygonVerticesCountIndex++;
    }
    
    public boolean testAabbPoint(float px, float py) {
        this.transformationVector.set(this.maxX, this.maxY, 0f);
        this.transformationMatrix.transformProject(this.transformationVector);
        
        float ax = this.transformationVector.x();
        float ay = this.transformationVector.y();
        
        this.transformationVector.set(this.minX, this.minY, 0f);
        this.transformationMatrix.transformProject(this.transformationVector);
        
        float bx = this.transformationVector.x();
        float by = this.transformationVector.y();
        
        float tmaxX = Math.max(ax, bx);
        float tmaxY = Math.max(ay, by);
        
        float tminX = Math.min(ax, bx);
        float tminY = Math.min(ay, by);
        
        return !(px < tminX || px > tmaxX || py < tminY || py > tmaxY);
    }
    
    public void finish() {
        float[] verts = Arrays.copyOf(this.convexPolygonVertices, this.convexPolygonVerticesIndex);

        float quadWidth = this.maxX - this.minX;
        float quadHeight = this.maxY - this.minY;

        for (int i = 0; i < verts.length; i += VERTEX_SIZE) {
            float x = verts[i + 0];
            float y = verts[i + 1];
            //i + 2
            //i + 3
            float r = verts[i + 4];
            float g = verts[i + 5];
            float b = verts[i + 6];
            float a = verts[i + 7];

            r *= this.polygonColor.x();
            g *= this.polygonColor.y();
            b *= this.polygonColor.z();
            a *= this.polygonColor.w();

            float u = (x - this.minX) / quadWidth;
            float v = (y - this.minY) / quadHeight;

            this.transformationVector.set(u, v, 0f, 1f);
            this.textureTransformationMatrix.transformProject(this.transformationVector);

            u = this.transformationVector.x();
            v = this.transformationVector.y();

            this.transformationVector.set(x, y, 0f, 1f);
            this.transformationMatrix.transformProject(this.transformationVector);

            x = this.transformationVector.x();
            y = this.transformationVector.y();

            verts[i + 0] = x;
            verts[i + 1] = y;
            verts[i + 2] = u;
            verts[i + 3] = v;
            verts[i + 4] = r;
            verts[i + 5] = g;
            verts[i + 6] = b;
            verts[i + 7] = a;
        }

        int[] count = Arrays.copyOf(this.convexPolygonVerticesCount, this.convexPolygonVerticesCountIndex);
        int[] first = new int[count.length];

        int firstCount = 0;
        for (int i = 0; i < count.length; i++) {
            first[i] = firstCount;
            firstCount += count[i];
        }

        int tex = this.texture;
        if (tex <= 0) {
            tex = WHITE_TEXTURE;
        }

        if (!this.queuedDraws.isEmpty() && this.queuedDraws.get(this.queuedDraws.size() - 1).texture == tex) {
            int lastIndex = this.queuedDraws.size() - 1;
            QueuedDraw drawAt = this.queuedDraws.get(lastIndex);

            int drawAtAmountOfVertices = drawAt.verts.length / VERTEX_SIZE;
            for (int i = 0; i < first.length; i++) {
                first[i] = drawAtAmountOfVertices + first[i];
            }

            float[] newVerts = new float[drawAt.verts.length + verts.length];
            int[] newCount = new int[drawAt.count.length + count.length];
            int[] newFirst = new int[drawAt.first.length + first.length];

            System.arraycopy(drawAt.verts, 0, newVerts, 0, drawAt.verts.length);
            System.arraycopy(drawAt.count, 0, newCount, 0, drawAt.count.length);
            System.arraycopy(drawAt.first, 0, newFirst, 0, drawAt.first.length);

            System.arraycopy(verts, 0, newVerts, drawAt.verts.length, verts.length);
            System.arraycopy(count, 0, newCount, drawAt.count.length, count.length);
            System.arraycopy(first, 0, newFirst, drawAt.first.length, first.length);

            this.queuedDraws.set(this.queuedDraws.size() - 1, new QueuedDraw(newVerts, newCount, newFirst, tex));
        } else {
            this.queuedDraws.add(new QueuedDraw(verts, count, first, tex));
        }
    }

    public void draw() {
        QueuedDraw[] draws = this.queuedDraws.toArray(QueuedDraw[]::new);
        this.queuedDraws.clear();
        
        if (draws.length == 0) {
            return;
        }

        glUseProgram(SHADER_PROGRAM);
        glDisable(GL_DEPTH_TEST);
        glBindVertexArray(VAO);
        
        for (QueuedDraw e : draws) {
            glBufferData(GL_ARRAY_BUFFER, e.verts, GL_STREAM_DRAW);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, e.texture);
            glUniform1i(UNIFORM_POLY_TEXTURE, 0);

            glMultiDrawArrays(GL_TRIANGLE_FAN, e.first, e.count);
        }
        
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(0);
    }

}
