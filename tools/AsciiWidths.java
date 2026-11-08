import javax.imageio.ImageIO; import java.io.File; import java.nio.file.*;
/** Vanilla ascii.png advances: trimmed width (8px cells, scale 1) + 1; space comes from the space provider (4). */
public class AsciiWidths { public static void main(String[] a) throws Exception {
  var img = ImageIO.read(new File(a[0])); int cell = img.getWidth() / 16; float scale = 8f / cell;
  StringBuilder out = new StringBuilder("# Vanilla minecraft:font/ascii.png advances (trimmed width + 1); space from include/space.json.\n20=4\n");
  for (int c = 0x21; c <= 0x7E; c++) { int col = c % 16, row = c / 16, right = 0;
    for (int x = cell - 1; x >= 0 && right == 0; x--) for (int y = 0; y < cell; y++) if ((img.getRGB(col * cell + x, row * cell + y) >>> 24) != 0) { right = x + 1; break; }
    out.append(Integer.toHexString(c)).append('=').append((int) (0.5f + right * scale) + 1).append('\n'); }
  Files.writeString(Path.of(a[1]), out); System.out.println("cell=" + cell + "\n" + out.toString().lines().filter(l -> l.matches("(41|61|31|2e|69|6c|5b|7c)=.*")).toList());
}}
