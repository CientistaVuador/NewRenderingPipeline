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
package cientistavuador.newrenderingpipeline.util.bakedlighting;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 *
 * @author Cien
 */
public class LightmapQuad {

    private final int index;
    private final float width;
    private final float height;
    private final Matrix4f meshToQuad;
    private final Matrix4f quadToMesh;
    
    public LightmapQuad(int index, float width, float height, Matrix4fc meshToQuad) {
        this.index = index;
        this.width = width;
        this.height = height;
        this.meshToQuad = new Matrix4f(meshToQuad);
        this.quadToMesh = new Matrix4f(meshToQuad).invert();
    }

    public int getIndex() {
        return index;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Matrix4fc getMeshToQuad() {
        return meshToQuad;
    }

    public Matrix4fc getQuadToMesh() {
        return quadToMesh;
    }
    
}
