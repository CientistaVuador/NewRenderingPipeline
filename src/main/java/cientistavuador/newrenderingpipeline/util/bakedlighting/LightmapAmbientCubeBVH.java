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

import java.util.ArrayList;
import java.util.List;
import org.joml.Intersectionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class LightmapAmbientCubeBVH {

    public static LightmapAmbientCubeBVH create(List<LightmapAmbientCube> ambientCubes) {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        
        int lightmaps = 0;
        if (!ambientCubes.isEmpty()) {
            lightmaps = ambientCubes.get(0).getNumberOfAmbientCubes();
        }
        
        List<LightmapAmbientCubeBVH> current = new ArrayList<>();
        
        for (int i = 0; i < ambientCubes.size(); i++) {
            LightmapAmbientCube ambientCube = ambientCubes.get(i);
            
            if (ambientCube.getNumberOfAmbientCubes() != lightmaps) {
                throw new IllegalArgumentException("Ambient cube at index "+i+" has a invalid amount of lightmaps!");
            }
            
            float radius = ambientCube.getRadius();

            min.set(ambientCube.getPosition()).sub(radius, radius, radius);
            max.set(ambientCube.getPosition()).add(radius, radius, radius);

            current.add(new LightmapAmbientCubeBVH(
                    ambientCubes,
                    radius,
                    min,
                    max,
                    i,
                    null, null
            ));
        }
        
        if (current.isEmpty()) {
            return new LightmapAmbientCubeBVH(ambientCubes, 0f, null, null, -1, null, null);
        }

        List<LightmapAmbientCubeBVH> next = new ArrayList<>();

        while (current.size() > 1) {
            for (int i = 0; i < current.size(); i++) {
                LightmapAmbientCubeBVH currentBVH = current.get(i);

                if (currentBVH == null) {
                    continue;
                }

                int closestIndex = -1;
                double closestDistance = Double.POSITIVE_INFINITY;
                for (int j = (i + 1); j < current.size(); j++) {
                    LightmapAmbientCubeBVH otherBVH = current.get(j);

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

                LightmapAmbientCubeBVH otherBVH = current.get(closestIndex);

                current.set(i, null);
                current.set(closestIndex, null);

                Vector3fc cmin = currentBVH.getMin();
                Vector3fc cmax = currentBVH.getMax();
                Vector3fc tmin = otherBVH.getMin();
                Vector3fc tmax = otherBVH.getMax();

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
                
                
                LightmapAmbientCubeBVH merge = new LightmapAmbientCubeBVH(
                        ambientCubes,
                        (currentBVH.getAverageRadius() + otherBVH.getAverageRadius()) * 0.5f,
                        min, max,
                        -1,
                        currentBVH, otherBVH
                );
                next.add(merge);
            }

            current = next;
            next = new ArrayList<>();
        }

        return current.get(0);
    }
    
    private final List<LightmapAmbientCube> ambientCubes;
    private final float averageRadius;
    
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();
    private final Vector3f center = new Vector3f();

    private final int ambientCubeIndex;
    
    private final LightmapAmbientCubeBVH left;
    private final LightmapAmbientCubeBVH right;

    public LightmapAmbientCubeBVH(
            List<LightmapAmbientCube> ambientCubes,
            float averageRadius,
            Vector3fc min, Vector3fc max,
            int ambientCubeIndex,
            LightmapAmbientCubeBVH left, LightmapAmbientCubeBVH right
    ) {
        this.ambientCubes = ambientCubes;
        this.averageRadius = averageRadius;
        if (min != null && max != null) {
            this.min.set(min);
            this.max.set(max);
        }
        this.center.set(this.max).add(this.min).mul(0.5f);
        this.ambientCubeIndex = ambientCubeIndex;
        this.left = left;
        this.right = right;
    }

    public List<LightmapAmbientCube> getAmbientCubes() {
        return ambientCubes;
    }

    public float getAverageRadius() {
        return averageRadius;
    }

    public Vector3fc getMin() {
        return min;
    }

    public Vector3fc getMax() {
        return max;
    }

    public Vector3fc getCenter() {
        return center;
    }
    
    public int getAmbientCubeIndex() {
        return ambientCubeIndex;
    }
    
    public LightmapAmbientCubeBVH getLeft() {
        return left;
    }

    public LightmapAmbientCubeBVH getRight() {
        return right;
    }
    
    private void search(
            LightmapAmbientCubeBVH bvh,
            List<LightmapAmbientCube> list, 
            float x, float y, float z,
            float searchRadius
    ) {
        if (bvh == null) {
            return;
        }
        if (!Intersectionf.testAabSphere(
                bvh.getMin().x(), bvh.getMin().y(), bvh.getMin().z(),
                bvh.getMax().x(), bvh.getMax().y(), bvh.getMax().z(),
                x, y, z,
                searchRadius * searchRadius
        )) {
            return;
        }
        
        int cubeIndex = bvh.getAmbientCubeIndex();
        if (cubeIndex >= 0) {
            LightmapAmbientCube cube = bvh.getAmbientCubes().get(cubeIndex);
            
            Vector3fc cubePos = cube.getPosition();
            float cubeRadius = cube.getRadius();
            if (Intersectionf.testSphereSphere(
                    cubePos.x(), cubePos.y(), cubePos.z(), cubeRadius * cubeRadius,
                    x, y, z, searchRadius * searchRadius
            )) {
                list.add(cube);
            }
        }
        
        search(bvh.getLeft(), list, x, y, z, searchRadius);
        search(bvh.getRight(), list, x, y, z, searchRadius);
    }
    
    public List<LightmapAmbientCube> search(float x, float y, float z, float searchRadius) {
        List<LightmapAmbientCube> cubes = new ArrayList<>();
        search(this, cubes, x, y, z, searchRadius);
        return cubes;
    }
}
