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
package com.norconex.committer.sql;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterException;

class SqlClientTest {

    @Test
    void testNoDriverThrowingException() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            new SqlClient(new SqlCommitterConfig());
        });
    }

    @Test
    void testWrongDriverPathThrowingException() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            new SqlClient(new SqlCommitterConfig()
                    .setDriverClass("blah")
                    .setConnectionUrl("blah")
                    .setTableName("blah")
                    .setPrimaryKey("blah")
                    .setDriverPath("abcdef://i.am.bad"));
        });
    }

}
