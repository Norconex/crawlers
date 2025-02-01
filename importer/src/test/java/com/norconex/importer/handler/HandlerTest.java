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
package com.norconex.importer.handler;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.handler.condition.Condition.AllOf;
import com.norconex.importer.handler.condition.ConditionalDocHandler.If;
import com.norconex.importer.handler.condition.ConditionalDocHandler.IfNot;
import com.norconex.importer.handler.condition.impl.DateCondition;
import com.norconex.importer.handler.condition.impl.DomCondition;
import com.norconex.importer.handler.condition.impl.NumericCondition;
import com.norconex.importer.mock.MockDocHandler;

public class HandlerTest { //NOSONAR

    @Test
    void test() {
        // @formatter:off
        var handler =
            new If(new DateCondition())
                .setThenHandlers(List.of(
                    new MockDocHandler().setMessage("yo!"),
                    new MockDocHandler().setMessage("yeah!")))
                .setElseHandlers(List.of(
                    new MockDocHandler().setMessage("else!"),
                    new IfNot(new AllOf(List.of(
                            new DomCondition(),
                            new NumericCondition())))
                        .setThenHandlers(List.of(
                            new MockDocHandler().setMessage("potato"),
                            Configurable.configure(
                                    new Reject(), cfg -> cfg.setMessage(
                                            "Don't like it"))))));
        // @formatter:on

        var cfg = new ImporterConfig();
        cfg.setHandlers(
                List.of((DocHandler) handler,
                        new MockDocHandler().setMessage("yep")));

        System.out.println("CFG: " + cfg);
        BeanMapper.DEFAULT.assertWriteRead(cfg, Format.JSON);
        BeanMapper.DEFAULT.assertWriteRead(cfg, Format.YAML);
        BeanMapper.DEFAULT.assertWriteRead(cfg, Format.XML);
    }
}
