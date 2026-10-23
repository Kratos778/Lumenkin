# Lumenkin

**Movimento luminoso e fluido** — Aplicação Android nativa em Kotlin para transformar vídeos (incluindo 4K/2K) em papéis de parede animados (live wallpapers) reais, otimizada para dispositivos de baixo desempenho e Android Go Edition.

## Identidade e Missão

- **Nome**: Lumenkin (Lumen + Kinesis)
- **Missão**: Experiência visual fluida, elegante e dinâmica com máxima eficiência energética e poupança rigorosa de memória.
- **Público-alvo**: Dispositivos de baixo desempenho, Android 15 Go Edition e hardware limitado.

## Pilares Técnicos

### 1. Motor Nativo (WallpaperService)
Integração oficial via `WallpaperService` + `WallpaperManager`. Não é uma camada de UI sobreposta — é um serviço real do sistema.

### 2. Compressão Dinâmica de Resolução
Vídeos pesados (4K/2K) são adaptados para resoluções otimizadas (ex: 720p) antes de serem aplicados, respeitando a densidade de pixels do ecrã.

### 3. Gestão Anti-Sobreposição de Ficheiros
- Caminho e nome **fixos**: `lumenkin_active_wallpaper.mp4`
- Antes de aplicar um novo wallpaper:
  1. Destrói a instância anterior do player
  2. Apaga o ficheiro existente
  3. Grava e carrega o novo vídeo
- Garantia de zero ficheiros órfãos.

### 4. Gestão Inteligente de Energia
- `MediaPlayer` é pausado em `onVisibilityChanged` e quando o ecrã desliga (`ACTION_SCREEN_OFF`).
- Consumo de processamento desce para zero com o ecrã desligado.

### 5. Áudio Mudo por Padrão
Qualquer wallpaper inicia com volume `0f, 0f`. O utilizador ativa o som explicitamente.

## Arquitetura

```
app/
├── src/main/
│   ├── java/com/lumenkin/
│   │   ├── MainActivity.kt
│   │   ├── wallpaper/
│   │   │   └── LumenkinWallpaperService.kt
│   │   └── util/
│   │       ├── WallpaperFileManager.kt
│   │       └── VideoOptimizer.kt
│   ├── res/
│   └── AndroidManifest.xml
```

## Requisitos

- minSdk 24 (Android 7.0)
- targetSdk 35
- Kotlin 2.0+
- Sem dependências pesadas de terceiros

## Como construir

1. Abre o projeto no Android Studio
2. Sincroniza o Gradle
3. Executa no dispositivo/emulador

## Filosofia de Desenvolvimento

- Prioridade absoluta: baixo consumo de RAM e CPU
- Código modular, direto e legível
- Sem over-engineering
- Blocos prontos a integrar

---

**Mr.K** — Lumenkin nasce com base sólida, limpa e estruturada para desafiar qualquer limitação de hardware.
