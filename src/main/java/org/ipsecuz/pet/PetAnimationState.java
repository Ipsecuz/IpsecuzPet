package org.ipsecuz.pet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum PetAnimationState {
    IDLE("idle", false, 0, 0, "fly_idle", "walk", "sit"),
    WALK("walk", false, 0, 0, "fly", "run", "idle"),
    RUN("run", false, 0, 0, "walk", "fly", "idle"),
    FLY_IDLE("fly_idle", false, 0, 0, "fly", "idle"),
    FLY("fly", false, 0, 0, "fly_idle", "walk", "idle"),
    ATTACK("attack", true, 20, 50, "skill_cast", "run", "idle"),
    HURT("hurt", true, 15, 60, "sad", "idle"),
    SPAWN("spawn", true, 30, 65, "celebrate", "happy", "idle"),
    FEED("feed", true, 30, 40, "eat", "happy", "celebrate", "idle"),
    HAPPY("happy", true, 30, 20, "celebrate", "idle"),
    SKILL_CHARGE("skill_charge", true, 35, 80, "skill_cast", "attack", "idle"),
    SKILL_CAST("skill_cast", true, 25, 90, "attack", "idle"),
    EVOLVE("evolve", true, 50, 100, "skill_charge", "celebrate", "idle"),
    CELEBRATE("celebrate", true, 40, 70, "happy", "idle"),
    LEVEL_UP("celebrate", true, 40, 70, "happy", "idle"),
    SAD("sad", false, 0, 10, "hurt", "idle"),
    DEATH("death", true, 40, 70, "hurt", "sad", "idle");

    private final String primaryName;
    private final boolean transientState;
    private final int defaultDurationTicks;
    private final int priority;
    private final List<String> fallbacks;

    PetAnimationState(String primaryName, boolean transientState, int defaultDurationTicks, int priority, String... fallbacks) {
        this.primaryName = primaryName;
        this.transientState = transientState;
        this.defaultDurationTicks = defaultDurationTicks;
        this.priority = priority;
        this.fallbacks = Collections.unmodifiableList(Arrays.asList(fallbacks));
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public boolean isTransient() {
        return transientState;
    }

    public int getDefaultDurationTicks() {
        return defaultDurationTicks;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getFallbacks() {
        return fallbacks;
    }
}

