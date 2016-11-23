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
package com.antsdb.saltedfish.sql.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;

import com.antsdb.saltedfish.nosql.GTable;
import com.antsdb.saltedfish.nosql.Humpback;
import com.antsdb.saltedfish.nosql.HumpbackError;
import com.antsdb.saltedfish.nosql.HumpbackException;
import com.antsdb.saltedfish.nosql.Row;
import com.antsdb.saltedfish.nosql.RowIterator;
import com.antsdb.saltedfish.nosql.SlowRow;
import com.antsdb.saltedfish.sql.ExternalTable;
import com.antsdb.saltedfish.sql.Orca;
import com.antsdb.saltedfish.sql.OrcaException;
import com.antsdb.saltedfish.sql.meta.RuleMeta.Rule;
import com.antsdb.saltedfish.sql.vdm.KeyMaker;
import com.antsdb.saltedfish.sql.vdm.ObjectName;
import com.antsdb.saltedfish.sql.vdm.Transaction;
import com.antsdb.saltedfish.util.UberUtil;

import static com.antsdb.saltedfish.sql.OrcaConstant.*;

/**
 * meta data service
 *  
 * @author *-xguo0<@
 */
public class MetadataService {
    public static final String SYSTABLES = TABLENAME_SYSTABLE;
    public static final String SYSCOLUMNS = TABLENAME_SYSCOLUMN;
    public static final String SYSSEQUENCES = TABLENAME_SYSSEQUENCE;

    static Logger _log = UberUtil.getThisLogger();
    
    Orca orca;
    Map<Integer, TableMeta> cache = new ConcurrentHashMap<>();
	private long version;
    
    public MetadataService(Orca orca) {
        super();
        this.orca = orca;
    }
    
    public TableMeta getTable(Transaction trx, int id) {
        // are we in a transaction? read it from database if it is
        
        long trxid = trx.getTrxId();
        if (trxid != 0) {
            if (trx.isDddl()) {
                return loadTable(trx, id);
            }
        }
        
        // is it in the cache?
        
        TableMeta tableMeta = this.cache.get(id);
        
        // if not load it from storage
        
        synchronized(this) {
            tableMeta = loadTable(trx, id);
            if (tableMeta != null) {
                this.cache.put(tableMeta.getId(), tableMeta);
            }
            return tableMeta;
        }
    }
    
    public TableMeta getTable(Transaction trx, ObjectName tableName) {
        return getTable(trx, tableName.getNamespace(), tableName.getTableName());
    }
    
    public TableMeta getTable(Transaction trx, String ns, String tableName) {
        // check for external tables
        
        ExternalTable etable = this.orca.getExternalTable(ns, tableName);
        if (etable != null) {
            return etable.getMeta();
        }
        
        // are we in a transaction? read it from database if it is
        
        long trxid = trx.getTrxId();
        if (trxid != 0) {
            if (trx.isDddl()) {
                return loadTable(trx, ns, tableName);
            }
        }
        
        // is it in the cache?
        
        for (TableMeta i:this.cache.values()) {
            if (!i.getNamespace().equalsIgnoreCase(ns)) {
                continue;
            }
            if (!i.getTableName().equalsIgnoreCase(tableName)) {
                continue;
            }
            return i;
        }
        
        // if not load it from storage
        
        synchronized(this) {
            TableMeta tableMeta = loadTable(trx, ns, tableName);
            if (tableMeta != null) {
                this.cache.put(tableMeta.getId(), tableMeta);
            }
            return tableMeta;
        }
    }

