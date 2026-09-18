package cc.carretera;

import org.jcsp.lang.*;

import java.util.*;

// Clase principal que implementa la carretera como un proceso CSP
public class CarreteraCSP implements Carretera, CSProcess {

  // Canales para comunicación con los clientes
  private Any2OneChannel chEntrar;
  private Any2OneChannel chAvanzar;
  private Any2OneChannel chCircular;
  private Any2OneChannel chSalir;
  private Any2OneChannel chTick;

  // Configuración de la carretera
  private final int segmentos; // número de segmentos (tramos) por carril
  private final int carriles; // número total de carriles

  // Clase auxiliar para representar el estado de un coche
  private static class InfoCoche {
    Pos pos; // posición actual del coche
    int ticks; // ticks restantes antes de poder avanzar

    public InfoCoche(Pos pos, int ticks) { // Constructor para localizar al coche y su estado
      this.pos = pos;
      this.ticks = ticks;
    }
  }

  // Definicion de la Peticion de entrar
  private static class PetEntrar {
    String id;// id del coche
    int tks;// ticks del coche
    ChannelOutput resp; // Canal de respuesta del servidor

    PetEntrar(String id, int tks, ChannelOutput resp) { // Constructor de la propia peticion
      this.id = id;
      this.tks = tks;
      this.resp = resp;
    }
  }

  // Definicion de la Peticion de avanzar
  private static class PetAvanzar {
    String id; // id del coche
    int tks; // ticks del coche
    ChannelOutput resp; // Canal de respuesta del servidor

    PetAvanzar(String id, int tks, ChannelOutput resp) { // Constructor de la propia peticion
      this.id = id;
      this.tks = tks;
      this.resp = resp;
    }
  }

  // Definicion de la Peticion de circular
  private static class PetCircular {
    String id;// id del coche
    // los ticks para circular seran siempre 0
    ChannelOutput resp;// Canal de respuesta del servidor

    PetCircular(String id, ChannelOutput resp) {// Constructor de la propia peticion
      this.id = id;
      this.resp = resp;
    }
  }

  // Definicion de la Peticion de salida
  private static class PetSalir {
    String id;// id del coche
    // los ticks para circular seran siempre 0
    // no usa canal de respuesta

    PetSalir(String id) { // Constructor de la propia peticion
      this.id = id;
    }
  }

  // Constructor que inicializa los canales y lanza el proceso CSP servidor
  public CarreteraCSP(int segmentos, int carriles) {
    this.segmentos = segmentos;
    this.carriles = carriles;

    // Crear canales para cada tipo de operación
    chEntrar = Channel.any2one();
    chAvanzar = Channel.any2one();
    chCircular = Channel.any2one();
    chSalir = Channel.any2one();
    chTick = Channel.any2one();

    // Lanzar el proceso servidor en un hilo separado del resto
    new ProcessManager(this).start();
  }

  // Método usado por el cliente para entrar en la carretera
  public Pos entrar(String car, int tks) {
    One2OneChannel chResp = Channel.one2one(); // Canal de respuesta
    chEntrar.out().write(new PetEntrar(car, tks, chResp.out())); // Escribe por el canal de entrada la peticion
    return (Pos) chResp.in().read(); // devuelve la posicion escrita por el servidor en el canal de respuesta
  }

  // Método usado por el cliente para avanzar en la carretera
  public Pos avanzar(String car, int tks) {
    One2OneChannel chResp = Channel.one2one();// Canal de respuesta
    chAvanzar.out().write(new PetAvanzar(car, tks, chResp.out()));// Escribe por el canal de entrada la peticion
    return (Pos) chResp.in().read();// devuelve la posicion escrita por el servidor en el canal de respuesta
  }

  // Método usado por el cliente para salir de la carretera
  public void salir(String car) {
    chSalir.out().write(new PetSalir(car));// Escribe por el canal de entrada la peticion
  }

  // Método usado por el cliente para circular en la carretera
  public void circulando(String car) {
    One2OneChannel chResp = Channel.one2one();// Canal de respuesta
    chCircular.out().write(new PetCircular(car, chResp.out()));// Escribe por el canal de entrada la peticion
    chResp.in().read(); // Leemos la respuesta para validar
  }

