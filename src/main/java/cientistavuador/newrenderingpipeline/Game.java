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
package cientistavuador.newrenderingpipeline;

import cientistavuador.newrenderingpipeline.camera.FreeCamera;
import cientistavuador.newrenderingpipeline.debug.AabRender;
import cientistavuador.newrenderingpipeline.debug.LineRender;
import cientistavuador.newrenderingpipeline.newrendering.N3DModel;
import cientistavuador.newrenderingpipeline.newrendering.N3DModelImporter;
import cientistavuador.newrenderingpipeline.newrendering.N3DObject;
import cientistavuador.newrenderingpipeline.newrendering.N3DObjectRenderer;
import cientistavuador.newrenderingpipeline.newrendering.NAnimator;
import cientistavuador.newrenderingpipeline.newrendering.NCubemap;
import cientistavuador.newrenderingpipeline.newrendering.NCubemapImporter;
import cientistavuador.newrenderingpipeline.newrendering.NLight;
import cientistavuador.newrenderingpipeline.newrendering.NMap;
import cientistavuador.newrenderingpipeline.text.GLFontRenderer;
import cientistavuador.newrenderingpipeline.text.GLFontSpecifications;
import cientistavuador.newrenderingpipeline.ubo.CameraUBO;
import cientistavuador.newrenderingpipeline.ubo.UBOBindingPoints;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Scene;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    private NMap.BakeStatus status = null;

    private final NCubemap cubemap;

    private final NMap map;
    private final N3DObject triceratops;

    private final List<NLight> lights = new ArrayList<>();

    {
        try {
            //this.cubemap = NCubemapImporter.loadFromJar("cientistavuador/newrenderingpipeline/resources/image/generic_cubemap2.png", true, false);
            this.cubemap = NCubemap.NULL_CUBEMAP;
            
            List<N3DObject> mapObjects = new ArrayList<>();
            
            {
                N3DModel waterBottle3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/nrp.glb");
                N3DObject nrp = new N3DObject("nrp", waterBottle3DModel);
                mapObjects.add(nrp);
            }

            {
                N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_zacxophone_triceratops.glb");
                
                this.triceratops = new N3DObject("test model", model);
                this.triceratops.getPosition().set(-15f, 0.9f, 3f);
                this.triceratops.setAnimator(new NAnimator(model, "Armature|Armature|Fall"));
            }
            
            this.map = new NMap("map", mapObjects, NMap.DEFAULT_LIGHTMAP_MARGIN, 1f / 0.1f);

            System.out.println(this.map.getLightmapSize());

            {
                NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
                sun.getDirection().set(1f, -1f, 1f).normalize();
                sun.setDynamic(false);
                sun.setDiffuseSpecularAmbient(2f);
                this.lights.add(sun);
                
                {
                    NLight.NPointLight point = new NLight.NPointLight("point");
                    point.getPosition().set(-15.55f, 4.41f, 3.15f);
                    point.setDynamic(false);
                    point.setDiffuseSpecularAmbient(10f);
                    this.lights.add(point);
                }
                
                {
                    NLight.NPointLight point = new NLight.NPointLight("point");
                    point.getPosition().set(-15.47f, 4.71f, -9.44f);
                    point.setDynamic(false);
                    point.setDiffuseSpecularAmbient(10f);
                    this.lights.add(point);
                }
                
                {
                    NLight.NPointLight point = new NLight.NPointLight("point");
                    point.getPosition().set(-0.35f, 4.58f, -23.48f);
                    point.setDynamic(false);
                    point.setDiffuseSpecularAmbient(10f);
                    this.lights.add(point);
                }
                
                {
                    NLight.NSpotLight spot = new NLight.NSpotLight("spot");
                    spot.getPosition().set(11.10f, 3.94f, -9.56f);
                    spot.getDirection().set(0.89f, -0.45f, 0.02f);
                    spot.setDynamic(false);
                    spot.setDiffuseSpecularAmbient(10f);
                    this.lights.add(spot);
                }
                
                {
                    NLight.NPointLight point = new NLight.NPointLight("point");
                    point.getPosition().set(-0.28f, 4.61f, 2.53f);
                    point.setDynamic(false);
                    point.setDiffuseSpecularAmbient(20f);
                    this.lights.add(point);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Game() {

    }

    public void start() {
        this.camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));
    }

    public void loop() {
        this.camera.updateMovement();
        this.camera.updateUBO();
        
        this.triceratops.getAnimator().update(Main.TPF);

        for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
            N3DObjectRenderer.queueRender(this.map.getObject(i));
        }

        N3DObjectRenderer.queueRender(this.triceratops);
        
        N3DObjectRenderer.render(this.camera, this.lights, this.cubemap);

        AabRender.renderQueue(this.camera);
        LineRender.renderQueue(this.camera);

        if (this.status != null && !this.status.getTask().isDone()) {
            String text = this.status.getStatus() + '\n'
                    + String.format("%,.2f", this.status.getRaysPerSecond()) + " Rays Per Second" + '\n'
                    + String.format("%,.2f", this.status.getProgress() * 100.0) + "%";
            GLFontRenderer.render(-0.94f, 0.94f, GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK, text);
            GLFontRenderer.render(-0.95f, 0.95f, GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE, text);
        }

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + String.format("%,.2f", this.camera.getPosition().x()) + ",y:" + String.format("%,.2f", this.camera.getPosition().y()) + ",z:" + String.format("%,.2f", this.camera.getPosition().z()) + ")";
        Main.WINDOW_TITLE += " (dx:" + String.format("%,.2f", this.camera.getFront().x()) + ",dy:" + String.format("%,.2f", this.camera.getFront().y()) + ",dz:" + String.format("%,.2f", this.camera.getFront().z()) + ")";
    }

    public void mouseCursorMoved(double x, double y) {
        this.camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        this.camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_H && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_ENABLED = !N3DObjectRenderer.REFLECTIONS_ENABLED;
        }
        if (key == GLFW_KEY_P && action == GLFW_PRESS) {
            N3DObjectRenderer.PARALLAX_ENABLED = !N3DObjectRenderer.PARALLAX_ENABLED;
        }
        if (key == GLFW_KEY_B && action == GLFW_PRESS) {
            Scene scene = new Scene();
            
            Scene.EmissiveLight emissive = new Scene.EmissiveLight();
            scene.getLights().add(emissive);
            
            for (NLight light:this.lights) {
                scene.getLights().add(NMap.convertLight(light));
            }
            
            this.status = this.map.bake(scene);
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
