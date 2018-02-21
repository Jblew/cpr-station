/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public final class Blinker {
    private static final Blinker INSTANCE = new Blinker();
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory("blink-thread"));
    private final List<WeakReference<Blinkable>> buttons = Collections.synchronizedList(new LinkedList<>());

    private Blinker() {
        sched.scheduleAtFixedRate(() -> {
            blinkAll();
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void blinkAll() {
        for (WeakReference<Blinkable> ref : buttons) {
            Blinkable bbSafe = ref.get();

            if (bbSafe == null) {
                buttons.remove(ref);
            } else {
                bbSafe.blink();
            }
        }
    }

    public static void addBlinkable(Blinkable b) {
        INSTANCE.buttons.add(new WeakReference<>(b));
    }
    
    public static interface Blinkable {
        public void blink();
    }
}
