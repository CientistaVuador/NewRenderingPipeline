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
package cientistavuador.newrenderingpipeline.util;

import cientistavuador.newrenderingpipeline.Platform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.lwjgl.stb.STBDXT;
import static org.lwjgl.stb.STBDXT.stb_compress_dxt_block;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class TextureCompressor {

    public static final String TEMP_FOLDER_NAME = "compressor-b205ca81-4ae6-4108-859b-3a32789240bb";

    public static final String NVIDIA_TEXTURE_TOOLS_ENV = "NVIDIA_TEXTURE_TOOLS_ROOT";
    public static final String NVIDIA_TEXTURE_TOOLS_COMPRESS_EXE_PATH;
    public static final String NVIDIA_TEXTURE_TOOLS_DECOMPRESS_EXE_PATH;
    
    static {
        String compressName = "nvcompress";
        String decompressName = "nvdecompress";
        
        if (Platform.isWindows()) {
            compressName += ".exe";
            decompressName += ".exe";
        }
        
        int runningFrom = -1;
        
        String compressExePath = null;
        String decompressExePath = null;
        
        Path defaultInstallationPath = Path.of("C:", "Program Files", "NVIDIA Corporation", "NVIDIA Texture Tools");
        if (Files.exists(defaultInstallationPath)) {
            Path compress = defaultInstallationPath.resolve(compressName);
            Path decompress = defaultInstallationPath.resolve(decompressName);
            
            if (Files.exists(compress) && Files.exists(decompress)) {
                compressExePath = compress.toAbsolutePath().toString();
                decompressExePath = decompress.toAbsolutePath().toString();
                
                runningFrom = 0;
            }
        }
        
        String systemToolsEnv = System.getenv(NVIDIA_TEXTURE_TOOLS_ENV);
        if (systemToolsEnv != null) {
            Path compress = Path.of(systemToolsEnv, compressName);
            Path decompress = Path.of(systemToolsEnv, decompressName);
            
            if (Files.exists(compress) && Files.exists(decompress)) {
                compressExePath = compress.toAbsolutePath().toString();
                decompressExePath = decompress.toAbsolutePath().toString();
                
                runningFrom = 1;
            }
        }
        
        Path localPath = Path.of("NVIDIA Texture Tools");
        if (Files.exists(localPath)) {
            Path compress = localPath.resolve(compressName);
            Path decompress = localPath.resolve(decompressName);
            
            if (Files.exists(compress) && Files.exists(decompress)) {
                compressExePath = compress.toAbsolutePath().toString();
                decompressExePath = decompress.toAbsolutePath().toString();
                
                runningFrom = 2;
            }
        }
        
        NVIDIA_TEXTURE_TOOLS_COMPRESS_EXE_PATH = compressExePath;
        NVIDIA_TEXTURE_TOOLS_DECOMPRESS_EXE_PATH = decompressExePath;
        
        if (runningFrom == -1) {
            System.out.println("*");
            System.out.println("Warning: NVIDIA Texture Tools not found");
            System.out.println("Download from https://developer.nvidia.com/texture-tools-exporter");
            System.out.println("To install place into " + Paths.get("NVIDIA Texture Tools").toAbsolutePath().toString());
            System.out.println("OR");
            System.out.println("Set " + NVIDIA_TEXTURE_TOOLS_ENV);
            System.out.println("OR");
            System.out.println("Don't change the default installation folder: "+defaultInstallationPath.toString());
            System.out.println("*");
            System.out.println("The local executable has priority.");
            System.out.println("*");
        } else {
            switch (runningFrom) {
                case 0 -> {
                    System.out.println("Running NVIDIA Texture Tools from installation folder.");
                }
                case 1 -> {
                    System.out.println("Running NVIDIA Texture Tools from system.");
                }
                case 2 -> {
                    System.out.println("Running NVIDIA Texture Tools locally.");
                }
            }
            System.out.println(NVIDIA_TEXTURE_TOOLS_COMPRESS_EXE_PATH);
            System.out.println(NVIDIA_TEXTURE_TOOLS_DECOMPRESS_EXE_PATH);
        }
    }
    
    public static void init() {

    }
    
    public static boolean isNVIDIATextureToolsSupported() {
        return NVIDIA_TEXTURE_TOOLS_COMPRESS_EXE_PATH != null && NVIDIA_TEXTURE_TOOLS_DECOMPRESS_EXE_PATH != null;
    }
    
    private static void validateNVIDIATextureToolsSupported() {
        if (!isNVIDIATextureToolsSupported()) {
            throw new UnsupportedOperationException("NVIDIA Texture Tools not supported.");
        }
    }
    
    public static Process callNVIDIATextureTools(File workDir, boolean compress, String args) {
        validateNVIDIATextureToolsSupported();
        try {
            return Runtime.getRuntime().exec(
                    (compress ? NVIDIA_TEXTURE_TOOLS_COMPRESS_EXE_PATH : NVIDIA_TEXTURE_TOOLS_DECOMPRESS_EXE_PATH) + " " + args,
                    null,
                    workDir
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public static Path createTempCompressorFolder() throws IOException {
        Path workDir = Paths.get(System.getProperty("java.io.tmpdir"),
                TEMP_FOLDER_NAME,
                UUID.randomUUID().toString()
        );
        Files.createDirectories(workDir);

        return workDir;
    }
    
    public static int paddingSize4(int value) {
        int padding = value % 4;
        if (padding != 0) {
            padding = 4 - padding;
        }
        return value + padding;
    }

    public static int DXT5Size(int width, int height) {
        return paddingSize4(width) * paddingSize4(height);
    }

    public static byte[] compressDXT5BlockFallback(byte[] data, int width, int height, int x, int y) {
        if (data.length != (width * height * 4)) {
            throw new IllegalArgumentException("Invalid amount of bytes, required " + (width * height * 4) + ", found " + data.length);
        }
        PixelUtils.PixelStructure st = PixelUtils.getPixelStructure(width, height, 4, true);
        byte[] outputArray;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixels = stack.malloc(4 * 4 * 4);
            for (int yOffset = 0; yOffset < 4; yOffset++) {
                for (int xOffset = 0; xOffset < 4; xOffset++) {
                    for (int i = 0; i < 4; i++) {
                        pixels.put(data[PixelUtils.getPixelComponentIndex(st, x + xOffset, y + yOffset, i)]);
                    }
                }
            }
            pixels.flip();
            ByteBuffer output = stack.malloc(16);
            stb_compress_dxt_block(output, pixels, true, STBDXT.STB_DXT_HIGHQUAL);
            outputArray = new byte[output.capacity()];
            output.get(outputArray);
        }
        return outputArray;
    }
    
    public static byte[] compressDXT5Fallback(byte[] data, int width, int height) {
        if (data.length != (width * height * 4)) {
            throw new IllegalArgumentException("Invalid amount of bytes, required " + (width * height * 4) + ", found " + data.length);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(65535);
        for (int blockY = 0; blockY < height; blockY += 4) {
            for (int blockX = 0; blockX < width; blockX += 4) {
                try {
                    out.write(compressDXT5BlockFallback(data, width, height, blockX, blockY));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
        return out.toByteArray();
    }
    
    private TextureCompressor() {

    }

}
