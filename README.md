# ExpressionMod

Minecraft Fabric MOD for smooth eyelid blinking animations.

## Features

- **Smooth Eyelid Animation**: Eyelashes move smoothly instead of frame-by-frame texture switching
- **Customizable via Skin**: Draw expression textures in unused skin regions (0,0)-(7,7)
- **Emote Support**: Trigger expressions with keyboard shortcuts (F1-F6)
- **Multiplayer Compatible**: All players need the MOD to see each other's expressions

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.10+
- Fabric API

## Installation

1. Install Fabric Loader for Minecraft 1.21.4
2. Place the MOD JAR in `.minecraft/mods/`
3. Prepare your skin with expression textures (see SKIN_FORMAT.md)

## Key Bindings

| Key | Action |
|-----|--------|
| F1 | Smile |
| F2 | Angry |
| F3 | Sad |
| F4 | Surprised |
| F5 | Thinking |
| F6 | Shy |
| F7 | Clear Cache |

## Skin Format

See [SKIN_FORMAT.md](SKIN_FORMAT.md) for detailed instructions on preparing your skin.

### Quick Overview

Draw in the unused region (0,0)-(7,7) of your 64x64 skin:

```
Row 0: Eyelashes (8x1px)
Row 1-2: Eyes (8x2px)
Row 3: Skin color (8x1px)
Row 7, Col 7: Magenta marker (#FF00FF) to enable features
```

## How It Works

1. The MOD reads your skin and extracts eyelash/eye/skin textures from (0,0)-(7,7)
2. During rendering, the eyelash Y-position is animated smoothly (0-2px)
3. As eyelashes move down, the visible eye area shrinks from 2px to 0px
4. Blinks occur randomly every 3-6 seconds

## Building

```bash
./gradlew build
```

Output: `build/libs/expressionmod-1.0.0.jar`

## License

MIT License
