# Instrucciones de instalación y ejecución

## Requisitos

- **Java** 8 o superior

- **Librerías externas** (incluidas en el proyecto, carpeta `lib/`):

    - `aedlib.jar`
    - `cclib-0.4.9.jar` 
    - `jcsp.jar`  

- **IDE** recomendado

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/carlossanchezh/simulacion-concurrente-carretera-coches.git
```

### 2. Añade las librerías al classpath de tu IDE

- **IntelliJ IDEA**: File → Project Structure → Libraries → + y selecciona los .jar de lib/.

- **Eclipse**: clic derecho sobre el proyecto → Build Path → Configure Build Path → Libraries → Add External JARs.

- ...

> Puedes consultar las instrucciones de tu IDE favorito para añadir las librerías al classpath, estos son solo un ejemplo

## Ejecución

### Desde la línea de comandos 

1. Accede al directorio donde haya sido clonado el proyecto

2. Compilar todos los archivos `.java`:

```bash
javac -d . -cp .:lib/cclib-0.4.9.jar:lib/jcsp.jar:lib/aedlib.jar cc/carretera/*.java
```
> En Windows: sustituye `:` por `;` en el classpath 

```bash
javac -d . -cp ".;lib/cclib-0.4.9.jar;lib/jcsp.jar;lib/aedlib.jar" cc/carretera/*.java
```

3. Ejecutar el simulador:

```bash
java -cp .:lib/cclib-0.4.9.jar:lib/jcsp.jar:lib/aedlib.jar cc.carretera.CarreteraSim
```
> En Windows:

```bash
java -cp ".;lib/cclib-0.4.9.jar;lib/jcsp.jar;lib/aedlib.jar" cc.carretera.CarreteraSim
```

### Desde un IDE

1. Abre el proyecto en tu IDE.

2. Asegúrate de que las librerías de `lib/` están añadidas al classpath.

3. Ejecuta la clase principal cc.carretera.CarreteraSim.

### Selección de la implementación

El simulador permite elegir entre las dos implementaciones del recurso
compartido. En CarreteraSim.java (aproximadamente en la línea 505),
descomenta la que quieras usar:

Implementación con Monitores:

```java
//crPre = new CarreteraCSP(segmentos, carriles);
crPre = new CarreteraMonitor(segmentos, carriles);
```

Implementación con JCSP:

```java
crPre = new CarreteraCSP(segmentos, carriles);
//crPre = new CarreteraMonitor(segmentos, carriles);
```

> Si se está ejecutando desde la línea de comandos habrá que volver a compilar

### Uso del simulador

Al iniciar la aplicación se abre una ventana Swing con:

- **Start simulation** — inicia la simulación con coches aleatorios.

- **Pause simulation** — pausa o reanuda el avance del tiempo.

- **Tick** — avanza manualmente un tick.

- **Quit** — cierra la aplicación.

- **Cuadrícula** — muestra los segmentos (columnas) y carriles (filas), con los coches y sus ticks restantes.

 - **Área de texto** — registra las llamadas realizadas por los coches (entrar, avanzar, circulando, salir, tick).