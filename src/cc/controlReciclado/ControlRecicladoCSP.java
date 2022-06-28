package cc.controlReciclado;

import org.jcsp.lang.*;

import java.util.LinkedList;
import java.util.Queue;

public class ControlRecicladoCSP implements ControlReciclado, CSProcess {

  // vars
  private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

  private final int MAX_P_CONTENEDOR;
  private final int MAX_P_GRUA;

  // comunication channels
  private final Any2OneChannel chNotificarPeso;
  private final Any2OneChannel chIncrementarPeso;
  private final Any2OneChannel chNotificarSoltar;
  private final One2OneChannel chPepararSustitucion;
  private final One2OneChannel chNotificarSustitucion;

  // to defer petitions
  private static class PetIncrementarPeso {
    public int p;
    public One2OneChannel chACK;

    public PetIncrementarPeso (int p) {
      this.p = p;
      this.chACK = Channel.one2one();
    }
  }

  // constructor
  public ControlRecicladoCSP (int max_p_contenedor, int max_p_grua) {
    MAX_P_CONTENEDOR = max_p_contenedor;
    MAX_P_GRUA = max_p_grua;

    // channel creation
    chNotificarPeso = Channel.any2one();
    chIncrementarPeso = Channel.any2one();
    chNotificarSoltar = Channel.any2one();
    chPepararSustitucion = Channel.one2one();
    chNotificarSustitucion = Channel.one2one();

    new ProcessManager(this).start();
  }

  // PRE: 0 < p < MAX_P_GRUA
  // CPRE: self.estado =/= SUSTITUYENDO
  // notificarPeso(p)
  public void notificarPeso(int p) {
    // PRE
    if (p > MAX_P_GRUA || p <= 0) {
      throw new IllegalArgumentException();
    }

    // PRE OK, send pet
    chNotificarPeso.out().write(p);
    // wait confirmation
    chNotificarPeso.in().read();
  }

  // PRE: 0 < p < MAX_P_GRUA
  // CPRE: self.estado =/= SUSTITUYENDO /\
  //       self.peso + p <= MAX_P_CONTENEDOR
  // incrementarPeso(p)
  public void incrementarPeso(int p) {
    // PRE
    if (p > MAX_P_GRUA || p <= 0) {
      throw new IllegalArgumentException();
    }

    // PRE OK, pet creation
    PetIncrementarPeso petition = new PetIncrementarPeso(p);

    // send pet
    chIncrementarPeso.out().write(petition);
    // wait confirmation
    petition.chACK.in().read();
  }

  // PRE: --
  // CPRE: --
  // notificarSoltar()
  public void notificarSoltar() {
    // send pet
    chNotificarSoltar.out().write(null);
  }

  // PRE: --
  // CPRE: self = (_, sustituible, 0)
  // prepararSustitucion()
  public void prepararSustitucion() {
    // send pet
    chPepararSustitucion.out().write(null);
    // wait confirmation
    chPepararSustitucion.in().read();
  }

  // PRE: --
  // CPRE: --
  // notificarSustitucion()
  public void notificarSustitucion() {
    // send pet
    chNotificarSustitucion.out().write(null);
  }

  // SERVER
  public void run() {
    // initial state
    int peso = 0;
    Estado estado = Estado.LISTO;
    int accediendo = 0;

    // aux var
    boolean hayPet;

    Guard[] entradas = {
            chNotificarPeso.in(),
            chIncrementarPeso.in(),
            chNotificarSoltar.in(),
            chPepararSustitucion.in(),
            chNotificarSustitucion.in()
    };
    Alternative servicios = new Alternative(entradas);
    final int NOTIFICAR_PESO = 0;
    final int INCREMENTAR_PESO = 1;
    final int NOTIFICAR_SOLTAR = 2;
    final int PREPARAR_SUSTITUCION = 3;
    final int NOTIFICAR_SUSTITUCION = 4;
    // reception conditions
    final boolean[] sincCond = new boolean[5];

    sincCond[NOTIFICAR_SOLTAR] = true;
    sincCond[NOTIFICAR_SUSTITUCION] = true;

    // to store defer pets
    Queue<PetIncrementarPeso> petIncrementarCola = new LinkedList<>();

    // server loop
    while (true) {

      // aux vars for communicating with users
      hayPet = true;

      // update conditions
      if (estado != Estado.SUSTITUYENDO) {
        sincCond[NOTIFICAR_PESO] = true;
        sincCond[INCREMENTAR_PESO] = true;
      }
      else {
        sincCond[NOTIFICAR_PESO] = false;
        sincCond[INCREMENTAR_PESO] = false;
      }

      if (estado == Estado.SUSTITUIBLE && accediendo == 0) {
        sincCond[PREPARAR_SUSTITUCION] = true;
      }
      else {
        sincCond[PREPARAR_SUSTITUCION] = false;
      }

      switch (servicios.fairSelect(sincCond)) {
        case NOTIFICAR_PESO:
          // estado != Estado.SUSTITUYENDO
          // read pet
          int pesoLeido = (int) chNotificarPeso.in().read();

          if (pesoLeido + peso > MAX_P_CONTENEDOR) {
            estado = Estado.SUSTITUIBLE;
          }
          else {
            estado = Estado.LISTO;
          }

          // proccess pet
          chNotificarPeso.out().write(true);

          break;

        case INCREMENTAR_PESO:
          // read pet
          PetIncrementarPeso pet = (PetIncrementarPeso) chIncrementarPeso.in().read();

          // preccess pet or defer if !CPRE
          if (pet != null && ((pet.p + peso) <= MAX_P_CONTENEDOR)) {
            // CPRE
            peso = pet.p + peso;
            accediendo++;
            // send response
            pet.chACK.out().write(true);
          }
          // !CPRE
          else {
            petIncrementarCola.add(pet);
          }

          break;

        case NOTIFICAR_SOLTAR:
          // accediendo > 0 
          // read pet
          chNotificarSoltar.in().read();

          // proccess pet
          accediendo = accediendo - 1;

          break;

        case PREPARAR_SUSTITUCION:
          // estado == Estado.SUSTITUIBLE && accediendo == 0
          // read pet
          chPepararSustitucion.in().read();

          // proccess pet
          estado = Estado.SUSTITUYENDO;
          // send response
          chPepararSustitucion.out().write(true);
          // avoid loop
          hayPet = false;

          break;

        case NOTIFICAR_SUSTITUCION:
          // estado == Estado.SUSTITUYENDO && accediendo == 0
          // read pet
          chNotificarSustitucion.in().read();

          // proccess pet
          peso = 0;
          estado = Estado.LISTO;
          accediendo = 0;

          break;
      } // switch

      // defer pets
      while (hayPet) {
        hayPet = false;
        int tam = petIncrementarCola.size();
        PetIncrementarPeso actual;

        // while there are pets
        while (tam > 0) {
          // begin with the first pet
          actual = petIncrementarCola.peek();

          // CPRE = true
          if (actual.p + peso <= MAX_P_CONTENEDOR) {
            hayPet = true;
            peso = actual.p + peso;
            accediendo++;

            // dequeue pet
            petIncrementarCola.remove();

            // send response
            actual.chACK.out().write(true);
          }
          // !CPRE
          else {
            petIncrementarCola.remove();
            petIncrementarCola.add(actual);
          }
          tam--;
        }
      }
    } // server loop
  } // run() SERVER
}  // class ControlRecicladoCSP
