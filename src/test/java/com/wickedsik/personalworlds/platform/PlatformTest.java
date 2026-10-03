package com.wickedsik.personalworlds.platform;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for Platform installation.
 * The loader entrypoint must install exactly one platform before common code runs.
 */
class PlatformTest {

    @BeforeEach
    void setUp() {
        PlatformHolder.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        PlatformHolder.resetForTesting();
    }

    @Test
    @DisplayName("get() before install fails with a clear message")
    void get_beforeInstall_throws() {
        IllegalStateException e = assertThrows(IllegalStateException.class, Platform::get);
        assertTrue(e.getMessage().contains("Platform.install()"));
    }

    @Test
    @DisplayName("get() returns the installed platform")
    void get_afterInstall_returnsInstalled() {
        Platform platform = mock(Platform.class);
        Platform.install(platform);

        assertSame(platform, Platform.get());
    }

    @Test
    @DisplayName("Installing a second platform fails")
    void install_twice_throws() {
        Platform.install(mock(Platform.class));

        assertThrows(IllegalStateException.class, () -> Platform.install(mock(Platform.class)));
    }

    @Test
    @DisplayName("Installing null fails")
    void install_null_throws() {
        assertThrows(NullPointerException.class, () -> Platform.install(null));
    }
}
