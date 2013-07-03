/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.robot;

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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (nofollow ? 1231 : 1237);
        result = prime * result + (noindex ? 1231 : 1237);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RobotsMeta other = (RobotsMeta) obj;
        if (nofollow != other.nofollow)
            return false;
        if (noindex != other.noindex)
            return false;
        return true;
    }
}
