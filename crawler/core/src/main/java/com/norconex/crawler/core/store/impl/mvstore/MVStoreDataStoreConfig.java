/* Copyright 2019-2022 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mvstore;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
* <p>
* MVStore configuration parameters.  For advanced use only.
* Differences from MVStore defaults:
* All data size values are expected to be set in bytes.
* Light compression is enabled by default (compress = 1)
* </p>
* <p>
* For more info:
* </p>
* <ul>
*   <li><a href="http://www.h2database.com/html/mvstore.html">
*       MVStore documentation</a></li>
*   <li><a href="https://javadoc.io/doc/com.h2database/h2-mvstore/latest/">
*       Javadoc</a></li>
* </ul>
* @since 1.10.0
* @author Pascal Essiembre
*/
public class MVStoreDataStoreConfig {

   private Long pageSplitSize;
   private Integer compress = 1;
   private Integer cacheConcurrency;
   private Long cacheSize;
   private Integer autoCompactFillRate;
   private Long autoCommitBufferSize;
   private Long autoCommitDelay;

   /**
    * Get the max memory page size in bytes before splitting it, in bytes.
    * Defaults to 16KB.
    * @return page size
    */
   public Long getPageSplitSize() {
       return pageSplitSize;
   }
   // Default is 4KB for memory, and  16KB for disk, set in bytes.
   public void setPageSplitSize(Long pageSplitSize) {
       this.pageSplitSize = pageSplitSize;
   }

   // Default does not compress
   public Integer getCompress() {
       return compress;
   }
   // Default does not compress
   public void setCompress(Integer compress) {
       this.compress = compress;
   }

   // Default is 16 segments
   public Integer getCacheConcurrency() {
       return cacheConcurrency;
   }
   public void setCacheConcurrency(Integer cacheConcurrency) {
       this.cacheConcurrency = cacheConcurrency;
   }

   // Default is 16 MB, set in MB, set in bytes.
   public Long getCacheSize() {
       return cacheSize;
   }
   public void setCacheSize(Long cacheSize) {
       this.cacheSize = cacheSize;
   }

   // Default is 40 %
   public Integer getAutoCompactFillRate() {
       return autoCompactFillRate;
   }
   public void setAutoCompactFillRate(Integer autoCompactFillRate) {
       this.autoCompactFillRate = autoCompactFillRate;
   }

   // Default is 1024 KB, set in bytes.
   public Long getAutoCommitBufferSize() {
       return autoCommitBufferSize;
   }
   public void setAutoCommitBufferSize(Long autoCommitBufferSize) {
       this.autoCommitBufferSize = autoCommitBufferSize;
   }

   // Default is 1000 ms
   public Long getAutoCommitDelay() {
       return autoCommitDelay;
   }
   public void setAutoCommitDelay(Long autoCommitDelay) {
       this.autoCommitDelay = autoCommitDelay;
   }

   @Override
   public boolean equals(final Object other) {
       return EqualsBuilder.reflectionEquals(this, other);
   }
   @Override
   public int hashCode() {
       return HashCodeBuilder.reflectionHashCode(this);
   }
   @Override
   public String toString() {
       return new ReflectionToStringBuilder(
               this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
   }
}