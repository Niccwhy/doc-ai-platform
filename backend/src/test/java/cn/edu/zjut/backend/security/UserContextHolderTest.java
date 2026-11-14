package cn.edu.zjut.backend.security;

import cn.edu.zjut.backend.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void shouldStoreAndClearContext() {
        UserContext context = new UserContext(1L, 2L, "alice", "ADMIN");

        UserContextHolder.set(context);
        assertThat(UserContextHolder.get()).isEqualTo(context);

        UserContextHolder.clear();
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    void shouldThrowUnauthorizedWhenContextMissing() {
        BizException exception = catchThrowableOfType(UserContextHolder::getRequired, BizException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(401);
    }

    @Test
    void shouldNotLeakContextToOtherThreads() {
        UserContextHolder.set(new UserContext(1L, 2L, "alice", "ADMIN"));

        UserContext otherThreadContext = CompletableFuture.supplyAsync(UserContextHolder::get).join();

        assertThat(otherThreadContext).isNull();
    }
}