    private TableMeta loadTable(Transaction trx, int id) {
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSTABLE);
        long pRow = table.get(trx.getTrxId(), trx.getTrxTs(), KeyMaker.make(id));
        if (pRow == 0) {
        	return null;
        }
        SlowRow row = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
        return loadTable(trx, row);
    }
    
    private TableMeta loadTable(Transaction trx, String ns, String tableName) {
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSTABLE);
        for (RowIterator i=table.scan(trx.getTrxId(), trx.getTrxTs()); i.next();) {
            long pRow = i.getRowPointer();
            if (pRow == 0) {
                return null;
            }
            SlowRow row = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
            if (!ns.equalsIgnoreCase((String)row.get(ColumnId.systable_namespace.getId()))) {
                continue;
            }
            if (!tableName.equalsIgnoreCase((String)row.get(ColumnId.systable_table_name.getId()))) {
                continue;
            }
            return loadTable(trx, row);
        }
        return null;
    }
    
    private TableMeta loadTable(Transaction trx, SlowRow row) {
        TableMeta tableMeta = new TableMeta(row);
        loadColumns(trx, tableMeta);
        loadRules(trx, tableMeta);
     
        // create primary key maker
        
        if (tableMeta.pk != null) {
        	tableMeta.keyMaker = new KeyMaker(tableMeta.pk.getColumns(tableMeta), true);
        }
        else {
        	tableMeta.keyMaker = new KeyMaker(Collections.emptyList(), true);
        }
        
        // create index key maker
        
        for (IndexMeta i:tableMeta.indexes) {
        	KeyMaker maker = new KeyMaker(i.getColumns(tableMeta), i.isUnique());
        	i.keyMaker = maker;
        }
        
        // done
        
        return tableMeta;
    }
    
    private void loadRules(Transaction trx, TableMeta tableMeta) {
        int tableId = tableMeta.getId();
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULE);
        if (table == null) {
        	return;
        }
        for (RowIterator i=table.scan(trx.getTrxId(), trx.getTrxTs()); i.next();) {
            long pRow = i.getRowPointer();
            SlowRow row = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
            if (row == null) {
                break;
            }
            if (tableId != (int)row.get(ColumnId.sysrule_table_id.getId())) {
                continue;
            }
            int type = (Integer)row.get(ColumnId.sysrule_rule_type.getId());
            if (type == Rule.PrimaryKey.ordinal()) {
                PrimaryKeyMeta pk = new PrimaryKeyMeta(row);
                loadRuleColumns(trx, pk);
                tableMeta.pk = pk;
            }
            else if (type == Rule.Index.ordinal()) {
                IndexMeta index = new IndexMeta(row);
                loadRuleColumns(trx, index);
                tableMeta.getIndexes().add(index);
            }
            else if (type == Rule.ForeignKey.ordinal()) {
                ForeignKeyMeta fk = new ForeignKeyMeta(row);
                loadRuleColumns(trx, fk);
                tableMeta.getForeignKeys().add(fk);
            }
            else {
                throw new NotImplementedException();
            }
        }
    }

    private void loadRuleColumns(Transaction trx, RuleMeta<?> rule) {
        int ruleId = rule.getId();
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
        for (RowIterator i=table.scan(trx.getTrxId(), trx.getTrxTs()); i.next();) {
            long pRow = i.getRowPointer();
            SlowRow row = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
            if (row == null) {
                break;
            }
            if (ruleId != (int)row.get(ColumnId.sysrulecol_rule_id.getId())) {
                continue;
            }
            rule.ruleColumns.add(new RuleColumnMeta(row));
        }
    }

    private void loadColumns(Transaction trx, TableMeta tableMeta) {
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSCOLUMN);
        List<ColumnMeta> list = new ArrayList<>();
        for (RowIterator ii = table.scan(trx.getTrxId(), trx.getTrxTs()); ii.next();) {
            long pRow = ii.getRowPointer();
            if (pRow == 0) break;
            SlowRow i = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
            ColumnMeta columnMeta = new ColumnMeta(this.orca.getTypeFactory(), i);
            if (!UberUtil.safeEqual(tableMeta.getNamespace(), columnMeta.getNamespace())) {
                continue;
            } 
            if (!UberUtil.safeEqual(tableMeta.getTableName(), columnMeta.getTableName())) {
                continue;
            }
            list.add(columnMeta);
        }
        list.sort((col1, col2)-> {
            float delta = col1.getSequence() - col2.getSequence();
            if (delta > 0) {
                return 1;
            }
            else if (delta < 0) {
                return -1;
            }
            else {
                return 0;
            }
        });
        tableMeta.setColumns(list);
    }
    
    public void addTable(Transaction trx, TableMeta tableMeta) throws HumpbackException {
        long trxid = trx.getGuaranteedTrxId();
        GTable sysTable = getSysTable();
        tableMeta.row.setTrxTimestamp(trxid);
        sysTable.insert(tableMeta.row, 0);

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }

    public GTable getSysTable() {
        Humpback humpback = this.orca.getStroageEngine();
        return humpback.getTable(Orca.SYSNS, TABLEID_SYSTABLE);
    }

    public GTable getSysColumn() {
        Humpback humpback = this.orca.getStroageEngine();
        return humpback.getTable(Orca.SYSNS, TABLEID_SYSCOLUMN);
    }

    GTable getSysRule() {
        Humpback humpback = this.orca.getStroageEngine();
        return humpback.getTable(Orca.SYSNS, TABLEID_SYSRULE);
    }

    GTable getSysRuleColumn() {
        Humpback humpback = this.orca.getStroageEngine();
        return humpback.getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
    }

    public void dropTable(Transaction trx, TableMeta tableMeta) throws HumpbackException {
        long trxid = trx.getGuaranteedTrxId();
        GTable table = getSysTable();
        HumpbackError error = table.delete(trxid, tableMeta.getKey(), 1000);
        if (error != HumpbackError.SUCCESS) {
        	throw new OrcaException(error);
        }
        
        GTable sysColumn = getSysColumn();
        for (ColumnMeta column:tableMeta.columns) {
            sysColumn.delete(trxid, column.getKey(), 1000);
        }
        
        if (tableMeta.getPrimaryKey() != null) {
            deletePrimaryKey(trx, tableMeta.getPrimaryKey());
        }

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }
    
    private void deletePrimaryKey(Transaction trx, PrimaryKeyMeta pk) {
        long trxid = trx.getGuaranteedTrxId();
        GTable ruleTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULE);
        GTable ruleColumnTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
        ruleTable.delete(trxid, pk.row.getKey(), 1000);
        for (RuleColumnMeta i:pk.ruleColumns) {
            ruleColumnTable.delete(trxid, i.getKey(), 1000);
        }
        
        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }

    public void addColumn(Transaction trx, ColumnMeta columnMeta) throws HumpbackException {
        long trxid = trx.getGuaranteedTrxId();
        GTable sysColumn = getSysColumn();
        columnMeta.row.setTrxTimestamp(trxid);
        sysColumn.insert(columnMeta.row, 0);

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }
    
    public void modifyColumn(Transaction trx, ColumnMeta columnMeta) throws HumpbackException {
        long trxid = trx.getGuaranteedTrxId();
        GTable sysColumn = getSysColumn();
        sysColumn.put(trxid, columnMeta.row);

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }
    
	public void deleteColumn(Transaction trx, ColumnMeta column) {
        long trxid = trx.getGuaranteedTrxId();
        GTable sysColumn = getSysColumn();
        sysColumn.delete(trxid, column.row.getKey(), 1000);

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
	}
	
    public List<String> getNamespaces() {
        return this.orca.getStroageEngine().getNamespaces();
    }

    public List<String> getTables(Transaction trx, String ns) {
        List<String> tables = new ArrayList<String>();
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSTABLE);
        for (RowIterator i=table.scan(trx.getTrxId(), trx.getTrxTs()); i.next();) {
            long pRow = i.getRowPointer();
            SlowRow row = SlowRow.fromRowPointer(this.orca.getSpaceManager(), pRow);
            if (row == null) break;
            if (ns.equalsIgnoreCase((String)(row.get(ColumnId.systable_namespace.getId())))) {
                tables.add((String)row.get(ColumnId.systable_table_name.getId()));
            }
        }
        return tables;
    }
    
    public SequenceMeta getSequence(Transaction trx, ObjectName name) {
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSSEQUENCE);
        Row raw = table.getRow(
                  trx.getTrxId(), 
                  trx.getTrxTs(), 
                  KeyMaker.make(name.getNamespace() + "." + name.getTableName()));
        SlowRow row = SlowRow.from(raw);
        if (row == null) {
            return null;
        }
        SequenceMeta sequenceMeta = new SequenceMeta(row);
        return sequenceMeta;
    }
    
    public void addSequence(Transaction trx, SequenceMeta seq) {
        long trxid = trx.getGuaranteedTrxId();
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSSEQUENCE);
        seq.row.setTrxTimestamp(trxid);
        HumpbackError error = table.insert(seq.row, 0);
        if (error != HumpbackError.SUCCESS) {
        	throw new OrcaException(error);
        }

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }
    
    public void dropSequence(Transaction trx, SequenceMeta seq) {
        long trxid = trx.getGuaranteedTrxId();
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSSEQUENCE);
        table.delete(trxid, seq.getKey(), 1000);

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }

    public void updateSequence(long trxid, SequenceMeta seq) {
        GTable table = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSSEQUENCE);
        HumpbackError error = table.update(trxid, seq.row, 0);
        if (error != HumpbackError.SUCCESS) {
        	throw new OrcaException(error);
        }
    }

    public void addRule(Transaction trx, RuleMeta<?> rule) {
        GTable ruleTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULE);
        rule.row.setTrxTimestamp(trx.getGuaranteedTrxId());
        ruleTable.insert(rule.row, 0);
        GTable ruleColumnTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
        for (RuleColumnMeta i:rule.ruleColumns) {
        	i.setTrxTimestamp(trx.getGuaranteedTrxId());
            ruleColumnTable.insert(i.getRow(), 0);
        }

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
    }
    
	public void updateRule(Transaction trx, RuleMeta<?> rule) {
        GTable ruleTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULE);
        rule.row.setTrxTimestamp(trx.getGuaranteedTrxId());
        ruleTable.update(trx.getGuaranteedTrxId(), rule.row, 0);
        GTable ruleColumnTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
        for (RuleColumnMeta i:rule.ruleColumns) {
        	i.setTrxTimestamp(trx.getGuaranteedTrxId());
            ruleColumnTable.update(trx.getGuaranteedTrxId(), i.getRow(), 0);
        }

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
	}

    public synchronized void commit(Transaction trx, long version) {
    	if (version > 0) {
    		this.version = version;
    	}
        Map<Integer, TableMeta> aged = this.cache;
        this.cache = new ConcurrentHashMap<>(); 
        for (TableMeta i:aged.values()) {
            i.isAged = true;
        }
        
        // reset statement cache cuz statement may refer to meta-data and now they are aged.
        
        this.orca.clearStatementCache();
    }

	public void deleteRule(Transaction trx, RuleMeta<?> rule) {
		trx.setDdl(true);
        long trxid = trx.getGuaranteedTrxId();
        GTable sysRule = getSysRule();
        sysRule.delete(trxid, rule.row.getKey(), 0);
        GTable ruleColumnTable = this.orca.getStroageEngine().getTable(Orca.SYSNS, TABLEID_SYSRULECOL);
        for (RuleColumnMeta i:rule.ruleColumns) {
            ruleColumnTable.delete(trx.getGuaranteedTrxId(), i.getKey(), 0);
        }

        // doh, this is a ddl transaction, remember it
        
        trx.setDdl(true);
	}

	public void deleteRuleColumn(Transaction trx, RuleColumnMeta ruleColumn) {
		trx.setDdl(true);
		GTable sysRuleColumn = getSysRuleColumn();
		sysRuleColumn.delete(trx.getGuaranteedTrxId(), ruleColumn.getKey(), 0);
	}

	/**
	 * find a new unique name using the input as prefix
	 * 
	 * @param tableName
	 * @return
	 */
	public ObjectName findUniqueName(Transaction trx, ObjectName tableName) {
		for (;;) {
			if (getTable(trx, tableName) == null) {
				return tableName;
			}
			tableName.table = tableName.table + "_";
		}
	}

	public long getVersion() {
		return this.version;
	}

}