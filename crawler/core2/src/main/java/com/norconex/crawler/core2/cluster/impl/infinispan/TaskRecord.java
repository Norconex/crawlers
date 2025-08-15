package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * Metadata describing a distributed task execution (generic for all patterns).
 * This is a first-pass minimal record; fields can be extended safely.
 */
@Proto
public class TaskRecord {

    // Public fields directly annotated for Protostream (no getters)
    @ProtoField(number = 1)
    public String id; // unique internal id (scopedName|gen)
    @ProtoField(number = 2)
    public String scopedName; // crawlerId|sessionId|taskName
    @ProtoField(number = 3)
    public TaskType type;
    @ProtoField(number = 4, defaultValue = "0")
    public long generation; // for repeatable executions
    @ProtoField(number = 5)
    public TaskState state; // current state
    @ProtoField(number = 6, defaultValue = "0")
    public long startedAt;
    @ProtoField(number = 7)
    public Long completedAt;
    @ProtoField(number = 8)
    public String ownerNode; // node that started/coordinates
    @ProtoField(number = 9, collectionImplementation = HashSet.class)
    public Set<String> participants; // nodes involved (snapshot)
    @ProtoField(number = 10)
    public String error; // error message/stack summary
    @ProtoField(number = 11, defaultValue = "0")
    public long lastHeartbeat; // last time (ms epoch) of activity
    @ProtoField(number = 12)
    public Map<String, String> metrics; // ad-hoc metrics (stringified)

    @ProtoFactory
    public TaskRecord(
            String id,
            String scopedName,
            TaskType type,
            long generation,
            TaskState state,
            long startedAt,
            Long completedAt,
            String ownerNode,
            Set<String> participants,
            String error,
            long lastHeartbeat,
            Map<String, String> metrics) {
        this.id = id;
        this.scopedName = scopedName;
        this.type = type;
        this.generation = generation;
        this.state = state;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.ownerNode = ownerNode;
        this.participants =
                participants == null ? new HashSet<>() : participants;
        this.error = error;
        this.lastHeartbeat = lastHeartbeat;
        this.metrics = metrics;
    }

    public static TaskRecord create(String scopedName, TaskType type,
            long generation, String ownerNode, Set<String> participants,
            Map<String, String> metrics) {
        return new TaskRecord(scopedName + "|" + generation, scopedName, type,
                generation, TaskState.RUNNING,
                System.currentTimeMillis(), null, ownerNode, participants, null,
                System.currentTimeMillis(), metrics);
    }

    // Helper mutator-like methods returning new instance (immutability pattern retained)
    public TaskRecord withState(TaskState newState, String errorMsg) {
        return new TaskRecord(id, scopedName, type, generation, newState,
                startedAt,
                (newState == TaskState.COMPLETED || newState == TaskState.FAILED
                        || newState == TaskState.STOPPED)
                                ? System.currentTimeMillis()
                                : (completedAt == null ? 0L : completedAt),
                ownerNode, participants, errorMsg, System.currentTimeMillis(),
                metrics);
    }

    public TaskRecord withHeartbeat() {
        return new TaskRecord(id, scopedName, type, generation, state,
                startedAt, completedAt, ownerNode, participants, error,
                System.currentTimeMillis(), metrics);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScopedName() {
        return scopedName;
    }

    public void setScopedName(String scopedName) {
        this.scopedName = scopedName;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public long getGeneration() {
        return generation;
    }

    public void setGeneration(long generation) {
        this.generation = generation;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }

    public String getOwnerNode() {
        return ownerNode;
    }

    public void setOwnerNode(String ownerNode) {
        this.ownerNode = ownerNode;
    }

    public Set<String> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<String> participants) {
        this.participants = participants;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Map<String, String> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, String> metrics) {
        this.metrics = metrics;
    }

}
