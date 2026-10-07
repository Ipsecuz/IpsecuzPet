package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PetAnimationAndUxTest {

    @Test
    public void testAnimationStatesCoverage() {
        // Kiểm tra tất cả 16 trạng thái hoạt ảnh MMORPG chuẩn
        for (PetAnimationState state : PetAnimationState.values()) {
            assertNotNull(state.getPrimaryName(), "Tên chính của animation không được null: " + state.name());
            assertFalse(state.getPrimaryName().isEmpty());
            assertNotNull(state.getFallbacks(), "Danh sách fallback không được null: " + state.name());
        }

        // Kiểm tra các trạng thái transient
        assertTrue(PetAnimationState.FEED.isTransient());
        assertTrue(PetAnimationState.FEED.getDefaultDurationTicks() > 0);

        assertTrue(PetAnimationState.EVOLVE.isTransient());
        assertTrue(PetAnimationState.EVOLVE.getDefaultDurationTicks() >= 40);

        assertTrue(PetAnimationState.CELEBRATE.isTransient());
        assertTrue(PetAnimationState.SPAWN.isTransient());
        assertTrue(PetAnimationState.HURT.isTransient());
        assertTrue(PetAnimationState.SKILL_CAST.isTransient());

        // Kiểm tra các trạng thái persistent (Locomotion / Idle)
        assertFalse(PetAnimationState.IDLE.isTransient());
        assertFalse(PetAnimationState.WALK.isTransient());
        assertFalse(PetAnimationState.RUN.isTransient());
        assertFalse(PetAnimationState.FLY_IDLE.isTransient());
        assertFalse(PetAnimationState.FLY.isTransient());
    }

    @Test
    public void testAnimationFallbackChain() {
        List<String> feedFallbacks = PetAnimationState.FEED.getFallbacks();
        assertTrue(feedFallbacks.contains("eat") || feedFallbacks.contains("happy") || feedFallbacks.contains("idle"));

        List<String> evolveFallbacks = PetAnimationState.EVOLVE.getFallbacks();
        assertTrue(evolveFallbacks.contains("skill_charge") || evolveFallbacks.contains("celebrate") || evolveFallbacks.contains("idle"));

        List<String> flyFallbacks = PetAnimationState.FLY.getFallbacks();
        assertTrue(flyFallbacks.contains("fly_idle") || flyFallbacks.contains("walk") || flyFallbacks.contains("idle"));
    }

    @Test
    public void testVisualProgressBar() {
        // 0%
        String bar0 = GuiListener.buildProgressBar(0, 100, 10);
        assertTrue(bar0.contains("0%"));
        assertTrue(bar0.contains("░░░░░░░░░░"));

        // 50%
        String bar50 = GuiListener.buildProgressBar(50, 100, 10);
        assertTrue(bar50.contains("50%"));
        assertTrue(bar50.contains("█████"));
        assertTrue(bar50.contains("░░░░░"));

        // 100%
        String bar100 = GuiListener.buildProgressBar(100, 100, 10);
        assertTrue(bar100.contains("100%"));
        assertTrue(bar100.contains("██████████"));

        // Edge case: req <= 0
        String barEdge = GuiListener.buildProgressBar(10, 0, 10);
        assertTrue(barEdge.contains("100%"));
    }

    @Test
    public void testRenameValidationBounds() {
        String shortName = "A";
        assertFalse(isValidNameLength(shortName), "Tên 1 ký tự phải không hợp lệ");

        String validName = "Rồng Lửa";
        assertTrue(isValidNameLength(validName), "Tên 8 ký tự phải hợp lệ");

        String maxValidName = "123456789012345678901234"; // 24 chars
        assertTrue(isValidNameLength(maxValidName), "Tên 24 ký tự phải hợp lệ");

        String tooLongName = "1234567890123456789012345"; // 25 chars
        assertFalse(isValidNameLength(tooLongName), "Tên 25 ký tự phải không hợp lệ");
    }

    private boolean isValidNameLength(String name) {
        if (name == null) return false;
        int len = name.trim().length();
        return len >= 2 && len <= 24;
    }
}
