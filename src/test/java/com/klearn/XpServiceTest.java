package com.klearn;

import com.klearn.model.User;
import com.klearn.repository.UserRepository;
import com.klearn.service.XpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private XpService xpService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setTotalXp(50); // Đang ở Level 1 (0-99)
        testUser.setCurrentLevel(1);
    }

    @Test
    void addXp_ShouldNotUpdate_WhenUserNotFound() {
        // Giả lập (Mock) hành vi: Tìm user id 99 trả về rỗng
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        xpService.addXp(99L, 10);

        // Xác minh (Verify) rằng hàm save() không bao giờ được gọi
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void addXp_ShouldNotUpdate_WhenXpRewardIsZeroOrNegative() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        xpService.addXp(1L, 0);
        xpService.addXp(1L, -10);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void addXp_ShouldAddXpWithoutLevelUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Cộng 20 XP -> Tổng là 70 XP (< 100 nên vẫn Level 1)
        xpService.addXp(1L, 20);

        assertEquals(70, testUser.getTotalXp());
        assertEquals(1, testUser.getCurrentLevel());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void addXp_ShouldAddXpAndLevelUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Cộng 60 XP -> Tổng là 110 XP (>= 100 nên lên Level 2)
        xpService.addXp(1L, 60);

        assertEquals(110, testUser.getTotalXp());
        assertEquals(2, testUser.getCurrentLevel());
        verify(userRepository, times(1)).save(testUser);
    }
}