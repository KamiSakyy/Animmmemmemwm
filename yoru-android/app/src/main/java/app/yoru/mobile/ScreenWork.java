package app.yoru.mobile;

import java.util.*;

/** Loading remains owned until the main-thread result callback has been applied. */
final class ScreenWork {

  static final class Ticket {

    final int screen;
    final long id;

    Ticket(int screen, long id) {
      this.screen = screen;
      this.id = id;
    }
  }

  private long next;
  private final Map<Integer, Set<Long>> pending = new HashMap<>();

  synchronized Ticket begin(int screen) {
    Ticket ticket = new Ticket(screen, ++next);
    pending.computeIfAbsent(screen, key -> new HashSet<>()).add(ticket.id);
    return ticket;
  }

  synchronized void finish(Ticket ticket) {
    Set<Long> ids = pending.get(ticket.screen);
    if (ids == null) return;
    ids.remove(ticket.id);
    if (ids.isEmpty()) pending.remove(ticket.screen);
  }

  synchronized Set<Integer> cancelAll() {
    Set<Integer> screens = new HashSet<>(pending.keySet());
    pending.clear();
    return screens;
  }

  synchronized boolean busy(int screen) {
    return pending.containsKey(screen);
  }
}
