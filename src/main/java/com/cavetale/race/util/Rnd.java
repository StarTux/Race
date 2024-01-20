package com.cavetale.race.util;

import java.util.List;
import java.util.Random;

public final class Rnd {
    private static final Random RND = new Random();

    private Rnd() { }

    public static Random get() {
        return RND;
    }

    public static int nextInt(int in) {
        return RND.nextInt(in);
    }

    public static boolean nextBoolean() {
        return RND.nextBoolean();
    }

    public static double nextDouble() {
        return RND.nextDouble();
    }

    public static <E> E pick(E[] array) {
        if (array.length == 0) return null;
        return array[RND.nextInt(array.length)];
    }

    public static <E> E pick(List<E> list) {
        if (list.isEmpty()) return null;
        return list.get(RND.nextInt(list.size()));
    }
}
