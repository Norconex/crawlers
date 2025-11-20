package com.norconex.crawler.core.junit.cluster;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ClusteredExtension.class)
public @interface Clustered {
}
