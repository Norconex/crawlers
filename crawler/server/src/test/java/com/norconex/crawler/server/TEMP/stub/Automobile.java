/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.TEMP.stub;

import com.norconex.commons.lang.config.Configurable;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class Automobile implements Transportation, Configurable<AutomobileConfig> {

//    private String name = "Automobile";
//
//    @Override
//    public String getName() {
//        return name;
//    }

    private final AutomobileConfig configuration = new AutomobileConfig();

    @Override
    @Valid
    public AutomobileConfig getConfiguration() {
        return configuration;
    }

    public void doSomething() {
        System.out.println("Like what?");
    }

}
