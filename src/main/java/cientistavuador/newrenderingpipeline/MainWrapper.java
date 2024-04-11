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

import cientistavuador.newrenderingpipeline.natives.Natives;
import cientistavuador.newrenderingpipeline.sound.SoundSystem;
import com.formdev.flatlaf.FlatDarkLaf;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.system.NativeLibraryLoader;
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.openal.ALC11.*;

/**
 *
 * @author Cien
 */
public class MainWrapper {

    static {
        Locale.setDefault(Locale.US);

        System.out.println(UUID.randomUUID().toString());

        System.out.println("  /$$$$$$  /$$        /$$$$$$  /$$");
        System.out.println(" /$$__  $$| $$       /$$__  $$| $$");
        System.out.println("| $$  \\ $$| $$      | $$  \\ $$| $$");
        System.out.println("| $$  | $$| $$      | $$$$$$$$| $$");
        System.out.println("| $$  | $$| $$      | $$__  $$|__/");
        System.out.println("| $$  | $$| $$      | $$  | $$    ");
        System.out.println("|  $$$$$$/| $$$$$$$$| $$  | $$ /$$");
        System.out.println(" \\______/ |________/|__/  |__/|__/");

        FlatDarkLaf.setup();

        String osName = System.getProperty("os.name");
        System.out.println("Running on " + osName);

        try {
            Path path = Natives.extract().toAbsolutePath();
            
            org.lwjgl.system.Configuration.LIBRARY_PATH.set(path.toString());
            PhysicsRigidBody.logger2.setLevel(Level.WARNING);
            NativeLibraryLoader.loadLibbulletjme(true, path.toFile(), "Release", "Sp");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        boolean error = false;
        
        try {
            Main.main(args);
        } catch (Throwable e) {
            e.printStackTrace(System.out);

            Toolkit.getDefaultToolkit().beep();

            JFrame dummyFrame = new JFrame("dummy frame");
            dummyFrame.setLocationRelativeTo(null);
            dummyFrame.setVisible(true);
            dummyFrame.toFront();
            dummyFrame.setVisible(false);
            dummyFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            PrintStream messageStream = new PrintStream(byteArray);
            e.printStackTrace(messageStream);
            messageStream.flush();
            String message = new String(byteArray.toByteArray(), StandardCharsets.UTF_8);

            JOptionPane.showMessageDialog(
                    dummyFrame,
                    message,
                    "Game crashed!",
                    JOptionPane.ERROR_MESSAGE
            );
            
            error = true;
        }

        try {
            alcMakeContextCurrent(0);
            alcDestroyContext(SoundSystem.CONTEXT);
            alcCloseDevice(SoundSystem.DEVICE);
            glfwTerminate();
        } catch (Throwable e) {
            error = true;
            e.printStackTrace(System.out);
        }
        
        if (!error) {
            System.exit(0);
        } else {
            System.exit(-1);
        }
    }

}
