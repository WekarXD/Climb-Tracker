# Climb Tracker — Diseño

Fecha: 2026-10-04
Estado: implementado. La app ha cambiado desde este diseño; ver la sección 13.

## 1. Propósito

App Android de uso personal para escalada en boulder.
A partir de una foto de la pared detecta las presas, permite definir un
bloque (circuito) eligiendo presas por color o una a una, y lleva el registro
de intentos y encadenes de cada bloque.

**Usuario:** una sola persona, en su rocódromo y en spray walls / muros
caseros. El APK se instala directamente en el móvil.

**Criterios de éxito**

- Desde una foto de una pared de rocódromo con presas de colores vivos, se
  obtiene un bloque de un solo color en pocos toques: foto, toque en el color,
  dos o tres correcciones.
- En un spray wall se puede montar un circuito presa a presa con pulsación
  larga.
- Los bloques y sus intentos persisten entre sesiones, sin conexión.

## 2. Alcance

### Incluido en la primera versión

- Foto desde la cámara o la galería, con recorte del tramo de pared.
- Detección de presas en el dispositivo mediante visión clásica por color.
- Paleta de colores detectados y selección por color.
- Añadir o quitar presas sueltas con pulsación larga.
- Creación manual de una presa donde la detección no encontró ninguna.
- Marcado de presas de inicio y de top.
- Varios bloques sobre una misma pared.
- Registro de intentos y encadenes; estado del bloque deducido.
- Almacenamiento local.

### Fuera de la primera versión

- Marcado de presas "solo pies".
- Estadísticas, gráficas, sesiones de entrenamiento.
- Cuentas, sincronización, compartir bloques, copia de seguridad en la nube.
- Detección con modelo de IA (queda previsto el punto de extensión).
- iOS.

## 3. Conceptos

- **Pared (`Wall`)**: una foto junto con las presas detectadas en ella.
- **Presa (`Hold`)**: región de la foto, con contorno, color medio y grupo de
  color. Puede ser detectada o manual.
- **Grupo de color**: conjunto de presas de color similar dentro de una pared.
  Cada grupo es una entrada de la paleta.
- **Bloque (`Boulder`)**: subconjunto de presas de una pared, cada una con un
  rol (normal, inicio, top), más nombre, grado y notas.
- **Intento (`Attempt`)**: registro con fecha y resultado (fallo o encadenado).

## 4. Pantallas

### 4.1 Inicio: lista de bloques

- Cada elemento muestra miniatura de la foto con el circuito resaltado,
  nombre, grado y estado.
- Filtro por estado: todos / proyecto / encadenado / flash.
- Botón "+" que lleva a *Nueva pared*.
- Estado vacío con una invitación a crear el primer bloque.

### 4.2 Nueva pared

- Tres orígenes: hacer foto, elegir de la galería, o reutilizar una pared ya
  guardada.
- Tras elegir una foto nueva se muestra un paso de **recorte**: un rectángulo
  ajustable para encuadrar el tramo de pared, dejando fuera suelo, techo y
  paredes vecinas. Por defecto abarca toda la foto y se puede aceptar sin
  tocar.
- A continuación se ejecuta la detección sobre la zona recortada mostrando un
  indicador de progreso y se abre el editor. La pared se guarda ya recortada.

### 4.3 Editor de circuito

- Foto a pantalla completa con zoom y desplazamiento.
- Presas detectadas con contorno tenue. Presas del circuito resaltadas; el
  resto de la foto se oscurece.
- Paleta inferior con un botón por grupo de color.
- Barra de herramientas: **Circuito** (por defecto), **Inicio**, **Top**.
- Botones: deshacer, sensibilidad de detección, guardar.
- Al guardar se piden nombre y grado.

### 4.4 Detalle del bloque

- Foto con el circuito, nombre, grado, notas.
- Historial de intentos, del más reciente al más antiguo.
- Botones "Intento" (registra un fallo) y "Encadenado".
- Acciones: editar circuito, editar datos, borrar bloque.

## 5. Interacción en el editor

