package com.norconex.crawler.core2.cluster;

import com.norconex.crawler.core.session.CrawlSession;

/**
 * A continuous task unit of work. The framework invokes {@link #executeOne}
 * repeatedly on participating nodes until stop or completion conditions occur.
 */
public interface ClusterContinuousTask {

    WorkResult executeOne(CrawlSession session);

    default void onStart(CrawlSession session) {
    }

    default void onStop(CrawlSession session) {
    }

    enum Status {
        WORK_DONE, // Performed useful work
        NO_WORK, // No work available this iteration
        FAILED_RETRYABLE, // Transient failure, keep going
        FAILED_FATAL // Fatal failure, stop this worker (others may continue)
    }

    record WorkResult(Status status) {
        public static WorkResult workDone() {
            return new WorkResult(Status.WORK_DONE);
        }

        public static WorkResult noWork() {
            return new WorkResult(Status.NO_WORK);
        }

        public static WorkResult retryableFailure() {
            return new WorkResult(Status.FAILED_RETRYABLE);
        }

        public static WorkResult fatalFailure() {
            return new WorkResult(Status.FAILED_FATAL);
        }
    }
}
