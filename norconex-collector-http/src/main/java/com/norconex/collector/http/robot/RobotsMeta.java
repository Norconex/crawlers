/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.robot;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;

public class RobotsMeta {
    private final boolean nofollow;
    private final boolean noindex;
    public RobotsMeta(boolean nofollow, boolean noindex) {
        super();
        this.nofollow = nofollow;
        this.noindex = noindex;
    }
    public boolean isNofollow() {
        return nofollow;
    }
    public boolean isNoindex() {
        return noindex;
    }
    @Override
    public String toString() {
        return "RobotsMeta [nofollow=" + nofollow + ", noindex=" + noindex
                + "]";
    }
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof RobotsMeta)) {
            return false;
        }
        RobotsMeta castOther = (RobotsMeta) other;
        return new EqualsBuilder().append(nofollow, castOther.nofollow)
                .append(noindex, castOther.noindex).isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(nofollow).append(noindex)
                    .toHashCode();
    }
}
