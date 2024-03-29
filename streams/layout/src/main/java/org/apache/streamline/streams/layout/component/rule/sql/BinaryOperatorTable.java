/*
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.streamline.streams.layout.component.rule.sql;

import com.google.common.collect.ImmutableMap;
import org.apache.streamline.streams.layout.component.rule.expression.Operator;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

public class BinaryOperatorTable {
    private final static ImmutableMap<SqlBinaryOperator, Operator> operatorTable;

    static  {
        operatorTable = buildTable();
    }

    public static Operator getOperator(SqlBinaryOperator sqlBinaryOperator) {
        Operator operator = operatorTable.get(sqlBinaryOperator);
        if (operator == null) {
            throw new UnsupportedOperationException("Operator " + sqlBinaryOperator.getName() + " is not supported");
        }
        return operator;
    }

    private static ImmutableMap<SqlBinaryOperator, Operator> buildTable() {
        return ImmutableMap.<SqlBinaryOperator, Operator>builder()
        .put(SqlStdOperatorTable.GREATER_THAN, Operator.GREATER_THAN)
        .put(SqlStdOperatorTable.LESS_THAN, Operator.LESS_THAN)
        .put(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, Operator.GREATER_THAN_EQUALS_TO)
        .put(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, Operator.LESS_THAN_EQUALS_TO)
        .put(SqlStdOperatorTable.EQUALS, Operator.EQUALS)
        .put(SqlStdOperatorTable.NOT_EQUALS, Operator.NOT_EQUAL)
        .put(SqlStdOperatorTable.AND, Operator.AND)
        .put(SqlStdOperatorTable.OR, Operator.OR)
        .build();
    }
}
