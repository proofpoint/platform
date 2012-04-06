/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.rack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.testing.Assertions;
import org.apache.bval.jsr303.ApacheValidationProvider;
import org.testng.annotations.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestRackServletConfig
{
    private static final Validator validator = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(RackServletConfig.class)
                .setRackConfigPath("config.ru"));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.of("rackserver.rack-config-path", "rack-configuration.ru");

        RackServletConfig expected = new RackServletConfig().setRackConfigPath("rack-configuration.ru");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidConfig()
    {
        RackServletConfig config = new RackServletConfig().setRackConfigPath(null);
        Assertions.assertEqualsIgnoreOrder(getBeanValidationErrors(config), ImmutableList.of("rackConfigPath may not be null"));

    }

    private static List<String> getBeanValidationErrors(RackServletConfig config)
    {
        Set<ConstraintViolation<RackServletConfig>> violations = validator.validate(config);
        if (violations.isEmpty()) {
            return Collections.emptyList();
        } else {
            ImmutableList.Builder<String> messages = new ImmutableList.Builder<String>();
            for (ConstraintViolation<?> violation : violations) {
                messages.add(violation.getPropertyPath().toString() + " " + violation.getMessage());
            }

            return messages.build();
        }
    }
}
