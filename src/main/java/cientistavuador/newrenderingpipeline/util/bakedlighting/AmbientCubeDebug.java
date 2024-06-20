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

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.geometry.Geometries;
import cientistavuador.newrenderingpipeline.resources.mesh.MeshData;
import cientistavuador.newrenderingpipeline.util.BetterUniformSetter;
import cientistavuador.newrenderingpipeline.util.ProgramCompiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class AmbientCubeDebug {
    
    public static final int INSTANCES_PER_DRAWCALL = 64;
    
    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            uniform mat4 projectionView;
            uniform mat4 model[INSTANCES_PER_DRAWCALL];
            
            layout (location = 0) in vec3 vertexPosition;
            layout (location = 2) in vec3 vertexNormal;
            
            flat out int instanceID;
            out vec3 fragNormal;
            
            void main() {
                instanceID = gl_InstanceID;
                fragNormal = vertexNormal;
                gl_Position = projectionView * model[instanceID] * vec4(vertexPosition, 1.0);
            }
            """,
            """
            #version 330 core
            
            uniform vec3 ambientCube[6 * INSTANCES_PER_DRAWCALL];
            
            flat in int instanceID;
            in vec3 fragNormal;
            
            vec3 ACESFilm(vec3 rgb) {
                float a = 2.51;
                float b = 0.03;
                float c = 2.43;
                float d = 0.59;
                float e = 0.14;
                return (rgb*(a*rgb+b))/(rgb*(c*rgb+d)+e);
            }
            
            vec3 gammaCorrection(vec3 rgb) {
                return pow(rgb, vec3(1.0/2.2));
            }
            
            vec3 ambientLight(vec3 normal) {
                vec3 normalSquared = normal * normal;
                ivec3 negative = ivec3(normal.x < 0.0, normal.y < 0.0, normal.z < 0.0);
                vec3 ambient = normalSquared.x * ambientCube[(instanceID * 6) + negative.x]
                    + normalSquared.y * ambientCube[(instanceID * 6) + negative.y + 2]
                    + normalSquared.z * ambientCube[(instanceID * 6) + negative.z + 4];
                return ambient;
            }
            
            layout (location = 0) out vec4 outColor;
            
            void main() {
                vec3 normal = normalize(fragNormal);
                outColor = vec4(gammaCorrection(ACESFilm(ambientLight(normal))), 1.0);
            }
            """,
            new HashMap<>() {{
                put("INSTANCES_PER_DRAWCALL", Integer.toString(INSTANCES_PER_DRAWCALL));
            }}
    );
    
    private static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void render(
            List<LightmapAmbientCube> cubesList,
            Matrix4fc projection, Matrix4fc view, Vector3dc position
    ) {
        if (cubesList.isEmpty()) {
            return;
        }
        
        Matrix4f projectionView = new Matrix4f(projection).mul(view);
        
        {
            MeshData sphere = Geometries.DEBUG_SPHERE;
            
            Vector3fc min = sphere.getBVH().getMin();
            Vector3fc max = sphere.getBVH().getMax();
            
            List<LightmapAmbientCube> filteredList = new ArrayList<>();
            for (LightmapAmbientCube e:cubesList) {
                float rX = (float) (e.getPosition().x() - position.x());
                float rY = (float) (e.getPosition().y() - position.y());
                float rZ = (float) (e.getPosition().z() - position.z());
                
                float minX = rX + min.x();
                float minY = rY + min.y();
                float minZ = rZ + min.z();
                
                float maxX = rX + max.x();
                float maxY = rY + max.y();
                float maxZ = rZ + max.z();
                
                if (projectionView.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
                    filteredList.add(e);
                }
            }
            cubesList = filteredList;
        }
        
        if (cubesList.isEmpty()) {
            return;
        }
        
        glUseProgram(SHADER_PROGRAM);
        
        BetterUniformSetter.uniformMatrix4fv(UNIFORMS.locationOf("projectionView"), projectionView);
        
        Matrix4f model = new Matrix4f();
        Vector3f totalSideColor = new Vector3f();
        
        for (int i = 0; i < cubesList.size(); i += INSTANCES_PER_DRAWCALL) {
            int instances = INSTANCES_PER_DRAWCALL;
            for (int j = 0; j < INSTANCES_PER_DRAWCALL; j++) {
                int finalIndex = i + j;
                if (finalIndex >= cubesList.size()) {
                    instances = j;
                    break;
                }
                
                LightmapAmbientCube f = cubesList.get(finalIndex);
                
                model
                        .identity()
                        .translate(
                                (float) (f.getPosition().x() - position.x()),
                                (float) (f.getPosition().y() - position.y()),
                                (float) (f.getPosition().z() - position.z())
                        )
                        ;
                
                BetterUniformSetter.uniformMatrix4fv(UNIFORMS.locationOf("model["+j+"]"), model);
                for (int side = 0; side < AmbientCube.SIDES; side++) {
                    totalSideColor.zero();
                    for (int k = 0; k < f.getNumberOfAmbientCubes(); k++) {
                        totalSideColor.add(f.getAmbientCube(k).getSide(side));
                    }
                    glUniform3f(
                            UNIFORMS.locationOf("ambientCube["+((j * 6) + side)+"]"),
                            totalSideColor.x(),
                            totalSideColor.y(),
                            totalSideColor.z()
                    );
                }
            }
            
            MeshData sphere = Geometries.DEBUG_SPHERE;
            
            sphere.bind();
            glDrawElementsInstanced(GL_TRIANGLES, sphere.getAmountOfIndices(), GL_UNSIGNED_INT, 0, instances);
            sphere.unbind();
            
            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += (sphere.getAmountOfIndices() * instances);
        }
        
        glUseProgram(0);
    }
    
    private AmbientCubeDebug() {
        
    }
    
}