  // Método usado por el cliente para avanzar el tiempo
  public void tick() {
    chTick.out().write(null);// Escribe por el canal de entrada
  }

  // Método principal del proceso servidor
  public void run() {

    final int SERVICIOS = 5;
    final int ENTRAR = 0, AVANZAR = 1, CIRCULAR = 2, SALIR = 3, TICK = 4;

    final boolean[] sincCond = new boolean[SERVICIOS]; // array para tratar las CPRE

    Map<String, InfoCoche> coches = new HashMap<>(); // mapa donde estan todos los coches de la carretera
    Queue<String> listaAvanzar = new LinkedList<>(); // registro de coches pendientes a avanzar (han circulado)
    Queue<String> listaCircular = new LinkedList<>(); // registro de coches pendientes de circular (ticks a 0)
    LinkedList<PetEntrar> colaEsperaEntrar = new LinkedList<>(); // registro de coches que no pudieron entrar
    LinkedList<PetAvanzar> colaEsperaAvanzar = new LinkedList<>();// registro de coches que no pudieron avanzar
    LinkedList<PetCircular> colaEsperaCircular = new LinkedList<>();// registro de coches que no pudieron circular

    AltingChannelInput[] entradas = new AltingChannelInput[SERVICIOS];
    entradas[ENTRAR] = chEntrar.in();
    entradas[AVANZAR] = chAvanzar.in();
    entradas[CIRCULAR] = chCircular.in();
    entradas[SALIR] = chSalir.in();
    entradas[TICK] = chTick.in();

    // Estructura que selecciona entre los canales activos (con CSP fair select)
    Alternative servicios = new Alternative(entradas);

    // Por definicion, siempre se puede salir o hacer tick
    sincCond[SALIR] = true;
    sincCond[TICK] = true;

    // Bucle principal del proceso servidor
    while (true) {
      // Condiciones de sincronización evitando sobrecargar las colas de espera

      sincCond[ENTRAR] = !carrilesLibres(1, coches).isEmpty(); // ha de haber carriles libres en el segmento 1 para que
                                                               // puedan entrar coches (podria ser true sin embargo de
                                                               // esta manera añadimos a la cola de espera el menor
                                                               // numero de coches posible evitando comprobar tantos
                                                               // coches cada tick)

      sincCond[AVANZAR] = !listaAvanzar.isEmpty(); // ha de haber coches disponibles para avanzar (podria ser true pero
                                                   // asi protege al codigo frente a pruebas maliciosas con coches que
                                                   // traten de por ejemplo avanzar antes de circular de no entrar en la
                                                   // cola de peticiones aplazadas y tambien de añadir a la cola de
                                                   // espera el menor numero de coches posibles)

      sincCond[CIRCULAR] = !listaCircular.isEmpty(); // ha de haber coches disponibles para circular (podria ser true
                                                     // pero asi protege al codigo evitando demasiadas comprobaciones en
                                                     // la lista de espera para circular ya que todos los coches que no
                                                     // cumplan ticks==0 se añadirian y conllevaria su respectiva
                                                     // comprobacion)

      // Selección del servicio que está listo
      int servicio = servicios.fairSelect(sincCond);
      // Switch de los procesos
      switch (servicio) {
        // Lógica para aceptar coches que quieren entrar
        case ENTRAR: {
          PetEntrar pet = (PetEntrar) chEntrar.in().read(); // Leemos peticion
          Set<Integer> libres = carrilesLibres(1, coches); // Observamos si hay carriles libres

          if (!libres.isEmpty()) { // Si hay carriles libres entra
            int carril = libres.iterator().next();// primer carril libre
            Pos pos = new Pos(1, carril);
            coches.put(pet.id, new InfoCoche(pos, pet.tks));// mete el coche en la carretera
            pet.resp.write(pos); // devuelve la posicion en el canal de respuesta
          } else {
            colaEsperaEntrar.addLast(pet); // Se guarda para más tarde cuando puedan entrar
          }
          break;
        }

        // Lógica para permitir avanzar a los coches
        case AVANZAR: {
          PetAvanzar pet = (PetAvanzar) chAvanzar.in().read();// Leemos peticion
          String id = pet.id;
          InfoCoche coche = coches.get(id);
          int segNuevo = coche.pos.getSegmento() + 1;
          Set<Integer> libres = carrilesLibres(segNuevo, coches);// carriles libres en el siguiente segmento

          if (!libres.isEmpty()) { // si hay carriles libres avanzamos el coche
            int nuevoCarril = libres.iterator().next();
            coche.pos = new Pos(segNuevo, nuevoCarril);// nueva posicion del coche
            coche.ticks = pet.tks;
            pet.resp.write(coche.pos); // devuelve la posicion en el canal de respuesta
            listaAvanzar.remove(id); // se elimina de la lista de avanzar
          } else {
            colaEsperaAvanzar.addLast(pet);// Se guarda para más tarde cuando puedan avanzar
          }

          break;
        }

        // Lógica para circular
        case CIRCULAR: {
          PetCircular pet = (PetCircular) chCircular.in().read();// Leemos peticion
          String id = pet.id;

          if (puedeCircular(id, coches)) { // si puede circular (ticks==0)
            if (!listaAvanzar.contains(id)) { // y no ha circulado ya (no esta en avanzar)
              listaAvanzar.add(id); // lo añade a aavanzar
            }
            pet.resp.write(null);// escribe en el canal de respuesta
            listaCircular.remove(pet.id);// lo elimina de la lista para circular
          } else {
            colaEsperaCircular.add(pet); // Se guarda para más tarde cuando puedan circular
          }
          break;
        }

        // Lógica para permitir salir a los coches
        case SALIR: {
          PetSalir pet = (PetSalir) chSalir.in().read();// Leemos peticion
          String id = pet.id;

          // Eliminamos el coche de todas las listas
          coches.remove(id);
          listaAvanzar.remove(id);
          listaCircular.remove(id);
          break;
        }

        case TICK: {
          chTick.in().read();// Leemos la peticion de entrada

          for (String id : coches.keySet()) { // comprueba todos los coches en la carretera
            InfoCoche coche = coches.get(id);
            coche.ticks = Math.max(0, coche.ticks - 1); // En todos los coches tick-1
            if (coche.ticks == 0 &&
                !listaAvanzar.contains(id) &&
                !listaCircular.contains(id)) {
              listaCircular.add(id); // Se mueve a circular si no estaba en avanzar o circualar y ticks==0
            }
          }
          break;
        }
      }

      // atender peticiones pendientes que puedan ser atendidas
      atenderEsperasAvanzar(colaEsperaAvanzar, coches, listaAvanzar);
      atenderEsperasCircular(colaEsperaCircular, coches, listaCircular, listaAvanzar);
      atenderEsperasEntrar(colaEsperaEntrar, coches);

    }
  }

