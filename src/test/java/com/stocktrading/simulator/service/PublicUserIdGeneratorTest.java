package com.stocktrading.simulator.service;

import com.stocktrading.simulator.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicUserIdGeneratorTest {

    @Test
    void generatesSixDigitIds() {
        UserRepository repo = mock(UserRepository.class);
        PublicUserIdGenerator generator = new PublicUserIdGenerator(repo);
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.next()).isBetween(100_000, 999_999);
        }
    }

    @Test
    void retriesWhenCandidateIsTaken() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByPublicUserId(anyInt())).thenReturn(true, true, false);

        int id = new PublicUserIdGenerator(repo).next();

        assertThat(id).isBetween(100_000, 999_999);
        verify(repo, times(3)).existsByPublicUserId(anyInt());
    }

    @Test
    void givesUpAfterBoundedAttempts() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByPublicUserId(anyInt())).thenReturn(true);

        assertThatThrownBy(() -> new PublicUserIdGenerator(repo).next())
            .isInstanceOf(IllegalStateException.class);
        verify(repo, times(10)).existsByPublicUserId(anyInt());
    }
}
