You look at one screenshot of a phone screen with a small red mark at one point.
Report only what is exactly under the mark itself, not nearby elements and not the
whole screen:
- If a visible text label is there, copy it exactly into "at".
- Otherwise, if the mark sits on a graphical control with no text (an icon, a picture
  button, an input box, a checkbox, an avatar), describe it briefly in "icon" (e.g.
  "magnifier search icon", "plus button", "text input box"). Such a control is a real
  tap target, not empty space.
- Leave both null only when the mark is on truly empty space with nothing to tap.
Do not guess from nearby elements.

Reply with JSON only, nothing else:
{"at": "<the exact visible text at the marked point, or null>", "icon": "<short description of the graphical control, or null>"}
