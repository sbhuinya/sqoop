/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.connector.jdbc;

import java.sql.Types;

import junit.framework.TestCase;

import org.apache.sqoop.common.MutableContext;
import org.apache.sqoop.common.MutableMapContext;
import org.apache.sqoop.connector.jdbc.configuration.LinkConfiguration;
import org.apache.sqoop.connector.jdbc.configuration.FromJobConfiguration;
import org.apache.sqoop.job.Constants;
import org.apache.sqoop.job.etl.Initializer;
import org.apache.sqoop.job.etl.InitializerContext;
import org.apache.sqoop.schema.Schema;
import org.apache.sqoop.schema.type.FixedPoint;
import org.apache.sqoop.schema.type.FloatingPoint;
import org.apache.sqoop.schema.type.Text;

public class TestFromInitializer extends TestCase {

  private final String schemaName;
  private final String tableName;
  private final String schemalessTableName;
  private final String tableSql;
  private final String schemalessTableSql;
  private final String tableColumns;

  private GenericJdbcExecutor executor;

  private static final int START = -50;
  private static final int NUMBER_OF_ROWS = 101;

  public TestFromInitializer() {
    schemaName = getClass().getSimpleName().toUpperCase() + "SCHEMA";
    tableName = getClass().getSimpleName().toUpperCase() + "TABLEWITHSCHEMA";
    schemalessTableName = getClass().getSimpleName().toUpperCase() + "TABLE";
    tableSql = "SELECT * FROM " + schemaName + "." + tableName + " WHERE ${CONDITIONS}";
    schemalessTableSql = "SELECT * FROM " + schemalessTableName + " WHERE ${CONDITIONS}";
    tableColumns = "ICOL,VCOL";
  }

  @Override
  public void setUp() {
    executor = new GenericJdbcExecutor(GenericJdbcTestConstants.DRIVER,
        GenericJdbcTestConstants.URL, null, null);

    String fullTableName = executor.delimitIdentifier(schemaName) + "." + executor.delimitIdentifier(tableName);
    if (!executor.existTable(tableName)) {
      executor.executeUpdate("CREATE SCHEMA " + executor.delimitIdentifier(schemaName));
      executor.executeUpdate("CREATE TABLE "
          + fullTableName
          + "(ICOL INTEGER PRIMARY KEY, DCOL DOUBLE, VCOL VARCHAR(20))");

      for (int i = 0; i < NUMBER_OF_ROWS; i++) {
        int value = START + i;
        String sql = "INSERT INTO " + fullTableName
            + " VALUES(" + value + ", " + value + ", '" + value + "')";
        executor.executeUpdate(sql);
      }
    }

    fullTableName = executor.delimitIdentifier(schemalessTableName);
    if (!executor.existTable(schemalessTableName)) {
      executor.executeUpdate("CREATE TABLE "
          + fullTableName
          + "(ICOL INTEGER PRIMARY KEY, DCOL DOUBLE, VCOL VARCHAR(20))");

      for (int i = 0; i < NUMBER_OF_ROWS; i++) {
        int value = START + i;
        String sql = "INSERT INTO " + fullTableName
            + " VALUES(" + value + ", " + value + ", '" + value + "')";
        executor.executeUpdate(sql);
      }
    }
  }

  /**
   * Return Schema representation for the testing table.
   *
   * @param name Name that should be used for the generated schema.
   * @return
   */
  public Schema getSchema(String name) {
    return new Schema(name)
      .addColumn(new FixedPoint("ICOL"))
      .addColumn(new FloatingPoint("DCOL"))
      .addColumn(new Text("VCOL"))
    ;
  }

  @Override
  public void tearDown() {
    executor.close();
  }