  // Método auxiliar que comprueba si un coche cumple los requisitos para cirular
  private boolean puedeCircular(String id, Map<String, InfoCoche> coches) {
    InfoCoche c = coches.get(id);
    return c != null && c.ticks == 0 && c.pos.getSegmento() <= segmentos;
  }

  // Método auxiliar que devuelve un set de los carriles libres de un determinado
  // segmento
  private Set<Integer> carrilesLibres(int seg, Map<String, InfoCoche> coches) {
    Set<Integer> ocupados = new HashSet<>();// crea un set de carriles ocupados
    for (Object c : coches.values()) {
      Pos p = ((InfoCoche) c).pos;
      if (p.getSegmento() == seg) { // comprueba si los coches estan en el segmento
        ocupados.add(p.getCarril()); // añade el carril ocupado al set
      }
    }
    Set<Integer> libres = new HashSet<>();
    for (int i = 1; i <= carriles; i++) { // recorre los carriles del segmento
      if (!ocupados.contains(i)) // si el carril no esta en el set ocupados
        libres.add(i);// lo añade a libres
    }
    return libres;
  }

  // Método auxiliar para desbloquear peticiones de entrada aun no atendidas
  private void atenderEsperasEntrar(LinkedList<PetEntrar> colaEsperaEntrar, Map<String, InfoCoche> coches) {
    boolean huboEntrada; // flag para comprobar si la lista de peticiones aplazadas es modificada
    do {
      huboEntrada = false; // flag de entrada aplazada flase
      Iterator<PetEntrar> it = colaEsperaEntrar.iterator();

      while (it.hasNext()) { // recorre toda la lista de peticiones
        PetEntrar pet = it.next();
        Set<Integer> libres = carrilesLibres(1, coches);
        if (!libres.isEmpty()) { // si hay carriles libres entra el coche
          int carril = libres.iterator().next();
          Pos pos = new Pos(1, carril);
          coches.put(pet.id, new InfoCoche(pos, pet.tks));
          pet.resp.write(pos);// devuelve la posicion en el canal de respuesta
          it.remove();// lo elimina de la cola de pendientes
          huboEntrada = true; // activa el flag ya que hubo una entrada (se vuelve a comprobar la lista de
                              // peticiones ya que esta fue modificada)
        }
      }
    } while (huboEntrada);// hasta que la lista no se modifique (o este vacia por ello no mse modifica) se
                          // recorre buscando cumplir todas las peticiones aplzadas
  }

