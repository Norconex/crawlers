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
package com.norconex.crawler.core.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridCompute;

class CrawlSessionManagerTest {

    @Test
    void testUpdateHeartbeatExecutesTask() {
        var mockGrid = mock(Grid.class);
        var mockCompute = mock(GridCompute.class);
        var mockCtx = mock(CrawlContext.class);

        when(mockCtx.getGrid()).thenReturn(mockGrid);
        when(mockCtx.getId()).thenReturn("sessionId");
        when(mockGrid.getCompute()).thenReturn(mockCompute);

        CrawlSessionManager.updateHeartbeat(mockCtx);

        verify(mockCompute).executeTask(any());
    }
}
