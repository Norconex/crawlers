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

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Wrapper around a client Ignite instance, abstracting initialization
 * and closing.
 */
@EqualsAndHashCode
@ToString
class IgniteGridInstanceServer implements IgniteGridInstance {

    // have main here or re-use same main, knowing it is a server and not
    // a client?  Passing an extra flag maybe?
    public static void main(String[] args) {
        //        var cfg = new IgniteConfiguration();
        //        // Configure your Ignite settings here...
        //
        //        try (var ignite = Ignition.start(cfg)) {
        //            if (checkAndActivateCluster(ignite)) {
        //                System.out.println("Cluster activated successfully.");
        //            } else {
        //                System.err.println("Failed to activate cluster.");
        //            }
        //        } catch (IgniteException e) {
        //            System.err.println("Ignite failed to start: " + e.getMessage());
        //        }
    }

    @Override
    public Ignite get() {
        return Ignition.localIgnite();
    }
}