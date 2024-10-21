/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.ToString;

//TODO needed?  DELETE ME
@EqualsAndHashCode
@ToString
public class IgniteGridAttributes extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public static final String KEY_IS_LEADER = "activation.isLeader";
    public static final String KEY_EXPECTED_SERVER_COUNT =
            "activation.expectedServerCount";
    //    public static final String KEY_QUORUM = "activation.quorum";
    //    public static final String KEY_SERVER_TIMEOUT = "activation.serverTimeout";

    public boolean isActivationLeader() {
        return Boolean.TRUE.equals(get(KEY_IS_LEADER));
    }

    public IgniteGridAttributes setActivationLeader(boolean isLeader) {
        put(KEY_IS_LEADER, isLeader);
        return this;
    }

    public int getActivationExpectedServerCount() {
        return (int) getOrDefault(KEY_EXPECTED_SERVER_COUNT, 0);
    }

    public IgniteGridAttributes setActivationExpectedServerCount(int count) {
        put(KEY_EXPECTED_SERVER_COUNT, count);
        return this;
    }

    public IgniteGridAttributes() {
    }

    public IgniteGridAttributes(Map<? extends String, ? extends Object> m) {
        super(m);
    }

    //    public int getActivationQuorum() {
    //        return (int) getOrDefault(KEY_QUORUM, 0);
    //    }
    //
    //    public IgniteGridAttributes setActivationQuorum(int quorum) {
    //        put(KEY_QUORUM, quorum);
    //        return this;
    //    }
    //
    //    public long getActivationServerTimeout() {
    //        return (long) getOrDefault(KEY_SERVER_TIMEOUT, 60_000);
    //    }
    //
    //    public IgniteGridAttributes setActivationServerTimeout(long timeout) {
    //        put(KEY_SERVER_TIMEOUT, timeout);
    //        return this;
    //    }
}
