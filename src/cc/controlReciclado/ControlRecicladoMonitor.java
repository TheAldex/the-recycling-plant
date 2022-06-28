package cc.controlReciclado;

import es.upm.babel.cclib.Monitor;

import java.util.ArrayList;
import java.util.Iterator;


public final class ControlRecicladoMonitor implements ControlReciclado {
  // vars
  private enum Estado {LISTO, SUSTITUIBLE, SUSTITUYENDO}
  private final int MAX_P_CONTENEDOR;
  private final int MAX_P_GRUA;

  // shared resources
  private int peso;
  private Estado estado;
  private int accediendo;

  // mutex vars
  private Monitor mutex;
  private ArrayList<BloqueoGruas> gruas;

  private Monitor.Cond cNotificarPeso;
  private Monitor.Cond cPrepararSustitucion;

  // object to create conditions
  class BloqueoGruas {
    public int p;
    public Monitor.Cond m;

    public BloqueoGruas(int p, Monitor m) {
      this.p = p;
      this.m = m.newCond();
    }
  }

  // constructor
  public ControlRecicladoMonitor(int max_p_contenedor, int max_p_grua) {
    MAX_P_CONTENEDOR = max_p_contenedor;
    MAX_P_GRUA = max_p_grua;

    // initial state
    estado = Estado.LISTO;
    peso = 0;
    accediendo = 0;

    mutex = new Monitor();
    gruas = new ArrayList<>();
    cNotificarPeso = mutex.newCond();
    cPrepararSustitucion = mutex.newCond();
  }

  // CPRE: notificarPeso
  private boolean cpreNotificarPeso(){
    return estado != ControlRecicladoMonitor.Estado.SUSTITUYENDO;
  }

  // CPRE: incrementarPeso
  private boolean cpreIncrementarPeso(int p){
    return peso + p <= MAX_P_CONTENEDOR && estado != ControlRecicladoMonitor.Estado.SUSTITUYENDO;
  }

  // CPRE: prepararSustitución
  private boolean cprePrepararSustitucion() {
    return estado == ControlRecicladoMonitor.Estado.SUSTITUIBLE && accediendo == 0;
  }

  public void notificarPeso(int p) {
    // PRE evaluation
    if (p <= 0 || p > MAX_P_GRUA) {
      throw new IllegalArgumentException();
    }

    // begin mutex
    mutex.enter();
    // !CPRE -> block
    if (!cpreNotificarPeso()) {
      cNotificarPeso.await();
    }
    // CPRE
    if ((peso + p) > MAX_P_CONTENEDOR) {
      estado = Estado.SUSTITUIBLE;
    }
    else {
      estado = Estado.LISTO;
    }

    desbloqueo();
    mutex.leave();
  }

  public void incrementarPeso(int p) {
    // PRE evaluation
    if (p <= 0 || p > MAX_P_GRUA) {
      throw new IllegalArgumentException();
    }

    // begin mutex
    mutex.enter();
    // !CPRE -> block
    if (!cpreIncrementarPeso(p)) {
      BloqueoGruas petition = new BloqueoGruas(p, mutex);
      gruas.add(petition);
      petition.m.await();
    }
    // CPRE
    peso = peso + p;
    accediendo++;

    desbloqueo();
    mutex.leave();
  }

  public void notificarSoltar() {
    // begin mutex
    mutex.enter();
    // CPRE
    accediendo--;

    desbloqueo();
    mutex.leave();
  }

  public void prepararSustitucion() {
    // begin mutex
    mutex.enter();
    // !CPRE -> block
    if (!cprePrepararSustitucion()) {
      cPrepararSustitucion.await();
    }
    // CPRE
    estado = Estado.SUSTITUYENDO;
    // this operation does not unblock
    mutex.leave();
  }

  public void notificarSustitucion() {
    mutex.enter();
    // CPRE
    peso = 0;
    estado = Estado.LISTO;
    accediendo = 0;

    desbloqueo();
    mutex.leave();
  }

  // unblock code
  private void desbloqueo() {
    // notificarPeso() unblock
    if (cpreNotificarPeso() && cNotificarPeso.waiting() > 0) {
      cNotificarPeso.signal();
    }
    // prepararSustitucion() unblock
    else if(cprePrepararSustitucion() && cPrepararSustitucion.waiting() > 0) {
      cPrepararSustitucion.signal();
    }
    // incrementarPeso(p) unblock
    else {
      BloqueoGruas gruaBloqueada;
      Iterator<BloqueoGruas> iterator = gruas.iterator();

      // there is a crane to unblock
      boolean signaled = false;

      while (iterator.hasNext() && signaled==false) {
        gruaBloqueada = iterator.next();

        if (peso + gruaBloqueada.p <= MAX_P_CONTENEDOR) {
          // to unblock
          signaled = true;
        }
        // crane unblock
        if (signaled) {
          iterator.remove();
          gruaBloqueada.m.signal();
        }
      }
    }
  }
}
