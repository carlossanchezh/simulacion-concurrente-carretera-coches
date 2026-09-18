// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;

import java.util.*;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {

  private final int segmentos; // numero de carriles de la carretera
  private final int carriles; // numero de segmentos de la carretera

  private static class InfoCoche { // calse que aporta la informacion sobre el coche
    Pos pos; // posicion del coche en la carrtera
    int ticks;// ticks del coche
    String id;// id del coche
    Monitor.Cond esperandoCircular;// condicion de desbloqueo especifica para circulando() de cada coche

    public InfoCoche(Pos pos, int ticks, String id, Monitor.Cond esperandoCircular) { // constructor
      this.pos = pos; // posicion del coche en la carretra
      this.ticks = ticks; // ticks del coche
      this.id = id; // id del coche
      this.esperandoCircular = esperandoCircular; // condicion de desbloqueo especifica para circulando() de cada coche
    }
  }

  private final Monitor mutex = new Monitor(); // monitor del programa
  private final Monitor.Cond esperandoEntrar = mutex.newCond();// condicion para entrar();
  private final Monitor.Cond esperandoAvanzar = mutex.newCond();// condicion para avanzar();

  private final Map<String, InfoCoche> coches = new HashMap<>();// mapa para almacenar los coches que se encuentrar en
                                                                // la carretera clave= id
  private final Queue<String> listaAvanzar = new LinkedList<>();// Cola FIFO de los coches que estan esperando avanzar
  private final Queue<String> listaCircular = new LinkedList<>();// Cola FIFO de los coches que estan esperando Circular

  public CarreteraMonitor(int segmentos, int carriles) {// constructor de la carretera

    this.segmentos = segmentos; // inicializa segmentos
    this.carriles = carriles; // inicializa carriles
  }

  private Set<Integer> carrilesLibres(int seg) { // metodo que devuleve un Set<Integer> los carriles libres en un
                                                 // determinado segmento
    Set<Integer> ocupados = new HashSet<>(); // inicializa el set de carriles ocupados
    for (InfoCoche info : coches.values()) {
      if (info.pos.getSegmento() == seg) {
        ocupados.add(info.pos.getCarril()); // recorre el mapa de coches en la carretera y mira en que segmento estan si
                                            // es igual al que se analiza añade el carril al Set<Integer> de ocupados
      }
    }
    Set<Integer> libres = new HashSet<>();// inicializa el set de carriles libres
    for (int i = 1; i <= carriles; i++) {
      if (!ocupados.contains(i)) {
        libres.add(i); // analiza el numero de carriles en el Set<Integer> ocupados y si hay algun
                       // carril que no este ocupado lo mete en el Set<Integer> libres
      }
    }
    return libres; // devuelve un Set<Integer> con los carriles libres
  }

  public Pos entrar(String id, int tks) {
    mutex.enter();// inicia bloqueo para otros hilos
    try {

      if (carrilesLibres(1).isEmpty()) {
        esperandoEntrar.await(); // si no hay carriles libres en el segmento 1 espera

      }

      // seccion critica
      int carril = carrilesLibres(1).iterator().next(); // primer carril libre del segmento 1
      Pos pos = new Pos(1, carril); // crea una posicion del segmento 1 y el primer carril libre
      coches.put(id, new InfoCoche(pos, tks, id, mutex.newCond())); // añade el coche al mapa e inicializa su respentiva
                                                                    // condicion de Circular
      // sale de la seccion critica

      // signal a otros hilos
      desbloquear();

      return pos;// devuleve la nueva posicion del coche

    } finally {
      mutex.leave(); // libera el desbloqueo
    }

  }

  public Pos avanzar(String id, int tks) {
    mutex.enter();// inicia bloqueo para otros hilos
    try {
      listaAvanzar.add(id);// añade el coche a la lista de los que quieren avanzar
      InfoCoche coche = coches.get(id); // obtiene la informacion del cohe
      int actualSeg = coche.pos.getSegmento();// obtiene el segmento en el que se encuentra el coche

      if (coche.ticks > 0 || carrilesLibres(actualSeg + 1).isEmpty()) {
        // esperar
        esperandoAvanzar.await(); // Si los ticks del coche no son 0 o el carril al que tiene que avanzar esta
                                  // ocupado espera a avanzar
        coche = coches.get(id); // refrescar coche ante un posible cambio
        actualSeg = coche.pos.getSegmento();// actualiza el segmento del coch ante un posible cambio

      }

      // seccion critica
      listaAvanzar.remove(id); // elimina de la lista de los que quieren avanzar al que ya esta avanzando
      int carril = carrilesLibres(actualSeg + 1).iterator().next(); // comprueba el primer carril libre del segmento
                                                                    // siguiente
      Pos nuevaPos = new Pos(actualSeg + 1, carril);// crea una pos con el siguiente segmento y el primer carril libre
      coche.pos = nuevaPos; // actualiza la posicion del coche
      coche.ticks = tks;// actualiza sus ticks
      // sale de la seccion critica

      // signal a otros hilos
      desbloquear();

      return nuevaPos;// devuelve la nueva posicion en la que se encuentra el coche

    } finally {
      mutex.leave();// libera el desbloqueo
    }

  }

  public void circulando(String id) {
    mutex.enter();// inicia bloqueo para otros hilos
    try {
      InfoCoche coche = coches.get(id); // obtiene la informacion del coche que quiere circular
      if (coche.ticks > 0 || coche == null) {
        // esperar
        coche.esperandoCircular.await(); // si los ticks del coche no son 0 o no existe el coche espera a circular
        coche = coches.get(id); // refresca el estado del coche ante un posible cambio
      }

      // seccion critica
      listaCircular.remove(id);// elimina al coche de la lista de los que quieren circular

      if (!listaCircular.isEmpty()) {
        String siguienteId = listaCircular.peek(); // mira el primer coche de la lista para circular
        InfoCoche siguienteCoche = coches.get(siguienteId); // obtiene la informacion del coche
        if (siguienteCoche != null) {
          siguienteCoche.esperandoCircular.signal();// si el siguiente coche no es nulo le da permiso para circular
        }
      }
      // fin seccion critica

    } finally {
      mutex.leave();// libera el desbloqueo
    }
  }

  public void salir(String id) {
    try {
      mutex.enter();// inicia bloqueo para otros hilos

      coches.remove(id); // elimina al coche que ha slido del mapa de coches en l carrtera
      listaAvanzar.remove(id); // elimin al coche de la lista para avanzar
      listaCircular.remove(id); // elimina al cohe de la lista para circular

      // signal a los demas hilos
      desbloquear();

    } finally {
      mutex.leave();// libera el desbloqueo
    }

  }

  public void tick() {
    mutex.enter();// inicia bloqueo para otros hilos
    try {

      for (InfoCoche coche : coches.values()) {
        coche.ticks = Math.max(0, coche.ticks - 1);

        if (coche.ticks == 0 && !listaAvanzar.contains(coche.id) && !listaCircular.contains(coche.id)) {
          listaCircular.add(coche.id);
        }
      }

      // signal a los demas hilos
      desbloquear();

    } finally {
      mutex.leave();// libera el desbloqueo
    }
  }

  private void desbloquear() {// metodo para desbloquear tos los hilos posibles
    if (!desbloquearCirculando()) { // si algun coche puede circular circula
      if (!desbloquearAvanzar()) { // si algun coche puede avanzar avanza
        desbloquearEntrar(); // si algun coche puede entrar entra
      }
    }
  }

  private void desbloquearEntrar() {
    if (!carrilesLibres(1).isEmpty()) {
      esperandoEntrar.signal(); // si hay carriles libres en el segmento 1
    }
  }

  private boolean desbloquearAvanzar() {
    if (listaAvanzar.isEmpty())// si la cola de los que quieren avanzar no esta vacia
      return false;

    String id = listaAvanzar.peek(); // id del primer coche de la cola para avanzar
    InfoCoche coche = coches.get(id);// info del coche
    int segActual = coche.pos.getSegmento();// segmento en le que se encuentra el cohe
    int segSiguiente = segActual + 1;// segmento al que quiere avanzar

    if (segActual < segmentos && coche.ticks == 0 && !carrilesLibres(segSiguiente).isEmpty()) {
      esperandoAvanzar.signal(); // si el segmento actual esta dentro de los avanzables y los ticks son 0 y hay
                                 // carriles libres en el carril al que va a vanzar da permiso para avanzar
      return true; // señalamos solo uno para evitar señales excesivas
    }

    return false;
  }

  private boolean desbloquearCirculando() {
    if (listaCircular.isEmpty()) {
      return false;
    }

    if (!listaCircular.isEmpty()) {// si la lista de los que quieren circular no esta vacia
      String id = listaCircular.peek(); // mira el primer coche de la lista
      InfoCoche coche = coches.get(id); // info del coche
      coche.esperandoCircular.signal();// permite circular al coche
    }
    return true;
  }
}
