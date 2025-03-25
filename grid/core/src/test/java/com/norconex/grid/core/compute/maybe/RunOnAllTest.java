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
package com.norconex.grid.core.compute.maybe;

class RunOnAllTest {

    //    private Grid mockGrid;
    //    private RunOnAll runOnAll;
    //
    //    @BeforeEach
    //    void setUp() {
    //        mockGrid = mock(Grid.class);
    //        runOnAll = new RunOnAll(mockGrid);
    //    }
    //
    //    @Test
    //    void execute_shouldSubmitTaskToAllNodes() throws Exception {
    //        @SuppressWarnings("unchecked")
    //        Callable<String> callable = mock(Callable.class);
    //        when(callable.call()).thenReturn("result");
    //
    //        // Simulate remote execution by returning completed Futures
    //        Future<String> mockFuture = CompletableFuture.completedFuture("result");
    //        when(mockGrid.runOnAllNodes(eq("job42"), any()))
    //                .thenReturn(List.of(mockFuture));
    //
    //        Future<List<String>> resultFuture = runOnAll.execute("job42", callable);
    //        var result = resultFuture.get();
    //
    //        assertThat(result)
    //                .isNotNull()
    //                .containsExactly("result");
    //
    //        verify(mockGrid).runOnAllNodes(eq("job42"), any());
    //    }
}

//TODO maybe create an annotation that will by default mock for this project
// but can be made specific to a grid impl and have grid impl use the
// same tests
