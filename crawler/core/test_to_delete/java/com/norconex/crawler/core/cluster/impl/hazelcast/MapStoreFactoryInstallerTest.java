package com.norconex.crawler.core.cluster.impl.hazelcast;

class MapStoreFactoryInstallerTest {

    //    @Test
    //    void testInstallFactoryReplacesStringClassName() {
    //        Config cfg = new Config();
    //        MapConfig mc = new MapConfig("testMap");
    //        MapStoreConfig msc = new MapStoreConfig();
    //        msc.setEnabled(true);
    //        msc.setClassName(
    //                "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.StringJdbcMapStore");
    //        mc.setMapStoreConfig(msc);
    //        cfg.addMapConfig(mc);
    //
    //        boolean installed =
    //                MapStoreFactoryInstaller.installTypedFactoryIfNeeded(
    //                        cfg, "testMap", java.lang.String.class);
    //
    //        assertThat(installed).isTrue();
    //        MapStoreConfig installedCfg =
    //                cfg.getMapConfig("testMap").getMapStoreConfig();
    //        assertThat(installedCfg.getFactoryImplementation()).isNotNull();
    //        assertThat(installedCfg.getClassName()).isNull();
    //    }
    //
    //    @Test
    //    void testNoopWhenDisabled() {
    //        Config cfg = new Config();
    //        MapConfig mc = new MapConfig("testMap");
    //        MapStoreConfig msc = new MapStoreConfig();
    //        msc.setEnabled(false);
    //        mc.setMapStoreConfig(msc);
    //        cfg.addMapConfig(mc);
    //
    //        boolean installed =
    //                MapStoreFactoryInstaller.installTypedFactoryIfNeeded(
    //                        cfg, "testMap", java.lang.String.class);
    //        assertThat(installed).isFalse();
    //    }
    //
    //    @Test
    //    void testFactoryClassNameReplaced() {
    //        Config cfg = new Config();
    //        MapConfig mc = new MapConfig("testMap");
    //        MapStoreConfig msc = new MapStoreConfig();
    //        msc.setEnabled(true);
    //        msc.setFactoryClassName(
    //                "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcMapStoreFactory");
    //        mc.setMapStoreConfig(msc);
    //        cfg.addMapConfig(mc);
    //
    //        boolean installed =
    //                MapStoreFactoryInstaller.installTypedFactoryIfNeeded(
    //                        cfg, "testMap", java.lang.String.class);
    //
    //        assertThat(installed).isTrue();
    //        MapStoreConfig installedCfg =
    //                cfg.getMapConfig("testMap").getMapStoreConfig();
    //        assertThat(installedCfg.getFactoryImplementation()).isNotNull();
    //        assertThat(installedCfg.getFactoryClassName()).isNull();
    //    }
}
