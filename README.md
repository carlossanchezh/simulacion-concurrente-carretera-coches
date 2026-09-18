# Simulación concurrente de carretera con coches 

## Descripción

Este proyecto implementa una simulación concurrente de una carretera de varios carriles y segmentos en **Java**. El objetivo es modelar el acceso concurrente de múltiples coches a un recurso compartido (la carretera), donde cada coche debe:

1. **Entrar** en el primer segmento de la carretera solicitando un carril libre.

2. **Circular** durante un número determinado de ticks (velocidad).

3. **Avanzar** al siguiente segmento cuando haya terminado de circular y exista un carril libre en el segmento destino.

4. **Salir** de la carretera al completar todos los segmentos.

El sistema debe garantizar exclusión mutua, ausencia de interbloqueos y justicia entre los coches que compiten por los recursos (carriles y segmentos). Se ofrecen dos implementaciones del recurso compartido:

- `CarreteraMonitor`: basada en monitores (librería `es.upm.babel.cclib.Monitor`), con condiciones de espera por coche.

- `CarreteraCSP`: basada en procesos CSP (JCSP), con un proceso servidor que gestiona las peticiones mediante un fair select.

Además, se incluye una interfaz gráfica (`CarreteraSim`) que permite visualizar en tiempo real el estado de la carretera, los coches circulando, los ticks restantes y las llamadas realizadas.

## Arquitectura

### Componentes principales

`Pos` — representa una posición en la carretera (segmento, carril).

`Carretera` — interfaz que define el contrato de operaciones que los coches pueden realizar: `entrar`, `avanzar`, `circulando`, `salir`, `tick`.

`CarreteraMonitor` — implementación con monitores y condiciones (`cclib`).

`CarreteraCSP` — implementación con canales y un proceso servidor JCSP (`fairSelect`).

`Coche` / `Reloj` — hilos que ejecutan el protocolo del coche y generan ticks de tiempo.

`CarreteraSim` — lanza hilos, gestiona la simulación y muestra el estado en una ventana Swing.

### Funcionamiento

#### Flujo de un coche 

1. `entrar(id, tks)`: el coche solicita entrar en el segmento 1.

- Si hay carril libre, se le asigna el primero disponible.

- Si no, espera en la cola de entrada.

2. `circulando(id)`: el coche circula hasta que sus ticks llegan a 0.

- El método bloquea al hilo hasta que ticks == 0.

3. `avanzar(id, tks)`: cuando ha terminado de circular, solicita avanzar al siguiente segmento.

- Si hay carril libre en el segmento destino, se le asigna.

- Si no, espera en la cola de avance.

4. `salir(id)`: al completar todos los segmentos, abandona la carretera.

#### Flujo de tiempo (`tick`)

El método `tick()` decrementa en 1 los ticks de todos los coches en la carretera. Cuando los ticks de un coche llegan a 0, se añade a la lista de circulación para que pueda ser desbloqueado y entrar en el siguiente segmento o salir de la carretera.

#### Sincronización

- **Monitor**: se usan `mutex.enter()` / `mutex.leave()` y `Cond.await()` / `Cond.signal()`.

- **CSP**: se usan canales `Any2OneChannel` para peticiones y `One2OneChannel` para respuestas, con un proceso servidor que atiende mediante `fairSelect`.

#### Simulación gráfica

`CarreteraSim` crea una ventana Swing con:

- Una cuadrícula que representa segmentos × carriles.

- Botones para iniciar, pausar y avanzar tick a tick.

- Un área de texto que registra las llamadas realizadas por los coches.

Los coches se simulan como hilos (`Thread`) que ejecutan el protocolo entrar → circulando → (avanzar → circulando)* → salir. El tiempo avanza automáticamente (cada 5 s) o manualmente según la configuración.

### Tecnologías

- **Java**
- **Java Swing**
- **Monitores**
- **JCSP**

## Estructura del proyecto

```plaintext 
lib/
├── aedlib.jar             # Librería que implementa estructuras de datos
├── cclib-0.4.9.jar        # Librería que implementa los monitores               
└── jcsp.jar               # Librería que implementa canales y procesos CSP

src/cc/carretera/
├── Carretera.java          # Interfaz del recurso compartido
├── CarreteraMonitor.java   # Implementación con monitores
├── CarreteraCSP.java       # Implementación con JCSP
├── CarreteraSim.java       # Simulador con GUI Swing
├── Coche.java              # Hilo que ejecuta el protocolo de un coche
├── Pos.java                # Clase inmutable (segmento, carril)
└── Reloj.java              # Hilo que genera ticks periódicos
```
