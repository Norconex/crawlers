/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.committer.core;

/**
 * Commits documents to their final destination (e.g. search engine).
 * Implementors are encouraged to use one of the abstract committer classes,
 * which handles event handling and common use cases.
 */
public interface Committer extends AutoCloseable {

    /**
     * Initializes this committer. Invoked once per crawl session.
     * @param committerContext the committer context
     * @throws CommitterException problem with initialization
     */
    void init(CommitterContext committerContext) throws CommitterException;

    /**
     * Gets whether this committer accepts to commit the supplied request.
     * Useful when routing between multiple committers.
     * Invoked for every {@link CommitterRequest}. A request needs to be
     * accepted for {@link #upsert(UpsertRequest)} or
     * {@link #delete(DeleteRequest)} to be invoked.
     * @param request committer request
     * @return <code>true</code> if accepted
     * @throws CommitterException any error when accepting
     */
    boolean accept(CommitterRequest request) throws CommitterException;

    /**
     * Updates or inserts the entity represented by the supplied request
     * into the target of this committer.
     * @param upsertRequest request to update or insert
     * @throws CommitterException could not process upsert request
     */
    void upsert(UpsertRequest upsertRequest) throws CommitterException;

    /**
     * Deletes the entity represented by the supplied request
     * from the target of this committer.
     * @param deleteRequest request to delete
     * @throws CommitterException could not process delete request
     */
    void delete(DeleteRequest deleteRequest) throws CommitterException;

    /**
     * Close or release any open resources used by this committer.
     */
    @Override
    void close() throws CommitterException;

    /**
     * Cleans any persisted information (e.g. queue) to ensure next run will
     * start fresh.
     * Calling this method on Committers not persisting anything between
     * each execution will have no effect. This method does NOT delete anything
     * previously committed on the target repository.
     * @throws CommitterException something went wrong cleaning the committer.
     */
    void clean() throws CommitterException;
}
