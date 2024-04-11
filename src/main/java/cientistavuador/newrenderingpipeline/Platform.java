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

/**
 *
 * @author Cien
 */
public enum Platform {
    WINDOWS, LINUX, MACOSX;
    
    private static final Platform currentPlatform;
    
    static {
        String[] windowsNames = {"windows"};
        String[] linuxNames = {"linux", "freebsd", "sunos", "unix"};
        String[] macosNames = {"mac os x", "darwin"};
        
        String osName = System.getProperty("os.name").toLowerCase();
        
        Platform platform = null;
        findPlatform: {
            for (String name:windowsNames) {
                if (osName.startsWith(name)) {
                    platform = Platform.WINDOWS;
                    break findPlatform;
                }
            }
            for (String name:linuxNames) {
                if (osName.startsWith(name)) {
                    platform = Platform.LINUX;
                    break findPlatform;
                }
            }
            for (String name:macosNames) {
                if (osName.startsWith(name)) {
                    platform = Platform.MACOSX;
                    break findPlatform;
                }
            }
            if (platform == null) {
                throw new UnsupportedOperationException("Unknown platform: "+osName);
            }
        }
        
        currentPlatform = platform;
    }
    
    public static Platform get() {
        return currentPlatform;
    }
    
    public static boolean isWindows() {
        return currentPlatform.equals(WINDOWS);
    }
    
    public static boolean isLinux() {
        return currentPlatform.equals(LINUX);
    }
    
    public static boolean isMacOSX() {
        return currentPlatform.equals(MACOSX);
    }
    
}
