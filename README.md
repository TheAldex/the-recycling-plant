# THE RECYCLING PLANT PROBLEM BY ANGEL HERRANZ
# JAVA CONCURRENCY USING MONITORS AND JCSP

The solution is specified in ControlRecicladoMonitor class and ControlRecicladoCSP class

In a recycling plant certain metals are recovered from the scrap by a series of cranes equipped with strong electromagnets. These cranes are responsible for depositing the metals in a container until it is more or less full.

The cranes are operated through a class whose interface is called ApiGruas. Similarly, a class is available to control the displacement of the container, called ApiContenedor.
We want to implement a concurrent system to control the cranes and the container so that the capacity of the containers is not exceeded, and replace full containers with empty ones, ensuring that no attempt is ever made to deposit metal in the container area while the container is being replaced. To simplify the problem we will assume that there is enough space for some cranes not to get physically in the way of others.
We will have a process to control each crane and another to handle the replacement of containers. Communication and synchronization is the responsibility of a manager.

The idea is that the internal state of the shared resource is rich enough to determine when to request the replacement of the container and synchronize the processes that control the cranes with that circumstance.
More specifically, a replenishment state will be available which can take one of three values: LISTO, which means that the container can accept more load; SUSTITUIBLE, which indicates that at least one of the cranes carries more load than can be accommodated in the container, so such a crane requests a new container, and SUSTITUYENDO, state in which no cargo should be deposited as the container may be being replaced. Note that even in the replaceable state it is possible for a crane other than the one that has requested the replacement of the container to deposit a smaller quantity in the container.



