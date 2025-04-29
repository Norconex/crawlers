/* Copyright 2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.grid.core.util;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

/**
 * Configuration for a reliable task notifier that periodically sends
 * updates until the coordinator confirms acknowledgment.
 */
@Builder
@Getter
public class NotifierConfig {

    public static final int UNLIMITED_ATTEMPTS = -1;
    public static final Duration DEFAULT_MAX_INTERVAL = Duration.ofSeconds(5);

    /**
     * The result object containing the current node state.
     * This is optional — only needed if used inside callbacks.
     */
    public final Object result;

    /**
     * A supplier that returns true when the result has entered a terminal
     * state (e.g., COMPLETE or FAILED).
     */
    public final BooleanSupplier isDone;

    /**
     * A supplier that determines whether an update should be sent at the
     * current time.
     */
    public final BooleanSupplier shouldNotify;

    /**
     * The action to perform to send the update to the coordinator.
     */
    public final BooleanSupplier notify;

    //    /**
    //     * The timeout to apply when waiting for acknowledgment from the
    //     * coordinator. This should be used inside tryConfirmAck if time-bounded
    //     * waiting is required.
    //     */
    //    public final Duration ackTimeout;

    /**
     * A callback that is invoked when the coordinator acknowledges receipt of
     * the final state. This will be called exactly once upon successful
     * confirmation.
     */
    public final Runnable onSuccess;

    /**
     * A callback that is invoked if acknowledgment is not received after
     * {@code maxAttempts}. If {@code maxAttempts} is set to unlimited
     * (i.e., -1), this will never be called.
     */
    public final Runnable onFail;

    /**
     * The maximum number of attempts to confirm acknowledgment from the
     * coordinator after the result is done.
     * If set to {@code -1}, the notifier will retry indefinitely until
     * confirmation is received.
     * A value of {@code 0} disables all attempts (not recommended).
     */
    @Default
    public final int maxAttempts = UNLIMITED_ATTEMPTS;

    /**
     * <p>
     * Maximum interval duration between each attempt to send the
     * notification (or task result update) and possibly check for
     * acknowledgment, even when the {@link #shouldNotify} retuns false, to
     * also act as a heart beat.
     * </p>
     * <p>When done, the task will be executed periodically at this interval,
     * with each execution checking for acknowledgment from the coordinator.
     * If the acknowledgment is not received, the task will continue to
     * retry until the maximum number of attempts is reached or the
     * acknowledgment is confirmed.</p>
     *
     * <p>Note that a shorter interval can result in more frequent retries,
     * while a longer interval can reduce the load on the coordinator but
     * increase the time before success or failure is detected.</p>
     *
     * <p>Default maximum value is 5 seconds.</p>
     */
    @Default
    public final Duration maxNotifyInterval = DEFAULT_MAX_INTERVAL;
}
