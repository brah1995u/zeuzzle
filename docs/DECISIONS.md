# Decisions

## 2026-08-13 — Original runtime art for the first playable build

The repository began empty, without approved source art, fonts or audio. The initial playable build renders its storm, altar, hero silhouette, fortress, enemies, UI and effects procedurally with the canonical palette. It intentionally uses original geometric character forms rather than any reference-derived asset. This keeps the playable prototype self-contained while the approved production asset pipeline is established.

## 2026-08-13 — Campaign representation

The 90 campaign levels are deterministic data definitions generated from six worlds of fifteen levels. The first build varies casts, targets, structure rows, rewards and power selection. Full authored replay validation remains a later production gate.

## 2026-08-15 — Olympus Merge portrait redesign and bonus game

The approved portrait reference is now the visual source of truth for the idle merge screen and is rendered at a matching 858×1920 virtual resolution. Live score, glory, gestures, pause controls and boosters are layered as runtime interactions. A separate generated, text-free Olympus scene supports the playable Zeus Crystal Storm bonus mode; its HUD, crystals, coins, lightning, timer and reward persistence are code-rendered so gameplay remains deterministic and local.
