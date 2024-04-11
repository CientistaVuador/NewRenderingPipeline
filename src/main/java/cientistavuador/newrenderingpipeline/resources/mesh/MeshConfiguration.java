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
package cientistavuador.newrenderingpipeline.resources.mesh;

/**
 *
 * @author Cien
 */
public class MeshConfiguration {
    
    public static MeshConfiguration nothing(String name) {
        return new MeshConfiguration(
                name,
                false,
                false, 0, 0, 0
        );
    }
    
    public static MeshConfiguration lightmapped(String name) {
        return new MeshConfiguration(
                name,
                true,
                false, 0, 0, 0
        );
    }
    
    public static MeshConfiguration ambientOcclusion(String name, float aoSize, int aoRays, float rayOffset) {
        return new MeshConfiguration(
                name,
                false,
                true, aoSize, aoRays, rayOffset
        );
    }
    
    public static MeshConfiguration ambientOcclusion(String name, int rays) {
        return ambientOcclusion(name, 0.15f, rays, 0.001f);
    }
    
    public static MeshConfiguration ambientOcclusion(String name) {
        return ambientOcclusion(name, 512);
    }
    
    private final String name;
    private final boolean lightmapped;
    private final boolean vertexAOEnabled;
    private final float vertexAOSize;
    private final int vertexAORays;
    private final float vertexAORayOffset;
    
    public MeshConfiguration(
            String name,
            boolean lightmapped,
            boolean vertexAO,
            float vertexAOSize,
            int vertexAORays,
            float vertexAORayOffset
    ) {
        this.name = name;
        this.lightmapped = lightmapped;
        this.vertexAOEnabled = vertexAO;
        this.vertexAOSize = vertexAOSize;
        this.vertexAORays = vertexAORays;
        this.vertexAORayOffset = vertexAORayOffset;
    }
    
    public String getName() {
        return name;
    }

    public boolean isLightmapped() {
        return lightmapped;
    }

    public boolean isVertexAOEnabled() {
        return vertexAOEnabled;
    }

    public int getVertexAORays() {
        return vertexAORays;
    }

    public float getVertexAOSize() {
        return vertexAOSize;
    }

    public float getVertexAORayOffset() {
        return vertexAORayOffset;
    }
}
