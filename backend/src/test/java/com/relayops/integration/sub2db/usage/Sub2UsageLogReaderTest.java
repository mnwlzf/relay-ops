package com.relayops.integration.sub2db.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.relayops.integration.sub2db.Sub2ReadOnlyJdbc;
import com.relayops.integration.sub2db.Sub2ReadOnlyUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class Sub2UsageLogReaderTest {

    private final Sub2ReadOnlyJdbc jdbc = mock(Sub2ReadOnlyJdbc.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Sub2UsageLogReader reader = new Sub2UsageLogReader(jdbc);

    @BeforeEach
    void setUp() {
        when(jdbc.template()).thenReturn(jdbcTemplate);
    }

    @Test
    void returnsCurrentMaxIdAndMarksDataSourceAvailable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        assertThat(reader.currentMaxId()).isEqualTo(42L);

        verify(jdbc).markAvailable();
    }

    @Test
    void returnsZeroWhenAggregateReturnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThat(reader.currentMaxId()).isZero();

        verify(jdbc).markAvailable();
    }

    @Test
    void marksDataSourceUnavailableWhenQueryFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(reader::currentMaxId)
                .isInstanceOf(Sub2ReadOnlyUnavailableException.class)
                .hasMessageContaining("usage_logs")
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);

        verify(jdbc).markUnavailable(null);
    }

    @Test
    void propagatesUnavailableStateWithoutMarkingAnotherFailure() {
        Sub2ReadOnlyUnavailableException unavailable =
                new Sub2ReadOnlyUnavailableException("data source disabled");
        when(jdbc.template()).thenThrow(unavailable);

        assertThatThrownBy(reader::currentMaxId).isSameAs(unavailable);

        verify(jdbc, never()).markUnavailable(any());
    }

    @Test
    void reportsAvailabilityOnlyWhenConfiguredAndNotCoolingDown() {
        when(jdbc.isConfigured()).thenReturn(true);
        when(jdbc.isInCooldown()).thenReturn(false);
        assertThat(reader.isAvailable()).isTrue();

        when(jdbc.isInCooldown()).thenReturn(true);
        assertThat(reader.isAvailable()).isFalse();

        when(jdbc.isConfigured()).thenReturn(false);
        when(jdbc.isInCooldown()).thenReturn(false);
        assertThat(reader.isAvailable()).isFalse();
    }
}