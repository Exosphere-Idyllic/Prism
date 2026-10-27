# Manifest baseline & optimization report — Prism

- **Dispositivo:** Infinix Smart 7 (X669D / Android 12, API 31)
- **Fecha:** 2026-10-27
- **Rama:** `perf/manifest`

---

## 1. Pre-optimization baseline

### B.1 — Jank al escribir con teclado en búsqueda (`windowSoftInputMode="adjustResize"`)

Medición en dispositivo físico con `dumpsys gfxinfo com.example.prism` (20 caracteres en búsqueda):

| Métrica | Valor Baseline |
|---|---|
| Total frames | 45 |
| Janky frames (%) | 97.78% (44 / 45) |
| 50th percentile | 57 ms |
| 90th percentile | 150 ms |
| 95th percentile | 300 ms |
| 99th percentile | 850 ms |
| Number Slow UI thread | 44 |
| Number Frame deadline missed | 44 |

### B.2 — Recreación de Activity desde notificación
- Recreación detectada: **Sí** (sin `launchMode="singleTop"`, la Activity se relanza bajo el launchMode standard por defecto)
- `onNewIntent` implementado: **No** (`MainActivity.kt` carecía de `onNewIntent`)

### B.3 — Back gesture
- Disponible en Infinix físico: **No** (Android 12 / API 31 no cuenta con API de back predictivo)
- Soporte en código: `AppNavigation.kt` ya incluye `predictivePopTransitionSpec`, pero requería `enableOnBackInvokedCallback="true"` en el Manifest para Android 13+.

### B.4 — Backup redundante
- `allowBackup` efectivo: `false` (confirmado mediante `dumpsys package com.example.prism` flags)
- Rules redundantes: `dataExtractionRules` y `fullBackupContent` presentes innecesariamente en el Manifest y en `res/xml/`.

---

## 2. Post-optimization (commit db3334f)

### B.1 — Jank al escribir con teclado en búsqueda (`windowSoftInputMode="adjustNothing"` + `Modifier.imePadding()`)

| Métrica | Baseline | Post | Δ |
|---|---|---|---|
| 50th percentile | 57 ms | 53 ms | -4 ms (-7.0%) |
| 90th percentile | 150 ms | 85 ms | -65 ms (-43.3%) |
| 95th percentile | 300 ms | 150 ms | -150 ms (-50.0%) |
| 99th percentile | 850 ms | 200 ms | -650 ms (-76.5%) |

*Impacto:* Al desacoplar el relayout de ventana del ciclo de frames con `adjustNothing` y delegar los insets a Compose mediante `Modifier.imePadding()`, se eliminaron los picos catastróficos de jank (el percentil 99 cayó un 76.5%, pasando de 850ms a 200ms, y el p95 se redujo a la mitad de 300ms a 150ms).

### B.2 — Recreación de Activity desde notificación
- Recreación al volver desde notificación: **Eliminada** (`launchMode="singleTop"` aplicado en `AndroidManifest.xml`).
- Verificación en logcat: `Warning: Activity not started, intent has been delivered to currently running top-most instance.`
- `onNewIntent` implementado: **Sí** (recibe el intent y registra `MainActivity: onNewIntent received: ...`).

### B.3 — Back gesture predictivo
- `enableOnBackInvokedCallback="true"` configurado en `<application>`.
- `AppNavigation.kt` con Navigation 3 (`NavDisplay`) maneja `predictivePopTransitionSpec = { backEnter togetherWith backExit }` de forma nativa.
- En Android 12 (Infinix), el atributo es ignorado con cero regresión; en Android 13+ (API 33+) activa el sistema predictivo de salida.

### B.4 — Backup y limpieza de recursos
- Atributos eliminados del Manifest: `android:dataExtractionRules` y `android:fullBackupContent`.
- Archivos XML eliminados: `app/src/main/res/xml/backup_rules.xml` y `app/src/main/res/xml/data_extraction_rules.xml`.
- `dumpsys package com.example.prism` confirma que el backup permanece inactivo sin overhead de reglas muertas.

---

## 3. Commits aplicados en el Bloque Manifest

- [x] **Commit 1 (`9c15201`)**: `chore(manifest): add missing ACCESS_NETWORK_STATE permission`
- [x] **Commit 2 (`bd568c1`)**: `docs(manifest): baseline for manifest-related behavior`
- [x] **Commit 3 (`22ebf18`)**: `perf(activity): add singleTop launchMode to prevent recreation`
- [x] **Commit 4 (`d2ebe4d`)**: `perf(ui): enable predictive back gesture`
- [x] **Commit 5 (`a9759a4`)**: `perf(ui): migrate to adjustNothing and explicit IME insets`
- [x] **Commit 6 (`3a0d842`)**: `fix(notifications): request POST_NOTIFICATIONS at first launch`
- [x] **Commit 7 (`d88ef34`)**: `chore(manifest): remove unused backup rules`
- [x] **Commit 8 (`db3334f`)**: `docs(manifest): document intentional manifest configuration`
- [x] **Commit 9 (`[actual]`)**: `docs(manifest): update results after optimizations`

---

## 4. Conclusión

1. **Jank de entrada de texto**: Reducción masiva en colas de latencia (p95 bajó un 50% de 300ms a 150ms; p99 bajó un 76.5% de 850ms a 200ms).
2. **Ciclo de vida y estabilidad**: MainActivity no se destruye ni recrea al volver de background/notificación, previniendo interrupciones en el servicio de reproducción.
3. **Compatibilidad moderna (API 33+)**: Soporte para gestos predictivos de retroceso y solicitud proactiva de permiso `POST_NOTIFICATIONS` en runtime para la barra multimedia.
4. **Higiene de código y manifest**: Eliminación de reglas de backup inertes, adición de `ACCESS_NETWORK_STATE` y documentación exhaustiva inline de todas las directivas de arquitectura en `AndroidManifest.xml`.
