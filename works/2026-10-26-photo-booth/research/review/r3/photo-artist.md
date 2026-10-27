# Photo-artist review (packet3) - "따로 같이 네컷"

Lens: would people treasure the print. Stand-ins are judged only as rendered by the product.

## Findings, ranked

1. **Two people never share one light.** A is a cool, flat, low-contrast phone selfie (grey-green skin, lifted shadows). B is warm with strong highlights, red top, dark braids. On pink they read as two stickers, not one moment. Fix: before compositing, match the cut-out to the scene. Per-person gain on mean skin luminance (face region only) and a small shared white-balance pull toward the background hue. Cap at about 15% so skin does not shift identity. Add a faint shared pink rim or bounce on cut-out edges, 3-5% opacity.

2. **Dark skin is under-exposed, low-contrast and greyed.** A's face is a muddy 25-35% luminance with a clipped specular dot on the forehead (the window/ring-light blob). Under 뽀샤시 the face goes milky and ashy: the A cell in strip 2 looks fogged. Fix: make 뽀샤시 luminance-aware, not a uniform lift. Lift only mid-shadows with a gentle curve, keep a floor on black point, apply blur/softness to texture only (high-pass), and keep chroma. Do not add white-ish veil. Add a skin-tone-protect mask (HSV hue band, saturation) that limits brightening to about +10 L*.

3. **Cut-out edges are not believable at the bottom/sides.** Body cut ends in a hard horizontal edge at cell bottom, with ragged blue/teal fringe on A's sweater and a dark smudge at B's lower right. A's hand on the face gets chopped into a ghost patch (cut 1, cut 4). Fix: crop the cut-out bottom with the frame (extend alpha to 1 at the cell border so no fringe), apply 1-2 px alpha erosion plus colour decontamination (you already have +decontam, use it on all stills, the live path is worse), and drop the hand if the matte confidence is under a threshold rather than rendering a half hand.

4. **Sticker outline: glitter cheapens, solid white helps.** Glitter reads as speckled noise on faces this size and makes the matte errors louder because the outline hugs the bad edge. Solid white (normal) gives a clean, print-sticker look and hides small matte flaws. Black outline looks harsh on skin; dotted is busy. Fix: default to solid white normal. Keep glitter as an opt-in. Add a 3-4 px soft drop shadow under the outline so the sticker sits on the background instead of floating, and round the outline join (smooth the offset path) so the head silhouettes do not look bumpy.

5. **Composition inside the cell.** Fixed layout: both faces in the top half with a lot of empty pink above and unequal head sizes (B is about 15% bigger, and heads are cut at different heights). In the movable strip A gets small and cornered beside a large B, and it looks like a pasted thumbnail. Fix: normalise face width across the two (target eye distance equal within 10%) and align eye-line to about 38% of cell height. Crop tighter (reduce top headroom). In the movable case clamp the minimum A scale to about 70% of B and snap to a rule-of-thirds anchor.

6. **B&W tonality is flat.** Mono is a plain desaturate: dark faces collapse into a single grey, hair and background both go mid-grey, and the white vignette halo behind the pair turns into a glowing bubble. Fix: build mono from a weighted red-heavy channel mix (about 0.5R 0.4G 0.1B) so skin opens up, add an S-curve with a real black point, and a subtle grain. Do the conversion after matching the two people (finding 1), so the mono pair holds together.

7. **Background gradient and colour filters fight skin.** Warm/film push skin toward orange and the pink background goes salmon, which flattens the separation. Cool turns skin grey-blue. Fix: apply colour filters to the background and the people with different strengths (people at about 60%), and keep skin hue inside a fixed band after the filter.

8. **Banding in the gradient.** Visible concentric rings in the pink/purple background in filters-cell (halos). Fix: add 1-2% noise/dither at render time, which also helps the glitter and grain look consistent.

## Verdict
Fun and cohesive as a toy, not yet treasure-grade. The biggest wins for the least work: match light between the two cut-outs (1), skin-safe 뽀샤시 (2), solid white outline by default (4), and extending the cut-out to the cell border (3).
