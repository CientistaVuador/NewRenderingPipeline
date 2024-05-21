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
import cientistavuador.newrenderingpipeline.newrendering.N3DModelStore;
import cientistavuador.newrenderingpipeline.newrendering.N3DObject;
import cientistavuador.newrenderingpipeline.newrendering.N3DObjectRenderer;
import cientistavuador.newrenderingpipeline.newrendering.NAnimator;
import cientistavuador.newrenderingpipeline.newrendering.NCubemap;
import cientistavuador.newrenderingpipeline.newrendering.NCubemapIO;
import cientistavuador.newrenderingpipeline.newrendering.NLight;
import cientistavuador.newrenderingpipeline.ubo.CameraUBO;
import cientistavuador.newrenderingpipeline.ubo.UBOBindingPoints;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    private final NCubemap cubemap;

    private final N3DObject testModel;
    private final N3DObject myBalls;
    private final N3DObject waterBottle;
    private final N3DObject fox;

    private final List<NLight> lights = new ArrayList<>();

    {
        try {
            this.cubemap = NCubemapIO.loadFromJar("cientistavuador/newrenderingpipeline/resources/image/generic_cubemap2.png", true, false);

            {
                N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_zacxophone_triceratops.glb");

                FileOutputStream out = new FileOutputStream("model.n3dm");
                N3DModelStore.writeModel(model, out);
                out.close();

                FileInputStream in = new FileInputStream("model.n3dm");
                model = N3DModelStore.readModel(in);
                in.close();

                this.testModel = new N3DObject("test model", model);

                this.testModel.getPosition().set(8f, 10.75f, -25f);
                this.testModel.getRotation().rotateY((float) Math.toRadians(-90f + -45f));

                this.testModel.setAnimator(new NAnimator(model, "Armature|Armature|Fall"));
            }

            {
                N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/my_metallic_balls.glb");

                this.myBalls = new N3DObject("test model", model);
                this.myBalls.getPosition().set(0f, 20f, -15f);
            }

            {
                N3DModel waterBottle3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/nrp.glb");

                this.waterBottle = new N3DObject("water bottle", waterBottle3DModel);
                this.waterBottle.getPosition().set(0f, 25f, -15f);
                this.waterBottle.getScale().set(1f);
            }

            {
                N3DModel fox3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_Fox.glb");
                this.fox = new N3DObject("fox", fox3DModel);
                this.fox.getPosition().set(0f, 10f, -20f);
                this.fox.getScale().set(0.02f);

                NAnimator foxAnimator = new NAnimator(fox3DModel, "Run");
                this.fox.setAnimator(foxAnimator);
            }

            {
                NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
                sun.getDirection().set(1f, -1f, 1f).normalize();
                sun.getDiffuse().set(1f);
                sun.getSpecular().set(1f);
                sun.getAmbient().set(0.1f);
                this.lights.add(sun);

                NLight.NPointLight point = new NLight.NPointLight("point");
                point.getPosition().set(13f, 11f, -17f);
                //this.lights.add(point);
                
                
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
        
        this.fox.getAnimator().update(Main.TPF);
        this.testModel.getAnimator().update(Main.TPF);

        N3DObjectRenderer.queueRender(this.fox);
        N3DObjectRenderer.queueRender(this.waterBottle);
        N3DObjectRenderer.queueRender(this.myBalls);
        N3DObjectRenderer.queueRender(this.testModel);
        
        N3DObjectRenderer.render(this.camera, this.lights, this.cubemap);

        AabRender.renderQueue(this.camera);
        LineRender.renderQueue(this.camera);
        
        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(this.camera.getPosition().x()) + ",y:" + (int) Math.floor(this.camera.getPosition().y()) + ",z:" + (int) Math.ceil(this.camera.getPosition().z()) + ")";
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
    }

    public void mouseCallback(long window, int button, int action, int mods) {
        
    }
}
