/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.crawler.core.spoil;

/**
 * Markers indicating what to do with references that were once processed 
 * properly, but failed to get a good processing state a subsequent time around.
 */
public enum SpoiledReferenceStrategy { 
    /**
     * Deleting spoiled references sends them to the Committer
     * for deletions and they are removed from the internal reference cache.
     */
    DELETE,
    /**
     * Gracing spoiled references gives them one chance (and only one) to 
     * recover by not sending a deletion request to the Committer the first 
     * time, but doing so if the reference is still spoiled on the next
     * crawl.
     */
    GRACE_ONCE,
    /**
     * Ignoring spoiled references does not send a deletion request to the 
     * Committer.
     */
    IGNORE
}