| Gesto | Herramienta | Efecto |
|---|---|---|
| Toque en un color de la paleta | cualquiera | Si alguna presa de ese grupo no está en el circuito, las añade todas; si ya están todas, las quita todas |
| Toque sobre una presa | Circuito | Igual que tocar su grupo de color en la paleta |
| Toque sobre una presa | Inicio / Top | Pone o quita esa marca. Si la presa no estaba en el circuito, se añade ya marcada |
| Pulsación larga sobre una presa | cualquiera | Añade o quita solo esa presa, con vibración |
| Pulsación larga en zona sin presa | cualquiera | Crea una presa manual en ese punto y la añade al circuito |
| Pellizco / arrastre | cualquiera | Zoom y desplazamiento |
| Botón deshacer | — | Revierte la última acción |

**Reglas de roles**

- Máximo 2 presas de inicio y 1 de top. Al marcar una de más se desmarca la
  más antigua de ese rol.
- Una presa tiene un único rol; marcarla como top le quita el de inicio y
  viceversa.
- Quitar una presa del circuito elimina su rol.
- Inicio: contorno verde y etiqueta "S". Top: contorno rojo y etiqueta "T".

**Localización de la presa bajo el dedo:** punto dentro del contorno. Si no
cae dentro de ninguno, se toma la presa cuyo contorno esté a menos de un
margen de tolerancia (equivalente a 16 dp en pantalla, convertido a
coordenadas de imagen según el zoom); si hay varias, la más cercana. Si no hay
ninguna dentro del margen, se considera zona vacía.

**Presa manual:** si el crecimiento de región no produce una región válida
(demasiado pequeña o demasiado grande), se crea un círculo de radio fijo
centrado en el punto tocado.

## 6. Tracker

- Datos del bloque: nombre, grado, notas, fecha de creación.
- Grado: escala Fontainebleau por defecto (4 a 8C+), con opción de escala V
  (V0 a V16) en ajustes. Se guarda como texto en la escala elegida al crear
  el bloque; no se convierte entre escalas.
- Estado, deducido de los intentos ordenados por fecha:
  - **proyecto**: ningún intento con resultado encadenado.
  - **flash**: el primer intento registrado es un encadenado.
  - **encadenado**: hay algún encadenado y el primer intento fue un fallo.
- Un intento se puede borrar desde el historial.

## 7. Arquitectura

App Android única. Kotlin, Jetpack Compose, `minSdk` 26 (Android 8.0).
Cuatro piezas:

### 7.1 `detection` — Kotlin puro

Sin dependencias de Android, para poder probarlo en la JVM.

```kotlin
interface HoldDetector {
    fun detect(image: PixelImage, sensitivity: Float): DetectionResult
    fun growRegion(image: PixelImage, x: Int, y: Int): DetectedHold?
}
```

- `PixelImage`: ancho, alto y matriz de píxeles ARGB. La capa de UI reduce la
  foto a un lado largo de 640 px antes de pasarla.
- `DetectionResult`: lista de `DetectedHold` y lista de grupos de color.
- `DetectedHold`: contorno (polígono en coordenadas normalizadas 0–1), caja,
  centro, color medio e índice de grupo de color.

**Pasos de `detect`**

Algoritmo validado con un prototipo sobre fotos reales (ver sección 11). Los
valores indicados son los del prototipo, para una imagen de 640 px de lado
largo, y sirven de punto de partida.

1. Suavizado con filtro de mediana 5×5 para quitar ruido y los agujeros de
   los tornillos.
2. **Fondo local**: filtro de mediana con ventana grande (101 px). Cada zona
   se compara con su propio entorno, de modo que la pared puede tener varios
   colores (paneles claros, paneles negros, volúmenes grandes).
3. Conversión de la imagen y del fondo local a CIE Lab.
4. Máscara de primer plano: píxeles cuya diferencia de color (componentes
   a, b) con el fondo local supera 14, o que son más claros que su fondo en
   más de 38 de luminosidad, o más oscuros en más de 27. El umbral es
   asimétrico porque las presas grises sobre pared clara solo son algo más
   oscuras que ella (unos 30), mientras que el magnesio sobre un panel negro
   es más claro que su fondo y no debe contar. Las sombras suaves de la pared
   quedan por debajo (unos 7–15). La `sensitivity` (0–1) escala los tres
   umbrales. Valores ajustados con las fotos del autor (octubre de 2026).
