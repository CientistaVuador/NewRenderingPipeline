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

import cientistavuador.newrenderingpipeline.util.raycast.LocalRayResult;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NRayResult {
    
    private final Vector3d origin = new Vector3d();
    private final Vector3f direction = new Vector3f();
    private final Vector3d hitPosition = new Vector3d();
    private final double distance;
    private final N3DObject object;
    private final NGeometry geometry;
    private final LocalRayResult localRay;

    public NRayResult(Vector3dc origin, Vector3fc direction, Vector3dc hitPosition, N3DObject object, NGeometry geometry, LocalRayResult localRay) {
        if (origin != null) {
            this.origin.set(origin);
        }
        if (direction != null) {
            this.direction.set(direction);
        }
        if (hitPosition != null) {
            this.hitPosition.set(hitPosition);
        }
        this.distance = this.origin.distance(this.hitPosition);
        this.object = object;
        this.geometry = geometry;
        this.localRay = localRay;
    }

    public Vector3dc getOrigin() {
        return origin;
    }

    public Vector3fc getDirection() {
        return direction;
    }

    public Vector3dc getHitPosition() {
        return hitPosition;
    }

    public double getDistance() {
        return distance;
    }

    public N3DObject getObject() {
        return object;
    }

    public NGeometry getGeometry() {
        return geometry;
    }

    public LocalRayResult getLocalRay() {
        return localRay;
    }
    
}
