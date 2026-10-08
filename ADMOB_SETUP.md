# Activar AdMob real en Astro Siege

Guía paso a paso para reemplazar los IDs de test por los reales. El proyecto ya tiene todo el código de anuncios funcionando (banner, interstitial, rewarded) — lo único que falta es la cuenta de AdMob y pegar los IDs en dos archivos.

## 1. Creá la app en AdMob

1. Entrá a [admob.google.com](https://admob.google.com/) con la misma cuenta de Google que usás en Play Console (no hace falta que sea la misma, pero simplifica vincularlas después).
2. **Apps → Agregar app**. Cuando te pregunte "¿Tu app ya está publicada en una tienda de apps?":
   - Si todavía no subiste nada a Play Console → elegí **No** y completá manualmente nombre (`Astro Siege`) y plataforma (Android). Vas a poder vincularla a Play Console más adelante, apenas exista al menos un borrador ahí.
   - Si ya creaste el borrador de la ficha en Play Console (aunque no esté publicado) → elegí **Sí** y buscala por nombre o package (`com.nestordev.astrosiege`) — así queda vinculada desde el arranque y AdMob puede mostrarte métricas cruzadas con Play Console más adelante.
3. Al terminar, AdMob te muestra el **App ID**, con este formato: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`. Copialo.

## 2. Creá las 3 unidades de anuncio

Dentro de la app recién creada, **Unidades de anuncios → Agregar unidad de anuncios**, una por una:

| Formato | Nombre sugerido | Dónde se usa en el juego |
|---|---|---|
| Banner | `astro_siege_banner` | Hangar y Game Over |
| Interstitial | `astro_siege_interstitial` | Al salir de Game Over (máx. 1 cada 5 partidas) |
| Recompensado (rewarded) | `astro_siege_rewarded` | Continuar tras morir (opt-in) + monedas extra en el Hangar |

Cada unidad te da un ID con formato `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`. Copiá los tres.

## 3. Pegá los IDs en el proyecto

**a) App ID** → `app/src/main/AndroidManifest.xml`, buscá este bloque y reemplazá el valor (es el ID de test oficial de Google, no el tuyo):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713" />
```

**b) Las 3 unidades de anuncio** → `app/src/main/java/com/nestordev/astrosiege/AdManager.kt`, al principio del archivo:

```kotlin
const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
const val INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
const val REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
```

Reemplazá cada uno por el ID real que te dio AdMob para ese formato. Ojo con no mezclar el ID de banner en el de interstitial, etc. — son formatos distintos y AdMob rechaza el anuncio si no coincide.

## 4. Importante: no subas a Play Console con los IDs de test puestos

Las políticas de AdMob prohíben explícitamente dejar los IDs de test (`ca-app-pub-3940256099942544/...`) en una build de producción. Los IDs de test siempre muestran creativos de prueba de Google — sirven para desarrollar sin arriesgar tu cuenta, pero una vez que pegaste los reales, compilá y probá de nuevo antes de generar el AAB de release.

## 5. Consentimiento de anuncios (GDPR/UK — obligatorio si tu app puede tener usuarios ahí)

Google exige que las apps con AdMob implementen su **User Messaging Platform (UMP)** para pedir consentimiento de anuncios personalizados a usuarios en el Espacio Económico Europeo, Reino Unido y algunos otros mercados regulados — no es opcional, es requisito de política de AdMob (separado de la revisión de Google Play). El proyecto todavía **no tiene esto integrado** — es un SDK adicional (`play-services-ads` ya lo incluye como parte del paquete, pero hay que agregar el flujo de `ConsentInformation`/`UserMessagingPlatform` en `MainActivity`, antes de inicializar `MobileAds`). Si querés, te lo agrego en otra sesión — es código, no algo que dependa de tener los IDs reales primero, así que se puede hacer en paralelo o después, pero **antes de publicar en producción** si esperás usuarios en Europa/Reino Unido.

## 6. Probar con IDs reales sin generar tráfico inválido

Una vez que pusiste los IDs reales, si vas a jugar vos mismo para probar, hacelo como **dispositivo de test** — si no, AdMob puede marcar esos clics/impresiones como tráfico inválido (en casos repetidos, hasta suspender la cuenta). Dos formas:

- Más simple: dejá los IDs de test durante todo el desarrollo/QA y recién poné los reales en la build final que vas a subir a Play Console — no vuelvas a abrir esa build vos mismo para "probar que los anuncios se ven bien" más que lo mínimo indispensable.
- Si necesitás probar los IDs reales a fondo: registrá tu dispositivo como test device (`RequestConfiguration.Builder().setTestDeviceIds(listOf("TU_ID"))` — el ID de tu dispositivo aparece en el Logcat la primera vez que se carga un anuncio real, en un mensaje tipo "Use RequestConfiguration.Builder().setTestDeviceIds(...) to get test ads"). Pedime si querés que lo agregue al proyecto.

## 7. app-ads.txt (recomendado, no bloqueante)

Si en algún momento tenés un sitio web propio para Astro Siege (o usás el blog del desarrollador, `nestordev.blogspot.com`), AdMob recomienda publicar un archivo `app-ads.txt` ahí declarando que estás autorizado a vender el inventario de anuncios de la app. No es obligatorio para publicar ni para que los anuncios funcionen, pero sin él AdMob puede mostrar una advertencia de "vendedores no autorizados" y algunos anunciantes premium lo exigen para pujar. Se puede dejar para después del lanzamiento — no bloquea nada de lo anterior.

## 8. Checklist final antes de publicar

- [x] App creada en AdMob (astro-siege-android, ca-app-pub-4930505659937183~6794385855)
- [x] 3 unidades de anuncio creadas (banner, interstitial, rewarded)
- [x] App ID real pegado en `AndroidManifest.xml`
- [x] Los 3 ad unit IDs reales pegados en `AdManager.kt`
- [ ] Build compilada y probada en un dispositivo real con los IDs reales (banner en Hangar/Game Over, interstitial cada 5 Game Over, rewarded opt-in al morir y en "VER ANUNCIO: +50")
- [ ] Consentimiento UMP implementado (si esperás usuarios en EEE/Reino Unido) — avisame si querés que lo sume
- [ ] `app-ads.txt` publicado (opcional, se puede hacer después del lanzamiento)
