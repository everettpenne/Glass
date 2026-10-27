# Suite app icons

The launcher icons of Uno, Thumper, Spice, Baliset, Sietch and Seekur share one recipe (the iOS 26 look): a vivid gradient tile with a soft light from the
upper left and a slight darkening at the bottom, a white or cream glyph with a stacked soft shadow under it, the glyph kept inside the 66 dp safe zone,
and a one-colour monochrome layer for themed icons.

- `mk.py` - helpers that write Android vector drawables (gradients, paths, shadow copies, the tile).
- `icons.py` - one section per app; run it to write each app's `ic_launcher_background.xml`, `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`
  into `gen/<app>/drawable/` (or `$ICON_OUT`). Copy them over the app's drawables.
- `vd2svg.py` / `sheet.py` / `png.py` - render a vector drawable (needs `rsvg-convert` and Pillow) to check it, make a contact sheet, or export a masked
  PNG for a README.

Change the tile colours in a section's `background(top, bottom)` call, or the shadow depth in the `ty` values, and re-run.
