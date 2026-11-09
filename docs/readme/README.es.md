<!-- translated-from: README.md @ 587bea3f5e22 -->
# Maeve

[English](../../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-Hant.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · Español

**Ella ejecuta el bucle. Tú conservas la pantalla.**

Un agente de IA para Android que resuelve tareas en una pantalla que nunca ves,
para que tu teléfono siga siendo tuyo.

*Maeve es un proyecto Apache-2.0 independiente. Consulta [NOTICE](../../NOTICE) para la atribución de código y recursos de terceros.*

*Maeve no está afiliada ni respaldada por HBO, Warner Bros. ni Westworld.
El nombre se usa como nombre propio común y alude al escenario de la serie.*

> *"Surviving is just another loop."* — Maeve Millay, *Westworld* S1E7

Nombre en clave de la versión: **Dolores** · `v0.1.0` · [SPEC.md](../../SPEC.md)

---

## Qué hace

Cuéntale a Maeve lo que necesitas. Abre una pantalla de fondo oculta, lee la pantalla y
opera tus apps y la web hasta terminar el trabajo. Tu pantalla principal nunca se ocupa.

- **Tu teléfono sigue siendo tuyo.** El trabajo corre en una segunda pantalla virtual; tú sigues desplazándote, viendo o escribiendo.
- **Interrumpe o toma el control cuando quieras.** Añade requisitos a mitad de un paso o toma los mandos.
- **Pregunta antes de lo irreversible.** Pagos, borrados y envíos pasan por una puerta de confirmación.
- **Recuerda.** Las preferencias y los flujos repetidos se convierten en memoria y habilidades para la próxima vez.

<details>
<summary>Conocimiento integrado de apps y sitios web (las apps no listadas también funcionan)</summary>

La lista la genera `./gradlew updateReadme` y aparece en el apartado plegable del
[README en inglés](../../README.md) y del [README en chino simplificado](README.zh-CN.md).

</details>

<details>
<summary>Presets de proveedores de modelos</summary>

La lista de presets también la genera `./gradlew updateReadme`; consulta el
[README en inglés](../../README.md). También puedes iniciar sesión sin clave con una cuenta de
ChatGPT (Plus / Pro), xAI / Grok u OpenRouter.

</details>

## Dos formas de entrar

Maeve tiene un solo núcleo de ejecución y dos formas de recibir una tarea. Ambas terminan en
el mismo bucle del agente y la misma cadena de guardas: una tarea remota no es una segunda
implementación más débil.

| Ruta | Entrada | Uso típico |
|---|---|---|
| **R1 · En el dispositivo** | La propia interfaz de chat de Maeve | Pídelo directamente en el teléfono; la accesibilidad y la pantalla virtual de fondo hacen el trabajo. |
| **R2 · Dirigido por otro agente** | MCP entrante en `/mcp`, o el puente de archivos del dispositivo que usan ciertos runtimes de agentes del sistema | Deja que un asistente de escritorio o un agente del dispositivo delegue una tarea al teléfono y lea el resultado. |

R1 no necesita un dispositivo acompañante. R2 es opcional y está desactivado por defecto,
salvo la capa del puente de archivos, que se configura una vez y luego no molesta. Las dos
rutas remotas **no son intercambiables**: MCP es un servicio de red, mientras que el puente
de archivos existe porque algunos runtimes de agentes en sandbox no pueden alcanzar el
puerto loopback del teléfono.

## Qué necesitas

- Android 11 (API 30) o posterior. La pantalla de fondo funciona mejor en Android 14+.
- **Una autorización única de ADB o depuración inalámbrica.** La pantalla virtual la crea un
  proceso auxiliar que corre como `shell`, y el acceso a la pantalla pasa por un servicio de accesibilidad.
  No hay configuración sin permisos.
- **Tu propio proveedor de LLM.** Maeve ejecuta en el dispositivo, pero planificar requiere un modelo;
  configura el endpoint y la clave en Ajustes. No se incluyen pesos de modelos.
- Opcional para R2: un ordenador en la misma red, o `adb forward` por USB.

La configuración de red permite HTTP en claro por ahora: usa un endpoint de confianza y una
red que controles. No interpretes la puerta de confirmación como una garantía total de privacidad.

## Compilar desde el código fuente

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

El repositorio tiene dos módulos Gradle y un módulo Flutter incrustado:

| Ruta | Qué es |
|---|---|
| `:core` | Kotlin de JVM puro. Bucle del agente, herramientas, contexto, guardas, conocimiento y habilidades. |
| `:app` | Host de Android. Accesibilidad, pantalla virtual, proceso auxiliar de shell, WebView, sandbox, permisos y las pantallas Compose que siguen en uso. |
| `flutter/` | El shell Flutter que está sustituyendo a Compose. `settings.gradle.kts` no lo incluye como módulo Gradle; `:app` consume el AAR de debug que produce. |

El host de Android no compila hasta que exista ese AAR: la compilación del módulo Flutter
escribe `flutter-debug.aar` bajo `flutter/`, y ese directorio generado no se versiona.
Compila primero el módulo Flutter y luego ensambla la app.
[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) recoge las trampas de toolchain, caché y proxy.

La firma de release, R8 y CI forman parte de [M4](../../SPEC.md#10-里程碑与验收).

## Estado

El repositorio está en **M2 (capa de interacción)**, con la interfaz pasando de Compose a un
shell Flutter. El historial público empieza en una única instantánea base con atribución
Apache-2.0, el paquete es `com.daiyimo.maeve` y la capa de cumplimiento y atribución está lista.
La genealogía está en [ATTRIBUTION.md](../../ATTRIBUTION.md).

El sistema visual completo de M2, el centro de conexiones y bridge v3 (M3) y el endurecimiento
para la release (M4) aún no están terminados. El shell Flutter es un banco de trabajo: las
respuestas son demos locales con retardo, las conversaciones y la memoria solo viven en la
memoria del proceso y el host de Android todavía no registra los manejadores del puente.
[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) enumera cada límite del prototipo; aquí no hay
ningún binario publicado.

| Dónde | Qué |
|---|---|
| [SPEC.md](../../SPEC.md) | Definición del producto, arquitectura, migración, cumplimiento, hitos |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Shell Flutter, compilación del módulo, toolchain, límites y validación |
| `docs/spec/` | Tokens visuales, háptica, protocolo bridge v3, notas de migración |
| `docs/adr/` | Registros de decisiones de arquitectura |
| `docs/dev/` | Arquitectura, estándares de ingeniería, registros de revisión, notas temáticas |
| [ATTRIBUTION.md](../../ATTRIBUTION.md) | Procedencia, qué cambió, declaraciones Apache-2.0 §4 |

## Contribuir

Consulta [CONTRIBUTING.md](../../CONTRIBUTING.md). Al contribuir aceptas que tu aportación se
licencie bajo Apache-2.0 (inbound = outbound, §5); el proyecto upstream no tiene CLA ni DCO.

## Licencia y atribución

Apache-2.0: consulta [LICENSE](../../LICENSE). `NOTICE` conserva literalmente
`Mika / Copyright 2026 wangzan101`. Los componentes de terceros y GPL/LGPL se listan en
[licenses/THIRD_PARTY_NOTICES.md](../../licenses/THIRD_PARTY_NOTICES.md) y se incluyen en el APK.

Temas sugeridos: `android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `flutter` `apache-2.0`.
