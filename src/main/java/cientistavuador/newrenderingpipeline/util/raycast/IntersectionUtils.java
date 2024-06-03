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
package cientistavuador.newrenderingpipeline.util.raycast;

import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class IntersectionUtils {
    
    public static boolean testAabAab(Vector3fc minA, Vector3fc maxA, Vector3fc minB, Vector3fc maxB) {
        return Intersectionf.testAabAab(minA, maxA, minB, maxB);
    }
    
    public static boolean testRayAab(Vector3fc origin, Vector3fc dir, Vector3fc min, Vector3fc max) {
        return Intersectionf.testRayAab(origin, dir, min, max);
    }

    public static float intersectRayTriangle(Vector3fc origin, Vector3fc dir, Vector3fc a, Vector3fc b, Vector3fc c) {
        return Intersectionf.intersectRayTriangle(origin, dir, a, b, c, 1f / 100000f);
    }
    
    public static boolean lineSegmentLineSegment(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, Vector2f p) {
        float denom = 1f / ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));
        float t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) * denom;
        float u = -(((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) * denom);
        if (t >= 0f && t <= 1f && u >= 0f && u <= 1f) {
            p.set(
                    x1 + t * (x2 - x1),
                    y1 + t * (y2 - y1)
            );
            return true;
        }
        return false;
    }
    
    private IntersectionUtils() {

    }
}