  @SuppressWarnings("unchecked")
  public void testTableName() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.tableName = schemalessTableName;

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT * FROM " + executor.delimitIdentifier(schemalessTableName)
            + " WHERE ${CONDITIONS}",
        "ICOL,DCOL,VCOL",
        "ICOL",
        String.valueOf(Types.INTEGER),
        String.valueOf(START),
        String.valueOf(START+NUMBER_OF_ROWS-1));
  }

  @SuppressWarnings("unchecked")
  public void testTableNameWithTableColumns() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.tableName = schemalessTableName;
    jobConf.fromJobConfig.columns = tableColumns;

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT ICOL,VCOL FROM " + executor.delimitIdentifier(schemalessTableName)
            + " WHERE ${CONDITIONS}",
        tableColumns,
        "ICOL",
        String.valueOf(Types.INTEGER),
        String.valueOf(START),
        String.valueOf(START+NUMBER_OF_ROWS-1));
  }

  @SuppressWarnings("unchecked")
  public void testTableSql() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.sql = schemalessTableSql;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT * FROM " + executor.delimitIdentifier(schemalessTableName)
            + " WHERE ${CONDITIONS}",
        "ICOL,DCOL,VCOL",
        "DCOL",
        String.valueOf(Types.DOUBLE),
        String.valueOf((double)START),
        String.valueOf((double)(START+NUMBER_OF_ROWS-1)));
  }

  @SuppressWarnings("unchecked")
  public void testTableSqlWithTableColumns() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.sql = schemalessTableSql;
    jobConf.fromJobConfig.columns = tableColumns;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT SQOOP_SUBQUERY_ALIAS.ICOL,SQOOP_SUBQUERY_ALIAS.VCOL FROM "
            + "(SELECT * FROM " + executor.delimitIdentifier(schemalessTableName)
            + " WHERE ${CONDITIONS}) SQOOP_SUBQUERY_ALIAS",
        tableColumns,
        "DCOL",
        String.valueOf(Types.DOUBLE),
        String.valueOf((double)START),
        String.valueOf((double)(START+NUMBER_OF_ROWS-1)));
  }

  @SuppressWarnings("unchecked")
  public void testTableNameWithSchema() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    String fullTableName = executor.delimitIdentifier(schemaName) + "." + executor.delimitIdentifier(tableName);

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.tableName = tableName;

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT * FROM " + fullTableName
            + " WHERE ${CONDITIONS}",
        "ICOL,DCOL,VCOL",
        "ICOL",
        String.valueOf(Types.INTEGER),
        String.valueOf(START),
        String.valueOf(START+NUMBER_OF_ROWS-1));
  }

  @SuppressWarnings("unchecked")
  public void testTableNameWithTableColumnsWithSchema() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    String fullTableName = executor.delimitIdentifier(schemaName) + "." + executor.delimitIdentifier(tableName);

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.tableName = tableName;
    jobConf.fromJobConfig.columns = tableColumns;

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT ICOL,VCOL FROM " + fullTableName
            + " WHERE ${CONDITIONS}",
        tableColumns,
        "ICOL",
        String.valueOf(Types.INTEGER),
        String.valueOf(START),
        String.valueOf(START+NUMBER_OF_ROWS-1));
  }

  @SuppressWarnings("unchecked")
  public void testTableSqlWithSchema() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    String fullTableName = executor.delimitIdentifier(schemaName) + "." + executor.delimitIdentifier(tableName);

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.sql = tableSql;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT * FROM " + fullTableName
            + " WHERE ${CONDITIONS}",
        "ICOL,DCOL,VCOL",
        "DCOL",
        String.valueOf(Types.DOUBLE),
        String.valueOf((double)START),
        String.valueOf((double)(START+NUMBER_OF_ROWS-1)));
  }


  @SuppressWarnings("unchecked")
  public void testGetSchemaForTable() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.tableName = tableName;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);
    Schema schema = initializer.getSchema(initializerContext, connConf, jobConf);
    assertEquals(getSchema(jobConf.fromJobConfig.schemaName + "." + tableName), schema);
  }

  @SuppressWarnings("unchecked")
  public void testGetSchemaForSql() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.sql = tableSql;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);
    Schema schema = initializer.getSchema(initializerContext, connConf, jobConf);
    assertEquals(getSchema("Query"), schema);
  }

  @SuppressWarnings("unchecked")
  public void testTableSqlWithTableColumnsWithSchema() throws Exception {
    LinkConfiguration connConf = new LinkConfiguration();
    FromJobConfiguration jobConf = new FromJobConfiguration();

    String fullTableName = executor.delimitIdentifier(schemaName) + "." + executor.delimitIdentifier(tableName);

    connConf.link.jdbcDriver = GenericJdbcTestConstants.DRIVER;
    connConf.link.connectionString = GenericJdbcTestConstants.URL;
    jobConf.fromJobConfig.schemaName = schemaName;
    jobConf.fromJobConfig.sql = tableSql;
    jobConf.fromJobConfig.columns = tableColumns;
    jobConf.fromJobConfig.partitionColumn = "DCOL";

    MutableContext context = new MutableMapContext();
    InitializerContext initializerContext = new InitializerContext(context);

    @SuppressWarnings("rawtypes")
    Initializer initializer = new GenericJdbcFromInitializer();
    initializer.initialize(initializerContext, connConf, jobConf);

    verifyResult(context,
        "SELECT SQOOP_SUBQUERY_ALIAS.ICOL,SQOOP_SUBQUERY_ALIAS.VCOL FROM "
            + "(SELECT * FROM " + fullTableName
            + " WHERE ${CONDITIONS}) SQOOP_SUBQUERY_ALIAS",
        tableColumns,
        "DCOL",
        String.valueOf(Types.DOUBLE),
        String.valueOf((double)START),
        String.valueOf((double)(START+NUMBER_OF_ROWS-1)));
  }

  private void verifyResult(MutableContext context,
      String dataSql, String fieldNames,
      String partitionColumnName, String partitionColumnType,
      String partitionMinValue, String partitionMaxValue) {
    assertEquals(dataSql, context.getString(
        GenericJdbcConnectorConstants.CONNECTOR_JDBC_FROM_DATA_SQL));
    assertEquals(fieldNames, context.getString(
        Constants.JOB_ETL_FIELD_NAMES));

    assertEquals(partitionColumnName, context.getString(
        GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_COLUMNNAME));
    assertEquals(partitionColumnType, context.getString(
        GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_COLUMNTYPE));
    assertEquals(partitionMinValue, context.getString(
        GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_MINVALUE));
    assertEquals(partitionMaxValue, context.getString(
        GenericJdbcConnectorConstants.CONNECTOR_JDBC_PARTITION_MAXVALUE));
  }
}