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
import cientistavuador.newrenderingpipeline.newrendering.NCubemapInfo;
import cientistavuador.newrenderingpipeline.newrendering.NCubemapRenderer;
import cientistavuador.newrenderingpipeline.newrendering.NCubemaps;
import cientistavuador.newrenderingpipeline.newrendering.NLight;
import cientistavuador.newrenderingpipeline.newrendering.NMap;
import cientistavuador.newrenderingpipeline.physics.PlayerController;
import cientistavuador.newrenderingpipeline.popups.BakePopup;
import cientistavuador.newrenderingpipeline.popups.ContinuePopup;
import cientistavuador.newrenderingpipeline.text.GLFontRenderer;
import cientistavuador.newrenderingpipeline.text.GLFontSpecifications;
import cientistavuador.newrenderingpipeline.ubo.CameraUBO;
import cientistavuador.newrenderingpipeline.ubo.UBOBindingPoints;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Lightmapper;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Scene;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.joml.Vector3f;
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

    private final NCubemap skybox;
    private final String[] cubemapNames = {
        "gold_room",
        "animation_room",
        "emissive_room",
        "water_room",
        "white_room_0",
        "white_room_1",
        "main_room",
        "white_room_corridor",
        "gold_room_corridor",
        "water_room_corridor",
        "emissive_room_corridor",
        "animation_room_corridor"
    };
    private final NCubemapInfo[] cubemapInfos = {
        new NCubemapInfo(-15.62, 2.17, -9.59, -20.59, -0.14, -14.51, -10.49, 5.12, -4.47),
        new NCubemapInfo(-15.40, 2.49, 3.93, -20.60, -0.13, -2.21, -10.50, 5.63, 7.91),
        new NCubemapInfo(15.22, 1.48, 2.90, 10.39, -0.08, -2.19, 20.44, 5.35, 7.90),
        new NCubemapInfo(-0.15, 1.90, -23.44, -5.30, -0.21, -28.60, 4.78, 5.24, -18.52),
        new NCubemapInfo(12.78, 1.63, -9.40, 10.39, -0.08, -14.59, 15.44, 5.35, -4.4),
        new NCubemapInfo(17.91, 1.63, -9.29, 15.44, -0.08, -14.59, 20.44, 5.35, -4.4),
        new NCubemapInfo(-0.31, 3.41, 2.68, -5.59, -0.10, -13.01, 5.50, 5.42, 18.13),
        new NCubemapInfo(7.62, 1.78, -9.50, 10.40, -0.11, -8.40, 5.44, 2.62, -10.50),
        new NCubemapInfo(-7.98, 1.67, -9.65, -10.55, -0.11, -10.49, -5.55, 2.62, -8.42),
        new NCubemapInfo(-0.19, 1.50, -15.30, -1.30, -0.21, -18.52, 0.80, 3.20, -13.03),
        new NCubemapInfo(8.07, 1.81, 3.07, 5.40, -0.22, 1.80, 10.36, 2.68, 3.98),
        new NCubemapInfo(-7.99, 1.48, 2.75, -10.56, -0.22, 1.80, -5.59, 2.68, 4.00)
    };

    private NCubemaps cubemaps;

    private NMap map;
    private final N3DObject triceratops;
    private final N3DObject plasticBall;

    private NMap nextMap;

    private final NLight.NSpotLight flashlight = new NLight.NSpotLight("flashlight");
    private final List<NLight> lights = new ArrayList<>();
    private final Scene scene = new Scene();

    private final PhysicsSpace physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
    private final PlayerController playerController = new PlayerController();

    {
        try {
            this.skybox = NCubemapImporter.loadFromJar("cientistavuador/newrenderingpipeline/resources/image/generic_cubemap2.png", true, false);
            
            List<N3DObject> mapObjects = new ArrayList<>();

            {
                N3DModel waterBottle3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/nrp.glb");
                N3DObject nrp = new N3DObject("nrp", waterBottle3DModel);
                mapObjects.add(nrp);
            }

            {
                N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_zacxophone_triceratops.glb");

                this.triceratops = new N3DObject("triceratops", model);
                this.triceratops.getPosition().set(-15f, 0.9f, 3f);
                this.triceratops.setAnimator(new NAnimator(model, "Armature|Armature|Fall"));
            }

            {
                N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/plastic_ball.glb");

                this.plasticBall = new N3DObject("plastic ball", model);
            }

            this.map = new NMap("map", mapObjects, NMap.DEFAULT_LIGHTMAP_MARGIN, 1f / 0.2f);

            this.triceratops.setMap(this.map);
            this.plasticBall.setMap(this.map);

            System.out.println(StringUtils.formatMemory(Lightmapper.approximatedMemoryUsage(this.map.getLightmapSize(), 9)));

            System.out.println(this.map.getLightmapSize());

            this.flashlight.setInnerConeAngle(10f);
            this.flashlight.setOuterConeAngle(40f);
            this.flashlight.setDiffuseSpecularAmbient(1f);
            this.lights.add(this.flashlight);

            {
                NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
                sun.getDirection().set(0.46f, -0.71f, 0.53f);
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
                    NLight.NSpotLight spot = new NLight.NSpotLight("spot");
                    spot.getPosition().set(-0.35f, 0.1f, -23.48f);
                    spot.getDirection().set(0f, 1f, 0f);
                    spot.setDynamic(false);
                    spot.setDiffuseSpecularAmbient(100f);
                    this.lights.add(spot);
                }

                {
                    NLight.NSpotLight spot = new NLight.NSpotLight("white room spotlight");
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
                    point.setDiffuseSpecularAmbient(10f);
                    this.lights.add(point);
                }

            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        Scene.EmissiveLight emissive = new Scene.EmissiveLight();
        this.scene.getLights().add(emissive);

        Scene.AmbientLight ambient = new Scene.AmbientLight();
        ambient.setDiffuse(
                this.skybox.getCubemapColor().x() * 2f,
                this.skybox.getCubemapColor().y() * 2f,
                this.skybox.getCubemapColor().z() * 2f
        );
        this.scene.getLights().add(ambient);

        for (NLight light : this.lights) {
            if (light.isDynamic()) {
                continue;
            }
            this.scene.getLights().add(NMap.convertLight(light));
        }

        this.cubemaps = new NCubemaps(this.skybox, null);

        this.camera.setMovementDisabled(true);
        this.playerController.getCharacterController().addToPhysicsSpace(this.physicsSpace);

        this.physicsSpace.setGravity(new com.jme3.math.Vector3f(0f, -9.8f * Main.TO_PHYSICS_ENGINE_UNITS, 0f));
        this.physicsSpace.addCollisionObject(new PhysicsRigidBody(this.map.getMeshCollision(), 0f));
    }

    private Game() {

    }

    public void start() {
        this.camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));
    }

    private final Vector3f plasticBallRotation = new Vector3f(0f, 0f, 1f);

    public void loop() {
        this.camera.updateMovement();
        this.camera.updateUBO();

        this.playerController.update(this.camera.getFront(), this.camera.getRight());

        this.camera.setPosition(
                this.playerController.getEyePosition().x(),
                this.playerController.getEyePosition().y(),
                this.playerController.getEyePosition().z()
        );

        this.physicsSpace.update((float) Main.TPF);

        if (this.playerController.getCharacterController().getPosition().y() < -10f) {
            this.playerController.getCharacterController().setPosition(0f, 0.1f, 0f);
        }

        this.triceratops.getAnimator().update(Main.TPF);

        this.plasticBallRotation.rotateY((float) (Main.TPF * 0.5));
        this.plasticBall.getPosition().set(this.plasticBallRotation).mul(3f).add(15.29, 1.95, -9.52);

        this.flashlight.getPosition().set(this.camera.getPosition());
        this.flashlight.getDirection().set(this.camera.getFront());

        if (this.nextMap != null) {
            this.map = this.nextMap;
            this.nextMap = null;
        }

        for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
            N3DObjectRenderer.queueRender(this.map.getObject(i));
        }

        N3DObjectRenderer.queueRender(this.triceratops);
        N3DObjectRenderer.queueRender(this.plasticBall);

        N3DObjectRenderer.render(this.camera, this.lights, this.cubemaps);

        AabRender.renderQueue(this.camera);
        LineRender.renderQueue(this.camera);

        if (this.status != null && this.status.getTask().isDone()) {
            try {
                this.status.getTask().get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

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
        Main.WINDOW_TITLE += " (p:" + String.format("%,.2f", this.camera.getRotation().x()) + ",y:" + String.format("%,.2f", this.camera.getRotation().y()) + ",r:" + String.format("%,.2f", this.camera.getRotation().z()) + ")";
    }

    public void mouseCursorMoved(double x, double y) {
        this.camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        this.camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_ENABLED = !N3DObjectRenderer.REFLECTIONS_ENABLED;
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            N3DObjectRenderer.PARALLAX_ENABLED = !N3DObjectRenderer.PARALLAX_ENABLED;
        }
        if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_DEBUG = !N3DObjectRenderer.REFLECTIONS_DEBUG;
        }
        if (key == GLFW_KEY_F4 && action == GLFW_PRESS) {
            bake:
            {
                if (this.status != null && !this.status.getTask().isDone()) {
                    break bake;
                }
                
                if (this.camera.isCaptureMouse()) {
                    this.camera.pressEscape();
                }
                
                BakePopup.show(
                        (p) -> {
                            BakePopup.fromScene(this.scene, p);
                        },
                        (p) -> {
                            p.getBakeButton().setEnabled(false);
                            
                            BakePopup.toScene(this.scene, p);

                            List<N3DObject> list = new ArrayList<>();
                            for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
                                list.add(this.map.getObject(i));
                            }

                            final NMap newMap = new NMap(
                                    this.map.getName(),
                                    list,
                                    NMap.DEFAULT_LIGHTMAP_MARGIN,
                                    this.scene.getPixelToWorldRatio()
                            );

                            this.nextMap = newMap;

                            int size = newMap.getLightmapSize();
                            long requiredMemory = Lightmapper.approximatedMemoryUsage(size, this.scene.getSamplingMode().numSamples());
                            
                            ContinuePopup.show(p,
                                    "Lightmap Size: " + size + "x" + size + "\n"
                                    + "Required Memory: " + StringUtils.formatMemory(requiredMemory) + "\n"
                                    + "\n"
                                    + "Do you want to continue?",
                                    (e) -> {
                                        this.status = newMap.bake(this.scene);
                                        e.setVisible(false);
                                        e.dispose();
                                        
                                        p.setVisible(false);
                                        p.dispose();
                                    },
                                    (e) -> {
                                        e.setVisible(false);
                                        e.dispose();
                                        
                                        p.getBakeButton().setEnabled(true);
                                    }
                            );
                        },
                        (p) -> {
                            BakePopup.toScene(this.scene, p);
                        }
                );
            }
        }
        if (key == GLFW_KEY_F5 && action == GLFW_PRESS) {
            boolean reflectionsEnabled = N3DObjectRenderer.REFLECTIONS_ENABLED;
            boolean reflectionsDebug = N3DObjectRenderer.REFLECTIONS_DEBUG;

            N3DObjectRenderer.REFLECTIONS_ENABLED = false;
            N3DObjectRenderer.SPECULAR_ENABLED = false;
            N3DObjectRenderer.HDR_OUTPUT = true;
            N3DObjectRenderer.REFLECTIONS_DEBUG = false;

            List<NCubemap> cubemapsList = new ArrayList<>();
            for (int i = 0; i < this.cubemapNames.length; i++) {
                String name = this.cubemapNames[i];
                NCubemapInfo info = this.cubemapInfos[i];

                for (int j = 0; j < this.map.getNumberOfObjects(); j++) {
                    N3DObjectRenderer.queueRender(this.map.getObject(j));
                }

                NCubemap cubemap = NCubemapRenderer.render(
                        name,
                        info,
                        256,
                        this.lights,
                        this.cubemaps
                );

                cubemapsList.add(cubemap);
            }

            this.cubemaps = new NCubemaps(this.skybox, cubemapsList);

            N3DObjectRenderer.REFLECTIONS_ENABLED = reflectionsEnabled;
            N3DObjectRenderer.SPECULAR_ENABLED = true;
            N3DObjectRenderer.HDR_OUTPUT = false;
            N3DObjectRenderer.REFLECTIONS_DEBUG = reflectionsDebug;
        }
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            if (this.flashlight.getDiffuse().x() == 0f) {
                this.flashlight.setDiffuseSpecularAmbient(1f);
            } else {
                this.flashlight.setDiffuseSpecularAmbient(0f);
            }
        }
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
            this.playerController.jump();
        }
        if (key == GLFW_KEY_V && action == GLFW_PRESS) {
            this.playerController.getCharacterController().setNoclipEnabled(!this.playerController.getCharacterController().isNoclipEnabled());
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