5. Limpieza morfológica (apertura y cierre 3×3).
6. Agrupación de colores: k-means con k = 8 sobre el color Lab de los
   píxeles de primer plano, fusionando después los grupos cuyos centros
   distan menos de 16. Los grupos resultantes forman la paleta.
7. Componentes conectadas por separado para cada grupo de color. Así dos
   presas vecinas de distinto color salen como presas distintas. Después se
   funden las componentes contiguas que son el mismo material con distinta
   luz: ambas sin color (negro, gris, blanco) o del mismo tono. Con ello las
   caras de un volumen, o el lado iluminado y el sombreado de una presa,
   forman una sola presa. Los filtros de los pasos 8 y 9 se aplican a la
   presa ya fundida.
8. Filtro por área: se descartan componentes menores de 14 px o mayores del
   6 % de la imagen.
9. **Prueba de anillo**: se descarta la componente si más del 25 % de los
   píxeles de un anillo a su alrededor tienen su mismo color (distancia
   menor de 14). Elimina los trozos de pared que quedan marcados en las
   fronteras entre paneles de distinto color.
10. Por cada componente restante: trazado del contorno, simplificación del
    polígono, caja, centro y color medio.

El filtro de mediana de ventana grande es el paso más costoso. En Kotlin se
calcula sobre una copia reducida de la imagen (un cuarto de lado) y se
reescala, o con mediana por histograma deslizante.

**`growRegion`**: crecimiento de región desde el píxel tocado, admitiendo
vecinos cuyo color esté dentro de una tolerancia respecto al color semilla.
Devuelve `null` si la región resultante queda fuera de los límites de área.

**Limitaciones conocidas y aceptadas**

- Presas de color parecido al de la pared no se detectan (confirmado: presas
  blancas sobre pared blanca).
- Presas muy manchadas de magnesio pueden fragmentarse o perderse.
- Dos presas del mismo color en contacto salen como una sola.
- Las personas delante de la pared se detectan como presas (ropa y piel
  aparecen incluso como un grupo de color propio).
- Las presas negras brillantes pueden salir fragmentadas por los reflejos.
- Los volúmenes mayores que la ventana de fondo local se tratan como pared;
  los medianos salen como presas seleccionables.
- En fotos con perspectiva muy forzada o con varias paredes a distintas
  distancias el resultado empeora claramente. El uso previsto es una foto
  de frente a un tramo de pared.

La corrección manual (pulsación larga) y el control de sensibilidad son la
respuesta a estas limitaciones en la primera versión.

### 7.2 `data` — persistencia local

Base de datos Room.

| Tabla | Campos |
|---|---|
| `Wall` | id, ruta de la foto, ancho, alto, fecha de creación |
| `Hold` | id, wallId, contorno (serializado), color medio, grupo de color, manual |
| `Boulder` | id, wallId, nombre, grado, notas, fecha de creación |
| `BoulderHold` | boulderId, holdId, rol (NORMAL / START / TOP), orden de marcado |
| `Attempt` | id, boulderId, fecha, resultado (FAIL / SEND) |

- Borrados en cascada: pared → presas y bloques; bloque → sus presas e
  intentos.
- Las fotos se copian al almacenamiento privado de la app, ya corregida la
  orientación; en la base solo se guarda la ruta.
- Repositorios que exponen `Flow` para las listas y funciones `suspend` para
  las escrituras.

### 7.3 `editor` — lógica del circuito

Sin interfaz ni dependencias de Android.

- `EditorState` inmutable: presas de la pared, presas seleccionadas con su
  rol, herramienta activa, pila de deshacer.
- Funciones puras que devuelven un estado nuevo: `toggleColorGroup`,
  `toggleHold`, `toggleRole`, `addManualHold`, `setTool`, `undo`.
- `hitTest(punto, tolerancia)`: devuelve la presa bajo el dedo o ninguna.
- La pila de deshacer guarda instantáneas de la selección, con un límite de
  50 entradas.

### 7.4 `ui` — pantallas Compose

