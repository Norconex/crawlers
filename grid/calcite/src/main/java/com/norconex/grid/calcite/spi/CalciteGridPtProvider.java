package com.norconex.grid.calcite.spi;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.BasePolymorphicTypeProvider;
import com.norconex.grid.calcite.CalciteGridConnector;
import com.norconex.grid.core.GridConnector;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CalciteGridPtProvider extends BasePolymorphicTypeProvider {

    @Override
    protected void register(Registry r) {
        r.add(GridConnector.class, CalciteGridConnector.class);
    }
}
