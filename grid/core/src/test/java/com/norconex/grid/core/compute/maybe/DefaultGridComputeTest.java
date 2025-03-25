package com.norconex.grid.core.compute.maybe;

class DefaultGridComputeTest {

    //    private Grid mockGrid;
    //    private DefaultGridCompute compute;
    //
    //    @BeforeEach
    //    void setUp() {
    //        mockGrid = mock(Grid.class);
    //        compute = new DefaultGridCompute(mockGrid);
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    @Test
    //    void runOnOne_delegatesToRunOnOne() throws Exception {
    //        try (MockedConstruction<RunOnOne> mocked =
    //                Mockito.mockConstruction(RunOnOne.class,
    //                        (mock, context) -> when(mock.execute(any(), any()))
    //                                .thenReturn(mock(Future.class)))) {
    //
    //            Future<?> future = compute.runOnOne("job1", mock(Callable.class));
    //
    //            assertThat(future).isNotNull();
    //            assertThat(mocked.constructed()).hasSize(1);
    //        }
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    @Test
    //    void runOnOneOnce_delegatesToRunOnOne() throws Exception {
    //        try (MockedConstruction<RunOnOne> mocked =
    //                Mockito.mockConstruction(RunOnOne.class,
    //                        (mock, context) -> when(mock.execute(any(), any()))
    //                                .thenReturn(mock(Future.class)))) {
    //
    //            Future<?> future =
    //                    compute.runOnOneOnce("job1", mock(Callable.class));
    //
    //            assertThat(future).isNotNull();
    //            assertThat(mocked.constructed()).hasSize(1);
    //        }
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    @Test
    //    void runOnAll_delegatesToRunOnAll() throws Exception {
    //        try (MockedConstruction<RunOnAll> mocked =
    //                Mockito.mockConstruction(RunOnAll.class,
    //                        (mock, context) -> when(mock.execute(any(), any()))
    //                                .thenReturn(mock(Future.class)))) {
    //
    //            Future<?> future = compute.runOnAll("job1", mock(Callable.class));
    //
    //            assertThat(future).isNotNull();
    //            assertThat(mocked.constructed()).hasSize(1);
    //        }
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    @Test
    //    void runOnAllOnce_delegatesToRunOnAll() throws Exception {
    //        try (MockedConstruction<RunOnAll> mocked =
    //                Mockito.mockConstruction(RunOnAll.class,
    //                        (mock, context) -> when(mock.execute(any(), any()))
    //                                .thenReturn(mock(Future.class)))) {
    //
    //            Future<?> future =
    //                    compute.runOnAllOnce("job1", mock(Callable.class));
    //
    //            assertThat(future).isNotNull();
    //            assertThat(mocked.constructed()).hasSize(1);
    //        }
    //    }
    //
    //    @SuppressWarnings("unchecked")
    //    @Test
    //    void runOnAllSynchronized_delegatesToRunSynchronized() throws Exception {
    //        try (MockedConstruction<RunSynchronized> mocked =
    //                Mockito.mockConstruction(RunSynchronized.class,
    //                        (mock, context) -> when(mock.execute(any(), any()))
    //                                .thenReturn(mock(Future.class)))) {
    //
    //            Future<?> future =
    //                    compute.runOnAllSynchronized("job1", mock(Callable.class));
    //
    //            assertThat(future).isNotNull();
    //            assertThat(mocked.constructed()).hasSize(1);
    //        }
    //    }
}