- Las cuatro pantallas de la sección 4, con navegación Compose y un ViewModel
  por pantalla.
- Lienzo del editor: dibuja foto, oscurecido y contornos; convierte gestos en
  acciones de `editor`, traduciendo coordenadas de pantalla a coordenadas de
  imagen según zoom y desplazamiento.
- Cámara: contrato `TakePicture` con la app de cámara del sistema. Galería:
  selector de fotos del sistema. Ninguno requiere permisos en tiempo de
  ejecución.
- La detección se ejecuta fuera del hilo principal.

## 8. Flujo de datos

1. El usuario hace o elige una foto.
2. `ui` corrige la orientación, copia la foto al almacenamiento privado y
   genera la versión reducida.
3. `detection.detect` devuelve presas y grupos de color.
4. `data` guarda la `Wall` y sus `Hold`.
5. El editor carga la pared; cada gesto produce un nuevo `EditorState`.
6. Al guardar, `data` escribe `Boulder` y `BoulderHold`. Las presas manuales
   se guardan como `Hold` de la pared con `manual = true`.
7. Desde el detalle, cada intento se guarda como `Attempt`; el estado del
   bloque se recalcula a partir de la lista.

## 9. Tratamiento de errores

| Situación | Comportamiento |
|---|---|
| Ninguna presa detectada | Aviso en el editor, con acceso al control de sensibilidad; se puede seguir montando el circuito a mano |
| Más de 400 presas detectadas (pared muy texturizada) | Aviso sugiriendo bajar la sensibilidad |
| Cambio de sensibilidad con un circuito ya empezado | Pide confirmación: repetir la detección descarta la selección actual |
| Cambio de sensibilidad en una pared que ya tiene bloques guardados | No permitido: el control aparece deshabilitado, porque repetir la detección invalidaría las presas de esos bloques. Las correcciones se hacen con presas manuales |
| Foto con orientación en metadatos | Se rota antes de guardar y de detectar |
| No se puede leer la foto elegida | Mensaje de error y vuelta a *Nueva pared* |
| Guardar un bloque sin presas | Botón de guardar deshabilitado |
| Guardar sin nombre | Se asigna un nombre por defecto ("Bloque N") |
| Borrar una pared con bloques | Confirmación indicando cuántos bloques se borrarán |
| Falta el archivo de la foto de una pared guardada | El bloque se muestra con un marcador de imagen no disponible; los datos e intentos siguen accesibles |

## 10. Pruebas

- **`detection`** (tests unitarios en JVM, escritos antes que el código):
  imágenes sintéticas con formas de colores sobre fondo uniforme y con ruido,
  comprobando número de presas, posición, color y agrupación; casos límite
  (imagen sin presas, todo primer plano, presas en el borde). Además,
  tests de rango sobre las tres fotos reales de `fotos-referencia/`: el
  número de presas debe caer en un intervalo alrededor del obtenido con el
  prototipo (sección 11).
- **`editor`** (tests unitarios en JVM): cada acción, las reglas de roles,
  deshacer, y `hitTest` con y sin tolerancia.
- **Tracker**: deducción del estado a partir de secuencias de intentos.
- **`data`**: tests de Room sobre base en memoria, incluidos los borrados en
  cascada.
- **`ui`**: verificación manual en el móvil por `adb`. Los gestos y el aspecto
  no se pueden comprobar sin dispositivo o emulador.

## 11. Validación del algoritmo

El algoritmo se validó primero con un prototipo desechable en Python (OpenCV) y después con
la implementación en Kotlin, que reproduce sus resultados.

Los tests de rango del detector usan tres fotos hechas por el autor en su rocódromo, en
`fotos-referencia/`:

| Foto | Contenido | Presas detectadas |
|---|---|---|
| `pared-volumen-negro-amarillas.jpg` | Pared clara, volumen negro, presas amarillas y grises | 29 |
| `pared-panel-negro-turquesas.jpg` | Panel negro con presas turquesas y blancas, una persona | 33 |
| `pared-triangulos-varios-colores.jpg` | Triángulos negros y presas de varios colores | 47 |

