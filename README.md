# CoreFixes

A lightweight Paper/Purpur plugin that fixes vanilla mechanics broken by the server implementation.

## Fixes

### Wind Burst (Mace enchantment)
On Paper/Purpur servers the upward launch effect from the Wind Burst enchantment doesn't work — the server sends a `ClientboundSetEntityMotionPacket` which the 1.21+ client ignores for the local player. CoreFixes intercepts the hit and sends a `ClientboundPlayerPositionPacket` with relative delta movement so the player actually gets launched.

## Requirements

- Paper / Purpur 1.21.1+
- Java 21+

## Installation

Drop the jar into your `plugins/` folder and restart the server.

---

# CoreFixes (RU)

Лёгкий плагин для Paper/Purpur, исправляющий ванильные механики, сломанные серверной реализацией.

## Исправления

### Порыв ветра (зачарование булавы)
На серверах Paper/Purpur зачарование «Порыв ветра» не подбрасывает игрока вверх — сервер отправляет `ClientboundSetEntityMotionPacket`, который клиент 1.21+ игнорирует для локального игрока. CoreFixes перехватывает удар и отправляет `ClientboundPlayerPositionPacket` с relative delta movement, чтобы игрок действительно взлетал.

## Требования

- Paper / Purpur 1.21.1+
- Java 21+

## Установка

Положи jar в папку `plugins/` и перезапусти сервер.
