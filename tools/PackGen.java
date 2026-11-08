import org.crebs.coreui.pack.ResourcePackGenerator;

import java.nio.file.Path;

/**
 * Generates plugins/CoreUi/Pack/CoreUi.zip offline, exactly as the plugin does on startup.
 * Usage: java -cp <CoreUi jar;paper-api;libs> PackGen.java <CoreUi data dir> <CoreUi jar> <externalTextShader>
 */
public class PackGen {
    public static void main(String[] args) throws Exception {
        var result = new ResourcePackGenerator(Path.of(args[0]), Path.of(args[1]), Boolean.parseBoolean(args[2])).generate();
        System.out.println("OK " + result.path() + " glyphs=" + result.glyphs().size());
    }
}
