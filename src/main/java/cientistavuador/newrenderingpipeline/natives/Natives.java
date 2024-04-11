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
package cientistavuador.newrenderingpipeline.natives;

import cientistavuador.newrenderingpipeline.Platform;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Cien
 */
public class Natives {

    public static final String TEMP_FOLDER_NAME = "natives-1ac84d0b-8897-414b-8939-eb8d71c1ba7e";

    public static Path extract() throws IOException {
        byte[] nativesZipData;
        try (InputStream nativesZipStream = Natives.class.getResourceAsStream("natives.zip")) {
            if (nativesZipStream == null) {
                throw new IOException("natives.zip not found");
            }
            nativesZipData = nativesZipStream.readAllBytes();
        }

        byte[] nativesZipDataSha256;
        try {
            nativesZipDataSha256 = MessageDigest.getInstance("SHA256").digest(nativesZipData);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

        String nativesZipDataSha256String;
        {
            StringBuilder b = new StringBuilder();
            for (byte e : nativesZipDataSha256) {
                String hex = Integer.toString(e & 0xFF, 16);
                if (hex.length() <= 1) {
                    b.append('0');
                }
                b.append(hex);
            }
            nativesZipDataSha256String = b.toString();
        }

        Path directory = Paths.get(
                System.getProperty("java.io.tmpdir"),
                TEMP_FOLDER_NAME,
                nativesZipDataSha256String
        );
        Files.createDirectories(directory);

        try (ZipInputStream nativesZipRead = new ZipInputStream(new ByteArrayInputStream(nativesZipData), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = nativesZipRead.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                
                String filename = e.getName();
                
                if (!filename.endsWith(".txt")) {
                    if (Platform.isWindows() && !filename.endsWith(".dll")) {
                        continue;
                    }

                    if (Platform.isMacOSX() && !filename.endsWith(".dylib")) {
                        continue;
                    }

                    if (Platform.isLinux() && !filename.endsWith(".so")) {
                        continue;
                    }
                }
                
                Path path = directory.resolve(filename);
                byte[] data = nativesZipRead.readAllBytes();

                if (!Files.exists(path) || !Arrays.equals(data, Files.readAllBytes(path))) {
                    Files.write(path, data);
                }
            }
        }

        return directory;
    }

    private Natives() {

    }
}
