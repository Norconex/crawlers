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
package com.norconex.grid.core.compute;

public abstract class RunOnAllTest { //extends BaseGridTest {

    //    @Test
    //    void runOnAllTest() {
    //        // store 5 items in each of 3 thread, which should total 15 entries in
    //        // map
    //        var set = getGrid().storage().getSet("test", String.class);
    //        ConcurrentUtil.get(ConcurrentUtil.run(threadIndex -> () -> {
    //            new RunOnAll(getGrid(), false).execute("testJob", () -> {
    //                for (var i = 0; i < 5; i++) {
    //                    set.add(threadIndex + "-" + i);
    //                }
    //                return null;
    //            });
    //        }, 3));
    //
    //        assertThat(set.size()).isEqualTo(15);
    //
    //    }

    //
    //    public static Future<Void> on3MockNodes(Runnable runnable) {
    //        var set = getGrid().storage().getSet("test", String.class);
    //        ConcurrentUtil.get(ConcurrentUtil.run(threadIndex -> () -> {
    //
    //    }
}
