package cc.controlReciclado;

import es.upm.babel.cclib.Semaphore;
import es.upm.babel.cclib.ConcIO;
import java.util.Random;

public class ApiContenedor {
  public final int MAX_P_CONTENEDOR;

  private Semaphore mutex = new Semaphore(1);

  private Random random = new Random(0);

  private int peso = 0;

  private boolean preparado = true;

  private boolean atascado = false;

  public ApiContenedor(int max_p_contenedor) {
    MAX_P_CONTENEDOR = max_p_contenedor;
  }

  public void sustituir() {
    if (peso > MAX_P_CONTENEDOR) {
      while (true) {
        ConcIO.printfnl ("ERROR: el contenedor no se puede mover, peso " + peso);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException x) {}
      }
    }
    preparado = false;
    try {
      ConcIO.printfnl ("Retirando contenedor con peso " + peso);
      mutex.await();
      int t = random.nextInt(peso / 10);
      peso = 0;
      mutex.signal();
      Thread.sleep(t);
    } catch (InterruptedException x) {}
    if (atascado) {
      while (true) {
        ConcIO.printfnl ("ERROR: contenedor atascado por chatarra en carril.");
        try {
          Thread.sleep(1000);
        } catch (InterruptedException x) {}
      }

    }
    preparado = true;
  }

  public void incrementar(int p) {
    boolean sobrepeso;
    mutex.await();
    peso += p;
    sobrepeso = peso > MAX_P_CONTENEDOR;
    mutex.signal();
    if (!preparado) {
      atascado = true;
    }
    if (sobrepeso) {
      ConcIO.printfnl ("PESO LÍMITE SOBREPASADO: ¡" + peso + "!");
    }
  }
}
