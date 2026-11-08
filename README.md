<div align="center">

# CoreUi

**An Ore UI-style menu library for Paper plugins.**

Create menus with buttons, toggles, sliders, dropdowns and custom images using a Java builder API.

[![Features](https://img.shields.io/badge/Navigation-Features-007ACC?style=for-the-badge)](#features)
[![Requirements](https://img.shields.io/badge/Navigation-Requirements-007ACC?style=for-the-badge)](#requirements)
[![Example](https://img.shields.io/badge/Navigation-Example-007ACC?style=for-the-badge)](#example)
[![Custom Glyphs](https://img.shields.io/badge/Navigation-Custom_Glyphs-007ACC?style=for-the-badge)](#custom-glyphs)
[![Credits](https://img.shields.io/badge/Navigation-Credits-007ACC?style=for-the-badge)](#credits)

</div>

---

## Features

| Feature | Description |
| :--- | :--- |
| **Menus** | Build menus with positioned elements and click callbacks. |
| **Controls** | Add buttons, checkboxes, toggles, sliders and dropdowns. |
| **Themes** | Use light and dark themes with Adventure components and MiniMessage colours. |
| **Custom glyphs** | Display your own PNG images in menus, chat and action bars. |
| **Resource pack** | Generate a ZIP containing fonts, textures, shaders and custom glyphs. |
| **Version overlays** | Include shaders targeting Minecraft 1.21.11 through 26.3 in one pack. |
| **Menu values** | Remember control values during the server session and reset them through the builder. |

## Requirements

| Dependency | Version |
| :--- | :--- |
| Paper | **26.3** |
| Java | **25+** |

---

## Example

### Built-in demo

Install this build on Paper 26.3 with Java 25 or newer, then restart the server.
CoreUi creates `plugins/CoreUi/Pack/CoreUi.zip` and installs the bundled
`plugins/CoreUi/Glyphs/example-arrow.png` without overwriting an existing file.
An empty installation therefore includes one example image glyph as well as the built-in UI assets.

1. Copy the generated `CoreUi.zip` into your Minecraft client's `resourcepacks` folder and enable it. For other players, distribute the pack through your server's resource-pack hosting.
2. Run `/coreui demo` (or `/coreui`) as an operator in-game.
3. Try the toggle, checkbox, slider, theme dropdown, reset button and click button.

Use `/coreui demo light` or `/coreui demo dark` to select the starting theme.
The permission is `coreui.demo` (default: operators). The demo starts with default values each time it is opened.
If you rebuild the pack, update the client's ZIP as well.

<img width="1333" height="872" alt="CoreUi menu example" src="https://github.com/user-attachments/assets/2924085e-00d4-43c9-b841-392f04737c4b" />

<details>
<summary><strong>View example</strong></summary>

```java
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.style.ThemeColour;

public void openSettings(Player player) {
    CoreUi coreUi = JavaPlugin.getPlugin(CoreUi.class);
    CoreMenu menu = CoreMenu.builder("myplugin-settings")
        .title("Settings")
        .header()
        .theme(ThemeColour.DARK)
        .rememberValues()
        .size(300, 180)
        .label("heading", "<gold>Your settings</gold>")
            .position(10, 12)
            .add()
        .toggle("notifications")
            .position(9, 30)
            .label("Notifications")
            .description("Receive server announcements")
            .initialValue(true)
            .onChange((p, enabled) -> p.sendMessage("Notifications: " + enabled))
            .add()
        .slider("volume")
            .position(9, 72)
            .label("Volume")
            .range(0, 100)
            .step(1)
            .initialValue(50)
            .onChange((p, value) -> p.sendMessage("Volume: " + value))
            .add()
        .button("reset")
            .compact()
            .position(9, 130)
            .label("Reset to defaults")
            .resetValues("notifications", "volume")
            .add()
        .build();
    coreUi.open(player, menu);
}
```

</details>

## Usage

Install the CoreUi plugin JAR in your server's `plugins` folder. Depend on its API from your own plugin without bundling CoreUi into your JAR.

### Gradle

Add this to your plugin's `build.gradle.kts`.

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.CrebsTheCoder:CoreUi:1.0.0") {
        isTransitive = false
    }
}
```

Keep your plugin's existing Paper API dependency. CoreUi uses Adventure from Paper.

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.CrebsTheCoder</groupId>
        <artifactId>CoreUi</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
        <exclusions>
            <exclusion>
                <groupId>*</groupId>
                <artifactId>*</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

## Resource Pack

CoreUi generates `plugins/CoreUi/Pack/CoreUi.zip` asynchronously on startup. The built-in pack sources are included in the plugin JAR from `src/main/resources/resource-pack`.

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/coreui` or `/coreui demo [light\|dark]` | `coreui.demo` | Open the built-in reference menu in-game. |
| `/coreui reload` | `coreui.reload` | Rebuild the pack and reload custom glyphs. |

The permission defaults to operators. Reloading is also available from the console.

The ZIP contains shared assets and shader overlays for `1.21.11`, `26.1.x`, `26.2.x` and `26.3`. Minecraft selects the matching overlay from one ZIP. Check rendering in-game on each version you deploy.

## Main Menu (`/메인메뉴`)

`org.crebs.coreui.menu.kokonut.MainMenu`: 이동 · 도감 · 꾸미기 · 우편함 · 설정 in the dex's book style (320×224 GUI px).
Permission `coreui.mainmenu` (operators while it is a prototype). `/메뉴` stays Kokonut-Core's utility menu.

- 도감 opens `/도감`; 우편함 opens Kokonut-Mail's `MailboxService.open` (softdepend, compile-only `kokonutMailJar`).
  이동 · 꾸미기 · 설정 only reply "준비 중" for now. The 우편함 card shows a red badge with
  `MailboxService.unclaimedCount` (memory only, may lag up to Kokonut-Mail's 20 s poll; hidden when unknown or with an
  older Kokonut-Mail that lacks the method).
- Gold and kokonut come from Kokonut-Core's `CurrencyUtil.snapshot` (shows `…` while loading or without Core).
  The second profile line shows the equipped title from Core's `TitleLookup` service, or the world name without one.
  Dex progress and the reward count still come from the dex sample data.
- Images: `bundled-glyphs/menu/ui/*` (panel, tab, card, well, chips); icons are vanilla items `menu/item/<key>` (32 px).

## Mailbox (`/우편함`)

With Kokonut-Mail installed, CoreUi registers a `MailboxView` on startup, so `/우편함`, the main menu and Core's
utility menu open `MailMenu` (book layout) instead of the chest GUI. Kokonut-Mail keeps the data, claiming and the
coupon editor; it falls back to its chest GUI when this view cannot open (glyphs not loaded, older plugin).

- Left page: 7 mails per page (oldest first), icon = first attachment (bundle icon for bundles), sender/date, red
  "처리 중" while a claim is locked; 모두 받기. Right page: details and attachments (8×4 per page) with stack sizes.
- Item names are never drawn: hovering a mail or an attachment shows the item's own tooltip (`HotspotBuilder.hover`).
- Item icons come from `bundled-vanilla-items.yml` (`item/<vanilla id>`, 16 px, one per vanilla item), generated by
  `java tools/VanillaItemIcons.java "%APPDATA%/.minecraft/versions/26.3/26.3.jar" src/main/resources/bundled-vanilla-items.yml`.
  To stay light each icon only has the font for text-line offset 2, so draw them where `y % 9 == 2`
  (`Glyph.supportsOffset`). Custom items (Nexo etc.) show their base material's icon; animated or model-only items
  show `menu/ui/unknown_item`. Some block items use a representative face (a chest shows planks).

## Item Dex (`/도감`)

A book-style item collection menu (광물·농작물·식물·물고기·유물·기타) with dex, reward and statistics pages.
It currently shows **sample data** (`DexCatalog.SAMPLE`, minerals only), so `coreui.dex` defaults to operators.

- Code: `org.crebs.coreui.menu.dex.ItemDexMenu` (layout, clicks) and `DexCatalog` (data records + sample).
- UI images (book, slots, buttons, tabs, bookmarks): placeholder PNGs in `src/main/resources/bundled-glyphs/dex`,
  generated by `py -3 tools/dex_glyphs.py` together with their ascents in `bundled-glyphs.yml`.
  On startup they are copied to `plugins/CoreUi/Glyphs/dex/` **only when missing**, so edited files are kept.
  To pick up new bundled images, delete that folder and run `/coreui reload`.
- Item icons and portraits are **vanilla textures**: `dex/item/<key>` (16 px) and `dex/portrait/<key>` (64 px).
  Their fonts point at `minecraft:item/...` in the client, so no Minecraft art is shipped. The list is
  `tools/dex_vanilla.txt`; after editing it run
  `java tools/VanillaGlyphs.java "%APPDATA%/.minecraft/versions/26.3/26.3.jar" tools/dex_vanilla.txt src/main/resources/bundled-vanilla-glyphs.yml`
  (it only stores sizes and advances). A PNG with the same id in `Glyphs/` overrides the vanilla texture.
  Not-yet-obtained items are the same glyph tinted dark (`GlyphBuilder.tint`).
- Paint order: CoreUi draws text lines top to bottom, so every dex glyph hangs from the line its top sits on
  (ascent 8; shorter images are padded to 8 px). An image therefore covers only elements whose top line is
  above its own. The lower book tiles start on a line boundary (y = 153); keep content from straddling y = 153.
- The menu is 480 px wide, so it fits a 1080p screen up to GUI scale 3.
- Keep every glyph image at most 256 px wide and tall; the client draws a missing-glyph box for larger bitmaps,
  and the elements after it on the same text line shift left. Tile wide art (the book and the main menu panel are tiled).

## Custom Glyphs

1. Add a PNG to `plugins/CoreUi/Glyphs`, such as `heart.png` or `icons/coin.png`.
2. Run `/coreui reload` and wait for the rebuild to finish.
3. Update your hosted resource pack and have players load it.

Glyph IDs are relative paths without `.png`: `heart` and `icons/coin`. Use lowercase letters, numbers, underscores and hyphens. Each glyph has its own font, keeping IDs stable when other images are added or removed.

Optional settings in `plugins/CoreUi/glyphs.yml` control display height and baseline alignment. Width scales proportionally. Defaults use the image height for both values.

```yaml
glyphs:
  heart:
    height: 16
    ascent: 12
```

Height must be between `1` and `256`. Ascent must be between `-256` and the configured height.

<details>
<summary><strong>View example</strong> · Display a custom glyph</summary>

After pack generation has finished, look up the glyph and use its Adventure component:

```java
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;

public void showHeart(Player player) {
    CoreUi coreUi = JavaPlugin.getPlugin(CoreUi.class);
    if (!coreUi.getGlyphs().all().containsKey("heart")) return;
    Glyph heart = coreUi.getGlyphs().get("heart");
    player.sendMessage(Component.text("Health: ").append(heart.component()));
    player.sendActionBar(heart.component());
}
```

To place it in a menu, use the glyph builder before calling `build()`:

```java
builder.glyph("health-icon", heart)
    .position(20, 40)
    .add();
```

Coordinates specify the top-left corner. The element uses the dimensions configured for that glyph.

</details>

`coreUi.getGlyphs().all()` returns the loaded glyphs. `coreUi.reloadPack()` returns a `CompletableFuture<Path>` that completes after the ZIP and registry have been updated. Failed rebuilds preserve the previous pack and registry. Reacquire glyphs and recreate menus after changing their dimensions. Schedule menu operations back onto the server thread after an asynchronous rebuild.

Use glyph elements for menu images; label measurements assume the built-in font. Custom images inside dropdown overlay text are not supported.

## Text Colours

`title`, `header`, `label`, `description` and `indicatorLabel` accept MiniMessage strings. Adventure components are also supported; `Component.text(...)` remains literal text.

```java
builder.label("heading", "<gold>Health</gold> <green>100</green>")
    .position(10, 12)
    .add();
```

Light and dark menus share one text font. Uncoloured text uses the theme's default colour. Animated dropdown overlay text currently uses its existing theme palette.

## Credits

- [Packer](https://github.com/DRAGNIL68/Packer) by **DRAGNIL68** is used to generate the resource-pack ZIP.
