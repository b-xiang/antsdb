/*-------------------------------------------------------------------------------------------------
 _______ __   _ _______ _______ ______  ______
 |_____| | \  |    |    |______ |     \ |_____]
 |     | |  \_|    |    ______| |_____/ |_____]

 Copyright (c) 2016, antsdb.com and/or its affiliates. All rights reserved. *-xguo0<@

 This program is free software: you can redistribute it and/or modify it under the terms of the
 GNU GNU Lesser General Public License, version 3, as published by the Free Software Foundation.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
-------------------------------------------------------------------------------------------------*/
package com.antsdb.saltedfish.nosql;

import com.antsdb.saltedfish.nosql.Gobbler.CommitEntry;
import com.antsdb.saltedfish.nosql.Gobbler.DdlEntry;
import com.antsdb.saltedfish.nosql.Gobbler.DeleteEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.DeleteRowEntry;
import com.antsdb.saltedfish.nosql.Gobbler.DeleteRowEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.IndexEntry;
import com.antsdb.saltedfish.nosql.Gobbler.IndexEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.InsertEntry;
import com.antsdb.saltedfish.nosql.Gobbler.InsertEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.LogEntry;
import com.antsdb.saltedfish.nosql.Gobbler.MessageEntry;
import com.antsdb.saltedfish.nosql.Gobbler.MessageEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.PutEntry;
import com.antsdb.saltedfish.nosql.Gobbler.PutEntry2;
import com.antsdb.saltedfish.nosql.Gobbler.RollbackEntry;
import com.antsdb.saltedfish.nosql.Gobbler.TimestampEntry;
import com.antsdb.saltedfish.nosql.Gobbler.TransactionWindowEntry;
import com.antsdb.saltedfish.nosql.Gobbler.UpdateEntry;
import com.antsdb.saltedfish.nosql.Gobbler.UpdateEntry2;

/**
 * implemented by caller
 * 
 * @author wgu0
 */
public interface ReplayHandler {
    default public void all(LogEntry entry) throws Exception {
    }
    
    default public void insert(InsertEntry entry) throws Exception {
    }
    
    public void insert(InsertEntry2 entry) throws Exception;
    
    default public void update(UpdateEntry entry) throws Exception {
    }
    
    public void update(UpdateEntry2 entry) throws Exception;
    
    default public void put(PutEntry entry) throws Exception {
    }
    
    public void put(PutEntry2 entry) throws Exception;
    
    default public void index(IndexEntry entry) throws Exception {
    }
    
    public void index(IndexEntry2 entry) throws Exception;
    
    default public void delete(Gobbler.DeleteEntry entry) throws Exception {
    }
    
    public void delete(DeleteEntry2 entry) throws Exception;
    
    default public void commit(CommitEntry entry) throws Exception {
    }
    
    default public void rollback(RollbackEntry entry) throws Exception {
    }
    
    default public void message(MessageEntry entry) throws Exception {
    }

    public void message(MessageEntry2 entry) throws Exception;
    
    default public void transactionWindow(TransactionWindowEntry entry) throws Exception {
    }

    default public void timestamp(TimestampEntry entry) {
    }

    default public void deleteRow(DeleteRowEntry entry) throws Exception {
    }
    
    public void deleteRow(DeleteRowEntry2 entry) throws Exception;

    public void ddl(DdlEntry entry) throws Exception;
}