Los umbrales del paso 4 y la fusión del paso 7 se ajustaron con mediciones sobre estas fotos.
La valoración es visual: no hay un recuento presa a presa contra la realidad.

## 12. Entorno de desarrollo

En el equipo no hay JDK ni Android SDK. Se instalarán JDK 17 y las
herramientas de línea de comandos del Android SDK (sin Android Studio). El
proyecto se compila con Gradle y genera un APK de depuración instalable por
`adb`.

## 13. Cambios posteriores al diseño original

Lo que sigue se añadió después de aprobar este documento y sustituye a lo que digan las
secciones anteriores cuando haya contradicción.

### Aspecto

- Tema cálido (fondo crema, tarjetas blancas, acento terracota) con variante oscura, que sigue
  el modo del sistema. El editor, el recorte y las tarjetas que se comparten son iguales en ambos.
- **Inicio**: rejilla de dos columnas con tarjetas de foto, agrupadas por día ("Sesión del
  5 de octubre"). Filtros por estado ("En progreso", "Encadenado", "Flash"), chips por color y
  un filtro "Desmontados".
- Barra inferior con tres pestañas: Proyectos, Rocódromos y Perfil.
- El inicio y el top se señalan con etiquetas "START" y "TOP" junto a la presa, no con
  contornos de color y letras.

### Intentos y progreso (sustituye a la sección 6)

- Al registrar un intento se elige sobre la foto la **última presa alcanzada**. Tocar la presa
  de top lo registra como encadenado; cualquier otra, como intento fallido.
- El **porcentaje** de un intento es la altura de esa presa entre el inicio y el top. El inicio
  es la altura media de las presas de inicio, o la presa más baja si no hay ninguna marcada; el
  top es la presa de top, o la más alta. Una presa que no sea la de top nunca pasa del 99 %.
- Un bloque sin encadenar muestra el mejor porcentaje de sus intentos. Sobre la foto se dibuja
  una línea a esa altura y se tiñe la zona inferior con el color del bloque.
- El color de un bloque es el del grupo de color con más presas en su circuito. Un bloque
  guardado sin nombre toma el nombre de ese color.
- El detalle muestra sesiones (días distintos con intentos), número de intentos y un anillo con
  el porcentaje, o "TOP" / "FLASH".

### Perfil

Estadísticas calculadas con los datos locales, por semana, mes, año o desde siempre: tops,
flashes y sesiones con la variación respecto al periodo anterior; volumen por día de la semana;
desglose por color y por grado; constancia de las últimas doce semanas con racha de semanas; y
récords. Un bloque cuenta como top solo la primera vez que se encadena.

### Menú del bloque

- **Corregir presas**: abre el editor con el circuito del bloque.
- **Escanear otro bloque en esta foto**: abre el editor sobre la misma pared para un bloque nuevo.
- **Editar nombre, grado y notas**.
- **Marcar como desmontado**: el bloque sale de la lista de proyectos y conserva su historial y
  su peso en las estadísticas. Se recupera desde el filtro "Desmontados".
- **Quitar de mis proyectos**: borra el bloque y sus intentos.

### Datos (amplía la sección 7.2)

- `Attempt` gana `lastHoldId` (versión 2 de la base de datos).
- `Boulder` gana `archived` (versión 3).
- El esquema de cada versión se guarda en `app/schemas/` desde la versión 3.

### Detección (amplía la sección 7.1)

Ya recogido en los pasos 4 y 7: umbral de luminosidad asimétrico, umbral de color de 14 y
fusión de regiones contiguas del mismo material. Además:

- Dos regiones solo se funden si comparten un borde de al menos el 12 % del perímetro de la
  menor. Las caras de un volumen comparten una arista entera; dos presas que se rozan, unos
  píxeles.
- Una mota sin color rodeada por una presa (el agujero del tornillo, un brillo) se absorbe en
  ella. Una mota de color no: es una presa montada sobre un volumen.
- Se descartan las formas con mucho más contorno que área (perímetro al cuadrado sobre área
  superior a 6 veces el de un círculo).
- **Repetir la detección** está permitido también en paredes con bloques: cada presa de un
  circuito, y la última presa de cada intento, pasan a la presa nueva que ocupa su sitio. Una
  presa en uso que la detección nueva no encuentra se conserva. Sustituye a la fila de la
  sección 9 que lo prohibía.

- La detección trabaja sobre la foto a **1280 px de lado largo**, no 640. Los tamaños de los
  parámetros siguen dados para 640 px y el detector los escala; el anillo de la prueba de anillo
  también, para que quede fuera del borde difuminado de la presa.
- El umbral de color baja a 10, y una región con color se acepta desde 2 píxeles (a 640 px),
  frente a los 14 de una sin color. Los pies diminutos solo miden unos píxeles, y lo que los
  distingue del ruido (agujeros de tornillo, motas) es que tienen color.

Un último paso descarta lo que no es presa por su entorno: las motas sin color propio dentro
de una presa mayor (tornillos, brillos) y las filas de formas sin color parecidas, juntas y en
línea recta (letras pintadas en un panel, rejillas).

Una segunda pasada busca las presas del color de la pared, que la pasada por color no ve. Lo
que las delata es el relieve: la cara inferior en sombra y la sombra que proyectan dibujan casi
todo su contorno. Se calculan los bordes de la luminosidad suavizada, se quitan los de las
presas ya encontradas y los anillos diminutos de los agujeros de tornillo, y los bordes
restantes se agrupan por cercanía. Un grupo con el tamaño y la forma de una presa (ni una
línea, ni una rejilla llena de bordes) se da por presa, con su envolvente convexa como
contorno, y absorbe las sombras sueltas que hubiera dentro. Solo se aplica sobre pared clara.

Las presas de un color sin grupo propio, o apagadas por la sombra o el magnesio (las ocre
montadas sobre volúmenes oscuros), quedaban reducidas a su mota más viva. Ahora un grupo de
color absorbe los píxeles vecinos sin color de su mismo tono, a poca distancia de su parte
viva: sobre algo oscuro (un volumen, sombra) basta con muy poco color; entre cosas claras, que
suelen estar teñidas como la pared beige, hace falta claramente más color que la pared de
alrededor. Antes de eso, un píxel con color definido pasa al grupo de su tono aunque le quede
más cerca uno gris: hay presas en sombra de las que ningún píxel cae en su propio color.
Además, los trozos de una presa se unen por su color real medido, no por el grupo al que cayó
cada uno; dos tonos iguales con viveza muy distinta (una presa amarilla y la pared beige) no
se unen.

Dos presas del mismo color que se tocan forman una sola región. Se separan cuando la región
son dos cuerpos unidos por un cuello mucho más estrecho que ambos (`Necks`): se va quitando
grosor a la región y, si se parte cuando a los dos cuerpos aún les queda la mayor parte del
suyo, cada píxel se asigna al cuerpo más cercano. Solo se aplica a presas con color, porque una
presa gris y su sombra tienen esa misma forma. Con el cuello limitado al 30 % del cuerpo separa
una de las doce detecciones que abarcaban varias presas sin partir ninguna presa entera; con el
50 % separaba tres y partía dos.

**Calidad medida.** Un test compara el detector con presas anotadas a mano en las tres fotos de
referencia (`core/src/test/resources/holds`). Cifras vigentes, con las fotos recortadas para
que no salga nadie: **106 de 119 presas encontradas (89 %)** y **26 detecciones falsas de 121**.
Por tipo, las de color y las negras se encuentran casi todas; las gris claro o blancas sobre
pared clara son las que más se escapan.

Historial, con las fotos enteras (129 presas anotadas), por lo que no es comparable con la
cifra vigente:

| Estado del detector | Presas encontradas | Detecciones falsas |
|---|---|---|
| A 640 px | 95 de 129 | 27 de 107 |
| A 1280 px, con el descarte de letras | 108 de 129 | 34 de 134 |
| Con la pasada por contornos (presas claras: de 11 a 15 de 17) | 113 de 129 | 34 |
| Último estado antes de recortar las fotos | 116 de 129 | 19 de 124 |

### Siluetas con modelo local

El detector por color encuentra las presas y sus colores, y un modelo de segmentación
(MobileSAM, con ONNX Runtime) redibuja el contorno de cada una a partir de su caja. El modelo
no encuentra presas ni decide colores. Una máscara que no puede ser la presa detectada (vacía,
mucho mayor, en otro sitio) se ignora, las presas de pocos píxeles no pasan por el modelo y, si
este no puede ejecutarse, quedan los contornos del detector. El modelo se carga para cada foto
y se libera después. En un móvil añade unos 2,7 s por pared de 40 presas. Los dos archivos del
modelo no están en el repositorio: la compilación los descarga de la release `models-1`.

La ejecución del modelo vive en `core` (`SilhouetteRunner`) y solo necesita el runtime, así que
un test mide en el PC lo mismo que el de calidad del detector, después de las siluetas: 104 de
119 presas y 25 detecciones falsas de 120. Son dos presas menos que el detector solo, donde un
contorno que abarcaba dos presas se redibuja alrededor de una.

### Rocódromos

Tabla `gyms` (nombre) y columna opcional `gymId` en `walls`; borrar un rocódromo deja sus paredes
sin asignar. El rocódromo se asigna a la pared desde el menú del bloque, así que lo comparten
todos los bloques de esa foto, y una pared nueva hereda el último usado. Pestaña «Rocódromos» con
el resumen de cada uno, que al tocarlo filtra los proyectos; el perfil se filtra por rocódromo.
La copia de seguridad los incluye.

Cada rocódromo guarda su posición (`latitude`, `longitude`, opcionales): la toma la primera vez
que se asigna con el teléfono localizado, y se corrige desde su tarjeta. Al crear una pared se
elige el rocódromo a menos de 400 m (más el margen de error de la posición, hasta 3 km); si no
hay ninguno, el último usado. La posición se lee con `LocationManager`, sin servicios de Google,
y el permiso se pide una sola vez, al asignar un rocódromo. Base de datos en versión 5.

La pestaña «Rocódromos» lleva arriba un mapa (biblioteca MapLibre Native, que sustituye a
osmdroid, ya sin mantenimiento). El mapa es vectorial: se dibuja en el móvil con los datos de
OpenStreetMap que sirve OpenFreeMap, sin clave. Parte de sus estilos más sobrios («positron» y
«dark») y `ui/GymMap.kt` los adapta al cargarlos: nombres en el idioma de la app, tonos de la
app en el tema oscuro, y las referencias que esos estilos omiten (prados, nombres de parques
y lugares de interés). Los nombres de parajes que no son población ni parque no están
en esos datos. El crédito visible es «© OpenStreetMap ⓘ» y al tocarlo se despliega el completo) con los rocódromos propios
y los encontrados. Se busca por nombre entre los lugares con `leisure=sports_centre`,
los más cercanos al centro del mapa primero, preguntando a la vez a Nominatim (inmediato, pero
solo nombres completos) y a Photon (encuentra parte del nombre, pero puede tardar medio minuto) o por la zona visible
(Overpass, lugares con `sport=climbing` y nombre). Un resultado se añade como rocódromo nuevo
o se usa como posición de uno existente. Es lo único de la app que usa Internet: el mapa y las
búsquedas envían a esos servicios la zona o el texto consultados, nada más.

### Encuadre con perspectiva

El paso de recorte tiene un modo «Perspectiva» en el que cada esquina se mueve por separado
(`core/image/CropQuad.kt`). La zona marcada se endereza con una transformación proyectiva antes
de guardar la foto y detectar, de modo que una pared fotografiada de lado o desde abajo queda
como vista de frente. Con el modo apagado el recorte es el rectángulo de siempre.

### Idiomas

Los textos están en recursos: inglés por defecto (`res/values`) y español (`res/values-es`). Las
fechas y los números se escriben en el idioma de los textos, no en el del teléfono
(`ui/Texts.kt`). Las estadísticas siguen agrupando por el nombre del color en español, que se
traduce al mostrarlo. El nombre por defecto «Bloque N» del DAO no se traduce: solo se usa si el
editor no aporta nombre, y el editor siempre aporta el del color.

### Publicación

Icono adaptativo propio. Las versiones se compilan y publican con GitHub Actions al empujar una
etiqueta `vX.Y.Z`.
