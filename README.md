# Lumenkin

**Movimento luminoso e fluido** — Live wallpaper Android nativo em Kotlin.

## Package

```
com.elizierdias.lumenkin
```

- **applicationId fixo** → instalar uma nova build substitui a antiga (não cria app duplicada)
- **APK sempre com o mesmo nome**: `Lumenkin.apk`

## Build

```bash
gradle assembleDebug
# → app/build/outputs/apk/debug/Lumenkin.apk
```

## Aplicar wallpaper (nativo)

1. `WallpaperManager.setWallpaperComponent` (direto)
2. `ACTION_CHANGE_LIVE_WALLPAPER` (pré-visualização do sistema)
3. `ACTION_LIVE_WALLPAPER_CHOOSER` (lista)

## Estrutura

```
com.elizierdias.lumenkin/
├── MainActivity.kt
├── wallpaper/LumenkinWallpaperService.kt
└── util/WallpaperFileManager.kt
```
