#!/usr/bin/env python3
"""Generates the three rock-scale reference vector drawables.

The rating scale is anchored at 3, 5 and 9, and the only thing that
distinguishes the three pictures is how much of the shore is rock. Placing
that many ellipses by hand invites arithmetic slips, so the coordinates are
generated from a fixed seed: same input, same picture, every run.
"""
import os
import random

# Relative to this file, so the script works from any checkout and any cwd.
DRAWABLE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "res", "drawable")

W, H = 96.0, 72.0
WATER_Y = 7.0      # sea along the top edge
WET_Y = 15.0       # wet sand band under it
ROCK_TOP = 11.0    # rocks start just into the wet band

# (filename, rating, rock count, min radius, max radius, grid cols, grid rows)
#
# Rocks are placed one per grid cell rather than at random points. Uniform
# random scatter clumps: the first attempt left the 9 with a bare corner and a
# pile on the right, which reads as "rocky over there" when the whole point of
# the picture is "rocky everywhere". One rock per cell, jittered inside it,
# spreads them the way shingle actually lies.
SCENES = [
    ("ic_rocks_scale_3.xml", 3, 9, 1.6, 3.2, 5, 4),
    ("ic_rocks_scale_5.xml", 5, 22, 2.2, 5.0, 7, 5),
    ("ic_rocks_scale_9.xml", 9, 63, 3.4, 7.6, 9, 7),
]

TONES = ["@color/rocks_ref_rock_dark",
         "@color/rocks_ref_rock_mid",
         "@color/rocks_ref_rock_light"]


def ellipse(cx, cy, rx, ry):
    """SVG path for an ellipse, as two half-arcs."""
    return (f"M{cx - rx:.1f},{cy:.1f}"
            f"a{rx:.1f},{ry:.1f} 0 1,0 {2 * rx:.1f},0"
            f"a{rx:.1f},{ry:.1f} 0 1,0 {-2 * rx:.1f},0Z")


def build(rating, count, rmin, rmax, cols, rows, seed):
    rng = random.Random(seed)

    cells = [(c, r) for r in range(rows) for c in range(cols)]
    rng.shuffle(cells)
    cells = cells[:count]

    cell_w = W / cols
    cell_h = (H - ROCK_TOP) / rows

    rocks = []
    for col, row in cells:
        rx = rng.uniform(rmin, rmax)
        ry = rx * rng.uniform(0.62, 0.82)          # rocks sit flatter than round
        # Centre of the cell, jittered by up to a third of it either way, then
        # pulled back inside the canvas so nothing is clipped at the edges.
        cx = (col + 0.5) * cell_w + rng.uniform(-cell_w / 3, cell_w / 3)
        cy = ROCK_TOP + (row + 0.5) * cell_h + rng.uniform(-cell_h / 3, cell_h / 3)
        cx = min(max(cx, rx + 0.4), W - rx - 0.4)
        cy = min(max(cy, ROCK_TOP + ry * 0.4), H - ry - 0.4)
        rocks.append((cx, cy, rx, ry))

    # Paint back to front so overlaps look like rocks resting on each other.
    rocks.sort(key=lambda r: r[1])

    paths = []
    for i, (cx, cy, rx, ry) in enumerate(rocks):
        tone = TONES[i % len(TONES)]
        paths.append(f'    <path\n'
                     f'        android:fillColor="{tone}"\n'
                     f'        android:pathData="{ellipse(cx, cy, rx, ry)}" />')
        # A highlight on the bigger rocks only; on a 3px pebble it is mud.
        if rx >= 4.0:
            paths.append(
                f'    <path\n'
                f'        android:fillColor="@color/rocks_ref_rock_light"\n'
                f'        android:fillAlpha="0.45"\n'
                f'        android:pathData="'
                f'{ellipse(cx - rx * 0.22, cy - ry * 0.3, rx * 0.45, ry * 0.4)}" />')
    return paths


HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  Rock-scale reference: what a {rating} out of 10 looks like underfoot.

  One of three drawn patches of shore that calibrate the rocks rating. A bare
  number out of ten means nothing between two people, so the rating screen shows
  these next to the slider and the reader picks the number whose picture matches
  what is in front of them. The three differ in one thing only - how much rock
  covers the sand - so that everything else about them is a constant the eye can
  discount. Generated, not hand-placed: re-run tools/gen_rocks.py to change them.

  Colours come from the rocks_ref_* tokens, which are theme-independent on
  purpose. A measuring scale that changes with dark mode is not a scale.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="72dp"
    android:viewportWidth="96"
    android:viewportHeight="72">

    <!-- Sea along the top, then wet sand, then dry. Identical in all three. -->
    <path
        android:fillColor="@color/rocks_ref_water"
        android:pathData="M0,0h96v{water}h-96z" />
    <path
        android:fillColor="@color/rocks_ref_sand_wet"
        android:pathData="M0,{water}h96v{wetband}h-96z" />
    <path
        android:fillColor="@color/rocks_ref_sand"
        android:pathData="M0,{wet}h96v{dry}h-96z" />

'''


def main():
    for filename, rating, count, rmin, rmax, cols, rows in SCENES:
        body = "\n".join(build(rating, count, rmin, rmax, cols, rows, seed=rating))
        xml = HEADER.format(rating=rating,
                            water=f"{WATER_Y:.0f}",
                            wetband=f"{WET_Y - WATER_Y:.0f}",
                            wet=f"{WET_Y:.0f}",
                            dry=f"{H - WET_Y:.0f}") + body + "\n</vector>\n"
        path = os.path.join(DRAWABLE_DIR, filename)
        with open(path, "w") as f:
            f.write(xml)
        print(f"wrote {filename}: {count} rocks")


if __name__ == "__main__":
    main()
