# Astro Siege — proyecto Android

Wrapper nativo mínimo (una sola `Activity` con un `WebView`) que empaqueta el juego HTML5/Canvas
de Astro Siege como app de Android, con anuncios de AdMob, compra de "quitar anuncios" y un
recordatorio diario local — siguiendo la fase de **Packaging** del pipeline de lanzamiento
(`game-launch-pipeline`).

El juego en sí (HTML/CSS/JS) vive sin tocar en `app/src/main/assets/www/astro-siege.html`. Es
la misma build que el Artifact (https://claude.ai/artifact/Mks8ycPBQWaHrqU1QJ8CYN), con un
pequeño bridge nativo agregado (`Native` al principio del `<script>`) que solo se activa si
`window.AndroidBridge` existe — o sea, la versión web/Artifact sigue funcionando exactamente
igual, sin anuncios ni nada nativo, porque ahí ese bridge nunca está presente.

## Abrir el proyecto

1. Android Studio → **Open** → elegí esta carpeta (`AstroSiege/`).
2. Dejá que Gradle sincronice. Puede pedir actualizar el Android Gradle Plugin o el propio
   Android Studio si tu versión instalada es más vieja que las que usa este proyecto (AGP
   8.13.0, Gradle 8.13, Kotlin 2.2.20, compileSdk/targetSdk 36) — aceptá la actualización
   sugerida, es normal.
3. `applicationId` / paquete: **`com.nestordev.astrosiege`** (definido en
   `app/build.gradle.kts`). Cambialo ahí si preferís otro paquete — hacelo *antes* de crear la
   app en Play Console, porque el paquete no se puede cambiar después de publicar.
4. Con los IDs de prueba que ya vienen puestos (ver abajo) el proyecto compila y corre tal cual,
   mostrando anuncios de test de Google — no hace falta ninguna cuenta de AdMob para probarlo.

## Ficha de Play Store lista para publicar

`play_store_assets/` tiene todo lo que pide Play Console para la ficha: título + descripción
corta + descripción larga en inglés/español/portugués (`PLAY_STORE_LISTING.md`), 18 screenshots
de teléfono reales del juego (6 escenas × 3 idiomas, en `screenshots/`), el feature graphic
1024×500 en los tres idiomas (`feature_graphic/`) y el ícono de la ficha 512×512 (`icon/`).

## Dónde poner tus IDs reales de AdMob

Guía completa paso a paso en **`ADMOB_SETUP.md`** (en la raíz de este proyecto) — creación de la
app en AdMob, las 3 unidades de anuncio, dónde pegar cada ID, y el checklist de política
(consentimiento UMP, no subir con IDs de test, etc.). Resumen rápido acá:

Cuando crees la app en AdMob (vas a necesitar el paquete `com.nestordev.astrosiege` y, para
vincularla automáticamente, tenerla ya al menos como borrador en Play Console):

1. **App ID de AdMob** → `app/src/main/AndroidManifest.xml`, el `<meta-data>` con
   `android:name="com.google.android.gms.ads.APPLICATION_ID"`. Reemplazá el valor
   `ca-app-pub-3940256099942544~3347511713` (ID de test oficial de Google) por tu App ID real
   (formato `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`).
2. **Ad unit IDs** (banner, interstitial, rewarded) → creá los tres en AdMob (Apps → Astro
   Siege → Unidades de anuncios) y pegalos en
   `app/src/main/java/com/nestordev/astrosiege/AdManager.kt`, al principio del archivo:
   ```kotlin
   const val BANNER_UNIT_ID = "..."
   const val INTERSTITIAL_UNIT_ID = "..."
   const val REWARDED_UNIT_ID = "..."
   ```
3. **No subas a Play Console con los IDs de test puestos** — Google lo prohíbe explícitamente
   en política de AdMob. Son seguros solo para desarrollo/QA local.

## Dónde está cada anuncio (y por qué ahí)

Siguiendo los valores por defecto de monetización del pipeline (rewarded opcional, interstitial
muy espaciado, nunca a mitad de partida, banner solo en pantallas de menú):

- **Banner**: solo visible en Hangar y Game Over (pantallas de menú, con varios botones ya —
  nunca se confunde con un botón del juego). Se oculta apenas empieza a jugarse y en la
  pantalla de intro/atract (para no competir con el botón principal "TOCÁ PARA JUGAR"). Lo
  controla `GameBridge.onModeChange()`, que el juego llama en cada cambio de pantalla.
- **Interstitial**: se pre-carga en segundo plano y se muestra, como máximo, **una vez cada 5
  Game Over** (`AdManager.INTERSTITIAL_EVERY_N_GAME_OVERS` — bajado de 3 a 5 a pedido del
  usuario, "hay demasiada publicidad") — nunca en medio de una partida, solo al salir de la
  pantalla de Game Over (botón reintentar o cambiar nave).
- **Rewarded — continuar gratis**: al perder la última vida se muestra una pantalla propia del
  juego ("NAVE DESTRUIDA / ¿Mirás un video corto por una vida extra?") con dos botones — el
  video de AdMob solo se carga y se reproduce si el jugador toca explícitamente "VER VIDEO".
  Antes el video se disparaba automáticamente apenas se perdía la última vida, sin que el
  jugador lo pidiera — eso generaba la sensación de "demasiada publicidad" y además no es
  buena práctica para un rewarded (debe ser siempre opt-in). Si el jugador toca "NO, TERMINAR",
  o no hay video disponible, sigue el flujo normal a Game Over sin mostrar ningún anuncio.
- **Rewarded — monedas extra**: botón "VER ANUNCIO: +50 🪙" en el Hangar, monedas van directo a
  la economía existente del juego (mismo `localStorage` que las monedas por matar enemigos).
- **Quitar anuncios**: compra única (no consumible) vía Google Play Billing. Apaga banner e
  interstitial (el rewarded queda disponible igual, es opt-in del jugador). Ver siguiente
  sección para activarla.

## Activar la compra "Quitar anuncios"

1. Play Console → tu app → **Monetizar → Productos → Productos dentro de la app**.
2. Creá un producto **no consumible** con el ID exacto **`remove_ads`** (ese ID está
   hardcodeado en `BillingManager.PRODUCT_ID` — si querés otro ID, cambialo ahí también).
3. Hasta que ese producto exista en Play Console, el botón simplemente no hace nada útil (la
   compra falla con un toast de error) — el resto de la app funciona igual.
4. Las compras solo se pueden probar de verdad con un **build firmado subido a Play Console**
   (interno/cerrado alcanza) y una cuenta de tester agregada en licencias de prueba — Billing
   no funciona con builds de debug sin firmar ni instalados por fuera de Play.

## Firmar y generar el AAB de release

1. Android Studio → **Build → Generate Signed Bundle / APK → Android App Bundle**.
2. Si no tenés un keystore todavía: **Create new...** en ese mismo diálogo, guardalo en un
   lugar seguro **fuera** de este repo (el `.gitignore` ya excluye `*.jks`/`*.keystore` por las
   dudas) y anotá bien la contraseña — Google Play exige la misma clave para todas las
   actualizaciones futuras de esta app.
3. Elegí el build type `release`. El AAB firmado queda en `app/release/`.
4. Instalalo en un dispositivo real y jugá una partida completa antes de subirlo — confirmá que
   los anuncios de test aparecen donde deberían y que no hay crashes, antes de reemplazar los
   IDs de test por los reales.

## Ícono de la app

`gen_icons.py` genera todos los `mipmap-*/ic_launcher*.png` (legacy + adaptive icon) y el ícono
de notificación (`drawable-*/ic_notification.png`) a partir de la misma silueta de nave que usa
el juego, sobre el fondo oscuro de la paleta del juego. Es un placeholder funcional, no arte
final. Para cambiar el diseño: editá `gen_icons.py` y corré `python3 gen_icons.py` de nuevo, o
directamente reemplazá los PNG a mano / con la herramienta **Image Asset** de Android Studio
(clic derecho en `res` → New → Image Asset), usando `play_store_icon_512.png` como base.

## Otras cosas nativas agregadas (más allá de "un sitio en un ícono")

- **Vibración** en impactos y power-ups (`GameBridge.haptic`), llamada desde el propio HTML del
  juego en los mismos puntos donde ya sonaba un efecto de audio.
- **Guardado local**: el `WebView` tiene `domStorageEnabled` activado, así que el mismo
  `localStorage` que ya usa el juego (monedas, naves, high score, idioma) persiste de forma
  nativa entre sesiones — no se agregó una capa de guardado separada para no duplicar estado.
- **Recordatorio diario**: una notificación local (no requiere backend ni Firebase) una vez por
  día si el usuario no jugó, con permiso de notificaciones pedido la primera vez que abre la
  app (Android 13+). Si lo rechaza, simplemente nunca se dispara — no hay una pantalla de
  ajustes en el juego para esto, se administra desde la configuración de notificaciones del
  sistema.

## Localización

El propio juego ya soporta inglés/español/portugués con detección automática por idioma del
teléfono (ver `astro-siege-contexto.md` en el proyecto de Claude) — no hace falta duplicar
strings de juego a nivel Android. Los `strings.xml` de este proyecto (`values`, `values-es`,
`values-pt`) son solo para los textos 100% nativos: nombre de la app, canal/texto de la
notificación diaria, y los toasts de compras/anuncios.
