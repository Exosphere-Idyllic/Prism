# Manifest baseline — pre-optimization

Dispositivo: Infinix Smart 7 (X669D, Android 12)
Fecha: 2026-10-27
Rama: `perf/manifest`

## B.1 — Jank al abrir teclado en búsqueda (windowSoftInputMode="adjustResize")

Medición realizada en dispositivo físico (Infinix Smart 7 / Unisoc SC9863A1):
- Apertura de teclado e introducción de 20 caracteres de búsqueda (`testingsearchqueryxx`).

| Métrica | Valor |
|---|---|
| Total frames | 45 |
| Janky frames (%) | 97.78% (44 / 45) |
| 50th percentile | 57 ms |
| 90th percentile | 150 ms |
| 95th percentile | 300 ms |
| 99th percentile | 850 ms |
| Number Slow UI thread | 44 |
| Number Frame deadline missed | 44 |

*Observación:* Con `adjustResize`, el relayout continuo de ventana provocado por el teclado genera caídas de frames críticas en hardware de gama baja (97.7% jank, p50 en 57ms frente al umbral de 16.6ms).

## B.2 — Recreación de Activity desde notificación
- Recreación detectada: [x] Sí (sin `launchMode="singleTop"`, la Activity se relanza bajo el launchMode standard)
- onNewIntent implementado: [ ] No (`MainActivity.kt` no implementa `onNewIntent`)
- Observaciones: Sin `singleTop`, retornar a la app desde la notificación puede causar recomposición o recreación de la instancia de Activity.

## B.3 — Back gesture (verificar en Android 13+)
- Disponible en test Infinix actual: NO (Android 12 / API 31 no soporta predictive back callbacks a nivel de sistema)
- Emulador disponible: Sí (API 37 en Android Studio)
- Estado: Navigation 3 ya tiene configurado `predictivePopTransitionSpec`, pero requiere `enableOnBackInvokedCallback="true"` en el Manifest para que el sistema lo active en API 33+.

## B.4 — Backup
- allowBackup efectivo: `false` (confirmado con dumpsys package: `flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]`, sin `ALLOW_BACKUP`)
- Rules redundantes: `dataExtractionRules` y `fullBackupContent` son código muerto.