  // Método auxiliar para desbloquear peticiones para avanzar aun no atendidas
  private void atenderEsperasAvanzar(LinkedList<PetAvanzar> colaEsperaAvanzar, Map<String, InfoCoche> coches,
      Queue<String> listaAvanzar) {
    boolean huboAvance;// flag para comprobar si la lista de peticiones aplazadas es modificada
    do {
      huboAvance = false; // flag de avance apalzado a false
      Iterator<PetAvanzar> it = colaEsperaAvanzar.iterator();

      while (it.hasNext()) {// se recorre toda la lista
        PetAvanzar pet = it.next();
        InfoCoche coche = coches.get(pet.id);
        int segNuevo = coche.pos.getSegmento() + 1;
        Set<Integer> libres = carrilesLibres(segNuevo, coches);

        if (!libres.isEmpty()) { // si hay carriles libres en el siguiente segmento avanza
          int nuevoCarril = libres.iterator().next();// primer carril libre
          coche.pos = new Pos(segNuevo, nuevoCarril);
          coche.ticks = pet.tks;
          listaAvanzar.remove(pet.id);// lo elimina de la lista para avanzar
          pet.resp.write(coche.pos);
          it.remove(); // Eliminar de la cola de espera
          huboAvance = true;// activa el flag ya que hubo un avance (se vuelve a comprobar la lista de
                            // peticiones ya que esta fue modificada)

        }
      }
    } while (huboAvance);// hasta que la lista no se modifique (o este vacia por ello no mse modifica) se
                         // recorre buscando cumplir todas las peticiones aplzadas
  }

  // Método auxiliar para desbloquear peticiones para circular aun no atendidas
  private void atenderEsperasCircular(LinkedList<PetCircular> colaEsperaCircular, Map<String, InfoCoche> coches,
      Queue<String> listaCircular, Queue<String> listaAvanzar) {
    boolean huboCirculacion;// flag para comprobar si la lista de peticiones aplazadas es modificada
    do {
      huboCirculacion = false;// flag de circulacion aplazada a false
      Iterator<PetCircular> it = colaEsperaCircular.iterator();

      while (it.hasNext()) {// se comprueba toda la lista de peticiones aplazadas
        PetCircular pet = it.next();
        if (puedeCircular(pet.id, coches)) {// si puede circular (ticks==0)
          if (!listaAvanzar.contains(pet.id)) {// y no ha circulado ya (no esta en avanzar)
            listaAvanzar.add(pet.id);// lo añade a aavanzar
          }
          pet.resp.write(null);// escribe en el canal de respuesta
          listaCircular.remove(pet.id);// lo elimina de la lista para circular
          it.remove();// lo elimina de la cola de pendientes
          huboCirculacion = true;// activa el flag ya que hubo circulacion (se vuelve a comprobar la lista de
          // peticiones ya que esta fue modificada)
        }
      }
    } while (huboCirculacion);// hasta que la lista no se modifique (o este vacia por ello no mse modifica) se
                              // recorre buscando cumplir todas las peticiones aplzadas
  }
}
