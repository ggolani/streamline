/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.streamline.storage.impl.jdbc.provider.mysql.query;

import org.apache.streamline.storage.Storable;
import org.apache.streamline.storage.impl.jdbc.provider.sql.query.AbstractStorableSqlQuery;

public class MySqlInsertUpdateDuplicate extends AbstractStorableSqlQuery {

    public MySqlInsertUpdateDuplicate(Storable storable) {
        super(storable);
    }

    // the factor of 2 comes from the fact that each column is referred twice in the MySql query as follows
    // "INSERT INTO DB.TABLE (id, name, age) VALUES(1, "A", 19) ON DUPLICATE KEY UPDATE id=1, name="A", age=19";
    @Override
    protected void setParameterizedSql() {
        sql = "INSERT INTO " + tableName + " ("
                + join(getColumnNames(columns, "`%s`"), ", ")
                + ") VALUES(" + getBindVariables("?,", columns.size()) + ")"
                + " ON DUPLICATE KEY UPDATE " + join(getColumnNames(columns, "`%s` = ?"), ", ");
        log.debug(sql);
    }
}
