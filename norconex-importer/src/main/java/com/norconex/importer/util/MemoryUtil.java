/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.util;

/**
 * Memory-related utility methods.
 * @author Pascal Essiembre
 */
public final class MemoryUtil {

    private MemoryUtil() {
        super();
    }

    /**
     * Attempts to perform garbage collection and gets the JVM free memory.
     * The free memory is calculated by taking the JVM current free memory, plus
     * the difference between the maximum possible JVM memory and the
     * total JVM memory taken so far.
     * @return JVM free memory
     */
    public static long getFreeMemory() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.freeMemory() 
                + (runtime.maxMemory() - runtime.totalMemory());
    }
    
}
