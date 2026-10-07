package org.ipsecuz.pet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum PetAnimationState {
    IDLE("idle", false, 0, "fly_idle", "walk", "sit"),
    WALK("walk", false, 0, "fly", "run", "idle"),
    RUN("run", false, 0, "walk", "fly", "idle"),
    FLY_IDLE("fly_idle", false, 0, "fly", "idle"),
    FLY("fly", false, 0, "fly_idle", "walk", "idle"),
    ATTACK("attack", true, 20, "skill_cast", "run", "idle"),
    HURT("hurt", true, 15, "sad", "idle"),
    SPAWN("spawn", true, 30, "celebrate", "happy", "idle"),
    FEED("feed", true, 30, "eat", "happy", "celebrate", "idle"),
    HAPPY("happy", true, 30, "celebrate", "idle"),
    SKILL_CHARGE("skill_charge", true, 35, "skill_cast", "attack", "idle"),
    SKILL_CAST("skill_cast", true, 25, "attack", "idle"),
    EVOLVE("evolve", true, 50, "skill_charge", "celebrate", "idle"),
    CELEBRATE("celebrate", true, 40, "happy", "idle"),
    SAD("sad", false, 0, "hurt", "idle"),
    DEATH("death", true, 40, "hurt", "sad", "idle");

    private final String primaryName;
    private final boolean transientState;
    private final int defaultDurationTicks;
    private final List<String> fallbacks;

    PetAnimationState(String primaryName, boolean transientState, int defaultDurationTicks, String... fallbacks) {
        this.primaryName = primaryName;
        this.transientState = transientState;
        this.defaultDurationTicks = defaultDurationTicks;
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

    public List<String> getFallbacks() {
        return fallbacks;
    }
}
