# Category Icon Design

The category icon system follows the supplied One UI-inspired reference:

- 56dp rounded-squircle container on standard category tiles.
- Minimal geometric glyphs with consistent stroke/weight.
- Neutral tonal surfaces rather than glossy rainbow tiles.
- One restrained semantic accent color per category.
- Very subtle depth from low elevation only; no large glow or heavy shadow.
- Light and dark mode palettes are intentionally tuned rather than inverted.
- The same `CategoryVisual` component is used across Add Transaction, Categories, Create/Edit Category and transaction-related category surfaces.
- Every category from the verified Moneyview vocabulary is mapped to a meaningful custom glyph; new custom names use a neutral custom glyph without changing existing category IDs.
- User-selected category pictures are downsampled, stored in app-private storage and rendered inside the same rounded frame.
