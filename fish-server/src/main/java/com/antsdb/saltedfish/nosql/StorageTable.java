/*-------------------------------------------------------------------------------------------------
 _______ __   _ _______ _______ ______  ______
 |_____| | \  |    |    |______ |     \ |_____]
 |     | |  \_|    |    ______| |_____/ |_____]

 Copyright (c) 2016, antsdb.com and/or its affiliates. All rights reserved. *-xguo0<@

 This program is free software: you can redistribute it and/or modify it under the terms of the
 GNU Affero General Public License, version 3, as published by the Free Software Foundation.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
-------------------------------------------------------------------------------------------------*/
package com.antsdb.saltedfish.nosql;

/**
 * 
 * @author *-xguo0<@
 */
public interface StorageTable {
    public long get(long pKey);
    public boolean exist(long pKey);
    public long getIndex(long pKey);
    public ScanResult scan(
            long pKeyStart, 
            boolean includeStart, 
            long pKeyEnd, 
            boolean includeEnd,
            boolean isAscending);
    public void delete(long pKey);
    public void putIndex(long pIndexKey, long pRowKey, byte misc);
    public void put(Row row);
    public String getLocation(long pKey);
}