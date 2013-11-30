package org.quuux.eyecandy.utils;


import java.util.Random;

public class RandomGenerator {
    private final Random mRandom;

    private RandomGenerator() {
        mRandom = new Random();
    }

    public float randomRange(float min, float max) {
        return min + ((max-min) * mRandom.nextFloat());
    }

    public float randomPercentile() {
        return randomRange(0, 1);
    }

    public int randomInt(int min, int max) {
        return min + mRandom.nextInt(max-min);
    }

    public static RandomGenerator get() {
        return new RandomGenerator();
    }

}