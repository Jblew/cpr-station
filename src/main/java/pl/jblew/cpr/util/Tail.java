/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author teofil
 */
public class Tail<T> implements Iterable<T> {
    private final Object syncO = new Object();
    private final LinkedBlockingQueue<CountDownLatch> awaitLatches = new LinkedBlockingQueue<>();
    private final T[] buffer;
    private int firstPointer;
    private int lastPointer;
    private boolean hadOverflow = false;

    public Tail(int n) {
        buffer = (T[]) new Object[n];
        firstPointer = 0;
        lastPointer = 0;
    }

    public void add(T elem) {
        synchronized (syncO) {
            lastPointer++;

            if (lastPointer > buffer.length - 1) {
                lastPointer = 0;
            }

            if (lastPointer == firstPointer || hadOverflow) {
                firstPointer++;

                if (firstPointer > buffer.length - 1) {
                    firstPointer = 0;
                }
                hadOverflow = true;
            }

            buffer[lastPointer] = elem;

            while (!awaitLatches.isEmpty()) {
                CountDownLatch l = awaitLatches.poll();
                if (l != null) {
                    l.countDown();
                } else {
                    break;
                }
            }
        }
    }

    public T get(int index) {
        synchronized (syncO) {
            if (index < 0 || index >= buffer.length) {
                throw new IndexOutOfBoundsException();
            }

            int actualIndex = firstPointer + index;
            if (actualIndex > buffer.length - 1) {
                actualIndex -= buffer.length;
            }

            return buffer[actualIndex];
        }
    }

    public void addListenerLatch(CountDownLatch cdl) {
        awaitLatches.add(cdl);
    }

    @Override
    public Iterator<T> iterator() {
        return new TailIterator();
    }

    private class TailIterator implements Iterator<T> {
        private final Object syncO = new Object();
        private int index = 0;

        @Override
        public boolean hasNext() {
            synchronized (syncO) {
                return (index + 1) >= 0 && (index + 1) < buffer.length;
            }
        }

        @Override
        public T next() {
            synchronized (syncO) {
                index++;
                return get(index);
            }
        }

    }
}
