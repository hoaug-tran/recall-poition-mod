# Recall Potion Mod

A Fabric mod that adds Recall Potions to teleport players to their bed or death location, along with teleportation commands.

## Features
- **Home Recall Potion**: Teleports you to your bed location (or your `/sethome` location).
- **Death Recall Potion**: Teleports you to your last death location.

## Commands & Mechanics
- `/sethome`: Set your personal home location (has a configurable cooldown).
- `/htp <player>`: Send a teleport request to another player. 
  - The target player can accept with `/htpaccept` or deny with `/htpdeny`.
  - HTP requests can have a cooldown and an economy cost (TPA cost).
- `/recalladmin`: Admin command to configure cooldowns, timeouts, TPA cost, and reset player cooldowns.

## Recommendations
We highly recommend installing the **ServerShop** mod (`servershop`). When installed, you can configure `/htp` teleportations to cost money, integrating seamlessly with the server's economy!

## Setup
Built for Minecraft 1.21.x with Fabric Loader.
