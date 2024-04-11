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
package cientistavuador.newrenderingpipeline.geometry;

import cientistavuador.newrenderingpipeline.resources.mesh.MeshConfiguration;
import cientistavuador.newrenderingpipeline.resources.mesh.MeshData;
import cientistavuador.newrenderingpipeline.texture.Textures;
import cientistavuador.newrenderingpipeline.util.MeshStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Cien
 */
public class Geometries {

    public static final MeshData[] GARAGE;
    public static final MeshData CIENCOLA;
    public static final MeshData SPHERE;
    public static final MeshData MONKEY;
    public static final MeshData ASTEROID;

    static {
        Map<String, MeshData> meshes = GeometriesLoader.load(
                MeshConfiguration.ambientOcclusion("asteroid.obj"),
                MeshConfiguration.lightmapped("garage.obj"),
                MeshConfiguration.ambientOcclusion("ciencola.obj", 32)//,
        //MeshConfiguration.nothing("sphere.obj"),
        //MeshConfiguration.ambientOcclusion("monkey.obj", 8192)
        );
        MeshData bricks = meshes.get("garage.obj@bricks");
        MeshData concrete = meshes.get("garage.obj@concrete");
        MeshData grass = meshes.get("garage.obj@grass");
        MeshData red = meshes.get("garage.obj@red");
        bricks.setTextureHint(Textures.BRICKS);
        concrete.setTextureHint(Textures.CONCRETE);
        grass.setTextureHint(Textures.GRASS);
        red.setTextureHint(Textures.RED);
        GARAGE = new MeshData[]{concrete, grass, bricks, red};

        MeshData ciencola = meshes.get("ciencola.obj");
        
        {
            float[] verts = ciencola.getVertices();
            
            float scale = 0.15f;
            for (int i = 0; i < verts.length; i += MeshData.SIZE) {
                verts[i + MeshData.XYZ_OFFSET + 0] = verts[i + MeshData.XYZ_OFFSET + 0] * scale;
                verts[i + MeshData.XYZ_OFFSET + 1] = verts[i + MeshData.XYZ_OFFSET + 1] * scale;
                verts[i + MeshData.XYZ_OFFSET + 2] = verts[i + MeshData.XYZ_OFFSET + 2] * scale;
            }
            
            ciencola = new MeshData(ciencola.getName(), verts, ciencola.getIndices());
        }
        
        ciencola.setTextureHint(Textures.CIENCOLA);
        CIENCOLA = ciencola;

        MeshData sphere;
        //sphere = meshes.get("sphere.obj");
        try {
            MeshStore.MeshStoreOutput storedSphere = MeshStore.decode(Geometries.class.getResourceAsStream("sphere.mesh"));
            sphere = new MeshData(
                    "sphere",
                    storedSphere.vertices(),
                    storedSphere.indices()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        sphere.setTextureHint(Textures.RED);

        MeshData monkey;
        //monkey = meshes.get("monkey.obj");
        try {
            MeshStore.MeshStoreOutput storedMonkey = MeshStore.decode(Geometries.class.getResourceAsStream("monkey.mesh"));
            monkey = new MeshData(
                    "monkey",
                    storedMonkey.vertices(),
                    storedMonkey.indices()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        monkey.setTextureHint(Textures.RED);

        List<MeshData> toWrite = new ArrayList<>();
        //toWrite.add(monkey);
        //toWrite.add(sphere);
        for (MeshData m : toWrite) {
            try {
                try (FileOutputStream out = new FileOutputStream(new File(m.getName() + ".mesh"))) {
                    MeshStore.encode(
                            m.getVertices(),
                            MeshData.SIZE,
                            m.getIndices(),
                            out
                    );
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        SPHERE = sphere;
        MONKEY = monkey;
        
        ASTEROID = meshes.get("asteroid.obj");
        ASTEROID.setTextureHint(Textures.STONE);
    }

    public static void init() {

    }

    private Geometries() {

    }

}
