# Appearance

Generated NPCs are assembled from layers instead of using one fixed skin. Hand-made skins stay supported, for unique characters and anyone who wants them.

## Layers

Each layer is a 64x64 skin-format PNG. Fixed layers are drawn in full color and used as they are. Recolorable layers are recolored by palette swap (a gradient map), not by multiplying a tint, so dark colors keep their shading:

| Pixel in a recolorable layer | Becomes |
|---|---|
| `#202020` | palette shade 1: deepest shadow, inner lines |
| `#505050` | palette shade 2: shadow |
| `#808080` | palette shade 3: mid shade |
| `#B0B0B0` | palette shade 4: base color (most of the area) |
| `#E0E0E0` | palette shade 5: highlight |
| anything else | itself, unchanged |

- Every color option (a hair color, a dye, a skin tone, an eye color) is a palette of five colors, darkest to lightest, defined in a small text file. Using the same five grays in every part means a palette looks the same on hair, shirts and pants.
- Anything that isn't one of the five grays passes through untouched: pure white eye whites and black pupils on an eye layer (only the iris recolors), a brown belt on a recolorable shirt, a colored gem. So a part never needs splitting into two files just to keep a detail fixed.
- Pixel art only: no soft brushes or anti-aliasing, so every pixel is either one of the five grays or meant to stay as drawn.

| Layer | Recolorable? | Palette | Examples |
|---|---|---|---|
| Base | yes | skin tone | the body, in wide and slim arm versions |
| Eyes | yes (iris only) | eye color | several eye shapes; whites and pupils stay as drawn |
| Mouth | yes (the skin's palette) | skin tone | mouths and expressions; lips and shading in the grays |
| Facial hair | yes | hair color | beards, moustaches |
| Hair | yes | hair color | styles, using the outer layer for volume |
| Tops | either | dye color, if recolorable | shirts, tunics, dresses, aprons |
| Bottoms | either | dye color, if recolorable | trousers, skirts, shoes |
| Outfits | either | dye color, if recolorable | a top and bottom in one: uniforms, dresses, overalls |
| Accessories | no | | scarves, glasses, hats |

Tops and bottoms are recolorable or fixed per item: plain clothes come in any dye color, while patterned or detailed ones keep their drawn colors.

An outfit is drawn over the body like a top and bottom together, and while one is worn the separate top and bottom aren't drawn at all (they stay chosen, for when the outfit comes off). Random NPCs wear an outfit 35% of the time, if there are outfits for their gender; jobs will hand out their own (a guard's uniform in the faction's colors).

Layers are stacked in order and combined into a single texture per NPC, built once and cached by its exact combination. Two NPCs with identical looks share a texture. Rebuilding a 64x64 texture is cheap, so changing clothes costs almost nothing.

Variety comes from multiplying a modest amount of art: a dozen hairstyles in many colors, a range of skin tones, and clothing in dye colors already gives millions of combinations.

## Where they're from

Every NPC has a homeland: the biome it was first seen in (a child's is where it was born). Skin tone leans on how warm that biome is. Skin palettes are listed palest first, and the climate picks a spot along that list: tundra and taiga toward the pale end, plains and forests in the middle, deserts, outbacks and rainforests toward the dark end. The pick is spread about 1.3 palettes either way, so any place still has a range. Children take their skin tone from a parent instead.

The homeland also feeds speech: "Where are you from?" in the questions menu, remarks when the climate is colder or hotter than home ("I'm from the desert, I'm not built for this"), homesickness, and feeling at home on returning to their own biome.

## Clothing that changes

An NPC's look is split into **body** (skin, eyes, face, hair: fixed for life, except hair could grow or be cut later) and **outfits** (sets of clothing layers). NPCs change outfits with their job and schedule: work clothes for the farmer, an apron for the cook, nightclothes for sleeping, something nicer for a wedding. Armor still draws on top of everything, as it does now.

## Inheritance

Children inherit body traits: skin tone, hair color and eye color each from one parent or the other, and often a parent's hairstyle, eyes or mouth. Families visibly look related. Children don't get a skin tone from their homeland's climate; their parents' does that. Planned: a player who builds their own look from parts ([player creation](family.md#player-creation-planned)) passes it on to their children the same way; a player with a plain Minecraft skin passes on nothing yet.

## The art

Parts live in two places with the same layout: bundled with the mod (`assets/lifeblood/parts/`) and in the game folder's `npc_parts/`, which can be added to without rebuilding (`/lb reload parts` picks up changes). Game-folder files win over bundled ones with the same name.

- `<layer>/<name>.png`: a 64x64 skin-format part anyone can roll. Layer folders: `base`, `eyes`, `mouth`, `facial_hair`, `bottom`, `top`, `outfit`, `hair`, `accessory` (drawn in that order).
- `<layer>/male/<name>.png`, `<layer>/female/<name>.png`: parts only rolled for that gender (a command can still put them on anyone).
- `<name>_slim.png` beside `<name>.png`: a hand-made slim-armed version. Women are slim unless their skin model says otherwise (men follow their base part's arm width). A slim NPC wearing a part with no slim version gets the wide one squeezed: the faces as wide as the arm (front, back, top, bottom) lose an inner column, keeping both edges, which is how a wide skin is usually redrawn as slim.
- `palettes.txt` (a swatch sheet of the bundled palettes is in [palette_reference.html](palette_reference.html)): `[skin]`, `[hair]`, `[eyes]`, `[dye]` groups of `name: #c1 #c2 #c3 #c4 #c5` lines, darkest to lightest. The mod ships defaults (8 skin tones, 12 hair colors, 8 eye colors, and the 16 dye colors each in a bright and a muted version); a game-folder file adds to or overrides them.

Whether a part is recolorable isn't declared anywhere: any of the five grays in it get recolored, and everything else is drawn as is. The mouth is recolored with the base's skin palette and facial hair with the hair's, so they always match. Old-style 64x32 parts work too: they're upgraded like old skins (left limbs mirrored from the right, and a completely filled hat area cleared).

Random looks: every NPC without a skin file gets one from the parts available, rolled once (NPCs from before any parts existed get one when parts are added). Hair is almost always present (92% for men, 99% for women), facial hair 35% for men, an accessory 15%. Hair and facial hair share a color. A skin file set with `/lb look skin` still wins over the look.
