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

/**
 *
 * @author Cien
 */
public class NCubemaps {
    
    public static final NCubemaps NULL_CUBEMAPS = new NCubemaps(null, null);
    
    private final NCubemap skybox;
    private final NCubemap[] cubemaps;
    private final NCubemapBVH cubemapsBVH;

    public NCubemaps(NCubemap skybox, List<NCubemap> cubemaps) {
        if (skybox == null) {
            skybox = NCubemap.NULL_CUBEMAP;
        }
        if (cubemaps == null) {
            cubemaps = new ArrayList<>();
        }
        
        this.skybox = skybox;
        this.cubemaps = cubemaps.toArray(NCubemap[]::new);
        this.cubemapsBVH = NCubemapBVH.create(cubemaps);
    }

    public NCubemap getSkybox() {
        return skybox;
    }

    public int getNumberOfCubemaps() {
        return this.cubemaps.length;
    }
    
    public NCubemap getCubemap(int index) {
        return this.cubemaps[index];
    }
    
    public NCubemapBVH getCubemapsBVH() {
        return cubemapsBVH;
    }
    
}
