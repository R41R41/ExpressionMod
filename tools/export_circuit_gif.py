#!/usr/bin/env python3
"""Export native 854x480 Minecraft preview frames using a shared GIF palette. Requires Pillow."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--screenshots", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--label", default="Body circuit flow")
    args = parser.parse_args()
    frames = []
    for index in range(84):
        path = next(args.screenshots.glob(f"*_circuit-front-{index:02d}.png"))
        with Image.open(path) as source:
            if source.size != (854, 480):
                raise SystemExit(f"Unexpected native screenshot dimensions: {source.size}")
            frame = Image.new("RGB", (856, 680), "#1b2934")
            frame.paste(source.crop((320, 92, 534, 418)).resize((428, 652), Image.Resampling.NEAREST), (0, 28))
            frame.paste(source.crop((374, 92, 478, 188)).resize((416, 384), Image.Resampling.NEAREST), (440, 28))
            draw = ImageDraw.Draw(frame)
            draw.text((12, 8), args.label, fill="#dceef8")
            draw.text((452, 8), "Living eye gradient", fill="#dceef8")
            frames.append(frame)
    atlas = Image.new("RGB", (856, 680 * len(frames)))
    for index, frame in enumerate(frames):
        atlas.paste(frame, (0, 680 * index))
    palette = atlas.quantize(colors=256, dither=Image.Dither.NONE)
    indexed = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    indexed[0].save(args.output, save_all=True, append_images=indexed[1:], duration=200, loop=0, optimize=False)
    frames[14].save(args.output.with_name(args.output.stem + "-still.png"))
    print(f"Created {args.output.resolve()}: 84 native frames, 16.8s loop.")


if __name__ == "__main__":
    main()
