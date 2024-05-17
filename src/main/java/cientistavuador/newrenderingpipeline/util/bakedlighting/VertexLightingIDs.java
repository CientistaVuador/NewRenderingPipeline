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

import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class VertexLightingIDs {
    
    public static int generate(
            float[] vertices, int vertexSize,
            int xyzOffset, int outVertexLightingIdOffset
    ) {
        if (vertices.length % vertexSize != 0) {
            throw new IllegalArgumentException("Invalid amount of vertices!");
        }
        
        Map<Vector3f, Integer> positionMap = new HashMap<>();

        int indexCounter = 0;
        for (int i = 0; i < vertices.length; i += vertexSize) {
            Vector3f position = new Vector3f(
                    vertices[i + xyzOffset + 0],
                    vertices[i + xyzOffset + 1],
                    vertices[i + xyzOffset + 2]
            );

            Integer currentIndex = positionMap.get(position);
            if (currentIndex == null) {
                currentIndex = indexCounter;
                indexCounter++;
                
                positionMap.put(position, currentIndex);
            }
            
            vertices[i + outVertexLightingIdOffset + 0] = Float.intBitsToFloat(currentIndex);
        }

        return indexCounter;
    }

    private VertexLightingIDs() {

    }

}
