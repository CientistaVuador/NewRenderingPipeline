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

import java.util.ArrayList;
import java.util.List;
import org.joml.Intersectiond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NCubemapBVH {
    
    public static NCubemapBVH create(List<NCubemap> cubemaps) {
        List<NCubemapBVH> current = new ArrayList<>();
        
        for (NCubemap cubemap : cubemaps) {
            if (!cubemap.getCubemapInfo().isParallaxCorrected()) {
                throw new IllegalArgumentException("Cubemap is not parallax corrected, " + cubemap.getName() + ", " + cubemap.getUid());
            }
            
            current.add(new NCubemapBVH(
                    cubemap.getCubemapInfo().getMin(),
                    cubemap.getCubemapInfo().getMax(),
                    cubemap,
                    null, null
            ));
        }
        
        if (current.isEmpty()) {
            return new NCubemapBVH(null, null, null, null, null);
        }
        
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        
        List<NCubemapBVH> next = new ArrayList<>();
        
        while (current.size() > 1) {
            for (int i = 0; i < current.size(); i++) {
                NCubemapBVH currentBVH = current.get(i);
                
                if (currentBVH == null) {
                    continue;
                }
                
                int closestIndex = -1;
                double closestDistance = Double.POSITIVE_INFINITY;
                for (int j = (i + 1); j < current.size(); j++) {
                    NCubemapBVH otherBVH = current.get(j);
                    
                    if (otherBVH == null) {
                        continue;
                    }
                    
                    double distance = otherBVH.getCenter().distanceSquared(currentBVH.getCenter());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestIndex = j;
                    }
                }
                
                if (closestIndex == -1) {
                    next.add(currentBVH);
                    break;
                }
                
                NCubemapBVH otherBVH = current.get(closestIndex);
                
                current.set(i, null);
                current.set(closestIndex, null);
                
                Vector3dc cmin = currentBVH.getMin();
                Vector3dc cmax = currentBVH.getMax();
                Vector3dc tmin = otherBVH.getMin();
                Vector3dc tmax = otherBVH.getMax();
                
                min.set(
                        Math.min(cmin.x(), tmin.x()),
                        Math.min(cmin.y(), tmin.y()),
                        Math.min(cmin.z(), tmin.z())
                );
                max.set(
                        Math.max(cmax.x(), tmax.x()),
                        Math.max(cmax.y(), tmax.y()),
                        Math.max(cmax.z(), tmax.z())
                );
                
                NCubemapBVH merge = new NCubemapBVH(min, max, null, currentBVH, otherBVH);
                next.add(merge);
            }
            
            current = next;
            next = new ArrayList<>();
        }
        
        return current.get(0);
    }

    private final Vector3d min = new Vector3d(0f);
    private final Vector3d max = new Vector3d(0f);
    private final Vector3d center = new Vector3d(0f);
    private final NCubemap cubemap;
    private final NCubemapBVH left;
    private final NCubemapBVH right;

    private NCubemapBVH(
            Vector3dc min,
            Vector3dc max,
            NCubemap cubemap,
            NCubemapBVH left,
            NCubemapBVH right
    ) {
        if (min != null && max != null) {
            this.min.set(min);
            this.max.set(max);
            this.center.set(this.min).add(this.max).mul(0.5f);
        }
        this.cubemap = cubemap;
        this.left = left;
        this.right = right;
    }

    public Vector3dc getMin() {
        return min;
    }

    public Vector3dc getMax() {
        return max;
    }

    public Vector3dc getCenter() {
        return center;
    }

    public NCubemap getCubemap() {
        return cubemap;
    }

    public NCubemapBVH getLeft() {
        return left;
    }

    public NCubemapBVH getRight() {
        return right;
    }
    
    private void testAab(NCubemapBVH bvh,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            List<NCubemap> results) {
        if (bvh == null) {
            return;
        }
        if (Intersectiond.testAabAab(
                minX, minY, minZ,
                maxX, maxY, maxZ,
                bvh.getMin().x(), bvh.getMin().y(), bvh.getMin().z(),
                bvh.getMax().x(), bvh.getMax().y(), bvh.getMax().z()
        )) {
            if (bvh.getCubemap() != null) {
                results.add(bvh.getCubemap());
            }
            
            testAab(
                    bvh.getLeft(),
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    results
            );
            testAab(
                    bvh.getRight(),
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    results
            );
        }
    }
    
    public List<NCubemap> testAab(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        List<NCubemap> cubemaps = new ArrayList<>();
        testAab(this,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                cubemaps
        );
        return cubemaps;
    }
    
    public List<NCubemap> testAab(Vector3dc min, Vector3dc max) {
        return testAab(
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z()
        );
    }
    
    public List<NCubemap> testRelativeAab(Vector3dc cameraPosition, Vector3fc min, Vector3fc max) {
        return testAab(
                cameraPosition.x() + min.x(), cameraPosition.y() + min.y(), cameraPosition.z() + min.z(),
                cameraPosition.x() + max.x(), cameraPosition.y() + max.y(), cameraPosition.z() + max.z()
        );
    }
}
