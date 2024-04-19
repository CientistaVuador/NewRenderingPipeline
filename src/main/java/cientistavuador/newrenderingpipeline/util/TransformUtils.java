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

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class TransformUtils {
    public static void transformAabb(
            Vector3fc min, Vector3fc max,
            Matrix4fc transformation,
            Vector3f outMin, Vector3f outMax
    ) {
        outMin.set(min);
        outMax.set(max);
        
        transformation.transformProject(outMin);
        transformation.transformProject(outMax);
        
        float minX = Float.min(outMin.x(), outMax.x());
        float minY = Float.min(outMin.y(), outMax.y());
        float minZ = Float.min(outMin.z(), outMax.z());
        float maxX = Float.max(outMin.x(), outMax.x());
        float maxY = Float.max(outMin.y(), outMax.y());
        float maxZ = Float.max(outMin.z(), outMax.z());
        
        outMin.set(minX, minY, minZ);
        outMax.set(maxX, maxY, maxZ);
    }
    
    private TransformUtils() {
        
    }
}
